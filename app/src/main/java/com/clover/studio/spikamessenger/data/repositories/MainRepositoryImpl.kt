package com.clover.studio.spikamessenger.data.repositories

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.clover.studio.spikamessenger.data.AppDatabase
import com.clover.studio.spikamessenger.data.daos.ChatRoomDao
import com.clover.studio.spikamessenger.data.daos.RoomUserDao
import com.clover.studio.spikamessenger.data.daos.UserDao
import com.clover.studio.spikamessenger.data.models.entity.ChatRoom
import com.clover.studio.spikamessenger.data.models.entity.Message
import com.clover.studio.spikamessenger.data.models.entity.MessageWithRoom
import com.clover.studio.spikamessenger.data.models.entity.RoomAndMessageAndRecords
import com.clover.studio.spikamessenger.data.models.entity.RoomWithMessage
import com.clover.studio.spikamessenger.data.models.entity.User
import com.clover.studio.spikamessenger.data.models.entity.UserAndPhoneUser
import com.clover.studio.spikamessenger.data.models.junction.RoomUser
import com.clover.studio.spikamessenger.data.models.junction.RoomWithUsers
import com.clover.studio.spikamessenger.data.models.networking.responses.AuthResponse
import com.clover.studio.spikamessenger.data.models.networking.responses.ContactsSyncResponse
import com.clover.studio.spikamessenger.data.models.networking.responses.DeleteUserResponse
import com.clover.studio.spikamessenger.data.models.networking.responses.FileResponse
import com.clover.studio.spikamessenger.data.models.networking.responses.ForwardMessagesResponse
import com.clover.studio.spikamessenger.data.models.networking.responses.RoomResponse
import com.clover.studio.spikamessenger.data.models.networking.responses.Settings
import com.clover.studio.spikamessenger.data.models.networking.responses.ShareMediaResponse
import com.clover.studio.spikamessenger.data.repositories.data_sources.MainRemoteDataSource
import com.clover.studio.spikamessenger.utils.helpers.Resource
import com.clover.studio.spikamessenger.utils.helpers.RestOperations.performRestOperation
import com.clover.studio.spikamessenger.utils.helpers.RestOperations.queryDatabase
import com.clover.studio.spikamessenger.utils.helpers.RestOperations.queryDatabaseCoreData
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class MainRepositoryImpl @Inject constructor(
    private val mainRemoteDataSource: MainRemoteDataSource,
    private val userDao: UserDao,
    private val chatRoomDao: ChatRoomDao,
    private val roomUserDao: RoomUserDao,
    private val appDatabase: AppDatabase,
    private val sharedPrefs: SharedPreferencesRepository,
) : MainRepository {

    private var uploadCanceled = MutableLiveData(Pair("", false))

    override suspend fun getUserRooms() =
        performRestOperation(
            networkCall = { mainRemoteDataSource.getUserRooms() },
        ).responseData?.data?.list

    override suspend fun getUserByID(id: Int) =
        queryDatabase(
            databaseQuery = { userDao.getDistinctUserById(id) })

    override suspend fun getRoomById(roomId: Int) =
        performRestOperation(
            networkCall = { mainRemoteDataSource.getRoomById(roomId) },
        )

    override fun getRoomByIdLiveData(roomId: Int) =
        queryDatabase(
            databaseQuery = { chatRoomDao.getDistinctRoomById(roomId) }
        )

    override suspend fun createNewRoom(jsonObject: JsonObject): Resource<RoomResponse> {
        val response = performRestOperation(
            networkCall = { mainRemoteDataSource.createNewRoom(jsonObject) },
            saveCallResult = { it.data?.room?.let { room -> chatRoomDao.upsert(room) } }
        )

        CoroutineScope(Dispatchers.IO).launch {
            appDatabase.runInTransaction {
                CoroutineScope(Dispatchers.IO).launch {
                    val users: MutableList<User> = ArrayList()
                    val roomUsers: MutableList<RoomUser> = ArrayList()
                    for (user in response.responseData?.data?.room?.users!!) {
                        user.user?.let { users.add(it) }
                        roomUsers.add(
                            RoomUser(
                                response.responseData.data.room.roomId,
                                user.userId,
                                user.isAdmin
                            )
                        )
                    }
                    queryDatabaseCoreData(
                        databaseQuery = { roomUserDao.upsert(roomUsers) }
                    )
                    queryDatabaseCoreData(
                        databaseQuery = { userDao.upsert(users) }
                    )
                }
            }
        }
        return response
    }

    override fun getUserAndPhoneUserLiveData(localId: Int) =
        queryDatabase(
            databaseQuery = { userDao.getUserAndPhoneUserLiveData(localId) }
        )

    override suspend fun getUserAndPhoneUser(localId: Int) =
        queryDatabaseCoreData(
            databaseQuery = { userDao.getUserAndPhoneUser(localId) }
        )

    override suspend fun deleteUser(): Resource<DeleteUserResponse> {
        return performRestOperation(
            networkCall = { mainRemoteDataSource.deleteUser() }
        )
    }

    override suspend fun checkIfUserInPrivateRoom(userId: Int): Int? {
        return roomUserDao.doesPrivateRoomExistForUser(userId)
    }

    override fun getChatRoomAndMessageAndRecords() =
        queryDatabase(
            databaseQuery = { chatRoomDao.getChatRoomAndMessageAndRecords() }
        )

    override fun getRoomsUnreadCount() =
        queryDatabase(
            databaseQuery = { chatRoomDao.getDistinctRoomsUnreadCount() }
        )

    suspend fun syncContacts(shouldRefresh: Boolean): Resource<ContactsSyncResponse> {
        return syncContacts(
            userDao,
            shouldRefresh,
            sharedPrefs,
            baseDataSource = mainRemoteDataSource
        )
    }

    override fun getRoomWithUsersLiveData(roomId: Int) =
        queryDatabase(
            databaseQuery = { chatRoomDao.getRoomAndUsersLiveData(roomId) }
        )

    override fun getChatRoomsWithLatestMessage(): LiveData<Resource<List<RoomWithMessage>>> =
        queryDatabase(
            databaseQuery = { chatRoomDao.getAllRoomsWithLatestMessageAndRecord() }
        )

    override suspend fun getRecentContacts() =
        queryDatabaseCoreData(
            databaseQuery = { chatRoomDao.getRecentContacts() }
        )

    override suspend fun getRecentGroups() =
        queryDatabaseCoreData(
            databaseQuery = { chatRoomDao.getRecentGroups() }
        )

    override suspend fun getAllGroups() =
        queryDatabaseCoreData(
            databaseQuery = { chatRoomDao.getAllGroups() }
        )


    override suspend fun getSingleRoomData(roomId: Int) =
        queryDatabaseCoreData(
            databaseQuery = { chatRoomDao.getSingleRoomData(roomId) }
        )


    override suspend fun getRoomWithUsers(roomId: Int) =
        queryDatabaseCoreData(
            databaseQuery = { chatRoomDao.getRoomAndUsers(roomId) }
        )

    override suspend fun getUnreadCount() {
        val response = performRestOperation(
            networkCall = { mainRemoteDataSource.getUnreadCount() }
        )

        CoroutineScope(Dispatchers.IO).launch {
            if (response.responseData?.data?.unreadCounts != null) {
                val currentRooms = chatRoomDao.getAllRooms()
                val roomsToUpdate: MutableList<ChatRoom> = ArrayList()
                for (room in currentRooms) {
                    room.unreadCount = 0
                    for (item in response.responseData.data.unreadCounts) {
                        if (item.roomId == room.roomId) {
                            room.unreadCount = item.unreadCount
                            break
                        }
                    }
                    roomsToUpdate.add(room)
                }
                queryDatabaseCoreData(
                    databaseQuery = { chatRoomDao.upsert(roomsToUpdate) }
                )
            }
        }
    }

    override suspend fun updateUnreadCount(roomId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val currentRooms = chatRoomDao.getAllRooms()
            val roomsToUpdate: MutableList<ChatRoom> = ArrayList()
            for (room in currentRooms) {
                if (roomId == room.roomId) {
                    room.unreadCount = 0
                    queryDatabaseCoreData(
                        databaseQuery = { chatRoomDao.upsert(roomsToUpdate) }
                    )
                    break
                }
            }
        }
    }

    override suspend fun updatePushToken(jsonObject: JsonObject) =
        performRestOperation(
            networkCall = { mainRemoteDataSource.updatePushToken(jsonObject) },
        )

    override suspend fun updateUserData(jsonObject: JsonObject): Resource<AuthResponse> {
        val data = performRestOperation(
            networkCall = { mainRemoteDataSource.updateUser(jsonObject) },
            saveCallResult = { userDao.upsert(it.data.user) }
        )

        if (Resource.Status.SUCCESS == data.status) {
            sharedPrefs.accountCreated(true)
            data.responseData?.data?.user?.id?.let { sharedPrefs.writeUserId(it) }
        }

        return data
    }

    override suspend fun forwardMessages(jsonObject: JsonObject): Resource<ForwardMessagesResponse> {
        val data = performRestOperation(
            networkCall = { mainRemoteDataSource.forwardMessages(jsonObject) },
            saveCallResult = {
                val roomUsers = mutableListOf<RoomUser>()
                it.data.newRooms.flatMap { chatRoom ->
                    chatRoom.users.map { roomUser ->
                        roomUsers.add(
                            RoomUser(
                                userId = roomUser.userId,
                                roomId = chatRoom.roomId,
                                isAdmin = roomUser.isAdmin,
                            )
                        )
                    }
                }

                queryDatabaseCoreData(
                    databaseQuery = { chatRoomDao.upsert(it.data.newRooms) }
                )

                queryDatabaseCoreData(
                    databaseQuery = { roomUserDao.upsert(roomUsers) }
                )
            }
        )
        return data
    }

    override suspend fun shareMedia(jsonObject: JsonObject): Resource<ShareMediaResponse> {
        val data = performRestOperation(
            networkCall = { mainRemoteDataSource.shareMedia(jsonObject = jsonObject) },
            saveCallResult = {
                // TODO
            }
        )
        return data
    }

    override suspend fun uploadFiles(jsonObject: JsonObject): Resource<FileResponse> {
        val response: Resource<FileResponse> = if (uploadCanceled.value?.second == false) {
            performRestOperation(
                networkCall = { mainRemoteDataSource.uploadFile(jsonObject) },
            )
        } else {
            uploadCanceled.postValue(Pair(uploadCanceled.value?.first.toString(), false))
            Resource(Resource.Status.CANCEL, null, uploadCanceled.value?.first.toString())
        }
        return response
    }

    override suspend fun cancelUpload(messageId: String) {
        uploadCanceled.postValue(Pair(messageId, true))
    }

    override fun getAllMediaWithOffset(
        roomId: Int,
        limit: Int,
        offset: Int
    ) =
        queryDatabase(
            databaseQuery = { chatRoomDao.getAllMediaWithOffset(roomId, limit, offset) }
        )

    override suspend fun getMediaCount(roomId: Int) = chatRoomDao.getMediaCount(roomId)

    override suspend fun getSearchedMessages(query: String) =
        queryDatabaseCoreData(
            databaseQuery = { chatRoomDao.getSearchMessages(query) }
        )

    override suspend fun verifyFile(jsonObject: JsonObject) =
        performRestOperation(
            networkCall = { mainRemoteDataSource.verifyFile(jsonObject) },
        )

    override suspend fun updateRoom(
        jsonObject: JsonObject,
        roomId: Int
    ): Resource<RoomResponse> {
        val response = performRestOperation(
            networkCall = { mainRemoteDataSource.updateRoom(jsonObject, roomId) },
            saveCallResult = { it.data?.room?.let { room -> chatRoomDao.upsert(room) } }
        )

        if (response.responseData?.data?.room != null) {
            val room = response.responseData.data.room

            CoroutineScope(Dispatchers.IO).launch {
                appDatabase.runInTransaction {
                    CoroutineScope(Dispatchers.IO).launch {
                        val users: MutableList<User> = ArrayList()
                        val roomUsers: MutableList<RoomUser> = ArrayList()
                        for (user in room.users) {
                            user.user?.let { users.add(it) }
                            roomUsers.add(
                                RoomUser(
                                    room.roomId,
                                    user.userId,
                                    user.isAdmin
                                )
                            )
                        }
                        queryDatabaseCoreData(
                            databaseQuery = { userDao.upsert(users) }
                        )
                        queryDatabaseCoreData(
                            databaseQuery = { roomUserDao.upsert(roomUsers) }
                        )
                    }
                }
            }
        }
        return response
    }

    override suspend fun getUserSettings() = performRestOperation(
        networkCall = { mainRemoteDataSource.getUserSettings() },
    ).responseData?.data?.settings


    override suspend fun getBlockedList() {
        val response = performRestOperation(
            networkCall = { mainRemoteDataSource.getBlockedList() },
        )

        val userIds = response.responseData?.data?.blockedUsers?.map { it.id }
        if (userIds?.isNotEmpty() == true) {
            sharedPrefs.writeBlockedUsersIds(userIds)
        }
    }

    override suspend fun fetchBlockedUsersLocally(userIds: List<Int>) =
        queryDatabaseCoreData(
            databaseQuery = { userDao.getUsersByIds(userIds) }
        )

    override suspend fun blockUser(blockedId: Int) {
        val response = performRestOperation(
            networkCall = { mainRemoteDataSource.blockUser(blockedId) },
        )
        if (Resource.Status.SUCCESS == response.status) {
            val currentList: MutableList<Int> =
                sharedPrefs.readBlockedUserList() as MutableList<Int>
            currentList.add(blockedId)
            sharedPrefs.writeBlockedUsersIds(currentList)
        }
    }

    override suspend fun deleteBlock(userId: Int) {
        performRestOperation(
            networkCall = { mainRemoteDataSource.deleteBlock(userId) },
        )
    }

    override suspend fun deleteBlockForSpecificUser(userId: Int): Resource<List<User>> {
        val response = performRestOperation(
            networkCall = { mainRemoteDataSource.deleteBlockForSpecificUser(userId) }
        )
        return if (Resource.Status.SUCCESS == response.status) {
            val currentList = sharedPrefs.readBlockedUserList()
            val updatedList = currentList.filterNot { it == userId }
            sharedPrefs.writeBlockedUsersIds(updatedList)

            queryDatabaseCoreData(
                databaseQuery = { userDao.getUsersByIds(updatedList) }
            )
        } else Resource(
            Resource.Status.ERROR,
            null,
            response.message
        )
    }

    override suspend fun handleRoomMute(roomId: Int, doMute: Boolean) {
        if (doMute) {
            performRestOperation(
                networkCall = { mainRemoteDataSource.muteRoom(roomId) },
                saveCallResult = { chatRoomDao.updateRoomMuted(true, roomId) }
            )
        } else {
            performRestOperation(
                networkCall = { mainRemoteDataSource.unmuteRoom(roomId) },
                saveCallResult = { chatRoomDao.updateRoomMuted(false, roomId) }
            )
        }
    }

    override suspend fun handleRoomPin(roomId: Int, doPin: Boolean) {
        if (doPin) {
            performRestOperation(
                networkCall = { mainRemoteDataSource.pinRoom(roomId) },
                saveCallResult = { chatRoomDao.updateRoomPinned(true, roomId) }
            )
        } else {
            performRestOperation(
                networkCall = { mainRemoteDataSource.unpinRoom(roomId) },
                saveCallResult = { chatRoomDao.updateRoomPinned(false, roomId) }
            )
        }
    }
}

interface MainRepository : BaseRepository {
    // User calls
    suspend fun getUserByID(id: Int): LiveData<Resource<User>>
    suspend fun getRoomById(roomId: Int): Resource<RoomResponse>
    suspend fun updateUserData(jsonObject: JsonObject): Resource<AuthResponse>
    suspend fun forwardMessages(jsonObject: JsonObject): Resource<ForwardMessagesResponse>
    suspend fun shareMedia(jsonObject: JsonObject): Resource<ShareMediaResponse>
    fun getUserAndPhoneUserLiveData(localId: Int): LiveData<Resource<List<UserAndPhoneUser>>>
    suspend fun getUserAndPhoneUser(localId: Int): Resource<List<UserAndPhoneUser>>
    suspend fun deleteUser(): Resource<DeleteUserResponse>

    // Rooms calls
    suspend fun getUserRooms(): List<ChatRoom>?
    fun getRoomByIdLiveData(roomId: Int): LiveData<Resource<ChatRoom>>
    suspend fun createNewRoom(jsonObject: JsonObject): Resource<RoomResponse>
    suspend fun getSingleRoomData(roomId: Int): Resource<RoomAndMessageAndRecords>
    suspend fun getRoomWithUsers(roomId: Int): Resource<RoomWithUsers>
    suspend fun checkIfUserInPrivateRoom(userId: Int): Int?
    suspend fun handleRoomMute(roomId: Int, doMute: Boolean)
    suspend fun handleRoomPin(roomId: Int, doPin: Boolean)
    fun getChatRoomAndMessageAndRecords(): LiveData<Resource<List<RoomAndMessageAndRecords>>>
    fun getRoomWithUsersLiveData(roomId: Int): LiveData<Resource<RoomWithUsers>>
    fun getChatRoomsWithLatestMessage(): LiveData<Resource<List<RoomWithMessage>>>
    suspend fun getRecentContacts(): Resource<List<RoomWithUsers>>
    suspend fun getRecentGroups(): Resource<List<RoomWithUsers>>
    suspend fun getAllGroups(): Resource<List<RoomWithUsers>>
    fun getAllMediaWithOffset(roomId: Int, limit: Int, offset: Int): LiveData<Resource<List<Message>>>
    suspend fun getMediaCount(roomId: Int): Int
    suspend fun updateRoom(
        jsonObject: JsonObject,
        roomId: Int
    ): Resource<RoomResponse>

    suspend fun getUnreadCount()
    suspend fun updateUnreadCount(roomId: Int)
    fun getRoomsUnreadCount(): LiveData<Resource<Int>>

    // Settings calls
    suspend fun updatePushToken(jsonObject: JsonObject): Resource<Unit>
    suspend fun getUserSettings(): List<Settings>?
    suspend fun uploadFiles(jsonObject: JsonObject): Resource<FileResponse>
    suspend fun verifyFile(jsonObject: JsonObject): Resource<FileResponse>

    // Block calls
    suspend fun getBlockedList()
    suspend fun fetchBlockedUsersLocally(userIds: List<Int>): Resource<List<User>>
    suspend fun blockUser(blockedId: Int)
    suspend fun deleteBlock(userId: Int)
    suspend fun deleteBlockForSpecificUser(userId: Int): Resource<List<User>>

    // Upload file
    suspend fun cancelUpload(messageId: String)

    // Search calls
    suspend fun getSearchedMessages(query: String): Resource<List<MessageWithRoom>>
}
