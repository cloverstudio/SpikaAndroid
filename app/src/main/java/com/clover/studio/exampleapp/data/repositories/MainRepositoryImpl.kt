package com.clover.studio.exampleapp.data.repositories

import androidx.lifecycle.LiveData
import com.clover.studio.exampleapp.data.daos.ChatRoomDao
import com.clover.studio.exampleapp.data.daos.MessageDao
import com.clover.studio.exampleapp.data.daos.MessageRecordsDao
import com.clover.studio.exampleapp.data.daos.UserDao
import com.clover.studio.exampleapp.data.models.ChatRoom
import com.clover.studio.exampleapp.data.models.RoomAndMessageAndRecords
import com.clover.studio.exampleapp.data.models.User
import com.clover.studio.exampleapp.data.models.UserAndPhoneUser
import com.clover.studio.exampleapp.data.models.junction.RoomUser
import com.clover.studio.exampleapp.data.models.junction.RoomWithUsers
import com.clover.studio.exampleapp.data.models.networking.AuthResponse
import com.clover.studio.exampleapp.data.models.networking.ContactResponse
import com.clover.studio.exampleapp.data.models.networking.FileResponse
import com.clover.studio.exampleapp.data.models.networking.RoomResponse
import com.clover.studio.exampleapp.data.services.RetrofitService
import com.clover.studio.exampleapp.utils.Tools.getHeaderMap
import com.google.gson.JsonObject
import javax.inject.Inject

class MainRepositoryImpl @Inject constructor(
    private val retrofitService: RetrofitService,
    private val userDao: UserDao,
    private val messageDao: MessageDao,
    private val messageRecordsDao: MessageRecordsDao,
    private val chatRoomDao: ChatRoomDao,
    private val sharedPrefs: SharedPreferencesRepository
) : MainRepository {
    override suspend fun getUsers(page: Int): ContactResponse {
        val userData = retrofitService.getUsers(getHeaderMap(sharedPrefs.readToken()), page)

        if (userData.data?.list != null) {
            for (user in userData.data.list) {
                userDao.insert(user)
            }
        }
        return userData
    }

    override suspend fun getUserByID(id: Int) =
        userDao.getUserById(id)

    override suspend fun getUserLiveData(): LiveData<List<User>> =
        userDao.getUsers()

    override suspend fun getRoomById(userId: Int) =
        retrofitService.getRoomById(getHeaderMap(sharedPrefs.readToken()), userId)

    override suspend fun getRooms(page: Int): RoomResponse {
        val roomData = retrofitService.getRooms(getHeaderMap(sharedPrefs.readToken()), page)

        if (roomData.data?.list != null) {
            for (room in roomData.data.list) {
                val oldData = chatRoomDao.getRoomById(room.roomId)
                chatRoomDao.updateRoomTable(oldData, room)

                for (user in room.users) {
                    user.user?.let { userDao.insert(it) }
                    chatRoomDao.insertRoomWithUsers(
                        RoomUser(
                            room.roomId,
                            user.userId,
                            user.isAdmin
                        )
                    )
                }
            }
        }
        return roomData
    }

    override suspend fun getMessages() {
        val roomIds: MutableList<Int> = ArrayList()
        chatRoomDao.getRoomsLocally().forEach { roomIds.add(it.roomId) }

        if (roomIds.isNotEmpty()) {
            for (id in roomIds) {
                val messageData = retrofitService.getMessages(
                    getHeaderMap(sharedPrefs.readToken()),
                    id.toString()
                )

                if (messageData.data?.list != null) {
                    for (message in messageData.data.list) {
                        messageDao.insert(message)
                    }
                }
            }
        }
    }

    override suspend fun getRoomsLiveData(): LiveData<List<ChatRoom>> =
        chatRoomDao.getRooms()

    override suspend fun createNewRoom(jsonObject: JsonObject): RoomResponse {
        val response =
            retrofitService.createNewRoom(getHeaderMap(sharedPrefs.readToken()), jsonObject)

        val oldRoom = response.data?.room?.roomId?.let { chatRoomDao.getRoomById(it) }
        response.data?.room?.let { chatRoomDao.updateRoomTable(oldRoom, it) }

        for (user in response.data?.room?.users!!) {
            user.user?.let { userDao.insert(it) }
            chatRoomDao.insertRoomWithUsers(
                RoomUser(
                    response.data.room.roomId,
                    user.userId,
                    user.isAdmin
                )
            )
        }

        return response
    }

    override suspend fun getUserAndPhoneUser(): LiveData<List<UserAndPhoneUser>> =
        userDao.getUserAndPhoneUser()

    override suspend fun getChatRoomAndMessageAndRecords(): LiveData<List<RoomAndMessageAndRecords>> =
        chatRoomDao.getChatRoomAndMessageAndRecords()

    override suspend fun getRoomWithUsers(roomId: Int): RoomWithUsers =
        chatRoomDao.getRoomAndUsers(roomId)

    override suspend fun updatePushToken(jsonObject: JsonObject) =
        retrofitService.updatePushToken(getHeaderMap(sharedPrefs.readToken()), jsonObject)

    override suspend fun updateUserData(data: Map<String, String>): AuthResponse {
        val responseData =
            retrofitService.updateUser(getHeaderMap(sharedPrefs.readToken()), data)

        userDao.insert(responseData.data.user)
        sharedPrefs.writeUserId(responseData.data.user.id)

        return responseData
    }

    override suspend fun uploadFiles(
        jsonObject: JsonObject
    ) = retrofitService.uploadFiles(getHeaderMap(sharedPrefs.readToken()), jsonObject)

    override suspend fun verifyFile(jsonObject: JsonObject): FileResponse =
        retrofitService.verifyFile(getHeaderMap(sharedPrefs.readToken()), jsonObject)

    override suspend fun getMessageRecords() {
        val messageIds: MutableList<Int> = ArrayList()
        messageDao.getMessagesLocally().forEach { messageIds.add(it.id) }

        if (messageIds.isNotEmpty()) {
            for (messageId in messageIds) {
                val recordsData = retrofitService.getMessageRecords(
                    getHeaderMap(sharedPrefs.readToken()),
                    messageId.toString()
                )

                if (recordsData.data.messageRecords.isNotEmpty()) {
                    for (messageRecord in recordsData.data.messageRecords) {
                        messageRecordsDao.insert(messageRecord)
                    }
                }
            }
        }
    }

    override suspend fun updateRoom(jsonObject: JsonObject, roomId: Int, userId: Int) {
        val response =
            retrofitService.updateRoom(getHeaderMap(sharedPrefs.readToken()), jsonObject, roomId)

        val oldRoom = chatRoomDao.getRoomById(roomId)
        response.data?.room?.let { chatRoomDao.updateRoomTable(oldRoom, it) }

        if (response.data?.room != null) {
            val room = response.data.room
            val oldData = chatRoomDao.getRoomById(room.roomId)
            chatRoomDao.updateRoomTable(oldData, room)

            // Delete Room User if id has been passed through
            if (userId != 0) {
                chatRoomDao.deleteRoomUser(RoomUser(roomId, userId, false))
            }

            for (user in room.users) {
                user.user?.let { userDao.insert(it) }
                chatRoomDao.insertRoomWithUsers(
                    RoomUser(
                        room.roomId,
                        user.userId,
                        user.isAdmin
                    )
                )
            }
        }
    }
}

interface MainRepository {
    suspend fun getUsers(page: Int): ContactResponse
    suspend fun getUserByID(id: Int): LiveData<User>
    suspend fun getUserLiveData(): LiveData<List<User>>
    suspend fun getRoomById(userId: Int): RoomResponse
    suspend fun getRooms(page: Int): RoomResponse
    suspend fun getMessages()
    suspend fun getRoomsLiveData(): LiveData<List<ChatRoom>>
    suspend fun createNewRoom(jsonObject: JsonObject): RoomResponse
    suspend fun getUserAndPhoneUser(): LiveData<List<UserAndPhoneUser>>
    suspend fun getChatRoomAndMessageAndRecords(): LiveData<List<RoomAndMessageAndRecords>>
    suspend fun getRoomWithUsers(roomId: Int): RoomWithUsers
    suspend fun updatePushToken(jsonObject: JsonObject)
    suspend fun updateUserData(data: Map<String, String>): AuthResponse
    suspend fun uploadFiles(jsonObject: JsonObject): FileResponse
    suspend fun verifyFile(jsonObject: JsonObject): FileResponse
    suspend fun getMessageRecords()
    suspend fun updateRoom(jsonObject: JsonObject, roomId: Int, userId: Int)
}