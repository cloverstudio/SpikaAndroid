package com.clover.studio.exampleapp.ui.main.chat

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.clover.studio.exampleapp.BaseViewModel
import com.clover.studio.exampleapp.data.models.entity.Message
import com.clover.studio.exampleapp.data.models.entity.MessageBody
import com.clover.studio.exampleapp.data.models.junction.RoomWithUsers
import com.clover.studio.exampleapp.data.models.networking.NewNote
import com.clover.studio.exampleapp.data.repositories.ChatRepositoryImpl
import com.clover.studio.exampleapp.data.repositories.MainRepositoryImpl
import com.clover.studio.exampleapp.data.repositories.SharedPreferencesRepository
import com.clover.studio.exampleapp.ui.main.*
import com.clover.studio.exampleapp.utils.*
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
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
    val messageSendListener = MutableLiveData<Event<ChatStatesEnum>>()
    val sendMessageDeliveredListener = MutableLiveData<Event<ChatStatesEnum>>()
    val roomDataListener = MutableLiveData<Event<MainStates>>()
    val roomNotificationListener = MutableLiveData<Event<ChatStates>>()
    val fileUploadListener = MutableLiveData<Event<ChatStates>>()
    val mediaUploadListener = MutableLiveData<Event<ChatStates>>()
    val noteCreationListener = MutableLiveData<Event<ChatStates>>()
    val blockedListListener = MutableLiveData<Event<MainStates>>()

    fun storeMessageLocally(message: Message) = viewModelScope.launch {
        try {
            repository.storeMessageLocally(message)
        } catch (ex: Exception) {
            Tools.checkError(ex)
            return@launch
        }
    }

    fun sendMessage(jsonObject: JsonObject) = viewModelScope.launch {
        try {
            repository.sendMessage(jsonObject)
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            } else {
                messageSendListener.postValue(Event(ChatStatesEnum.MESSAGE_SEND_FAIL))
            }
            return@launch
        }

        messageSendListener.postValue(Event(ChatStatesEnum.MESSAGE_SENT))
    }

    fun getLocalUserId(): Int? {
        var userId: Int? = null

        viewModelScope.launch {
            userId = sharedPrefs.readUserId()
        }

        return userId
    }

    fun deleteLocalMessages(messages: List<Message>) = viewModelScope.launch {
        try {
            repository.deleteLocalMessages(messages)
        } catch (ex: Exception) {
            Tools.checkError(ex)
            return@launch
        }
    }

    fun deleteLocalMessage(message: Message) = viewModelScope.launch {
        try {
            repository.deleteLocalMessage(message)
        } catch (ex: Exception) {
            Tools.checkError(ex)
            return@launch
        }
    }

    fun sendMessagesSeen(roomId: Int) = viewModelScope.launch {
        try {
            repository.sendMessagesSeen(roomId)
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            }
            return@launch
        }
    }

    fun updateRoomVisitedTimestamp(visitedTimestamp: Long, roomId: Int) = viewModelScope.launch {
        try {
            repository.updatedRoomVisitedTimestamp(visitedTimestamp, roomId)
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            }
            return@launch
        }
    }

    fun updateRoom(jsonObject: JsonObject, roomId: Int, userId: Int) = viewModelScope.launch {
        try {
            repository.updateRoom(jsonObject, roomId, userId)
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            }
            return@launch
        }
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

    fun getPushNotificationStream(listener: SSEListener): Flow<Message> = flow {
        viewModelScope.launch {
            try {
                sseManager.startSSEStream(listener)
            } catch (ex: Exception) {
                Tools.checkError(ex)
                return@launch
            }
        }
    }

    fun getChatRoomAndMessageAndRecordsById(roomId: Int) = liveData {
        emitSource(repository.getChatRoomAndMessageAndRecordsById(roomId))
    }

    fun getSingleRoomData(roomId: Int) = viewModelScope.launch {
        try {
            roomDataListener.postValue(Event(SingleRoomData(repository.getSingleRoomData(roomId))))
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            } else {
                roomDataListener.postValue(Event(SingleRoomFetchFailed))
            }
            return@launch
        }
    }

    fun getRoomWithUsers(roomId: Int, message: Message) = viewModelScope.launch {
        try {
            roomNotificationListener.postValue(
                Event(
                    RoomNotificationData(
                        repository.getRoomWithUsers(
                            roomId
                        ), message
                    )
                )
            )
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            } else {
                roomNotificationListener.postValue(Event(RoomWithUsersFailed))
            }
            return@launch
        }
    }

    /**
     * This method handles mute/unmute of room depending on the data sent to it.
     *
     * @param roomId The room id to be muted in Int.
     * @param doMute Boolean which decides if the room should be muted or unmuted
     */
    fun handleRoomMute(roomId: Int, doMute: Boolean) = viewModelScope.launch {
        try {
            repository.handleRoomMute(roomId, doMute)
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            }
            return@launch
        }
    }

    /**
     * This method handles pin/unpin of room depending on the data sent to it.
     *
     * @param roomId The room id to be muted in Int.
     * @param doPin Boolean which decides if the room should be pinned or unpinned
     */
    fun handleRoomPin(roomId: Int, doPin: Boolean) = viewModelScope.launch {
        try {
            repository.handleRoomPin(roomId, doPin)
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            }
            return@launch
        }
    }

    fun sendReaction(jsonObject: JsonObject) = viewModelScope.launch {
        try {
            repository.sendReaction(jsonObject)
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            } else {
                Timber.d("Exception: $ex")
            }
        }
    }

    /* TODO: Commented methods can later be used to delete reactions
    fun deleteReaction(recordId: Int, userId: Int) = viewModelScope.launch {
        try {
            repository.deleteReaction(recordId, userId)
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            } else {
                Timber.d("Exception: $ex")
            }
        }
    }

    fun deleteAllReactions(messageId: Int) = viewModelScope.launch {
        try {
            repository.deleteAllReactions(messageId)
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            } else {
                Timber.d("Exception: $ex")
            }
        }
    }*/


    fun deleteRoom(roomId: Int) = viewModelScope.launch {
        try {
            repository.deleteRoom(roomId)
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            }
            return@launch
        }
    }

    fun leaveRoom(roomId: Int) = viewModelScope.launch {
        try {
            repository.leaveRoom(roomId)
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            }
            return@launch
        }
    }

    fun removeAdmin(roomId: Int, userId: Int) = viewModelScope.launch {
        try {
            repository.removeAdmin(roomId, userId)
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            }
            return@launch
        }
    }

    fun deleteMessage(messageId: Int, target: String) = viewModelScope.launch {
        try {
            repository.deleteMessage(messageId, target)
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            }
            return@launch
        }
    }

    fun editMessage(messageId: Int, jsonObject: JsonObject) = viewModelScope.launch {
        try {
            repository.editMessage(messageId, jsonObject)
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            }
            return@launch
        }
    }

    fun fetchNotes(roomId: Int) = viewModelScope.launch {
        try {
            repository.getNotes(roomId)
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            }
            return@launch
        }
    }

    fun getRoomNotes(roomId: Int) = liveData {
        emitSource(repository.getLocalNotes(roomId))
    }

    fun createNewNote(roomId: Int, newNote: NewNote) = viewModelScope.launch {
        try {
            repository.createNewNote(roomId, newNote)
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            }
            noteCreationListener.postValue(Event(NoteFailed))
            return@launch
        }
        noteCreationListener.postValue(Event(NoteCreated))
    }

    fun updateNote(noteId: Int, newNote: NewNote) = viewModelScope.launch {
        try {
            repository.updateNote(noteId, newNote)
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            }
            noteCreationListener.postValue(Event(NoteFailed))
            return@launch
        }

        noteCreationListener.postValue(Event(NoteUpdated))
    }

    fun deleteNote(noteId: Int) = viewModelScope.launch {
        try {
            repository.deleteNote(noteId)
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            }
            noteCreationListener.postValue(Event(NoteFailed))
            return@launch
        }

        noteCreationListener.postValue(Event(NoteDeleted))
    }

    fun unregisterSharedPrefsReceiver() = viewModelScope.launch {
        sharedPrefs.unregisterSharedPrefsReceiver()
    }

    fun blockedUserListListener() = liveData {
        emitSource(sharedPrefs.blockUserListener())
    }

    fun fetchBlockedUsersLocally(userIds: List<Int>) = viewModelScope.launch {
        try {
            val data = mainRepository.fetchBlockedUsersLocally(userIds)
            blockedListListener.postValue(Event(BlockedUsersFetched(data)))
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            }
            blockedListListener.postValue(Event(BlockedUsersFetchFailed))
            return@launch
        }
    }

    fun getBlockedUsersList() = viewModelScope.launch {
        try {
            mainRepository.getBlockedList()
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            }
            return@launch
        }
    }

    fun blockUser(blockedId: Int) = viewModelScope.launch {
        try {
            mainRepository.blockUser(blockedId)
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            }
            return@launch
        }
    }

    fun deleteBlock(userId: Int) = viewModelScope.launch {
        try {
            mainRepository.deleteBlock(userId)
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            }
            return@launch
        }
    }

    fun deleteBlockForSpecificUser(userId: Int) = viewModelScope.launch {
        try {
            mainRepository.deleteBlockForSpecificUser(userId)
        } catch (ex: Exception) {
            if (Tools.checkError(ex)) {
                setTokenExpiredTrue()
            }
            return@launch
        }
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
                            messageBody: MessageBody?
                        ) {
                            fileUploadListener.postValue(
                                Event(FileUploadVerified(path, mimeType, thumbId, fileId, messageBody))
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
                        messageBody: MessageBody?
                    ) {
                        mediaUploadListener.postValue(
                            Event(
                                MediaUploadVerified(
                                    path,
                                    mimeType,
                                    thumbId,
                                    fileId,
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
class RoomWithUsersFetched(val roomWithUsers: RoomWithUsers) : ChatStates()
object RoomWithUsersFailed : ChatStates()
class RoomNotificationData(val roomWithUsers: RoomWithUsers, val message: Message) : ChatStates()
object NoteCreated : ChatStates()
object NoteFailed : ChatStates()
object NoteUpdated : ChatStates()
object NoteDeleted : ChatStates()
object FilePieceUploaded : ChatStates()
class FileUploadError(val description: String) : ChatStates()
class FileUploadVerified(
    val path: String,
    val mimeType: String,
    val thumbId: Long,
    val fileId: Long,
    val messageBody: MessageBody?
) : ChatStates()

class MediaPieceUploaded(val isThumbnail: Boolean) : ChatStates()
class MediaUploadError(val description: String) : ChatStates()
class MediaUploadVerified(
    val path: String,
    val mimeType: String,
    val thumbId: Long,
    val fileId: Long,
    val messageBody: MessageBody?,
    val isThumbnail: Boolean
) : ChatStates()

enum class ChatStatesEnum { MESSAGE_SENT, MESSAGE_SEND_FAIL, MESSAGE_DELIVERED, MESSAGE_DELIVER_FAIL }