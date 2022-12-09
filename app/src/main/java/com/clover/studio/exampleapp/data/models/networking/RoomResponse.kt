package com.clover.studio.exampleapp.data.models.networking

import com.clover.studio.exampleapp.data.models.entity.ChatRoom
import com.clover.studio.exampleapp.data.models.entity.User

data class RoomResponse(
    val status: String?,
    val data: RoomData?
)

data class RoomData(
    val list: List<ChatRoom>?,
    val rooms: List<ChatRoom>?,
    val room: ChatRoom?,
    val count: Long?,
    val limit: Long?
)

data class RoomUsers(
    val userId: Int,
    val isAdmin: Boolean?,
    val user: User?
)