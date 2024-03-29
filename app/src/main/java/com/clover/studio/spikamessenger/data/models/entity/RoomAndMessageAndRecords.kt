package com.clover.studio.spikamessenger.data.models.entity

import android.os.Parcelable
import androidx.room.Embedded
import androidx.room.Relation
import com.clover.studio.spikamessenger.data.models.junction.RoomWithUsers
import kotlinx.parcelize.Parcelize

@Parcelize
data class RoomAndMessageAndRecords(
    @Embedded val roomWithUsers: RoomWithUsers,
    @Relation(entity = Message::class, parentColumn = "room_id", entityColumn = "room_id_message")
    val message: List<MessageAndRecords>?,
) : Parcelable