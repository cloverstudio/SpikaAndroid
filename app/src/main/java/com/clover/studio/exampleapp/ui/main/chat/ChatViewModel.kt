package com.clover.studio.exampleapp.ui.main.chat

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.clover.studio.exampleapp.BuildConfig
import com.clover.studio.exampleapp.data.models.Message
import com.clover.studio.exampleapp.data.repositories.ChatRepositoryImpl
import com.clover.studio.exampleapp.data.repositories.SharedPreferencesRepository
import com.clover.studio.exampleapp.utils.Const
import com.clover.studio.exampleapp.utils.Event
import com.clover.studio.exampleapp.utils.Tools
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepositoryImpl,
    private val sharedPrefs: SharedPreferencesRepository
) : ViewModel() {
    val messageSendListener = MutableLiveData<Event<ChatStatesEnum>>()
    val getMessagesListener = MutableLiveData<Event<ChatStates>>()
    val getMessagesTimestampListener = MutableLiveData<Event<ChatStates>>()
    val sendMessageDeliveredListener = MutableLiveData<Event<ChatStatesEnum>>()
    val socketStateListener = MutableLiveData<Event<ChatStates>>()

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
            Tools.checkError(ex)
            messageSendListener.postValue(Event(ChatStatesEnum.MESSAGE_SEND_FAIL))
            return@launch
        }

        messageSendListener.postValue(Event(ChatStatesEnum.MESSAGE_SENT))
    }

    fun getMessages(roomId: Int) = viewModelScope.launch {
        try {
            repository.getMessages(roomId.toString())
        } catch (ex: Exception) {
            Tools.checkError(ex)
            getMessagesListener.postValue(Event(MessageFetchFail))
            return@launch
        }

        getMessagesListener.postValue(Event(MessagesFetched))
    }

    fun getMessagesTimestamp(timestamp: Int) = viewModelScope.launch {
        try {
            val messages = repository.getMessagesTimestamp(timestamp).data?.list
            getMessagesTimestampListener.postValue(Event(MessagesTimestampFetched(messages!!)))
        } catch (ex: Exception) {
            Tools.checkError(ex)
            getMessagesTimestampListener.postValue(Event(MessageTimestampFetchFail))
        }
    }

    fun sendMessageDelivered(jsonObject: JsonObject) = viewModelScope.launch {
        try {
            repository.sendMessageDelivered(jsonObject)
        } catch (ex: Exception) {
            Tools.checkError(ex)
            sendMessageDeliveredListener.postValue(Event(ChatStatesEnum.MESSAGE_DELIVER_FAIL))
            return@launch
        }

        sendMessageDeliveredListener.postValue(Event(ChatStatesEnum.MESSAGE_DELIVERED))
    }

    fun getLocalMessages(roomId: Int) = liveData {
        emitSource(repository.getMessagesLiveData(roomId))
    }

    fun getLocalUserId(): Int? {
        var userId: Int? = null

        viewModelScope.launch {
            userId = sharedPrefs.readUserId()!!
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

    fun getPushNotificationStream(): Flow<Message> = flow {
        viewModelScope.launch {
            try {
                repository.getPushNotificationStream()
            } catch (ex: Exception) {
                Tools.checkError(ex)
                return@launch
            }
        }
    }
}

sealed class ChatStates
object MessagesFetched : ChatStates()
data class MessagesTimestampFetched(val messages: List<Message>) : ChatStates()
object MessageFetchFail : ChatStates()
object MessageTimestampFetchFail : ChatStates()
object SocketTimeout : ChatStates()

enum class ChatStatesEnum { MESSAGE_SENT, MESSAGE_SEND_FAIL, MESSAGE_DELIVERED, MESSAGE_DELIVER_FAIL }