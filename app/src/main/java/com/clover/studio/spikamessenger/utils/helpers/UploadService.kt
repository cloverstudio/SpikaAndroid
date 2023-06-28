package com.clover.studio.spikamessenger.utils.helpers

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.clover.studio.spikamessenger.R
import com.clover.studio.spikamessenger.data.models.FileData
import com.clover.studio.spikamessenger.data.models.entity.MessageBody
import com.clover.studio.spikamessenger.utils.Const
import com.clover.studio.spikamessenger.utils.FileUploadListener
import com.clover.studio.spikamessenger.utils.UploadDownloadManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UploadService : Service() {
    @Inject
    lateinit var uploadDownloadManager: UploadDownloadManager

    private var uploadJob: Job? = null

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val items = intent?.getParcelableArrayListExtra<FileData>("files")

        val notification = NotificationCompat.Builder(this, "upload_channel")
            .setContentTitle(getString(R.string.uploading_files))
            .setContentText(getString(R.string.upload_in_progress_notification))
            .setSmallIcon(R.drawable.img_spika_push_black)
            .build()

        // Start the foreground service with the notification
        startForeground(Const.Service.UPLOAD_SERVICE_ID, notification)

        // Continue with the upload process
        uploadJob = CoroutineScope(Dispatchers.Default).launch {
            items?.toList()?.let { uploadItems(it) }
            stopForeground(STOP_FOREGROUND_REMOVE) // Stop the foreground service
            stopSelf() // Stop the service after all items are uploaded
        }

        return START_STICKY
    }

    override fun onDestroy() {
        uploadJob?.cancel()
        super.onDestroy()
    }

    private suspend fun uploadItems(items: List<FileData>) {
        for (item in items) {
            uploadItem(item)
        }
    }

    private suspend fun uploadItem(item: FileData) {
        uploadDownloadManager.uploadFile(item, object : FileUploadListener {
            override fun filePieceUploaded() {

            }

            override fun fileUploadError(description: String) {

            }

            override fun fileUploadVerified(
                path: String,
                mimeType: String,
                thumbId: Long,
                fileId: Long,
                fileType: String,
                messageBody: MessageBody?
            ) {
                if (!item.isThumbnail) {
                    if (fileId > 0) item.messageBody?.fileId =
                        fileId

//                    sendMessage(
//                        fileType,
//                        messageBody?.text!!,
//                        messageBody.fileId!!,
//                        messageBody.thumbId!!,
//                        1,
//                        item.localId,
//                    )
                }
            }
        })
    }

//    private fun sendMessage(
//        messageFileType: String,
//        text: String,
//        fileId: Long,
//        thumbId: Long,
//        roomId: Int,
//        localId: String,
//    ) {
//        val jsonMessage = JsonMessage(
//            text,
//            messageFileType,
//            fileId,
//            thumbId,
//            roomId,
//            localId,
//            null
//        )
//
//        val jsonObject = jsonMessage.messageToJson()
//        Timber.d("Message object: $jsonObject")
//        viewModel.sendMessage(jsonObject)
//    }
}