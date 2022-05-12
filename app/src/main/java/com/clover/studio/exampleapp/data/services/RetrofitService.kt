package com.clover.studio.exampleapp.data.services

import com.clover.studio.exampleapp.data.models.networking.ContactResponse
import com.clover.studio.exampleapp.data.models.networking.MessageRecordsResponse
import com.clover.studio.exampleapp.data.models.networking.MessageResponse
import com.clover.studio.exampleapp.data.models.networking.RoomResponse
import com.clover.studio.exampleapp.data.models.networking.*
import com.clover.studio.exampleapp.utils.Const
import com.google.gson.JsonObject
import retrofit2.http.*

interface RetrofitService {
    @GET(Const.Networking.API_CONTACTS)
    suspend fun getUsers(
        @HeaderMap headers: Map<String, String?>
    ): ContactResponse

    @GET(Const.Networking.API_GET_ROOM_BY_ID)
    suspend fun getRoomById(
        @HeaderMap headers: Map<String, String?>,
        @Path(Const.Networking.USER_ID) userId: Int
    ): RoomResponse

    @POST(Const.Networking.API_POST_NEW_ROOM)
    suspend fun createNewRoom(
        @HeaderMap headers: Map<String, String?>,
        @Body jsonObject: JsonObject
    ): RoomResponse

    @GET(Const.Networking.API_POST_NEW_ROOM)
    suspend fun getRooms(
        @HeaderMap headers: Map<String, String?>
    ): RoomResponse

    @GET(Const.Networking.API_GET_MESSAGES)
    suspend fun getMessages(
        @HeaderMap headers: Map<String, String?>,
        @Path(Const.Networking.ROOM_ID) roomId: String
    ): MessageResponse

    @GET(Const.Networking.API_GET_MESSAGE_RECORDS)
    suspend fun getMessageRecords(
        @HeaderMap headers: Map<String, String?>,
        @Path(Const.Networking.MESSAGE_ID) messageId: String
    ): MessageRecordsResponse

    @PUT(Const.Networking.API_UPDATE_TOKEN)
    suspend fun updatePushToken(
        @HeaderMap headers: Map<String, String?>,
        @Body jsonObject: JsonObject
    )

    @FormUrlEncoded
    @PUT(value = Const.Networking.API_UPDATE_USER)
    suspend fun updateUser(
        @HeaderMap headers: Map<String, String?>,
        @FieldMap userMap: Map<String, String>
    ): AuthResponse

    @POST(value = Const.Networking.API_UPLOAD_FILE)
    suspend fun uploadFiles(
        @HeaderMap headers: Map<String, String?>,
        @Body jsonObject: JsonObject
    ): FileResponse

    @POST(value = Const.Networking.API_VERIFY_FILE)
    suspend fun verifyFile(
        @HeaderMap headers: Map<String, String?>,
        @Body jsonObject: JsonObject
    ): FileResponse
}