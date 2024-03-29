package com.clover.studio.spikamessenger.data.models.networking.responses

import com.clover.studio.spikamessenger.data.models.entity.Message

data class MessageAttributes(
    val fromUserName: String,
    val groupName: String?
)

data class FirebaseResponse(
    val messageAttributes: MessageAttributes,
    val message: Message,
    val roomAttributes: RoomAttributes
)

data class RoomAttributes(
    val unreadCount: Int,
    val muted: Boolean
)

