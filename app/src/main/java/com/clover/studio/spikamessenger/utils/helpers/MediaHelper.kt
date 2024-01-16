package com.clover.studio.spikamessenger.utils.helpers

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.clover.studio.spikamessenger.BuildConfig
import com.clover.studio.spikamessenger.MainApplication
import com.clover.studio.spikamessenger.R
import com.clover.studio.spikamessenger.utils.Const
import com.clover.studio.spikamessenger.utils.Tools
import java.io.File

object MediaHelper {
    fun convertMedia(
        context: Context,
        uri: Uri,
        fileMimeType: String?,
        tempFilesToCreate: MutableList<TempUri>,
        uriPairList: MutableList<Pair<Uri, Uri>>,
        thumbnailUris: MutableList<Uri>,
        currentMediaLocation: MutableList<Uri>
    ) {
        val thumbnailUri: Uri
        val fileUri: Uri

        if (fileMimeType?.contains(Const.JsonFields.VIDEO_TYPE) == true) {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(context, uri)

            val duration =
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
            val bitRate =
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLong() ?: 0

            if (Tools.getVideoSize(duration, bitRate)) {
                Toast.makeText(context, context.getString(R.string.video_error), Toast.LENGTH_LONG)
                    .show()
                return
            }

            val bitmap = mmr.frameAtTime
            val fileName = "VIDEO-${System.currentTimeMillis()}.mp4"
            val file =
                File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), fileName)

            file.createNewFile()

            val filePath = file.absolutePath
            Tools.genVideoUsingMuxer(uri, filePath)
            fileUri = FileProvider.getUriForFile(
                MainApplication.appContext,
                BuildConfig.APPLICATION_ID + ".fileprovider",
                file
            )
            val thumbnail =
                ThumbnailUtils.extractThumbnail(bitmap, bitmap!!.width, bitmap.height)
            thumbnailUri = Tools.convertBitmapToUri(context, thumbnail)
            tempFilesToCreate.add(TempUri(thumbnailUri, Const.JsonFields.VIDEO_TYPE))
            uriPairList.add(Pair(uri, thumbnailUri))

            mmr.release()
        } else {
            val bitmap =
                Tools.handleSamplingAndRotationBitmap(context, uri, false)
            fileUri = Tools.convertBitmapToUri(context, bitmap!!)

            val thumbnail =
                Tools.handleSamplingAndRotationBitmap(context, fileUri, true)
            thumbnailUri = Tools.convertBitmapToUri(context, thumbnail!!)
            tempFilesToCreate.add(TempUri(thumbnailUri, Const.JsonFields.IMAGE_TYPE))
        }

        uriPairList.add(Pair(uri, thumbnailUri))
        thumbnailUris.add(thumbnailUri)
        currentMediaLocation.add(fileUri)
    }
}