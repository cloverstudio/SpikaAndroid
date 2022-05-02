package com.clover.studio.exampleapp.data.daos

import androidx.lifecycle.LiveData
import androidx.room.*
import com.clover.studio.exampleapp.data.models.ChatRoom

@Dao
interface ChatRoomDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chatRoom: ChatRoom): Long

    @Query("SELECT * FROM room")
    fun getRooms(): LiveData<List<ChatRoom>>

    @Query("SELECT * FROM room WHERE id LIKE :roomId LIMIT 1")
    fun getRoomById(roomId: Int): LiveData<ChatRoom>

    @Delete
    suspend fun deleteRoom(chatRoom: ChatRoom)

    @Query("DELETE FROM room")
    suspend fun removeRooms()
}