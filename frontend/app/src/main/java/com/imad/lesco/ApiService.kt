package com.imad.lesco

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// ── Auth ──────────────────────────────────────────────────────────────────────

data class RegisterRequest(val name: String, val email: String, val password: String)
data class RegisterResponse(val id: Int, val name: String, val email: String)

// The backend login returns {access_token, token_type, user: {id, name, email, memberships}}
data class UserInfo(
    val id: Int,
    val name: String,
    val email: String,
    val memberships: List<MembershipInfo> = emptyList()
)
data class MembershipInfo(
    @SerializedName("house_id") val houseId: Int,
    val role: String
)
data class LoginResponse(
    val access_token: String,
    val token_type: String,
    val user: UserInfo
)
data class UserProfile(val id: Int, val name: String, val email: String)

// ── House ────────────────────────────────────────────────────────────────────

data class CreateHouseRequest(val name: String)
data class HouseResponse(
    val id: Int,
    val name: String,
    @SerializedName("invite_code") val inviteCode: String
)

// Backend returns {message, house_id} on join
data class JoinHouseRequest(
    @SerializedName("invite_code") val invite_code: String,
    @SerializedName("user_id")     val user_id: Int
)
data class JoinHouseResponse(val message: String, @SerializedName("house_id") val houseId: Int)

data class HouseMemberResponse(
    val id: Int,
    val name: String,
    val email: String,
    val role: String
)


// ── Room ─────────────────────────────────────────────────────────────────────

data class CreateRoomRequest(
    val name: String,
    @SerializedName("room_type") val room_type: String
)
data class RoomResponse(
    val id: Int,
    val name: String,
    @SerializedName("room_type") val roomType: String,
    @SerializedName("house_id")  val houseId: Int
)

// ── Device ───────────────────────────────────────────────────────────────────

data class CreateDeviceRequest(
    val name: String,
    @SerializedName("device_type") val device_type: String
)
data class DeviceResponse(
    val id: Int,
    val name: String,
    @SerializedName("device_type") val deviceType: String,
    @SerializedName("room_id")     val roomId: Int,
    val status: Boolean,
    val value: Int
)

// toggle returns {"status": "success"|"conflict_detected", "new_status": Boolean?}
data class ToggleResponse(
    val status: String,
    @SerializedName("new_status") val newStatus: Boolean?,
    val message: String? = null
)

// ── AI Recommendation ────────────────────────────────────────────────────────
// MUST match backend schemas.RecommendationOut exactly:
// { id, house_id, device_id, content, proposed_value, confidence_score, reason, created_at }

data class RecommendationResponse(
    val id: Int,
    @SerializedName("house_id")         val houseId: Int,
    @SerializedName("device_id")        val deviceId: Int?,
    val content: String,                         // was wrongly named "action" before
    @SerializedName("proposed_value")   val proposedValue: Int,
    @SerializedName("confidence_score") val confidenceScore: Float,
    val reason: String,
    @SerializedName("created_at")       val createdAt: String
)

data class FeedbackRequest(
    @SerializedName("recommendation_id") val recommendation_id: Int,
    @SerializedName("user_id")           val user_id: Int,
    val response: Boolean
)

// ── House Summary ────────────────────────────────────────────────────────────

data class RoomSummaryItem(
    val id: Int,
    val name: String,
    @SerializedName("active_devices_count") val activeDevicesCount: Int,
    @SerializedName("energy_saved_kwh")     val energySavedKwh: Double
)

data class HouseSummaryResponse(
    @SerializedName("house_id")           val houseId: Int,
    @SerializedName("house_name")         val houseName: String,
    @SerializedName("invite_code")        val inviteCode: String,
    @SerializedName("total_energy_saved") val totalEnergySaved: Double,
    val rooms: List<RoomSummaryItem>
)

// ── History ──────────────────────────────────────────────────────────────────

data class HistoryItem(
    val id: Int,
    @SerializedName("device_id")   val deviceId: Int,
    @SerializedName("device_name") val deviceName: String?,
    @SerializedName("room_name")   val roomName: String?,
    @SerializedName("user_id")     val userId: Int?,
    @SerializedName("user_name")   val userName: String?,
    @SerializedName("action_type") val actionType: String,
    @SerializedName("new_value")   val newValue: Int,
    val origin: String,
    val timestamp: String
)

// ── Notification ─────────────────────────────────────────────────────────────

data class NotificationItem(
    val id: Int,
    val message: String,
    @SerializedName("is_read")    val isRead: Boolean,
    @SerializedName("created_at") val createdAt: String
)

data class DeviceData(
    val id: Int,
    val name: String?,
    @SerializedName("device_type") val deviceType: String,
    val status: Boolean,
    val value: Int?,
    @SerializedName("room_id") val roomId: Int
)


// ── Preference ───────────────────────────────────────────────────────────────

data class PreferenceRequest(
    @SerializedName("user_id")  val userId: Int,
    val category: String,
    val value: Int,
    val context: String
)

data class PreferenceResponse(
    val id: Int,
    @SerializedName("user_id")  val userId: Int,
    val category: String,
    val value: Int,
    val context: String
)

// ── Weather ──────────────────────────────────────────────────────────────────

data class WeatherResponse(
    val temperature: Int,
    val condition: String,
    val humidity: Int
)


// ── API Interface ─────────────────────────────────────────────────────────────

interface ApiService {

    // ── Auth ──────────────────────────────────────────────────────────────────
    @POST("users/")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @FormUrlEncoded
    @POST("login")
    suspend fun login(
        @Field("username") email: String,
        @Field("password") password: String
    ): Response<LoginResponse>

    @GET("users/me/")
    suspend fun getMyProfile(
        @Header("Authorization") token: String
    ): Response<UserProfile>

    // ── House ─────────────────────────────────────────────────────────────────
    @POST("houses/")
    suspend fun createHouse(
        @Header("Authorization") token: String,
        @Body request: CreateHouseRequest
    ): Response<HouseResponse>

    @POST("houses/join/")
    suspend fun joinHouse(@Body request: JoinHouseRequest): Response<JoinHouseResponse>

    @GET("houses/{house_id}/members")
    suspend fun getHouseMembers(
        @Header("Authorization") token: String,
        @Path("house_id") houseId: Int
    ): Response<List<HouseMemberResponse>>

    // ── Rooms ─────────────────────────────────────────────────────────────────
    @POST("houses/{house_id}/rooms/")
    suspend fun createRoom(
        @Header("Authorization") token: String,
        @Path("house_id") houseId: Int,
        @Body request: CreateRoomRequest
    ): Response<RoomResponse>

    @GET("houses/{house_id}/rooms/")
    suspend fun getRooms(
        @Header("Authorization") token: String,
        @Path("house_id") houseId: Int
    ): Response<List<RoomResponse>>

    // ── Devices ───────────────────────────────────────────────────────────────
    @POST("rooms/{room_id}/devices/")
    suspend fun addDevice(
        @Header("Authorization") token: String,
        @Path("room_id") roomId: Int,
        @Body request: CreateDeviceRequest
    ): Response<DeviceResponse>

    @GET("rooms/{room_id}/devices/")
    suspend fun getDevices(
        @Header("Authorization") token: String,
        @Path("room_id") roomId: Int
    ): Response<List<DeviceResponse>>

    @POST("devices/{device_id}/toggle")
    suspend fun toggleDevice(
        @Header("Authorization") token: String,
        @Path("device_id") deviceId: Int
    ): Response<ToggleResponse>

    // ── Room Assignment ───────────────────────────────────────────────────────
    @POST("rooms/{room_id}/assign/{user_id}")
    suspend fun assignUserToRoom(
        @Header("Authorization") token: String,
        @Path("room_id") roomId: Int,
        @Path("user_id") userId: Int
    ): Response<Map<String, String>>

    // ── Conflict Resolution ───────────────────────────────────────────────────
    @GET("rooms/{room_id}/apply-logic/{category}")
    suspend fun applyConflictResolution(
        @Header("Authorization") token: String,
        @Path("room_id") roomId: Int,
        @Path("category") category: String
    ): Response<Map<String, Any>>

    // ── AI Recommendation ─────────────────────────────────────────────────────
    @GET("houses/{house_id}/recommendation/")
    suspend fun getRecommendation(
        @Header("Authorization") token: String,
        @Path("house_id") houseId: Int
    ): Response<RecommendationResponse>

    @POST("feedback/")
    suspend fun submitFeedback(
        @Body feedback: FeedbackRequest
    ): Response<Map<String, String>>

    // ── Summary & History ─────────────────────────────────────────────────────
    @GET("houses/{house_id}/summary")
    suspend fun getHouseSummary(
        @Header("Authorization") token: String,
        @Path("house_id") houseId: Int
    ): Response<HouseSummaryResponse>

    @GET("houses/{house_id}/history")
    suspend fun getHouseHistory(
        @Header("Authorization") token: String,
        @Path("house_id") houseId: Int
    ): Response<List<HistoryItem>>

    @GET("houses/{house_id}/devices/")
    suspend fun getHouseDevices(
        @Header("Authorization") token: String,
        @Path("house_id") houseId: Int
    ): Response<List<DeviceData>>

    // ── Notifications ─────────────────────────────────────────────────────────
    @GET("users/{user_id}/notifications")
    suspend fun getNotifications(
        @Header("Authorization") token: String,
        @Path("user_id") userId: Int
    ): Response<List<NotificationItem>>

    @POST("users/{user_id}/notifications/{notif_id}/read")
    suspend fun markNotificationRead(
        @Header("Authorization") token: String,
        @Path("user_id") userId: Int,
        @Path("notif_id") notifId: Int
    ): Response<Map<String, String>>

    // ── Preferences ───────────────────────────────────────────────────────────
    @POST("users/forgot-password")
    suspend fun forgotPassword(@Query("email") email: String): Response<Map<String, String>>

    @POST("users/verify/")
    suspend fun verifyEmail(
        @Query("email") email: String,
        @Query("code") code: String
    ): Response<Map<String, String>>

    @POST("users/reset-password")
    suspend fun resetPassword(
        @Query("email") email: String,
        @Query("code") code: String,
        @Query("new_password") newPassword: String
    ): Response<Map<String, String>>

    @POST("users/preferences/")
    suspend fun setPreference(
        @Header("Authorization") token: String,
        @Body preference: PreferenceRequest
    ): Response<Map<String, String>>

    @GET("users/preferences/")
    suspend fun getPreferences(
        @Header("Authorization") token: String
    ): Response<List<PreferenceResponse>>

    @GET("weather")
    suspend fun getLiveWeather(): Response<WeatherResponse>

    @POST("environment/push")
    suspend fun pushEnvironmentData(
        @Query("house_id") houseId: Int,
        @Query("temp") temp: Int,
        @Query("weather_desc") weatherDesc: String
    ): Response<Map<String, Any>>
}



// ── Retrofit Singleton ────────────────────────────────────────────────────────

object RetrofitInstance {
    // Replace this IP with your PC's local network IP where the backend is running
    private const val BASE_URL = ""

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

