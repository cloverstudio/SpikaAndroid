package com.clover.studio.exampleapp.data.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.clover.studio.exampleapp.data.AppDatabase
import com.clover.studio.exampleapp.data.models.networking.RoomUsers
import com.google.gson.annotations.SerializedName

@Entity(tableName = AppDatabase.TablesInfo.TABLE_CHAT_ROOM)
data class ChatRoom(

    @PrimaryKey
    @SerializedName("id")
    @ColumnInfo(name = "room_id")
    val roomId: Int,

    @ColumnInfo(name = "name")
    val name: String?,

    @ColumnInfo(name = "avatar_url")
    val avatarUrl: String?,

    @ColumnInfo(name = "type")
    val type: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long?,

    @ColumnInfo(name = "modified_at")
    val modifiedAt: Long?,

    @ColumnInfo(name = "visited_room")
    var visitedRoom: Long?
) {
    @Ignore
    val users: List<RoomUsers> = ArrayList()
}