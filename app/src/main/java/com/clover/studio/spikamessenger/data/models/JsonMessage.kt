package com.clover.studio.spikamessenger.data.models

import com.clover.studio.spikamessenger.utils.Const
import com.google.gson.JsonObject

data class JsonMessage(
    val msgText: String?,
    val mimeType: String,
    val fileId: Long?,
    val thumbId: Long?,
    val roomId: Int?,
    val localId: String?,
    var replyId: Long?,
) {
    fun messageToJson(): JsonObject {
        val jsonObject = JsonObject()
        val innerObject = JsonObject()

        innerObject.addProperty(
            Const.JsonFields.TEXT_TYPE,
            msgText
        )

        if (mimeType.contains(Const.JsonFields.IMAGE_TYPE)) {
            innerObject.addProperty(Const.JsonFields.FILE_ID, fileId)
            innerObject.addProperty(Const.JsonFields.THUMB_ID, thumbId)
            jsonObject.addProperty(Const.JsonFields.TYPE, mimeType)
        } else if (mimeType.contains(Const.JsonFields.FILE_TYPE) || mimeType.contains(Const.JsonFields.AUDIO_TYPE)) {
            innerObject.addProperty(Const.JsonFields.FILE_ID, fileId)
            jsonObject.addProperty(Const.JsonFields.TYPE, mimeType)
        } else if (mimeType.contains(Const.JsonFields.VIDEO_TYPE)) {
            innerObject.addProperty(Const.JsonFields.FILE_ID, fileId)
            innerObject.addProperty(Const.JsonFields.THUMB_ID, thumbId)
            jsonObject.addProperty(Const.JsonFields.TYPE, Const.JsonFields.VIDEO_TYPE)
        } else jsonObject.addProperty(Const.JsonFields.TYPE, mimeType)

        if (replyId == 0L){
            replyId = null
        }

        jsonObject.addProperty(Const.JsonFields.REPLY_ID, replyId)
        jsonObject.addProperty(Const.JsonFields.LOCAL_ID, localId)
        jsonObject.addProperty(Const.JsonFields.ROOM_ID, roomId)

        jsonObject.add(Const.JsonFields.BODY, innerObject)

        return jsonObject
    }
}
