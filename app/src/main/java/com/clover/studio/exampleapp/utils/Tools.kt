package com.clover.studio.exampleapp.utils

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.load.resource.bitmap.TransformationUtils.rotateImage
import com.clover.studio.exampleapp.BuildConfig
import com.clover.studio.exampleapp.MainApplication
import com.clover.studio.exampleapp.R
import com.clover.studio.exampleapp.data.models.entity.Message
import com.clover.studio.exampleapp.data.models.entity.MessageBody
import retrofit2.HttpException
import timber.log.Timber
import java.io.*
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt


const val BITMAP_WIDTH = 512
const val BITMAP_HEIGHT = 512
const val TO_MEGABYTE = 1000000
const val TO_KILOBYTE = 1000
const val TOKEN_EXPIRED_CODE = 401
const val TOKEN_INVALID_CODE = 403

object Tools {

    fun checkError(ex: Exception): Boolean {
        when (ex) {
            is IllegalArgumentException -> Timber.d("IllegalArgumentException ${ex.message}")
            is IOException -> Timber.d("IOException ${ex.message}")
            is HttpException ->
                if (TOKEN_EXPIRED_CODE == ex.code() || TOKEN_INVALID_CODE == ex.code()) {
                    Timber.d("Token error: ${ex.code()} ${ex.message}")
                    return true
                } else {
                    Timber.d("HttpException: ${ex.code()} ${ex.message}")
                }
            else -> Timber.d("UnknownError: ${ex.message}")
        }
        return false
    }

    fun formatE164Number(context: Context, countryCode: String?, phNum: String?): String? {
        val e164Number: String? = if (TextUtils.isEmpty(countryCode)) {
            phNum
        } else {

            val telephonyManager =
                getSystemService(context, TelephonyManager::class.java)
            val isoCode = telephonyManager?.simCountryIso

            Timber.d("Country code: ${isoCode?.uppercase()}")
            PhoneNumberUtils.formatNumberToE164(phNum, isoCode?.uppercase())
        }
        return e164Number
    }

    fun hashString(input: String): String {
        val hexChars = "0123456789abcdef"
        val bytes = MessageDigest
            .getInstance("SHA-256")
            .digest(input.toByteArray())
        val result = StringBuilder(bytes.size * 2)

        bytes.forEach {
            val i = it.toInt()
            result.append(hexChars[i shr 4 and 0x0f])
            result.append(hexChars[i and 0x0f])
        }

        return result.toString()
    }

    fun getHeaderMap(token: String?): Map<String, String?> {
        val map = mutableMapOf<String, String?>()
        if (!token.isNullOrEmpty()) {
            map[Const.Headers.ACCESS_TOKEN] = token
        }
        map[Const.Headers.OS_NAME] = Const.Headers.ANDROID
        map[Const.Headers.OS_VERSION] = Build.VERSION.SDK_INT.toString()
        map[Const.Headers.DEVICE_NAME] = Build.MODEL
        map[Const.Headers.APP_VERSION] = BuildConfig.VERSION_NAME
        map[Const.Headers.LANGUAGE] = Locale.getDefault().language

        return map
    }

    fun copyStreamToFile(
        activity: Activity,
        inputStream: InputStream,
        extension: String,
        fileName: String = ""
    ): File {
        var tempFileName = fileName
        if (tempFileName.isEmpty()) {
            tempFileName =
                "tempFile${System.currentTimeMillis()}.${extension.substringAfterLast("/")}"
        }
        val outputFile = File(activity.cacheDir, tempFileName)
        inputStream.use { input ->
            val outputStream = FileOutputStream(outputFile)
            outputStream.use { output ->
                val buffer = ByteArray(4 * 1024) // buffer size
                while (true) {
                    val byteCount = input.read(buffer)
                    if (byteCount < 0) break
                    output.write(buffer, 0, byteCount)
                }
                output.flush()
            }
        }
        return outputFile
    }

    fun calculateFileSize(size: Long): String? {
        val df = DecimalFormat("#.##")
        if (size > TO_MEGABYTE) {
            return df.format(size.toFloat().div(TO_MEGABYTE)) + "MB"
        }
        return df.format(size.toFloat().div(TO_KILOBYTE)) + "KB"
    }

    @Throws(IOException::class)
    fun createImageFile(activity: Activity?): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = activity?.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        )
        file.deleteOnExit()
        return file
    }

    fun convertBitmapToUri(activity: Activity, bitmap: Bitmap): Uri {
        val file = createImageFile(activity)

        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100 /*ignored for PNG*/, bos)
        val bitmapData = bos.toByteArray()

        val fos = FileOutputStream(file)
        fos.write(bitmapData)
        fos.flush()
        fos.close()

        return FileProvider.getUriForFile(
            activity,
            BuildConfig.APPLICATION_ID + ".fileprovider",
            file
        )
    }

    private fun calculateSHA256FileHash(updateFile: File?): String? {
        val digest: MessageDigest = try {
            MessageDigest.getInstance("SHA-256")
        } catch (e: NoSuchAlgorithmException) {
            Timber.d("Exception while getting digest", e)
            return null
        }
        val inputStream: InputStream = try {
            FileInputStream(updateFile)
        } catch (e: FileNotFoundException) {
            Timber.d("Exception while getting FileInputStream", e)
            return null
        }
        val buffer = ByteArray(8192)
        var read: Int
        return try {
            while (inputStream.read(buffer).also { read = it } > 0) {
                digest.update(buffer, 0, read)
            }
            val sha256sum: ByteArray = digest.digest()
            val bigInt = BigInteger(1, sha256sum)
            var output: String = bigInt.toString(16)
            // Fill to 32 chars
            output = String.format("%32s", output).replace(' ', '0')
            output
        } catch (e: IOException) {
            throw RuntimeException("Unable to process file for SHA-256", e)
        } finally {
            try {
                inputStream.close()
            } catch (e: IOException) {
                Timber.d("Exception on closing SHA-256 input stream", e)
            }
        }
    }

    @Throws(IOException::class)
    fun handleSamplingAndRotationBitmap(
        context: Context,
        selectedImage: Uri?,
        thumbnail: Boolean
    ): Bitmap? {
        // First decode with inJustDecodeBounds=true to check dimensions
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        var imageStream = context.contentResolver.openInputStream(selectedImage!!)
        BitmapFactory.decodeStream(imageStream, null, options)
        imageStream?.close()

        // Calculate inSampleSize
        if (thumbnail) {
            options.inSampleSize = calculateInSampleSize(options)
        }

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false
        imageStream = context.contentResolver.openInputStream(selectedImage)
        var img = BitmapFactory.decodeStream(imageStream, null, options)
        img = img?.let { rotateImageIfRequired(context, it, selectedImage) }
        return img
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
    ): Int {
        // Raw height and width of image
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > BITMAP_HEIGHT || width > BITMAP_WIDTH) {

            // Calculate ratios of height and width to requested height and width
            val heightRatio = (height.toFloat() / BITMAP_HEIGHT.toFloat()).roundToInt()
            val widthRatio = (width.toFloat() / BITMAP_WIDTH.toFloat()).roundToInt()

            // Choose the smallest ratio as inSampleSize value, this will guarantee a final image
            // with both dimensions larger than or equal to the requested height and width.
            inSampleSize = if (heightRatio < widthRatio) heightRatio else widthRatio

            // This offers some additional logic in case the image has a strange
            // aspect ratio. For example, a panorama may have a much larger
            // width than height. In these cases the total pixels might still
            // end up being too large to fit comfortably in memory, so we should
            // be more aggressive with sample down the image (=larger inSampleSize).
            val totalPixels = (width * height).toFloat()

            // Anything more than 2x the requested pixels we'll sample down further
            val totalReqPixelsCap = (BITMAP_WIDTH * BITMAP_HEIGHT * 2).toFloat()
            while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
                inSampleSize++
            }
        }
        return inSampleSize
    }

    @Throws(IOException::class)
    private fun rotateImageIfRequired(context: Context, img: Bitmap, selectedImage: Uri): Bitmap? {
        val input = context.contentResolver.openInputStream(selectedImage)
        val ei =
            input?.let { ExifInterface(it) }
        return when (ei?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270)
            else -> img
        }
    }

    fun sha256HashFromUri(
        activity: Activity,
        currentPhotoLocation: Uri,
        extension: String
    ): String? {
        val sha256FileHash: String?
        val inputStream =
            activity.contentResolver.openInputStream(currentPhotoLocation)
        sha256FileHash =
            calculateSHA256FileHash(copyStreamToFile(activity, inputStream!!, extension))

        return sha256FileHash
    }

    fun getFilePathUrl(fileId: Long): String {
        return "${BuildConfig.SERVER_URL}${Const.Networking.API_GET_FILE_FROM_ID}$fileId"
    }

    fun getRelativeTimeSpan(startDate: Long): CharSequence? {
        return DateUtils.getRelativeTimeSpanString(
            startDate, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
        )
    }

    fun generateRandomId(): String {
        return UUID.randomUUID().toString().substring(0, 13)
    }

    fun convertDurationMillis(time: Long): String {
        val millis: Long = time
        //val hour = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(
            TimeUnit.MILLISECONDS.toHours(
                time
            )
        )
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(
            TimeUnit.MILLISECONDS.toMinutes(
                time
            )

        )
        return String.format("%02d:%02d", minutes, seconds)

    }

    fun createCustomNotification(
        activity: Activity,
        title: String?,
        text: String?,
        imageUrl: String?,
        roomId: Int?
    ) {
        val remoteViews = RemoteViews(activity.packageName, R.layout.dialog_notification)
        remoteViews.setImageViewUri(R.id.iv_user_image, imageUrl?.toUri())
        remoteViews.setTextViewText(R.id.tv_title, title)
        remoteViews.setTextViewText(R.id.tv_message, text)

        val builder = NotificationCompat.Builder(activity, CHANNEL_ID)
            .setSmallIcon(R.drawable.img_spika_logo)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCustomContentView(remoteViews)
        with(NotificationManagerCompat.from(activity)) {
            // notificationId is a unique int for each notification that you must define
            roomId?.let { notify(it, builder.build()) }
        }
    }

    fun downloadFile(context: Context, message: Message) {
        try {
            val tmp = this.getFilePathUrl(message.body!!.fileId!!)
            val request = DownloadManager.Request(Uri.parse(tmp))
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            request.setTitle(message.body.file!!.fileName)
            request.setDescription("The file is downloading")
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                message.body.file!!.fileName
            )
            val manager =
                context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(context, "File is downloading", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Timber.d("$e")
        }
    }

    fun createTemporaryMessage(
        counter: Int,
        localUserId: Int?,
        roomId: Int,
        messageType: String,
        messageBody: MessageBody
    ): Message {
        // Time added will secure that the temporary items are at the bottom of the list
        var timeAdded = 100000
        if (counter > 0) {
            timeAdded += (counter + 1) * timeAdded
        }

        return Message(
            counter,
            localUserId,
            0,
            -1,
            0,
            roomId,
            messageType,
            messageBody,
            System.currentTimeMillis() + timeAdded,
            null,
            null,
            null,
            generateRandomId()
        )
    }

    fun fullDateFormat(dateTime: Long): String? {
        val simpleDateFormat = SimpleDateFormat("dd.MM.yyyy. HH:mm aa", Locale.getDefault())
        return simpleDateFormat.format(dateTime)
    }

    /**
     * Code generates video file in mp4 format vby decoding it piece by piece
     *
     * @param srcUri Source URI to be converted to mp4 format
     * @param dstPath Destination path for the converted video file
     * @param startMs Can be used for start time when trimming a video
     * @param endMs Can be used for end time when trimming video
     * @param useAudio Boolean which decides if new file will have audio
     * @param useVideo Boolean shich decides if new file will have video
     */
    @Throws(IOException::class)
    fun genVideoUsingMuxer(
        srcUri: Uri?,
        dstPath: String?,
        startMs: Long = 0L,
        endMs: Long = 0L,
        useAudio: Boolean = true,
        useVideo: Boolean = true
    ) {
        // Set up MediaExtractor to read from the source.
        val extractor = MediaExtractor()
        extractor.setDataSource(MainApplication.appContext, srcUri!!, null)
        val trackCount = extractor.trackCount
        // Set up MediaMuxer for the destination.
        val muxer = MediaMuxer(dstPath!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        // Set up the tracks and retrieve the max buffer size for selected
        // tracks.
        val indexMap: HashMap<Int, Int> = HashMap(trackCount)
        var bufferSize = -1
        for (i in 0 until trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            var selectCurrentTrack = false
            if (mime!!.startsWith("audio/") && useAudio) {
                selectCurrentTrack = true
            } else if (mime.startsWith("video/") && useVideo) {
                selectCurrentTrack = true
            }
            if (selectCurrentTrack) {
                extractor.selectTrack(i)
                val dstIndex = muxer.addTrack(format)
                indexMap[i] = dstIndex
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    val newSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                    bufferSize = newSize.coerceAtLeast(bufferSize)
                }
            }
        }
        if (bufferSize < 0) {
            bufferSize = DEFAULT_BUFFER_SIZE
        }
        // Set up the orientation and starting time for extractor.
        val retrieverSrc = MediaMetadataRetriever()
        retrieverSrc.setDataSource(MainApplication.appContext, srcUri)
        val degreesString = retrieverSrc.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
        )
        if (degreesString != null) {
            val degrees = degreesString.toInt()
            if (degrees >= 0) {
                muxer.setOrientationHint(degrees)
            }
        }

        if (startMs > 0) {
            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        }
        // Copy the samples from MediaExtractor to MediaMuxer. We will loop
        // for copying each sample and stop when we get to the end of the source
        // file or exceed the end time of the trimming.
        val offset = 0
        var trackIndex: Int
        val dstBuf: ByteBuffer = ByteBuffer.allocate(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()
        muxer.start()
        while (true) {
            bufferInfo.offset = offset
            bufferInfo.size = extractor.readSampleData(dstBuf, offset)
            if (bufferInfo.size < 0) {
                Log.d("LOGTAG", "Saw input EOS.")
                bufferInfo.size = 0
                break
            } else {
                bufferInfo.presentationTimeUs = extractor.sampleTime
                // Code used when trimming videos
                if (endMs > 0 && bufferInfo.presentationTimeUs > endMs * 1000L) {
                    Log.d("LOGTAG", "The current sample is over the trim end time.")
                    break
                } else {
                    bufferInfo.flags = extractor.sampleFlags
                    trackIndex = extractor.sampleTrackIndex
                    muxer.writeSampleData(
                        indexMap[trackIndex]!!, dstBuf,
                        bufferInfo
                    )
                    extractor.advance()
                }
            }
        }
        muxer.stop()
        muxer.release()
    }
}
