package com.clover.studio.exampleapp.data.repositories

import androidx.lifecycle.LiveData
import com.clover.studio.exampleapp.data.AppDatabase
import com.clover.studio.exampleapp.data.daos.*
import com.clover.studio.exampleapp.data.models.entity.Message
import com.clover.studio.exampleapp.data.models.entity.Note
import com.clover.studio.exampleapp.data.models.entity.RoomAndMessageAndRecords
import com.clover.studio.exampleapp.data.models.entity.User
import com.clover.studio.exampleapp.data.models.junction.RoomUser
import com.clover.studio.exampleapp.data.models.junction.RoomWithUsers
import com.clover.studio.exampleapp.data.models.networking.NewNote
import com.clover.studio.exampleapp.data.repositories.data_sources.ChatRemoteDataSource
import com.clover.studio.exampleapp.data.services.ChatService
import com.clover.studio.exampleapp.utils.Const
import com.clover.studio.exampleapp.utils.Tools.getHeaderMap
import com.clover.studio.exampleapp.utils.helpers.Resource
import com.clover.studio.exampleapp.utils.helpers.RestOperations.performRestOperation
import com.clover.studio.exampleapp.utils.helpers.RestOperations.queryDatabase
import com.clover.studio.exampleapp.utils.helpers.RestOperations.queryDatabaseCoreData
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val chatRemoteDataSource: ChatRemoteDataSource,
    private val chatService: ChatService,
    private val roomDao: ChatRoomDao,
    private val messageDao: MessageDao,
    private val userDao: UserDao,
    private val roomUserDao: RoomUserDao,
    private val notesDao: NotesDao,
    private val appDatabase: AppDatabase,
    private val sharedPrefsRepo: SharedPreferencesRepository,
) : ChatRepository {
    override suspend fun sendMessage(jsonObject: JsonObject) {
        val response =
            chatService.sendMessage(getHeaderMap(sharedPrefsRepo.readToken()), jsonObject)
        Timber.d("Response message $response")
        response.data?.message?.let {
            // Fields below should never be null except replyId. If null, there is a backend problem
            val replyId = it.replyId ?: 0L
            messageDao.updateMessage(
                it.id,
                it.fromUserId!!,
                it.totalUserCount!!,
                it.deliveredCount!!,
                it.seenCount!!,
                it.type!!,
                it.body!!,
                it.createdAt!!,
                it.modifiedAt!!,
                it.deleted!!,
                replyId,
                it.localId!!
            )
        }
    }

    override suspend fun storeMessageLocally(message: Message) {
        queryDatabaseCoreData(
            databaseQuery = { messageDao.upsert(message) }
        )
    }

    override suspend fun deleteLocalMessages(messages: List<Message>) {
        if (messages.isNotEmpty()) {
            val messagesIds = mutableListOf<Long>()
            for (message in messages) {
                messagesIds.add(message.id.toLong())
            }
            queryDatabaseCoreData(
                databaseQuery = { messageDao.deleteMessage(messagesIds) }
            )
        }
    }

    override suspend fun deleteLocalMessage(message: Message) {
        queryDatabaseCoreData(
            databaseQuery = { messageDao.delete(message) }
        )
    }

    override suspend fun sendMessagesSeen(roomId: Int) {
        performRestOperation(
            networkCall = { chatRemoteDataSource.sendMessagesSeen(roomId) })
    }

    override suspend fun deleteMessage(messageId: Int, target: String) {
        val response = performRestOperation(
            networkCall = { chatRemoteDataSource.deleteMessage(messageId, target) })

        // Just replace old message with new one. Deleted message just has a body with new text
        if (response.responseData?.data?.message != null) {
            val deletedMessage = response.responseData.data.message
            deletedMessage.type = Const.JsonFields.TEXT_TYPE

            queryDatabaseCoreData(
                databaseQuery = { messageDao.upsert(deletedMessage) }
            )
        }
    }

    override suspend fun editMessage(messageId: Int, jsonObject: JsonObject) {
        val response =
            performRestOperation(
                networkCall = { chatRemoteDataSource.editMessage(messageId, jsonObject) })

        if (response.responseData?.data?.message != null) {
            messageDao.upsert(response.responseData.data.message)
        }
    }

    override suspend fun updatedRoomVisitedTimestamp(visitedTimestamp: Long, roomId: Int) {
        queryDatabaseCoreData(
            databaseQuery = { roomDao.updateRoomVisited(visitedTimestamp, roomId) }
        )
    }

    override suspend fun getRoomWithUsersLiveData(roomId: Int) =
        queryDatabase(
            databaseQuery = { roomDao.getDistinctRoomAndUsers(roomId) }
        )

    override suspend fun getRoomWithUsers(roomId: Int) =
        queryDatabaseCoreData(
            databaseQuery = { roomDao.getRoomAndUsers(roomId) }
        )

    // TODO
    override suspend fun updateRoom(jsonObject: JsonObject, roomId: Int, userId: Int) {
        val response =
            chatService.updateRoom(getHeaderMap(sharedPrefsRepo.readToken()), jsonObject, roomId)

        CoroutineScope(Dispatchers.IO).launch {
            appDatabase.runInTransaction {
                CoroutineScope(Dispatchers.IO).launch {
                    val oldRoom = roomDao.getRoomById(roomId)
                    response.data?.room?.let { roomDao.updateRoomTable(oldRoom, it) }

                    val users: MutableList<User> = ArrayList()
                    val roomUsers: MutableList<RoomUser> = ArrayList()
                    if (response.data?.room != null) {
                        val room = response.data.room

                        // Delete Room User if id has been passed through
                        if (userId != 0) {
                            roomUserDao.delete(RoomUser(roomId, userId, false))
                        }

                        for (user in room.users) {
                            user.user?.let { users.add(it) }
                            roomUsers.add(
                                RoomUser(
                                    room.roomId, user.userId, user.isAdmin
                                )
                            )
                        }
                        userDao.upsert(users)
                        roomUserDao.upsert(roomUsers)
                    }
                }
            }
        }
    }

    override suspend fun getRoomUserById(roomId: Int, userId: Int): Boolean? =
        queryDatabaseCoreData(
            databaseQuery = { roomUserDao.getRoomUserById(roomId, userId).isAdmin }
        ).responseData


    override suspend fun getChatRoomAndMessageAndRecordsById(roomId: Int) =
        queryDatabase(
            databaseQuery = { roomDao.getDistinctChatRoomAndMessageAndRecordsById(roomId) }
        )

    override suspend fun handleRoomMute(roomId: Int, doMute: Boolean) {
        if (doMute) {
            performRestOperation(
                networkCall = { chatRemoteDataSource.muteRoom(roomId) },
                saveCallResult = { roomDao.updateRoomMuted(true, roomId) }
            )
        } else {
            performRestOperation(
                networkCall = { chatRemoteDataSource.unmuteRoom(roomId) },
                saveCallResult = { roomDao.updateRoomMuted(true, roomId) }
            )
        }
    }

    override suspend fun handleRoomPin(roomId: Int, doPin: Boolean) {
        if (doPin) {
            performRestOperation(
                networkCall = { chatRemoteDataSource.pinRoom(roomId) },
                saveCallResult = { roomDao.updateRoomPinned(true, roomId) }
            )
        } else {
            performRestOperation(
                networkCall = { chatRemoteDataSource.unpinRoom(roomId) },
                saveCallResult = { roomDao.updateRoomPinned(true, roomId) }
            )
        }
    }

    // TODO -- here
    override suspend fun getSingleRoomData(roomId: Int): RoomAndMessageAndRecords =
        roomDao.getSingleRoomData(roomId)

    override suspend fun sendReaction(jsonObject: JsonObject) =
        chatService.postReaction(getHeaderMap(sharedPrefsRepo.readToken()), jsonObject)

    override suspend fun getNotes(roomId: Int) {
        val response = chatService.getRoomNotes(getHeaderMap(sharedPrefsRepo.readToken()), roomId)

        response.data.notes?.let { notesDao.upsert(it) }
    }

    override suspend fun getLocalNotes(roomId: Int): LiveData<List<Note>> =
        notesDao.getDistinctNotes(roomId)

    override suspend fun createNewNote(roomId: Int, newNote: NewNote) {
        val response =
            chatService.createNote(getHeaderMap(sharedPrefsRepo.readToken()), roomId, newNote)

        response.data.note?.let { notesDao.upsert(it) }
    }

    override suspend fun updateNote(noteId: Int, newNote: NewNote) {
        val response =
            chatService.updateNote(getHeaderMap(sharedPrefsRepo.readToken()), noteId, newNote)

        response.data.note?.let { notesDao.upsert(it) }
    }

    override suspend fun deleteNote(noteId: Int) {
        val response = chatService.deleteNote(getHeaderMap(sharedPrefsRepo.readToken()), noteId)

        response.data.deleted?.let { if (it) notesDao.deleteNote(noteId) }
    }

    override suspend fun deleteRoom(roomId: Int) {
        val response = chatService.deleteRoom(getHeaderMap(sharedPrefsRepo.readToken()), roomId)
        if (response.data?.room?.deleted == true) {
            roomDao.deleteRoom(roomId)
        }
    }

    override suspend fun leaveRoom(roomId: Int) {
        val response = chatService.leaveRoom(getHeaderMap(sharedPrefsRepo.readToken()), roomId)
        if (Const.JsonFields.SUCCESS == response.status) {
            roomDao.updateRoomExit(roomId, true)
        }
    }

    override suspend fun removeAdmin(roomId: Int, userId: Int) {
        roomUserDao.removeAdmin(roomId, userId)
    }
}

interface ChatRepository {
    // Message calls
    suspend fun sendMessage(jsonObject: JsonObject)
    suspend fun storeMessageLocally(message: Message)
    suspend fun deleteLocalMessages(messages: List<Message>)
    suspend fun deleteLocalMessage(message: Message)
    suspend fun sendMessagesSeen(roomId: Int)
    suspend fun deleteMessage(messageId: Int, target: String)
    suspend fun editMessage(messageId: Int, jsonObject: JsonObject)

    // Room calls
    suspend fun updatedRoomVisitedTimestamp(visitedTimestamp: Long, roomId: Int)
    suspend fun getRoomWithUsersLiveData(roomId: Int): LiveData<Resource<RoomWithUsers>>
    suspend fun getRoomWithUsers(roomId: Int): Resource<RoomWithUsers>
    suspend fun updateRoom(jsonObject: JsonObject, roomId: Int, userId: Int)
    suspend fun getRoomUserById(roomId: Int, userId: Int): Boolean?
    suspend fun getSingleRoomData(roomId: Int): RoomAndMessageAndRecords
    suspend fun getChatRoomAndMessageAndRecordsById(roomId: Int): LiveData<Resource<RoomAndMessageAndRecords>>
    suspend fun handleRoomMute(roomId: Int, doMute: Boolean)
    suspend fun handleRoomPin(roomId: Int, doPin: Boolean)
    suspend fun deleteRoom(roomId: Int)
    suspend fun leaveRoom(roomId: Int)
    suspend fun removeAdmin(roomId: Int, userId: Int)

    // Reaction calls
    suspend fun sendReaction(jsonObject: JsonObject)

    // Notes calls
    suspend fun getNotes(roomId: Int)
    suspend fun getLocalNotes(roomId: Int): LiveData<List<Note>>
    suspend fun createNewNote(roomId: Int, newNote: NewNote)
    suspend fun updateNote(noteId: Int, newNote: NewNote)
    suspend fun deleteNote(noteId: Int)
}