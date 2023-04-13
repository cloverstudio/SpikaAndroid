package com.clover.studio.exampleapp.data.repositories

import androidx.lifecycle.LiveData
import com.clover.studio.exampleapp.data.AppDatabase
import com.clover.studio.exampleapp.data.daos.ChatRoomDao
import com.clover.studio.exampleapp.data.daos.RoomUserDao
import com.clover.studio.exampleapp.data.daos.UserDao
import com.clover.studio.exampleapp.data.models.entity.ChatRoom
import com.clover.studio.exampleapp.data.models.entity.RoomAndMessageAndRecords
import com.clover.studio.exampleapp.data.models.entity.User
import com.clover.studio.exampleapp.data.models.entity.UserAndPhoneUser
import com.clover.studio.exampleapp.data.models.junction.RoomUser
import com.clover.studio.exampleapp.data.models.junction.RoomWithUsers
import com.clover.studio.exampleapp.data.models.networking.responses.AuthResponse
import com.clover.studio.exampleapp.data.models.networking.responses.FileResponse
import com.clover.studio.exampleapp.data.models.networking.responses.RoomResponse
import com.clover.studio.exampleapp.data.models.networking.responses.Settings
import com.clover.studio.exampleapp.data.repositories.data_sources.MainRemoteDataSource
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

class MainRepositoryImpl @Inject constructor(
    private val mainRemoteDataSource: MainRemoteDataSource,
    private val userDao: UserDao,
    private val chatRoomDao: ChatRoomDao,
    private val roomUserDao: RoomUserDao,
    private val appDatabase: AppDatabase,
    private val sharedPrefs: SharedPreferencesRepository
) : MainRepository {

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
        )

        val oldRoom = response.responseData?.data?.room?.roomId?.let {
            queryDatabaseCoreData(
                databaseQuery = { chatRoomDao.getRoomById(it) }
            ).responseData
        }

        response.responseData?.data?.room?.let {
            queryDatabaseCoreData(
                databaseQuery = { chatRoomDao.updateRoomTable(oldRoom, it) }
            )
        }

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

    override fun getUserAndPhoneUser(localId: Int) =
        queryDatabase(
            databaseQuery = { userDao.getUserAndPhoneUser(localId) }
        )

    override suspend fun checkIfUserInPrivateRoom(userId: Int): Int? {
        return roomUserDao.doesPrivateRoomExistForUser(userId)
    }

    override fun getChatRoomAndMessageAndRecords() =
        queryDatabase(
            databaseQuery = { chatRoomDao.getDistinctChatRoomAndMessageAndRecords() }
        )

    override fun getRoomsLiveData() =
        queryDatabase(
            databaseQuery = { chatRoomDao.getDistinctRoomsUnreadCount() }
        )

    override fun getRoomWithUsersLiveData(roomId: Int) =
        queryDatabase(
            databaseQuery = { chatRoomDao.getRoomAndUsersLiveData(roomId) }
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
                Timber.d("Rooms to update: $roomsToUpdate")
                queryDatabaseCoreData(
                    databaseQuery = { chatRoomDao.upsert(roomsToUpdate) }
                )
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

    override suspend fun uploadFiles(jsonObject: JsonObject) =
        performRestOperation(
            networkCall = { mainRemoteDataSource.uploadFile(jsonObject) },
        )

    override suspend fun verifyFile(jsonObject: JsonObject) =
        performRestOperation(
            networkCall = { mainRemoteDataSource.verifyFile(jsonObject) },
        )

    override suspend fun updateRoom(
        jsonObject: JsonObject,
        roomId: Int,
        userId: Int
    ): Resource<RoomResponse> {
        val response = performRestOperation(
            networkCall = { mainRemoteDataSource.updateRoom(jsonObject, roomId) },
        )

        val oldRoom = queryDatabaseCoreData(
            databaseQuery = { chatRoomDao.getRoomById(roomId) }
        ).responseData

        response.responseData?.data?.room?.let {
            queryDatabaseCoreData(
                databaseQuery = { chatRoomDao.updateRoomTable(oldRoom, it) }
            )
        }

        if (response.responseData?.data?.room != null) {
            val room = response.responseData.data.room

            // Delete Room User if id has been passed through
            if (userId != 0) {
                roomUserDao.delete(RoomUser(roomId, userId, false))
            }

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

    override suspend fun deleteBlockForSpecificUser(userId: Int) {
        val response = performRestOperation(
            networkCall = { mainRemoteDataSource.deleteBlockForSpecificUser(userId) },
        )
        if (Resource.Status.SUCCESS == response.status) {
            val currentList = sharedPrefs.readBlockedUserList()
            val updatedList = currentList.filterNot { it == userId }
            sharedPrefs.writeBlockedUsersIds(updatedList)
        }
    }

    override suspend fun handleRoomMute(roomId: Int, doMute: Boolean) {
        if (doMute) {
            performRestOperation(
                networkCall = { mainRemoteDataSource.muteRoom(roomId) },
                saveCallResult = { chatRoomDao.updateRoomPinned(true, roomId) }
            )
        } else {
            performRestOperation(
                networkCall = { mainRemoteDataSource.unmuteRoom(roomId) },
                saveCallResult = { chatRoomDao.updateRoomPinned(false, roomId) }
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

interface MainRepository {
    // User calls
    suspend fun getUserByID(id: Int): LiveData<Resource<User>>
    suspend fun getRoomById(roomId: Int): Resource<RoomResponse>
    suspend fun updateUserData(jsonObject: JsonObject): Resource<AuthResponse>
    fun getUserAndPhoneUser(localId: Int): LiveData<Resource<List<UserAndPhoneUser>>>

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
    suspend fun updateRoom(jsonObject: JsonObject, roomId: Int, userId: Int): Resource<RoomResponse>
    suspend fun getUnreadCount()
    fun getRoomsLiveData(): LiveData<Resource<Int>>

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
    suspend fun deleteBlockForSpecificUser(userId: Int)
}