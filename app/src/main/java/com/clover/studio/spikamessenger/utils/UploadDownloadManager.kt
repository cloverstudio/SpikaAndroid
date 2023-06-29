@file:Suppress("BlockingMethodInNonBlockingContext")

package com.clover.studio.spikamessenger.utils

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import com.clover.studio.spikamessenger.MainApplication
import com.clover.studio.spikamessenger.data.models.FileData
import com.clover.studio.spikamessenger.data.models.FileMetadata
import com.clover.studio.spikamessenger.data.models.UploadFile
import com.clover.studio.spikamessenger.data.models.entity.MessageBody
import com.clover.studio.spikamessenger.data.repositories.MainRepositoryImpl
import com.clover.studio.spikamessenger.utils.helpers.Resource
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.*


fun getChunkSize(fileSize: Long): Int = if (fileSize > ONE_GB) ONE_MB * 2 else ONE_MB

const val ONE_MB = 1024 * 1024
const val ONE_GB = 1024 * 1024 * 1024

/**
 * Methods below will handle all file upload logic for the app. The interface below will communicate
 * upload status to the subscribers. The caller will have to handle progress bar status by itself and
 * can update the status based on pieces sent or final file verification.
 */
class UploadDownloadManager constructor(
    private val repository: MainRepositoryImpl
) {
    private var chunkCount = 0
    private var cancelUpload = false

    /**
     * Method will handle file upload process. The caller will have to supply the required parameters
     *
     * @param activity The calling activity
     * @param fileUri The Uri path value of the file being uploaded
     * @param fileType The type of the file being uploaded, in the context of the app. (avatar, message, group avatar...)
     * @param filePieces The number of the pieces the file has been divided to based on the maximum
     *  chunk size
     * @param file The file that is being uploaded to the backend
     * @param fileUploadListener The interface listener which will notify caller about update status.
     */
    suspend fun uploadFile(
        fileData: FileData,
        fileUploadListener: FileUploadListener
    ) {
        var mimeType = MainApplication.appContext.contentResolver.getType(fileData.fileUri)!!
        cancelUpload = false

        if (mimeType.contains(Const.JsonFields.AVI_TYPE)) {
            mimeType = Const.JsonFields.FILE_TYPE
        }

        val fileMetadata: FileMetadata? =
            Tools.getMetadata(MainApplication.appContext, fileUri, mimeType, isThumbnail)

        chunkCount = 0
        BufferedInputStream(FileInputStream(fileData.file)).use { bis ->
            var len: Int
            var piece = 0L
            val temp = ByteArray(getChunkSize(fileData.file.length()))
            val randomId = UUID.randomUUID().toString().substring(0, 7)

            while ((bis.read(temp).also { len = it } > 0) && !cancelUpload) {
                val uploadFile = UploadFile(
                    Base64.encodeToString(
                        temp,
                        0,
                        len,
                        0
                    ),
                    piece,
                    fileData.filePieces,
                    fileData.file.length(),
                    mimeType,
                    fileData.file.name.toString(),
                    randomId,
                    Tools.sha256HashFromUri(
                        fileData.fileUri,
                        mimeType
                    ),
                    fileData.fileType,
                    fileMetadata
                )

                Timber.d("Chunk count $chunkCount")
                startUploadAPI(
                    uploadFile,
                    mimeType,
                    fileData.filePieces,
                    fileData.isThumbnail,
                    fileData.messageBody,
                    fileUploadListener
                )

                piece++
            }
        }
    }

    private suspend fun startUploadAPI(
        uploadFile: UploadFile,
        mimeType: String,
        chunks: Int,
        isThumbnail: Boolean = false,
        messageBody: MessageBody?,
        fileUploadListener: FileUploadListener
    ) {
        try {
            val response = repository.uploadFiles(uploadFile.chunkToJson())
            if (Resource.Status.ERROR == response.status) {
                cancelUpload = true
                fileUploadListener.fileUploadError("File upload canceled")
                return
            }
            fileUploadListener.filePieceUploaded()
        } catch (ex: Exception) {
            Tools.checkError(ex)
            fileUploadListener.fileUploadError(ex.message.toString())
            chunkCount = 0
            return
        }
        chunkCount++

        Timber.d("Should verify? $chunkCount, $chunks")
        if (chunkCount == chunks) {
            verifyFileUpload(uploadFile, mimeType, isThumbnail, messageBody, fileUploadListener)
            chunkCount = 0
        }
    }

    private suspend fun verifyFileUpload(
        uploadFile: UploadFile,
        mimeType: String,
        isThumbnail: Boolean = false,
        messageBody: MessageBody?,
        fileUploadListener: FileUploadListener
    ) {
        try {
            val file = repository.verifyFile(uploadFile.fileToJson()).responseData?.data?.file
            Timber.d("UploadDownload FilePath = ${file?.path}")
            Timber.d("Mime type = $mimeType")
            if (file != null) {
                if (isThumbnail) file.path?.let {
                    fileUploadListener.fileUploadVerified(
                        it,
                        mimeType,
                        file.id.toLong(),
                        0,
                        file.type!!,
                        messageBody
                    )
                }
                else file.path?.let {
                    fileUploadListener.fileUploadVerified(
                        it,
                        mimeType,
                        0,
                        file.id.toLong(),
                        file.type!!,
                        messageBody
                    )
                }
            } else fileUploadListener.fileUploadError("Some error")

        } catch (ex: Exception) {
            Tools.checkError(ex)
            fileUploadListener.fileUploadError(ex.message.toString())
            return
        }
    }
}

interface FileUploadListener {
    fun filePieceUploaded()
    fun fileUploadError(description: String)
    fun fileUploadVerified(
        path: String,
        mimeType: String,
        thumbId: Long = 0,
        fileId: Long = 0,
        fileType: String,
        messageBody: MessageBody?
    )
}
