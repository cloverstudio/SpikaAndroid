package com.clover.studio.exampleapp.data.services

import com.clover.studio.exampleapp.data.models.User
import com.clover.studio.exampleapp.data.models.networking.AuthResponse
import com.clover.studio.exampleapp.utils.Const
import retrofit2.Response
import retrofit2.http.*

interface RetrofitService {
    // implement calls to API
    @GET
    suspend fun getUsers(): Response<List<User>>

    @FormUrlEncoded
    @POST(value = Const.Networking.API_AUTH)
    suspend fun sendUserData(
        @Field("telephoneNumber") phoneNumber: String,
        @Field("telephoneNumberHashed") phoneNumberHashed: String,
        @Field("countryCode") countryCode: String,
        @Field("deviceId") deviceId: String
    ): AuthResponse

    @FormUrlEncoded
    @POST(value = Const.Networking.API_VERIFY_CODE)
    suspend fun verifyUserCode(
        @Field("code") code: String,
        @Field("deviceId") deviceId: String
    ): AuthResponse

    @POST(value = Const.Networking.API_CONTACTS)
    suspend fun sendContacts(
        @Body contacts: List<String>
    ): AuthResponse
}