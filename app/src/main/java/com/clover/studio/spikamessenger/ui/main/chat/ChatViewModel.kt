package com.clover.studio.spikamessenger.ui.main.chat

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.clover.studio.spikamessenger.BaseViewModel
import com.clover.studio.spikamessenger.data.models.entity.Message
import com.clover.studio.spikamessenger.data.models.entity.MessageBody
import com.clover.studio.spikamessenger.data.models.entity.User
import com.clover.studio.spikamessenger.data.models.junction.RoomWithUsers
import com.clover.studio.spikamessenger.data.models.networking.NewNote
import com.clover.studio.spikamessenger.data.models.networking.responses.ForwardMessagesResponse
import com.clover.studio.spikamessenger.data.models.networking.responses.MessageResponse
import com.clover.studio.spikamessenger.data.models.networking.responses.NotesResponse
import com.clover.studio.spikamessenger.data.models.networking.responses.ThumbnailDataResponse
import com.clover.studio.spikamessenger.data.repositories.ChatRepositoryImpl
import com.clover.studio.spikamessenger.data.repositories.MainRepositoryImpl
import com.clover.studio.spikamessenger.utils.Event
import com.clover.studio.spikamessenger.utils.SSEListener
import com.clover.studio.spikamessenger.utils.SSEManager
import com.clover.studio.spikamessenger.utils.Tools
import com.clover.studio.spikamessenger.utils.helpers.Resource
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepositoryImpl,
    private val mainRepository: MainRepositoryImpl,
    sseManager: SSEManager,
) : BaseViewModel(), SSEListener {
    val messageSendListener = MutableLiveData<Event<Resource<MessageResponse?>>>()
    val noteCreationListener = MutableLiveData<Event<Resource<NotesResponse?>>>()
    val noteDeletionListener = MutableLiveData<Event<NoteDeletion>>()
    val blockedListListener = MutableLiveData<Event<Resource<List<User>?>>>()
    val forwardListener = MutableLiveData<Event<Resource<ForwardMessagesResponse?>>>()
    private val liveDataLimit = MutableLiveData(20)
    private val mediaItemsLimit = MutableLiveData(10)
    val messagesReceived = MutableLiveData<List<Message>>()
    val searchMessageId = MutableLiveData(0)
    val roomWithUsers = MutableLiveData<RoomWithUsers>()
    val thumbnailData = MutableLiveData<Event<Resource<ThumbnailDataResponse?>>>()

    init {
        sseManager.setupListener(this)
    }

    private fun updateCounterLimit() {
        val currentLimit = liveDataLimit.value ?: 0
        liveDataLimit.postValue(currentLimit + 1)
    }

    fun storeMessageLocally(message: Message) = CoroutineScope(Dispatchers.IO).launch {
        repository.storeMessageLocally(message)
        updateCounterLimit()
    }

    override fun newMessageReceived(message: Message) {
        viewModelScope.launch {
            updateCounterLimit()
            val currentMessages = messagesReceived.value?.toMutableList() ?: mutableListOf()
            val isMessageNew = currentMessages.none { it.id == message.id }

            if (isMessageNew) {
                currentMessages.add(message)
                messagesReceived.value = currentMessages
            }
        }
    }

    fun clearMessages() {
        viewModelScope.launch {
            messagesReceived.value = emptyList()
        }
    }

    fun sendMessage(jsonObject: JsonObject, localId: String) = viewModelScope.launch {
        val response = repository.sendMessage(jsonObject)
        if (response.status == Resource.Status.SUCCESS) {
            resolveResponseStatus(messageSendListener, response)
        } else {
            updateMessages(
                Resource.Status.ERROR.toString(),
                localId
            )
        }
    }

    fun getLocalUserId(): Int? {
        var userId: Int? = null

        viewModelScope.launch {
            userId = sharedPrefs.readUserId()
        }

        return userId
    }

    fun deleteLocalMessage(message: Message) = viewModelScope.launch {
        repository.deleteLocalMessage(message)
    }

    fun sendMessagesSeen(roomId: Int) = viewModelScope.launch {
        repository.sendMessagesSeen(roomId)
    }

    fun updateRoom(jsonObject: JsonObject, roomId: Int) = viewModelScope.launch {
        repository.updateRoom(jsonObject, roomId)
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

    suspend fun getRoomUsers(roomId: Int): RoomWithUsers? = repository.getRoomUsers(roomId)

    fun getRoomAndUsers(roomId: Int) = repository.getRoomWithUsersLiveData(roomId)

    fun getMessageAndRecords(roomId: Int) = liveDataLimit.switchMap {
        repository.getMessagesAndRecords(roomId, it, 0)
    }

    fun fetchNextSet(roomId: Int) {
        val currentLimit = liveDataLimit.value ?: 0
        Timber.d("Current limit 1: $currentLimit, ${liveDataLimit.value}")
        if (getMessageCount(roomId = roomId) > currentLimit)
            liveDataLimit.value = currentLimit + 20
        Timber.d("Current limit 2: $currentLimit, ${liveDataLimit.value}")
    }

    private fun getMessageCount(roomId: Int): Int {
        var messageCount: Int

        runBlocking {
            messageCount = repository.getMessageCount(roomId)
        }

        return messageCount
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

    fun deleteReaction(messageRecordId: Long) = viewModelScope.launch {
        repository.deleteReaction(messageRecordId)
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

    fun updateMessages(messageStatus: String, localId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            repository.updateMessageStatus(messageStatus, localId)
        }
    }

    fun updateLocalUri(localId: String, uri: String) {
        CoroutineScope(Dispatchers.IO).launch {
            repository.updateLocalUri(localId, uri)
        }
    }
    fun updateThumbUri(localId: String, uri: String) = viewModelScope.launch {
            repository.updateThumbUri(localId, uri)
    }

    fun editMessage(messageId: Int, jsonObject: JsonObject) =
        CoroutineScope(Dispatchers.IO).launch {
            repository.editMessage(messageId, jsonObject)
        }

    fun fetchNotes(roomId: Int) =
        CoroutineScope(Dispatchers.IO).launch {
            repository.getNotes(roomId)
        }

    fun getRoomNotes(roomId: Int) = repository.getLocalNotes(roomId)

    fun createNewNote(roomId: Int, newNote: NewNote) = CoroutineScope(Dispatchers.IO).launch {
        resolveResponseStatus(noteCreationListener, repository.createNewNote(roomId, newNote))
    }

    fun updateNote(noteId: Int, newNote: NewNote) = CoroutineScope(Dispatchers.IO).launch {
        resolveResponseStatus(noteCreationListener, repository.updateNote(noteId, newNote))
    }

    fun deleteNote(noteId: Int) = viewModelScope.launch {
        noteDeletionListener.postValue(Event(NoteDeletion(repository.deleteNote(noteId))))
    }

    fun unregisterSharedPrefsReceiver() = viewModelScope.launch {
        sharedPrefs.unregisterSharedPrefsReceiver()
    }

    fun blockedUserListListener() = liveData {
        emitSource(sharedPrefs.blockUserListener())
    }

    fun fetchBlockedUsersLocally(userIds: List<Int>) = viewModelScope.launch {
        resolveResponseStatus(blockedListListener, mainRepository.fetchBlockedUsersLocally(userIds))
    }

    fun getBlockedUsersList() = viewModelScope.launch {
        mainRepository.getBlockedList()
    }

    fun deleteBlockForSpecificUser(userId: Int) = viewModelScope.launch {
        resolveResponseStatus(
            blockedListListener,
            mainRepository.deleteBlockForSpecificUser(userId)
        )
    }

    fun getUnreadCount() = viewModelScope.launch {
        mainRepository.getUnreadCount()
    }

    fun updateUnreadCount(roomId: Int) = viewModelScope.launch {
        mainRepository.updateUnreadCount(roomId)
    }

    fun forwardMessage(jsonObject: JsonObject, singleChat: Boolean) =
        CoroutineScope(Dispatchers.IO).launch {
            if (singleChat) {
                resolveResponseStatus(forwardListener, mainRepository.forwardMessages(jsonObject))
            } else {
                mainRepository.forwardMessages(jsonObject)
            }
        }

    fun cancelUploadFile(messageId: String) = viewModelScope.launch {
        mainRepository.cancelUpload(messageId)
    }

    fun getAllMediaWithOffset(roomId: Int) = mediaItemsLimit.switchMap {
        mainRepository.getAllMediaWithOffset(roomId = roomId, limit = it, offset = 0)
    }

    fun fetchNextMediaSet(roomId: Int) {
        val currentLimit = mediaItemsLimit.value ?: 0
        if (getMediaCount(roomId) > currentLimit) mediaItemsLimit.value = currentLimit + 5
    }

    private fun getMediaCount(roomId: Int): Int {
        var mediaCount: Int

        runBlocking {
            mediaCount = mainRepository.getMediaCount(roomId)
        }

        return mediaCount
    }

    fun getPageMetadata(url: String) = viewModelScope.launch {
        val response = repository.getPageMetadata(url)
        resolveResponseStatus(thumbnailData, response)
    }
}

class NoteDeletion(val response: Resource<NotesResponse>)
class FileUploadVerified(
    val path: String,
    val mimeType: String,
    val thumbId: Long,
    val fileId: Long,
    val messageBody: MessageBody?,
)
