package com.clover.studio.spikamessenger.data.services

import com.clover.studio.spikamessenger.data.models.networking.*
import com.clover.studio.spikamessenger.data.models.networking.responses.*
import com.clover.studio.spikamessenger.utils.Const
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.*

interface RetrofitService {
    // Room section
    @GET(Const.Networking.API_GET_ROOM_BY_ID)
    suspend fun getRoomById(
        @HeaderMap headers: Map<String, String?>,
        @Path(Const.Networking.USER_ID) userId: Int
    ): Response<RoomResponse>

    @POST(Const.Networking.API_POST_NEW_ROOM)
    suspend fun createNewRoom(
        @HeaderMap headers: Map<String, String?>,
        @Body jsonObject: JsonObject
    ): Response<RoomResponse>

    @PUT(Const.Networking.API_UPDATE_ROOM)
    suspend fun updateRoom(
        @HeaderMap headers: Map<String, String?>,
        @Body jsonObject: JsonObject,
        @Path(Const.Networking.ROOM_ID) roomId: Int
    ): Response<RoomResponse>

    @GET(Const.Networking.API_POST_NEW_ROOM)
    suspend fun fetchAllUserRooms(
        @HeaderMap headers: Map<String, String?>
    ): Response<RoomResponse>

    @POST(Const.Networking.API_MUTE_ROOM)
    suspend fun muteRoom(
        @HeaderMap headers: Map<String, String?>,
        @Path(Const.Networking.ROOM_ID) roomId: Int
    ): Response<MuteResponse>

    @POST(Const.Networking.API_UNMUTE_ROOM)
    suspend fun unmuteRoom(
        @HeaderMap headers: Map<String, String?>,
        @Path(Const.Networking.ROOM_ID) roomId: Int
    ): Response<MuteResponse>

    @POST(Const.Networking.API_PIN_ROOM)
    suspend fun pinRoom(
        @HeaderMap headers: Map<String, String?>,
        @Path(Const.Networking.ROOM_ID) roomId: Int
    ): Response<RoomResponse>

    @POST(Const.Networking.API_UNPIN_ROOM)
    suspend fun unpinRoom(
        @HeaderMap headers: Map<String, String?>,
        @Path(Const.Networking.ROOM_ID) roomId: Int
    ): Response<RoomResponse>

    @GET(Const.Networking.API_UNREAD_COUNT)
    suspend fun getUnreadCount(
        @HeaderMap headers: Map<String, String?>
    ): Response<UnreadCountResponse>
    // End Room section


    @PUT(Const.Networking.API_UPDATE_TOKEN)
    suspend fun updatePushToken(
        @HeaderMap headers: Map<String, String?>,
        @Body jsonObject: JsonObject
    ): Response<Unit>

    @PUT(value = Const.Networking.API_UPDATE_USER)
    suspend fun updateUser(
        @HeaderMap headers: Map<String, String?>,
        @Body jsonObject: JsonObject
    ): Response<AuthResponse>

    @POST(value = Const.Networking.API_UPLOAD_FILE)
    suspend fun uploadFiles(
        @HeaderMap headers: Map<String, String?>,
        @Body jsonObject: JsonObject
    ): Response<FileResponse>

    @POST(value = Const.Networking.API_VERIFY_FILE)
    suspend fun verifyFile(
        @HeaderMap headers: Map<String, String?>,
        @Body jsonObject: JsonObject
    ): Response<FileResponse>

    @GET(Const.Networking.API_USER_SETTINGS)
    suspend fun getSettings(
        @HeaderMap headers: Map<String, String?>
    ): Response<UserSettingsResponse>

    // Start Block section
    @GET(Const.Networking.API_BLOCK)
    suspend fun getBlockedList(
        @HeaderMap headers: Map<String, String?>
    ): Response<BlockResponse>

    @POST(Const.Networking.API_BLOCK)
    suspend fun blockUser(
        @HeaderMap headers: Map<String, String?>,
        @Body blockedId: BlockedId
    ): Response<BlockResponse>

    @DELETE(Const.Networking.API_DELETE_BLOCK)
    suspend fun deleteBlock(
        @HeaderMap headers: Map<String, String?>,
        @Path(Const.Networking.ID) userId: Int
    ): Response<BlockResponse>

    @DELETE(Const.Networking.API_DELETE_BLOCK_FOR_USER)
    suspend fun deleteBlockForSpecificUser(
        @HeaderMap headers: Map<String, String?>,
        @Path(Const.Networking.USER_ID) userId: Int
    ): Response<BlockResponse>

    @FormUrlEncoded
    @POST(Const.Networking.API_CONTACTS)
    suspend fun syncContacts(
        @HeaderMap headers: Map<String, String?>,
        @Field(Const.Networking.CONTACTS) contacts: List<String>,
        @Field(Const.Networking.IS_LAST_PAGE) isLastPage: Boolean
    ): Response<ContactsSyncResponse>

    @DELETE(Const.Networking.API_UPDATE_USER)
    suspend fun deleteUser(
        @HeaderMap headers: Map<String, String?>
    ): Response<DeleteUserResponse>

    @POST(Const.Networking.API_FORWARD)
    suspend fun forwardMessages(
        @HeaderMap headers: Map<String, String?>,
        @Body jsonObject: JsonObject
    ): Response<ForwardMessagesResponse>

    @POST(Const.Networking.API_SHARE_MEDIA)
    suspend fun shareMedia(
        @HeaderMap headers: Map<String, String?>,
        @Body jsonObject: JsonObject,
    ): Response<ShareMediaResponse>

    // End block section
}