package com.clover.studio.exampleapp.data.models

import androidx.room.*
import com.clover.studio.exampleapp.data.AppDatabase
import com.clover.studio.exampleapp.data.models.networking.RoomUsers
import com.google.gson.annotations.SerializedName

@Entity(tableName = AppDatabase.TablesInfo.TABLE_CHAT_ROOM)
data class ChatRoom(

    @SerializedName("id")
    @ColumnInfo(name = "room_id")
    val roomId: Int,

    @ColumnInfo(name = "name")
    val name: String?,

    @ColumnInfo(name = "users")
    @TypeConverters(TypeConverter::class)
    val users: ArrayList<RoomUsers>? = ArrayList(),

    @ColumnInfo(name = "avatar_url")
    val avatarUrl: String?,

    @ColumnInfo(name = "type")
    val type: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long?
) {
    @Transient
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = AppDatabase.TablesInfo.ID)
    var id: Int = 0
}