package com.clover.studio.exampleapp.data.repositories

import com.clover.studio.exampleapp.data.daos.ChatDao
import com.clover.studio.exampleapp.data.daos.ChatUserDao
import com.clover.studio.exampleapp.data.models.Message
import com.clover.studio.exampleapp.data.services.ChatService
import com.clover.studio.exampleapp.utils.Tools.getHeaderMap
import com.google.gson.JsonObject
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val chatService: ChatService,
    private val chatDao: ChatDao,
    private val chatUserDao: ChatUserDao,
    private val sharedPrefsRepo: SharedPreferencesRepository
) : ChatRepository {
    override suspend fun sendMessage(jsonObject: JsonObject): Message =
        chatService.sendMessage(getHeaderMap(sharedPrefsRepo.readToken()!!), jsonObject)

    override suspend fun getMessages(roomId: String): List<Message> =
        chatService.getMessages(getHeaderMap(sharedPrefsRepo.readToken()!!), roomId)
}

interface ChatRepository {
    suspend fun sendMessage(jsonObject: JsonObject): Message
    suspend fun getMessages(roomId: String): List<Message>
}