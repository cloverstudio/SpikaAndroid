package com.clover.studio.spikamessenger.data.models.networking.responses

import com.clover.studio.spikamessenger.data.models.entity.Message

data class ShareMediaResponse (
    val messages: ArrayList<Message>,
    val newRooms: ArrayList<RoomData>
)