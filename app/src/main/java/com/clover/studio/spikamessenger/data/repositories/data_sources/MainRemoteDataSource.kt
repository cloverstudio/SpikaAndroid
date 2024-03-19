package com.clover.studio.spikamessenger.data.repositories.data_sources

import com.clover.studio.spikamessenger.data.models.networking.BlockedId
import com.clover.studio.spikamessenger.data.repositories.SharedPreferencesRepository
import com.clover.studio.spikamessenger.data.services.RetrofitService
import com.clover.studio.spikamessenger.utils.Tools.getHeaderMap
import com.clover.studio.spikamessenger.utils.helpers.BaseDataSource
import com.google.gson.JsonObject
import javax.inject.Inject

class MainRemoteDataSource @Inject constructor(
    private val retrofitService: RetrofitService,
    private val sharedPrefs: SharedPreferencesRepository
) : BaseDataSource() {
    suspend fun getRoomById(userId: Int) = getResult {
        retrofitService.getRoomById(getHeaderMap(sharedPrefs.readToken()), userId)
    }

    suspend fun getUserRooms() = getResult {
        retrofitService.fetchAllUserRooms(getHeaderMap(sharedPrefs.readToken()))
    }

    suspend fun createNewRoom(jsonObject: JsonObject) = getResult {
        retrofitService.createNewRoom(getHeaderMap(sharedPrefs.readToken()), jsonObject)
    }

    suspend fun updateRoom(jsonObject: JsonObject, roomId: Int) = getResult {
        retrofitService.updateRoom(getHeaderMap(sharedPrefs.readToken()), jsonObject, roomId)
    }

    suspend fun verifyFile(jsonObject: JsonObject) = getResult {
        retrofitService.verifyFile(getHeaderMap(sharedPrefs.readToken()), jsonObject)
    }

    suspend fun uploadFile(jsonObject: JsonObject) = getResult {
        retrofitService.uploadFiles(getHeaderMap(sharedPrefs.readToken()), jsonObject)
    }

    suspend fun updatePushToken(jsonObject: JsonObject) = getResult {
        retrofitService.updatePushToken(getHeaderMap(sharedPrefs.readToken()), jsonObject)
    }

    suspend fun updateUser(jsonObject: JsonObject) = getResult {
        retrofitService.updateUser(getHeaderMap(sharedPrefs.readToken()), jsonObject)
    }

    suspend fun forwardMessages(jsonObject: JsonObject) = getResult {
        retrofitService.forwardMessages(getHeaderMap(sharedPrefs.readToken()), jsonObject)
    }

    suspend fun shareMedia(jsonObject: JsonObject) = getResult {
        retrofitService.shareMedia(getHeaderMap(sharedPrefs.readToken()), jsonObject)
    }

    suspend fun getUserSettings() = getResult {
        retrofitService.getSettings(getHeaderMap(sharedPrefs.readToken()))
    }

    suspend fun deleteBlock(userId: Int) = getResult {
        retrofitService.deleteBlock(getHeaderMap(sharedPrefs.readToken()), userId)
    }

    suspend fun blockUser(blockedId: Int) = getResult {
        retrofitService.blockUser(getHeaderMap(sharedPrefs.readToken()), BlockedId(blockedId))
    }

    suspend fun getBlockedList() = getResult {
        retrofitService.getBlockedList(getHeaderMap(sharedPrefs.readToken()))
    }

    suspend fun deleteBlockForSpecificUser(userId: Int) = getResult {
        retrofitService.deleteBlockForSpecificUser(getHeaderMap(sharedPrefs.readToken()), userId)
    }

    suspend fun muteRoom(roomId: Int) = getResult {
        retrofitService.muteRoom(getHeaderMap(sharedPrefs.readToken()), roomId)
    }

    suspend fun unmuteRoom(roomId: Int) = getResult {
        retrofitService.unmuteRoom(getHeaderMap(sharedPrefs.readToken()), roomId)
    }

    suspend fun pinRoom(roomId: Int) = getResult {
        retrofitService.pinRoom(getHeaderMap(sharedPrefs.readToken()), roomId)
    }

    suspend fun unpinRoom(roomId: Int) = getResult {
        retrofitService.unpinRoom(getHeaderMap(sharedPrefs.readToken()), roomId)
    }

    suspend fun getUnreadCount() = getResult {
        retrofitService.getUnreadCount(getHeaderMap(sharedPrefs.readToken()))
    }

    suspend fun deleteUser() = getResult {
        retrofitService.deleteUser(getHeaderMap(sharedPrefs.readToken()))
    }

    override suspend fun syncContacts(contacts: List<String>, isLastPage: Boolean) = getResult {
        retrofitService.syncContacts(getHeaderMap(sharedPrefs.readToken()), contacts, isLastPage)
    }
}
