package com.imad.lesco

object SessionManager {
    var userId: Int = -1
    var houseId: Int = -1
    var role: String = "member"          // "owner" | "member"
    var assignedRoomId: Int = -1         // -1 = pas encore assigné

    fun isOwner() = role == "owner"

    fun clear() {
        userId = -1
        houseId = -1
        role = "member"
        assignedRoomId = -1
    }
}
