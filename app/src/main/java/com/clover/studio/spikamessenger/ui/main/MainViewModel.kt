package com.clover.studio.spikamessenger.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.clover.studio.spikamessenger.BaseViewModel
import com.clover.studio.spikamessenger.data.models.FileData
import com.clover.studio.spikamessenger.data.models.entity.Message
import com.clover.studio.spikamessenger.data.models.entity.MessageBody
import com.clover.studio.spikamessenger.data.models.entity.MessageWithRoom
import com.clover.studio.spikamessenger.data.models.entity.PrivateGroupChats
import com.clover.studio.spikamessenger.data.models.entity.User
import com.clover.studio.spikamessenger.data.models.entity.UserAndPhoneUser
import com.clover.studio.spikamessenger.data.models.junction.RoomWithUsers
import com.clover.studio.spikamessenger.data.models.networking.responses.AuthResponse
import com.clover.studio.spikamessenger.data.models.networking.responses.ContactsSyncResponse
import com.clover.studio.spikamessenger.data.models.networking.responses.DeleteUserResponse
import com.clover.studio.spikamessenger.data.models.networking.responses.RoomResponse
import com.clover.studio.spikamessenger.data.repositories.MainRepositoryImpl
import com.clover.studio.spikamessenger.data.repositories.SSERepositoryImpl
import com.clover.studio.spikamessenger.data.repositories.SharedPreferencesRepository
import com.clover.studio.spikamessenger.ui.main.chat.FileUploadVerified
import com.clover.studio.spikamessenger.utils.Event
import com.clover.studio.spikamessenger.utils.FileUploadListener
import com.clover.studio.spikamessenger.utils.SSEListener
import com.clover.studio.spikamessenger.utils.SSEManager
import com.clover.studio.spikamessenger.utils.Tools
import com.clover.studio.spikamessenger.utils.UploadDownloadManager
import com.clover.studio.spikamessenger.utils.helpers.Resource
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: MainRepositoryImpl,
    private val sharedPrefsRepo: SharedPreferencesRepository,
    private val sseManager: SSEManager,
    private val sseRepository: SSERepositoryImpl,
    private val uploadDownloadManager: UploadDownloadManager
) : BaseViewModel(), SSEListener {
    val usersListener = MutableLiveData<Event<Resource<AuthResponse?>>>()
    val checkRoomExistsListener = MutableLiveData<Event<Resource<RoomResponse?>>>()
    val createRoomListener = MutableLiveData<Event<Resource<RoomResponse?>>>()
    val roomWithUsersListener = MutableLiveData<Event<Resource<RoomWithUsers?>>>()
    val roomNotificationListener = MutableLiveData<Event<RoomNotificationData>>()
    val blockedListListener = MutableLiveData<Event<Resource<List<User>?>>>()
    val fileUploadListener = MutableLiveData<Event<Resource<FileUploadVerified?>>>()
    val newMessageReceivedListener = MutableLiveData<Event<Resource<Message?>>>()
    val contactSyncListener = MutableLiveData<Event<Resource<ContactsSyncResponse?>>>()
    val deleteUserListener = MutableLiveData<Event<Resource<DeleteUserResponse?>>>()
    val searchedMessageListener =
        MutableLiveData<Event<Pair<Resource<List<MessageWithRoom>?>, String>>>()
    val contactListener = MutableLiveData<Event<Resource<List<UserAndPhoneUser>?>>>()
    val recentContacts = MutableLiveData<Event<Resource<List<RoomWithUsers>?>>>()
    val allGroupsListener = MutableLiveData<Event<Resource<List<RoomWithUsers>?>>>()
    val recentGroupsListener = MutableLiveData<Event<Resource<List<RoomWithUsers>?>>>()
    val roomUsers: MutableList<PrivateGroupChats> = ArrayList()

    val shareRoomId : ArrayList<Int> = arrayListOf()
    val shareUserId : ArrayList<Int> = arrayListOf()

    init {
        sseManager.setupListener(this)
    }

    fun getLocalUser() = liveData {
        val localUserId = sharedPrefsRepo.readUserId()

        if (localUserId != null && localUserId != 0) {
            emitSource(repository.getUserByID(localUserId))
        } else {
            return@liveData
        }
    }

    fun checkIfFirstSSELaunch(): Boolean {
        var isFirstLaunch = false

        viewModelScope.launch {
            isFirstLaunch = sharedPrefsRepo.isFirstSSELaunch()
        }
        return isFirstLaunch
    }

    override fun newMessageReceived(message: Message) {
        resolveResponseStatus(
            newMessageReceivedListener,
            Resource(Resource.Status.SUCCESS, message, "")
        )
    }

    fun saveSelectedUsers(users: MutableList<PrivateGroupChats>) {
        roomUsers.addAll(users.toMutableSet())
    }

    fun getLocalUserId(): Int? {
        var userId: Int? = null

        viewModelScope.launch {
            userId = sharedPrefsRepo.readUserId()
        }
        return userId
    }

    suspend fun checkIfUserInPrivateRoom(userId: Int): Int? {
        return if (repository.checkIfUserInPrivateRoom(userId) != null) {
            repository.checkIfUserInPrivateRoom(userId)!!
        } else null
    }

    fun checkIfRoomExists(userId: Int) = viewModelScope.launch {
        resolveResponseStatus(checkRoomExistsListener, repository.getRoomById(userId))
    }

    fun createNewRoom(jsonObject: JsonObject) = CoroutineScope(Dispatchers.IO).launch {
        resolveResponseStatus(createRoomListener, repository.createNewRoom(jsonObject))
    }

    fun getPushNotificationStream(): Flow<Any> = flow {
        viewModelScope.launch {
            try {
                sseManager.startSSEStream()
            } catch (ex: Exception) {
                if (Tools.checkError(ex)) {
                    setTokenExpiredTrue()
                }
                return@launch
            }
        }
    }

    fun getSearchedMessages(query: String) = viewModelScope.launch {
        searchedMessageListener.postValue(Event(Pair(repository.getSearchedMessages(query), query)))
    }

    fun getUserAndPhoneUserLiveData(localId: Int) = repository.getUserAndPhoneUserLiveData(localId)

    fun getUserAndPhoneUser(localId: Int) = viewModelScope.launch {
        resolveResponseStatus(contactListener, repository.getUserAndPhoneUser(localId))
    }

    fun getChatRoomsWithLatestMessage() = repository.getChatRoomsWithLatestMessage()

    fun getRecentContacts() = viewModelScope.launch {
        resolveResponseStatus(recentContacts, repository.getRecentContacts())
    }

    fun getRecentGroups() = viewModelScope.launch {
        resolveResponseStatus(recentGroupsListener, repository.getRecentGroups())
    }

    fun getAllGroups() = viewModelScope.launch {
        resolveResponseStatus(allGroupsListener, repository.getAllGroups())
    }

    fun getRoomsLiveData() = repository.getRoomsUnreadCount()

    fun getRoomWithUsers(roomId: Int) =
        viewModelScope.launch {
            resolveResponseStatus(roomWithUsersListener, repository.getRoomWithUsers(roomId))
        }

    fun getRoomWithUsers(roomId: Int, message: Message) = viewModelScope.launch {
        val response = repository.getRoomWithUsers(roomId)
        roomNotificationListener.postValue(
            Event(
                RoomNotificationData(
                    response,
                    message
                )
            )
        )
    }

    fun getUnreadCount() = viewModelScope.launch {
        repository.getUnreadCount()
    }

    fun updatePushToken(jsonObject: JsonObject) = viewModelScope.launch {
        resolveResponseStatus(null, repository.updatePushToken(jsonObject))
    }

    fun updateUserData(jsonObject: JsonObject) = CoroutineScope(Dispatchers.IO).launch {
        resolveResponseStatus(usersListener, repository.updateUserData(jsonObject))
    }

    fun updateRoom(jsonObject: JsonObject, roomId: Int) = viewModelScope.launch {
        CoroutineScope(Dispatchers.Default).launch {
            resolveResponseStatus(
                createRoomListener,
                repository.updateRoom(jsonObject = jsonObject, roomId = roomId)
            )
        }
    }

    fun unregisterSharedPrefsReceiver() = viewModelScope.launch {
        sharedPrefsRepo.unregisterSharedPrefsReceiver()
    }

    fun blockedUserListListener() = liveData {
        emitSource(sharedPrefsRepo.blockUserListener())
    }

    fun fetchBlockedUsersLocally(userIds: List<Int>) = viewModelScope.launch {
        resolveResponseStatus(blockedListListener, repository.fetchBlockedUsersLocally(userIds))
    }

    fun getBlockedUsersList() = viewModelScope.launch {
        repository.getBlockedList()
    }

    fun blockUser(blockedId: Int) = viewModelScope.launch {
        repository.blockUser(blockedId)
    }

    /* Delete block method - uncomment when we need it
    fun deleteBlock(userId: Int) = viewModelScope.launch {
        repository.deleteBlock(userId)
    } */

    fun deleteBlockForSpecificUser(userId: Int) = viewModelScope.launch {
        repository.deleteBlockForSpecificUser(userId)
    }

    /**
     * This method handles mute/unmute of room depending on the data sent to it.
     *
     * @param roomId The room id to be muted in Int.
     * @param doMute Boolean which decides if the room should be muted or unmuted
     */
    fun handleRoomMute(roomId: Int, doMute: Boolean) = viewModelScope.launch {
        repository.handleRoomMute(roomId, doMute)
    }

    /**
     * This method handles pin/unpin of room depending on the data sent to it.
     *
     * @param roomId The room id to be muted in Int.
     * @param doPin Boolean which decides if the room should be pinned or unpinned
     */
    fun handleRoomPin(roomId: Int, doPin: Boolean) = viewModelScope.launch {
        repository.handleRoomPin(roomId, doPin)
    }

    fun uploadMedia(
        fileData: FileData
    ) = viewModelScope.launch {
        try {
            uploadDownloadManager.uploadFile(
                fileData,
                object : FileUploadListener {
                    override fun filePieceUploaded() {
                        resolveResponseStatus(
                            fileUploadListener,
                            Resource(Resource.Status.LOADING, null, fileData.isThumbnail.toString())
                        )
                    }

                    override fun fileUploadError(description: String) {
                        resolveResponseStatus(
                            fileUploadListener,
                            Resource(Resource.Status.ERROR, null, description)
                        )
                    }

                    override fun fileUploadVerified(
                        path: String,
                        mimeType: String,
                        thumbId: Long,
                        fileId: Long,
                        fileType: String,
                        messageBody: MessageBody?
                    ) {
                        val response = FileUploadVerified(
                            path,
                            mimeType,
                            thumbId,
                            fileId,
                            messageBody,
                        )
                        resolveResponseStatus(
                            fileUploadListener,
                            Resource(Resource.Status.SUCCESS, response, "")
                        )
                    }

                    override fun fileCanceledListener(messageId: String?) {
                        // ignore
                    }
                })
        } catch (ex: Exception) {
            resolveResponseStatus(
                fileUploadListener,
                Resource(Resource.Status.ERROR, null, ex.message.toString())
            )
        }
    }

    fun writeUserTheme(userTheme: String) = viewModelScope.launch {
        sharedPrefsRepo.writeUserTheme(userTheme)
    }

    fun syncContacts() = CoroutineScope(Dispatchers.IO).launch {
        if (sharedPrefsRepo.isTeamMode()) {
            sseRepository.syncUsers()
            contactSyncListener.postValue(Event(Resource(Resource.Status.SUCCESS, null, null)))
        } else {
            resolveResponseStatus(
                contactSyncListener,
                repository.syncContacts(shouldRefresh = true)
            )
        }
    }

    fun deleteUser() = viewModelScope.launch {
        resolveResponseStatus(deleteUserListener, repository.deleteUser())
    }

    fun isInTeamMode() = viewModelScope.launch {
        if (sharedPrefsRepo.isTeamMode()) {
            Timber.d("App is in team mode!")
        } else Timber.d("App is in messenger mode!")
    }

    fun shareMedia(jsonObject: JsonObject ) = viewModelScope.launch {
        repository.shareMedia(jsonObject = jsonObject)
    }
}

class RoomNotificationData(
    val response: Resource<RoomWithUsers>,
    val message: Message
)
