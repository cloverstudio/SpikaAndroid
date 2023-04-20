package com.clover.studio.exampleapp.ui.main

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.clover.studio.exampleapp.BaseViewModel
import com.clover.studio.exampleapp.data.models.entity.Message
import com.clover.studio.exampleapp.data.models.entity.MessageBody
import com.clover.studio.exampleapp.data.models.entity.RoomAndMessageAndRecords
import com.clover.studio.exampleapp.data.models.entity.User
import com.clover.studio.exampleapp.data.models.junction.RoomWithUsers
import com.clover.studio.exampleapp.data.models.networking.responses.AuthResponse
import com.clover.studio.exampleapp.data.models.networking.responses.RoomResponse
import com.clover.studio.exampleapp.data.repositories.MainRepositoryImpl
import com.clover.studio.exampleapp.data.repositories.SharedPreferencesRepository
import com.clover.studio.exampleapp.ui.main.chat.MediaUploadVerified
import com.clover.studio.exampleapp.utils.*
import com.clover.studio.exampleapp.utils.helpers.Resource
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: MainRepositoryImpl,
    private val sharedPrefsRepo: SharedPreferencesRepository,
    private val sseManager: SSEManager,
    private val uploadDownloadManager: UploadDownloadManager
) : BaseViewModel(), SSEListener {
    val usersListener = MutableLiveData<Event<Resource<AuthResponse?>>>()
    val checkRoomExistsListener = MutableLiveData<Event<Resource<RoomResponse?>>>()
    val createRoomListener = MutableLiveData<Event<Resource<RoomResponse?>>>()
    val roomWithUsersListener = MutableLiveData<Event<Resource<RoomWithUsers?>>>()
    val roomDataListener = MutableLiveData<Event<Resource<RoomAndMessageAndRecords?>>>()
    val roomNotificationListener = MutableLiveData<Event<RoomNotificationData>>()
    val blockedListListener = MutableLiveData<Event<Resource<List<User>?>>>()
    val mediaUploadListener = MutableLiveData<Event<Resource<MediaUploadVerified?>>>()
    val newMessageReceivedListener = MutableLiveData<Event<Resource<Message?>>>()

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
//        checkRoomExistsListener.postValue(Event(repository.getRoomById(userId)))
    }

    fun createNewRoom(jsonObject: JsonObject) = viewModelScope.launch {
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

    fun getUserAndPhoneUser(localId: Int) = repository.getUserAndPhoneUser(localId)

    fun getChatRoomAndMessageAndRecords() = repository.getChatRoomAndMessageAndRecords()

    fun getRoomsLiveData() = repository.getRoomsUnreadCount()

    fun getRoomByIdLiveData(roomId: Int) = repository.getRoomByIdLiveData(roomId)

    fun getSingleRoomData(roomId: Int) =
        viewModelScope.launch {
            resolveResponseStatus(roomDataListener, repository.getSingleRoomData(roomId))
        }

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

    fun updateRoom(jsonObject: JsonObject, roomId: Int, userId: Int) = viewModelScope.launch {
        Timber.d("RoomDataCalled")
        resolveResponseStatus(createRoomListener, repository.updateRoom(jsonObject, roomId, userId))
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
        activity: Activity,
        uri: Uri,
        fileType: String,
        uploadPieces: Int,
        fileStream: File,
        messageBody: MessageBody?,
        isThumbnail: Boolean
    ) = viewModelScope.launch {
        try {
            uploadDownloadManager.uploadFile(
                activity,
                uri,
                fileType,
                uploadPieces,
                fileStream,
                messageBody,
                isThumbnail,
                object : FileUploadListener {
                    override fun filePieceUploaded() {
                        resolveResponseStatus(
                            mediaUploadListener,
                            Resource(Resource.Status.LOADING, null, "")
                        )
                    }

                    override fun fileUploadError(description: String) {
                        resolveResponseStatus(
                            mediaUploadListener,
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
                        val response = MediaUploadVerified(
                            path,
                            mimeType,
                            thumbId,
                            fileId,
                            fileType,
                            messageBody,
                            isThumbnail
                        )
                        resolveResponseStatus(
                            mediaUploadListener,
                            Resource(Resource.Status.SUCCESS, response, "")
                        )
                    }
                })
        } catch (ex: Exception) {
            resolveResponseStatus(
                mediaUploadListener,
                Resource(Resource.Status.ERROR, null, ex.message.toString())
            )
        }
    }

    fun getUserTheme(): Int? {
        var theme: Int? = null
        viewModelScope.launch {
            theme = sharedPrefsRepo.readUserTheme()
        }
        return theme
    }

    fun writeUserTheme(userTheme: Int) = viewModelScope.launch {
        sharedPrefsRepo.writeUserTheme(userTheme)
    }
}

class RoomNotificationData(
    val response: Resource<RoomWithUsers>,
    val message: Message
)
