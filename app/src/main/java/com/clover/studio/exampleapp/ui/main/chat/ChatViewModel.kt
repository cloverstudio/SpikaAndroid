package com.clover.studio.exampleapp.ui.main.chat

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
import com.clover.studio.exampleapp.data.models.networking.NewNote
import com.clover.studio.exampleapp.data.models.networking.responses.MessageResponse
import com.clover.studio.exampleapp.data.models.networking.responses.NotesResponse
import com.clover.studio.exampleapp.data.repositories.ChatRepositoryImpl
import com.clover.studio.exampleapp.data.repositories.MainRepositoryImpl
import com.clover.studio.exampleapp.data.repositories.SharedPreferencesRepository
import com.clover.studio.exampleapp.utils.*
import com.clover.studio.exampleapp.utils.helpers.Resource
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepositoryImpl,
    private val mainRepository: MainRepositoryImpl,
    private val sharedPrefs: SharedPreferencesRepository,
    private val sseManager: SSEManager,
    private val uploadDownloadManager: UploadDownloadManager
) : BaseViewModel() {
    val messageSendListener = MutableLiveData<Event<Resource<MessageResponse?>>>()
    val roomDataListener = MutableLiveData<Event<Resource<RoomAndMessageAndRecords?>>>()
    val roomNotificationListener = MutableLiveData<Event<RoomNotificationData>>()
    val fileUploadListener = MutableLiveData<Event<ChatStates>>()
    val mediaUploadListener = MutableLiveData<Event<ChatStates>>()
    val noteCreationListener = MutableLiveData<Event<Resource<NotesResponse?>>>()
    val blockedListListener = MutableLiveData<Event<Resource<List<User>>>>()

    fun storeMessageLocally(message: Message) = CoroutineScope(Dispatchers.IO).launch {
        repository.storeMessageLocally(message)
    }

    fun setupSSEManager(listener: SSEListener) {
        sseManager.setupListener(listener)
    }

    fun sendMessage(jsonObject: JsonObject) = viewModelScope.launch {
        resolveResponseStatus(messageSendListener, repository.sendMessage(jsonObject))
    }

    fun getLocalUserId(): Int? {
        var userId: Int? = null

        viewModelScope.launch {
            userId = sharedPrefs.readUserId()
        }

        return userId
    }

    fun deleteLocalMessages(messages: List<Message>) = viewModelScope.launch {
        repository.deleteLocalMessages(messages)
    }

    fun deleteLocalMessage(message: Message) = viewModelScope.launch {
        repository.deleteLocalMessage(message)
    }

    fun sendMessagesSeen(roomId: Int) = viewModelScope.launch {
        repository.sendMessagesSeen(roomId)
    }

    fun updateRoomVisitedTimestamp(visitedTimestamp: Long, roomId: Int) = viewModelScope.launch {
        repository.updatedRoomVisitedTimestamp(visitedTimestamp, roomId)
    }

    fun updateRoom(jsonObject: JsonObject, roomId: Int, userId: Int) =
        CoroutineScope(Dispatchers.IO).launch {
            repository.updateRoom(jsonObject, roomId, userId)
        }

    fun isUserAdmin(roomId: Int, userId: Int): Boolean {
        var isAdmin = false

        runBlocking {
            try {
                isAdmin = repository.getRoomUserById(roomId, userId) == true
            } catch (ex: Exception) {
                Tools.checkError(ex)
            }
        }

        return isAdmin
    }

    fun getRoomAndUsers(roomId: Int) = liveData {
        emitSource(repository.getRoomWithUsersLiveData(roomId))
    }

//    fun getPushNotificationStream(listener: SSEListener): Flow<Message> = flow {
//        viewModelScope.launch {
//            try {
//                sseManager.startSSEStream(listener)
//            } catch (ex: Exception) {
//                Tools.checkError(ex)
//                return@launch
//            }
//        }
//    }

    fun getChatRoomAndMessageAndRecordsById(roomId: Int) = liveData {
        emitSource(repository.getChatRoomAndMessageAndRecordsById(roomId))
    }

    fun getSingleRoomData(roomId: Int) = viewModelScope.launch {
        resolveResponseStatus(roomDataListener, repository.getSingleRoomData(roomId))
    }

    fun getRoomWithUsers(roomId: Int, message: Message) = viewModelScope.launch {
        roomNotificationListener.postValue(
            Event(
                RoomNotificationData(
                    repository.getRoomWithUsers(
                        roomId
                    ), message
                )
            )
        )
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

    fun sendReaction(jsonObject: JsonObject) = viewModelScope.launch {
        repository.sendReaction(jsonObject)
    }

    fun deleteRoom(roomId: Int) = viewModelScope.launch {
        repository.deleteRoom(roomId)
    }

    fun leaveRoom(roomId: Int) = viewModelScope.launch {
        repository.leaveRoom(roomId)
    }

    fun removeAdmin(roomId: Int, userId: Int) = viewModelScope.launch {
        repository.removeAdmin(roomId, userId)
    }

    fun deleteMessage(messageId: Int, target: String) = CoroutineScope(Dispatchers.IO).launch {
        repository.deleteMessage(messageId, target)
    }

    fun editMessage(messageId: Int, jsonObject: JsonObject) =
        CoroutineScope(Dispatchers.IO).launch {
            repository.editMessage(messageId, jsonObject)
        }

    fun fetchNotes(roomId: Int) =
        CoroutineScope(Dispatchers.IO).launch {
            repository.getNotes(roomId)
        }

    fun getRoomNotes(roomId: Int) = liveData {
        emitSource(repository.getLocalNotes(roomId))
    }

    fun createNewNote(roomId: Int, newNote: NewNote) = CoroutineScope(Dispatchers.IO).launch {
        resolveResponseStatus(noteCreationListener, repository.createNewNote(roomId, newNote))
    }

    fun updateNote(noteId: Int, newNote: NewNote) = CoroutineScope(Dispatchers.IO).launch {
        repository.updateNote(noteId, newNote)
    }

    fun deleteNote(noteId: Int) = viewModelScope.launch {
        repository.deleteNote(noteId)
    }

    fun unregisterSharedPrefsReceiver() = viewModelScope.launch {
        sharedPrefs.unregisterSharedPrefsReceiver()
    }

    fun blockedUserListListener() = liveData {
        emitSource(sharedPrefs.blockUserListener())
    }

    fun fetchBlockedUsersLocally(userIds: List<Int>) = viewModelScope.launch {
        blockedListListener.postValue(Event(mainRepository.fetchBlockedUsersLocally(userIds)))
    }

    fun getBlockedUsersList() = viewModelScope.launch {
        mainRepository.getBlockedList()
    }

    fun blockUser(blockedId: Int) = viewModelScope.launch {
        mainRepository.blockUser(blockedId)
    }

    fun deleteBlock(userId: Int) = viewModelScope.launch {
        mainRepository.deleteBlock(userId)
    }

    fun deleteBlockForSpecificUser(userId: Int) = viewModelScope.launch {
        mainRepository.deleteBlockForSpecificUser(userId)
    }

    fun uploadFile(
        activity: Activity,
        uri: Uri,
        uploadPieces: Int,
        fileStream: File,
        type: String,
        messageBody: MessageBody?
    ) =
        viewModelScope.launch {
            try {
                uploadDownloadManager.uploadFile(
                    activity,
                    uri,
                    type,
                    uploadPieces,
                    fileStream,
                    messageBody,
                    false,
                    object : FileUploadListener {
                        override fun filePieceUploaded() {
                            fileUploadListener.postValue(Event(FilePieceUploaded))
                        }

                        override fun fileUploadError(description: String) {
                            fileUploadListener.postValue(Event(FileUploadError(description)))
                        }

                        override fun fileUploadVerified(
                            path: String,
                            mimeType: String,
                            thumbId: Long,
                            fileId: Long,
                            fileType: String,
                            messageBody: MessageBody?
                        ) {
                            fileUploadListener.postValue(
                                Event(
                                    FileUploadVerified(
                                        path,
                                        mimeType,
                                        thumbId,
                                        fileId,
                                        fileType,
                                        messageBody
                                    )
                                )
                            )
                        }
                    })
            } catch (ex: Exception) {
                fileUploadListener.postValue(Event(FileUploadError(ex.message.toString())))
            }
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
                        mediaUploadListener.postValue(Event(MediaPieceUploaded(isThumbnail)))
                    }

                    override fun fileUploadError(description: String) {
                        mediaUploadListener.postValue(Event(MediaUploadError(description)))
                    }

                    override fun fileUploadVerified(
                        path: String,
                        mimeType: String,
                        thumbId: Long,
                        fileId: Long,
                        fileType: String,
                        messageBody: MessageBody?
                    ) {
                        mediaUploadListener.postValue(
                            Event(
                                MediaUploadVerified(
                                    path,
                                    mimeType,
                                    thumbId,
                                    fileId,
                                    fileType,
                                    messageBody,
                                    isThumbnail
                                )
                            )
                        )
                    }
                })
        } catch (ex: Exception) {
            mediaUploadListener.postValue(Event(MediaUploadError(ex.message.toString())))
        }
    }
}

sealed class ChatStates
class RoomNotificationData(val response: Resource<RoomWithUsers>, val message: Message)
object FilePieceUploaded : ChatStates()
class FileUploadError(val description: String) : ChatStates()
class FileUploadVerified(
    val path: String,
    val mimeType: String,
    val thumbId: Long,
    val fileId: Long,
    val fileType: String,
    val messageBody: MessageBody?
) : ChatStates()

class MediaPieceUploaded(val isThumbnail: Boolean) : ChatStates()
class MediaUploadError(val description: String) : ChatStates()
class MediaUploadVerified(
    val path: String,
    val mimeType: String,
    val thumbId: Long,
    val fileId: Long,
    val fileType: String,
    val messageBody: MessageBody?,
    val isThumbnail: Boolean
) : ChatStates()
