package com.clover.studio.spikamessenger.utils.helpers

import android.app.DownloadManager
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.clover.studio.spikamessenger.BuildConfig
import com.clover.studio.spikamessenger.MainApplication
import com.clover.studio.spikamessenger.R
import com.clover.studio.spikamessenger.data.models.FileData
import com.clover.studio.spikamessenger.data.models.FileMetadata
import com.clover.studio.spikamessenger.data.models.entity.Message
import com.clover.studio.spikamessenger.data.models.entity.MessageBody
import com.clover.studio.spikamessenger.data.models.entity.MessageFile
import com.clover.studio.spikamessenger.utils.Const
import com.clover.studio.spikamessenger.utils.Tools
import com.clover.studio.spikamessenger.utils.getChunkSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

object FilesHelper {

    fun convertVideoItem(context: Context, uri: Uri): Bitmap? {
        var thumbnail : Bitmap? = null
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(context, uri)

        val duration =
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
        val bitRate =
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLong() ?: 0

        if (Tools.getVideoSize(duration, bitRate)) {
            Toast.makeText(context, context.getString(R.string.video_error), Toast.LENGTH_LONG)
                .show()
            return null
        }

        val bitmap =  mmr.frameAtTime
        mmr.release()

        bitmap?.let {
            thumbnail =
                ThumbnailUtils.extractThumbnail(bitmap, bitmap.width, bitmap.height)
        }

        return thumbnail
    }

    fun generateFilePath(context: Context, uri: Uri) : Uri{
        val fileName = "VIDEO-${System.currentTimeMillis()}.mp4"
        val file =
            File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), fileName)

        file.createNewFile()

        val filePath = file.absolutePath

        Tools.genVideoUsingMuxer(uri, filePath)
        return FileProvider.getUriForFile(
            MainApplication.appContext,
            BuildConfig.APPLICATION_ID + ".fileprovider",
            file
        )
    }

    fun uploadFile(
        isThumbnail: Boolean,
        uri: Uri,
        localId: String,
        roomId: Int,
        metadata: FileMetadata?
    ): FileData? {

        val messageBody = MessageBody(
            referenceMessage = null,
            text = "",
            fileId = 0,
            thumbId = 0,
            file = null,
            thumb = null,
            subjectId = null,
            objectIds = null,
            type = "",
            objects = null,
            subject = ""
        )
        val inputStream =
            MainApplication.appContext.contentResolver.openInputStream(uri) ?: return null

        val fileName = Tools.getFileNameFromUri(uri)
        val fileStream = Tools.copyStreamToFile(
            inputStream = inputStream,
            extension = Tools.getFileMimeType(MainApplication.appContext, uri),
            fileName = fileName
        )
        val uploadPieces =
            if ((fileStream.length() % getChunkSize(fileStream.length())).toInt() != 0)
                (fileStream.length() / getChunkSize(fileStream.length()) + 1).toInt()
            else (fileStream.length() / getChunkSize(fileStream.length())).toInt()

        val fileType = Tools.getFileType(uri)

        inputStream.close()

        return FileData(
            fileUri = uri,
            fileType = fileType,
            filePieces = uploadPieces,
            file = fileStream,
            messageBody = messageBody,
            isThumbnail = isThumbnail,
            localId = localId,
            roomId = roomId,
            messageStatus = null,
            metadata = metadata,
        )
    }

    private fun createTempFile(
        uri: Uri,
        type: String,
        localUserId: Int,
        roomId: Int,
        unsentMessages: MutableList<Message>
    ): Message {
        val fileName = Tools.getFileNameFromUri(uri)
        var size = 0L

        if (type == Const.JsonFields.FILE_TYPE) {
            val inputStream =
                MainApplication.appContext.contentResolver.openInputStream(uri)
            if (inputStream != null){
                size = Tools.copyStreamToFile(
                    inputStream = inputStream,
                    extension = Tools.getFileMimeType(MainApplication.appContext, uri),
                    fileName = fileName
                ).length()
                inputStream.close()
            }
        }

        val typeMedia =
            if (MainApplication.appContext.contentResolver.getType(uri) == Const.FileExtensions.AUDIO)
                Const.JsonFields.AUDIO_TYPE
            else
                type

        val fileMetadata: FileMetadata? =
            Tools.getMetadata(uri, type, true)
        Timber.d("File metadata: $fileMetadata")

        val file = MessageFile(
            id = 1,
            fileName = fileName,
            mimeType = "",
            size = size,
            metaData = fileMetadata,
            uri = uri.toString()
        )

        val tempMessage = MessageHelper.createTempFileMessage(
            roomId = roomId,
            localUserId = localUserId,
            messageType = typeMedia,
            unsentMessages = unsentMessages,
            file = file
        )

        if (typeMedia == Const.JsonFields.IMAGE_TYPE || typeMedia == Const.JsonFields.VIDEO_TYPE) {
            saveMediaToStorage(
                context = MainApplication.appContext,
                contentResolver = MainApplication.appContext.contentResolver,
                mediaUri = uri,
                id = tempMessage.localId
            )
        }

        return tempMessage
    }

    /**
     * Method creates a random Integer from its lowest value to 0. This is to ensure that the
     * message in question is a temporary message, since all real messages have positive values.
     * After creating a random value, the method will check the current list containing unsent
     * messages and see if any of the items currently have the random number designated. This
     * ensures that no two unsent messages have the same id value. If it finds the same value
     * in the list, it will generate a new value and goi through the check again.
     **/
    fun getUniqueRandomId(unsentMessages: MutableList<Message>): Int {
        var randomId = Tools.generateRandomInt()
        while (unsentMessages.any { randomId == it.id }) {
            randomId = Tools.generateRandomInt()
        }
        return randomId
    }

    private fun saveMediaToStorage(
        context: Context,
        contentResolver: ContentResolver,
        mediaUri: Uri,
        id: String?
    ): String? {
        val inputStream = contentResolver.openInputStream(mediaUri)
        var outputStream: OutputStream? = null
        var imagePath: String? = null

        try {
            val tempFile = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "$id.${Const.FileExtensions.JPG}"
            )
            outputStream = FileOutputStream(tempFile)

            // Simple compression
            val bitmap = BitmapFactory.decodeStream(inputStream)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)

            //inputStream?.copyTo(outputStream)
            imagePath = tempFile.absolutePath


        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            inputStream?.close()
            outputStream?.close()
        }

        return imagePath
    }

    suspend fun saveGifToStorage(context: Context, urlString: String) : File? {
        var file : File? = null

        try {
            val url = URL(urlString)
            val connection = withContext(Dispatchers.IO) {
                url.openConnection()
            } as HttpURLConnection

            connection.doInput = true

            withContext(Dispatchers.IO) {
                connection.connect()
            }

            val inputStream: InputStream = connection.inputStream

            val gifName = "gif_${System.currentTimeMillis()}.gif"

            file = saveFile(context, inputStream, gifName)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return file
    }

    private fun saveFile(context: Context, inputStream: InputStream, fileName: String): File {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File(directory, fileName)

        FileOutputStream(file).use { output ->
            val buffer = ByteArray(4 * 1024)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
            }
            output.flush()
        }

        return file
    }

    fun createTempMessage(
        uri: Uri,
        type: String,
        localUserId: Int,
        roomId: Int,
        unsentMessages: MutableList<Message>
    ) = createTempFile(
        uri = uri,
        type = type,
        localUserId = localUserId,
        roomId = roomId,
        unsentMessages = unsentMessages,
    )

    fun sendFiles(
        unsentMessages: List<Message>,
        uploadFiles: MutableList<FileData>,
        filesSelected: MutableList<Uri>,
        thumbnailUris: MutableList<Uri>,
        currentMediaLocation: MutableList<Uri>,
        roomId: Int
    ) {
        Timber.d("unsentMessages message :$unsentMessages")
        Timber.d("uploadFiles message :$uploadFiles")
        Timber.d("filesSelected message :$filesSelected")
        Timber.d("thumbnailUris message :$thumbnailUris")
        Timber.d("currentMediaLocation message :$currentMediaLocation")
        Timber.d("roomId message :$roomId")


        if (unsentMessages.isNotEmpty()) {
            for (unsentMessage in unsentMessages) {
                when (unsentMessage.type) {
                    Const.JsonFields.IMAGE_TYPE, Const.JsonFields.VIDEO_TYPE -> {
                        // Send thumbnail
                        uploadFiles(
                            isThumbnail = true,
                            uri = thumbnailUris.first(),
                            localId = unsentMessage.localId!!,
                            metadata = unsentMessage.body?.file?.metaData,
                            uploadFiles = uploadFiles,
                            roomId = roomId
                        )
                        // Send original image
                        uploadFiles(
                            isThumbnail = false,
                            uri = currentMediaLocation.first(),
                            localId = unsentMessage.localId,
                            metadata = unsentMessage.body?.file?.metaData,
                            roomId = roomId,
                            uploadFiles = uploadFiles
                        )
                        currentMediaLocation.removeFirst()
                        thumbnailUris.removeFirst()
                    }

                    Const.JsonFields.FILE_TYPE -> {
                        // Send file
                        uploadFiles(
                            isThumbnail = false,
                            uri = filesSelected.first(),
                            localId = unsentMessage.localId!!,
                            metadata = null,
                            roomId = roomId,
                            uploadFiles = uploadFiles
                        )
                        filesSelected.removeFirst()
                    }
                }
            }
        }
    }

    private fun uploadFiles(
        isThumbnail: Boolean,
        uri: Uri,
        localId: String,
        roomId: Int,
        metadata: FileMetadata?,
        uploadFiles: MutableList<FileData>
    ) {
        val uploadData: MutableList<FileData> = ArrayList()
        uploadData.add(
            uploadFile(
                isThumbnail,
                uri,
                localId,
                roomId,
                metadata
            )
        )
        uploadFiles.addAll(uploadData)
    }
    fun downloadFile(context: Context, message: Message) {
        try {
            val tmp = message.body?.fileId?.let { Tools.getFilePathUrl(it) }
            val request = DownloadManager.Request(Uri.parse(tmp))
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            request.setTitle(
                message.body?.file?.fileName ?: "${
                    MainApplication.appContext.getString(
                        R.string.spika
                    )
                }.jpg"
            )
            request.setDescription(context.getString(R.string.file_is_downloading))
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                message.body?.file!!.fileName
            )
            val manager =
                context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(
                context,
                context.getString(R.string.file_is_downloading),
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Timber.d("$e")
        }
    }
}

data class TempUri(
    val uri: Uri,
    val type: String,
)
