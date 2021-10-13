package com.clover.studio.exampleapp.data.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.clover.studio.exampleapp.data.AppDatabase
import com.google.gson.annotations.SerializedName

@Entity(tableName = AppDatabase.TablesInfo.TABLE_REACTION)
data class Reaction(

    @PrimaryKey
    @ColumnInfo(name = AppDatabase.TablesInfo.ID)
    val id: String,

    @SerializedName("user_id")
    @ColumnInfo(name = "user_id")
    val userId: String,

    @SerializedName("message_id")
    @ColumnInfo(name = "message_id")
    val messageId: String,

    @SerializedName("type")
    @ColumnInfo(name = "type")
    val type: String,

    @SerializedName("created_at")
    @ColumnInfo(name = "created_at")
    val createdAt: String,

    @SerializedName("modified_at")
    @ColumnInfo(name = "modified_at")
    val modifiedAt: String
)
