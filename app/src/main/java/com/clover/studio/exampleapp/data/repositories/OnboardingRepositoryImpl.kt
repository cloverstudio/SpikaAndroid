package com.clover.studio.exampleapp.data.repositories

import com.clover.studio.exampleapp.data.daos.UserDao
import com.clover.studio.exampleapp.data.models.networking.AuthResponse
import com.clover.studio.exampleapp.data.services.OnboardingService
import com.clover.studio.exampleapp.utils.Tools.getHeaderMap
import com.google.gson.JsonObject
import javax.inject.Inject

class OnboardingRepositoryImpl @Inject constructor(
    private val retrofitService: OnboardingService,
    private val userDao: UserDao,
    private val sharedPrefs: SharedPreferencesRepository
) : OnboardingRepository {
    override suspend fun sendUserData(
        jsonObject: JsonObject
    ) {
        retrofitService.sendUserData(
            getHeaderMap(sharedPrefs.readToken()), jsonObject
        )
    }

    override suspend fun verifyUserCode(
        jsonObject: JsonObject
    ): AuthResponse {
        val responseData =
            retrofitService.verifyUserCode(getHeaderMap(sharedPrefs.readToken()), jsonObject)

        userDao.insert(responseData.data.user)
        sharedPrefs.writeUserId(responseData.data.user.id)

        return responseData
    }

    override suspend fun sendUserContacts(
        contacts: List<String>
    ): AuthResponse = retrofitService.sendContacts(getHeaderMap(sharedPrefs.readToken()), contacts)

    override suspend fun updateUser(
        userMap: Map<String, String>
    ): AuthResponse {
        val responseData =
            retrofitService.updateUser(getHeaderMap(sharedPrefs.readToken()), userMap)

        userDao.insert(responseData.data.user)
        sharedPrefs.writeUserId(responseData.data.user.id)

        return responseData
    }

    override suspend fun uploadFiles(
        jsonObject: JsonObject
    ) = retrofitService.uploadFiles(getHeaderMap(sharedPrefs.readToken()), jsonObject)
}

interface OnboardingRepository {
    suspend fun sendUserData(
        jsonObject: JsonObject
    )

    suspend fun verifyUserCode(
        jsonObject: JsonObject
    ): AuthResponse

    suspend fun sendUserContacts(
        contacts: List<String>
    ): AuthResponse

    suspend fun updateUser(
        userMap: Map<String, String>
    ): AuthResponse

    suspend fun uploadFiles(
        jsonObject: JsonObject
    )
}