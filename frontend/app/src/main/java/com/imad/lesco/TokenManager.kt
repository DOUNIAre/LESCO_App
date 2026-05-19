package com.imad.lesco

object TokenManager {
    private var token: String? = null

    fun saveToken(newToken: String) {
        token = newToken
    }

    fun getToken(): String? = token

    fun getAuthHeader(): String = "Bearer ${token ?: ""}"

    fun clearToken() {
        token = null
    }

    fun isLoggedIn(): Boolean = token != null
}