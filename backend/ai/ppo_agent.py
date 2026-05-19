import torch
import torch.nn as nn
import torch.optim as optim
import numpy as np
from .config import PPO_CONFIG, PARAMETER_RANGES


class PPONetwork(nn.Module):
    def __init__(self, obs_dim: int, action_dim: int = 1):
        """
        PPO Network for continuous action output [0, 1].
        action_dim should be 1 for parameter-based recommendations.
        """
        super().__init__()
        self.shared = nn.Sequential(
            nn.Linear(obs_dim, 128),
            nn.Tanh(),
            nn.Linear(128, 128),
            nn.Tanh()
        )
        self.actor_mean = nn.Linear(128, action_dim)
        self.actor_log_std = nn.Parameter(torch.zeros(1, action_dim))
        self.critic = nn.Linear(128, 1)
    
    def forward(self, obs):
        x = self.shared(obs)
        # Sigmoid output ensures [0, 1] range
        action_mean = torch.sigmoid(self.actor_mean(x))
        action_log_std = self.actor_log_std.expand_as(action_mean)
        value = self.critic(x)
        return action_mean, action_log_std, value.squeeze(-1)


class PPOBuffer:
    def __init__(self, gamma: float = 0.99, gae_lambda: float = 0.95):
        self.states = []
        self.actions = []
        self.rewards = []
        self.dones = []
        self.log_probs = []
        self.values = []
        self.gamma = gamma
        self.gae_lambda = gae_lambda
    
    def add(self, state, action, reward, done, log_prob, value):
        self.states.append(state.copy())
        self.actions.append(action)
        self.rewards.append(reward)
        self.dones.append(done)
        self.log_probs.append(log_prob)
        self.values.append(value)
    
    def compute_advantages_and_returns(self, last_value: float = 0):
        advantages = []
        returns = []
        gae = 0
        
        for t in reversed(range(len(self.rewards))):
            if t == len(self.rewards) - 1:
                next_value = last_value
                next_non_terminal = 1.0 - self.dones[t]
            else:
                next_value = self.values[t + 1]
                next_non_terminal = 1.0 - self.dones[t + 1]
            
            delta = self.rewards[t] + self.gamma * next_value * next_non_terminal - self.values[t]
            gae = delta + self.gamma * self.gae_lambda * next_non_terminal * gae
            advantages.insert(0, gae)
            returns.insert(0, gae + self.values[t])
        
        advantages = torch.tensor(advantages, dtype=torch.float32)
        if advantages.std() > 1e-8:
            advantages = (advantages - advantages.mean()) / (advantages.std() + 1e-8)
        
        return advantages, torch.tensor(returns, dtype=torch.float32)
    
    def clear(self):
        self.states.clear()
        self.actions.clear()
        self.rewards.clear()
        self.dones.clear()
        self.log_probs.clear()
        self.values.clear()
    
    def __len__(self):
        return len(self.states)


class PPOAgent:
    def __init__(self, obs_dim: int, action_dim: int = 1, config_override: dict = None):
        """
        Initialize PPO Agent.
        action_dim defaults to 1 for parameter-based single-output recommendations.

        Args:
            obs_dim: Observation space dimensionality.
            action_dim: Action space dimensionality (default 1).
            config_override: Optional dict to override values from PPO_CONFIG.
                             Used by the Bayesian hyperparameter tuner.
        """
        cfg = {**PPO_CONFIG, **(config_override or {})}

        self.obs_dim = obs_dim
        self.action_dim = action_dim
        
        self.network = PPONetwork(obs_dim=obs_dim, action_dim=action_dim)
        self.optimizer = optim.Adam(self.network.parameters(), lr=cfg["lr"])
        # default to hvac gae_lambda for now
        self.buffer = PPOBuffer(gamma=cfg["gamma"], gae_lambda=cfg.get("gae_lambda", cfg.get("gae_lambda_hvac", 0.95)))
        
        self.clip_epsilon = cfg["clip_epsilon"]
        self.value_coef = cfg["value_coef"]
        self.entropy_coef = cfg["entropy_coef"]
        self.max_grad_norm = cfg["max_grad_norm"]
        self.ppo_epochs = cfg["ppo_epochs"]
        self.batch_size = cfg["batch_size"]
        
        self.steps_done = 0
    
    def denormalize(self, normalized_value: float, parameter_type: str) -> int:
        """
        Convert normalized [0, 1] value to actual parameter range.
        
        Args:
            normalized_value: Value in range [0, 1]
            parameter_type: "on_off", "temperature", or "duration"
        
        Returns:
            Actual parameter value (int)
        """
        param_range = PARAMETER_RANGES.get(parameter_type, {"min": 0, "max": 1})
        min_val = param_range["min"]
        max_val = param_range["max"]
        
        # Linear interpolation: [0, 1] -> [min, max]
        actual_value = min_val + (normalized_value * (max_val - min_val))
        return int(round(actual_value))
    
    def propose(self, state: np.ndarray) -> tuple:
        """
        Generate a proposal (normalized_value, confidence, log_prob).
        
        Args:
            state: numpy array of shape (obs_dim,)
        
        Returns:
            Tuple of (normalized_value: float in [0,1], confidence: float, log_prob: float)
        """
        state_t = torch.FloatTensor(state).unsqueeze(0)
        action_mean, action_log_std, value = self.network(state_t)
        
        action_std = torch.exp(action_log_std)
        dist = torch.distributions.Normal(action_mean, action_std)
        
        # For proposal, we use deterministic mean (already normalized to [0, 1])
        normalized_action = action_mean.squeeze(0).detach().numpy()[0]
        
        # Calculate a pseudo-confidence based on std
        confidence = max(0.0, min(1.0, 1.0 - action_std.mean().item()))
        
        log_prob = dist.log_prob(action_mean).sum(dim=-1).item()
        
        return normalized_action, confidence, log_prob
    
    def select_action_for_training(self, state: np.ndarray) -> tuple:
        """
        Stochastic action selection during training.
        
        Args:
            state: numpy array of shape (obs_dim,)
        
        Returns:
            Tuple of (normalized_action: float in [0,1], log_prob: float, value: float)
        """
        state_t = torch.FloatTensor(state).unsqueeze(0)
        action_mean, action_log_std, value = self.network(state_t)
        
        action_std = torch.exp(action_log_std)
        dist = torch.distributions.Normal(action_mean, action_std)
        
        action = dist.sample()
        # Clip action to [0, 1] valid range
        action = torch.clamp(action, 0.0, 1.0)
        
        log_prob = dist.log_prob(action).sum(dim=-1).item()
        self.steps_done += 1
        
        # Return scalar normalized action (extract from tensor)
        normalized_action = action.squeeze(0).detach().numpy()
        if isinstance(normalized_action, np.ndarray):
            normalized_action = normalized_action[0]
        
        return normalized_action, log_prob, value.item()
    
    def update(self, last_value: float = 0, feedback_reward: float = 0) -> dict:
        """
        Update with optional feedback reward from user.
        
        Args:
            last_value: Bootstrap value for last state
            feedback_reward: Additional reward from user feedback
        
        Returns:
            Dict with update metrics or {"update_done": False}
        """
        if len(self.buffer) < self.batch_size:
            return {"update_done": False}
        
        # Add feedback reward to the last transition if provided
        if feedback_reward != 0 and len(self.buffer.rewards) > 0:
            self.buffer.rewards[-1] += feedback_reward
        
        advantages, returns = self.buffer.compute_advantages_and_returns(last_value)
        
        states = torch.FloatTensor(np.array(self.buffer.states))
        actions = torch.FloatTensor(np.array(self.buffer.actions)).unsqueeze(-1)  # Shape: (N, 1)
        old_log_probs = torch.FloatTensor(self.buffer.log_probs)
        
        total_policy_loss = 0
        total_value_loss = 0
        total_entropy = 0
        n_updates = 0
        
        for _ in range(self.ppo_epochs):
            indices = np.random.permutation(len(states))
            
            for start in range(0, len(states), self.batch_size):
                end = min(start + self.batch_size, len(states))
                batch_idx = indices[start:end]
                
                action_mean, action_log_std, values = self.network(states[batch_idx])
                action_std = torch.exp(action_log_std)
                dist = torch.distributions.Normal(action_mean, action_std)
                
                batch_actions = actions[batch_idx]  # Shape: (batch_size, 1)
                
                new_log_probs = dist.log_prob(batch_actions).sum(dim=-1)
                entropy = dist.entropy().sum(dim=-1).mean()
                
                ratio = torch.exp(new_log_probs - old_log_probs[batch_idx])
                clipped_ratio = torch.clamp(ratio, 1 - self.clip_epsilon, 1 + self.clip_epsilon)
                
                policy_loss = -torch.min(
                    ratio * advantages[batch_idx],
                    clipped_ratio * advantages[batch_idx]
                ).mean()
                
                value_loss = nn.MSELoss()(values, returns[batch_idx])
                
                loss = policy_loss + self.value_coef * value_loss - self.entropy_coef * entropy
                
                self.optimizer.zero_grad()
                loss.backward()
                nn.utils.clip_grad_norm_(self.network.parameters(), self.max_grad_norm)
                self.optimizer.step()
                
                total_policy_loss += policy_loss.item()
                total_value_loss += value_loss.item()
                total_entropy += entropy.item()
                n_updates += 1
        
        self.buffer.clear()
        
        return {
            "update_done": True,
            "policy_loss": total_policy_loss / max(1, n_updates),
            "value_loss": total_value_loss / max(1, n_updates),
            "entropy": total_entropy / max(1, n_updates)
        }

    
    def add_transition(self, state, action, reward, done, log_prob, value):
        self.buffer.add(state, action, reward, done, log_prob, value)
    
    def learn_from_batch(self, states: np.ndarray, actions: np.ndarray, epochs: int = 10) -> float:
        """
        Perform supervised learning (Behavioral Cloning) on a batch of data.
        Used for pre-training the agent on historical user data.
        
        Args:
            states: numpy array of shape (N, obs_dim)
            actions: numpy array of shape (N,) containing normalized actions [0, 1]
            epochs: number of training iterations
            
        Returns:
            Average MSE loss across epochs.
        """
        self.network.train()
        states_t = torch.FloatTensor(states)
        actions_t = torch.FloatTensor(actions).unsqueeze(-1)  # Shape (N, 1)
        
        total_loss = 0
        for _ in range(epochs):
            self.optimizer.zero_grad()
            action_mean, _, _ = self.network(states_t)
            loss = nn.MSELoss()(action_mean, actions_t)
            loss.backward()
            nn.utils.clip_grad_norm_(self.network.parameters(), self.max_grad_norm)
            self.optimizer.step()
            total_loss += loss.item()
            
        return total_loss / max(1, epochs)

    def save(self, path: str):
        torch.save({
            'network_state_dict': self.network.state_dict(),
            'optimizer_state_dict': self.optimizer.state_dict(),
            'steps_done': self.steps_done
        }, path)
    
    def load(self, path: str):
        checkpoint = torch.load(path)
        self.network.load_state_dict(checkpoint['network_state_dict'])
        self.optimizer.load_state_dict(checkpoint['optimizer_state_dict'])
        self.steps_done = checkpoint['steps_done']