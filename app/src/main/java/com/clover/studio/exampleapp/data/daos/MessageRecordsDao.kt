package com.clover.studio.exampleapp.data.daos

import androidx.lifecycle.LiveData
import androidx.room.*
import com.clover.studio.exampleapp.data.models.entity.MessageRecords

@Dao
interface MessageRecordsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(messageRecords: MessageRecords): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(messageRecords: List<MessageRecords>)

    @Query("SELECT * FROM message_records WHERE message_id LIKE :messageId")
    fun getMessageRecords(messageId: Int): LiveData<List<MessageRecords>>

    @Query("SELECT * FROM message_records")
    suspend fun getMessageRecordsLocally(): List<MessageRecords>

    @Query("SELECT * FROM message_records WHERE id LIKE :messageId LIMIT 1")
    fun getMessageRecordById(messageId: String): LiveData<MessageRecords>

    @Delete
    suspend fun deleteMessageRecord(messageRecords: MessageRecords)

    @Query("DELETE FROM message_records")
    suspend fun removeMessageRecords()

    @Query("SELECT id FROM message_records WHERE message_id = :id AND user_id = :userId LIMIT 1")
    fun getMessageId(id: Int, userId: Int): Int?

    @Query("UPDATE message_records SET type = :type, created_at = :createdAt, reaction = NULL, modified_at = :modifiedAt WHERE message_id = :messageId AND user_id= :userId AND type='seen'")
    suspend fun updateMessageRecords(
        messageId: Int,
        type: String,
        createdAt: Long,
        modifiedAt: Long?,
        userId: Int,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReaction(messageRecords: MessageRecords): Long

    @Query("SELECT id FROM message_records WHERE message_id = :id AND user_id = :userId AND type='reaction'")
    fun getMessageReactionId(id: Int, userId: Int): Int?

    @Query("UPDATE message_records SET reaction = :reaction WHERE message_id = :messageId AND user_id= :userId AND type='reaction'")
    suspend fun updateReaction(
        messageId: Int,
        reaction: String,
        userId: Int,
    )
}