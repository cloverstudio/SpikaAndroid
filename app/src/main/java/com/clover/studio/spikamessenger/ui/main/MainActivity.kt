package com.clover.studio.spikamessenger.ui.main

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.Parcelable
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.asLiveData
import com.clover.studio.spikamessenger.MainApplication
import com.clover.studio.spikamessenger.R
import com.clover.studio.spikamessenger.data.models.FileData
import com.clover.studio.spikamessenger.data.models.entity.Message
import com.clover.studio.spikamessenger.databinding.ActivityMainBinding
import com.clover.studio.spikamessenger.ui.main.chat.ChatViewModel
import com.clover.studio.spikamessenger.ui.main.chat.bottom_sheets.ChatSelectorBottomSheet
import com.clover.studio.spikamessenger.ui.onboarding.startOnboardingActivity
import com.clover.studio.spikamessenger.utils.AppPermissions
import com.clover.studio.spikamessenger.utils.AppPermissions.notificationPermission
import com.clover.studio.spikamessenger.utils.Const
import com.clover.studio.spikamessenger.utils.EventObserver
import com.clover.studio.spikamessenger.utils.Tools
import com.clover.studio.spikamessenger.utils.dialog.DialogError
import com.clover.studio.spikamessenger.utils.extendables.BaseActivity
import com.clover.studio.spikamessenger.utils.extendables.DialogInteraction
import com.clover.studio.spikamessenger.utils.helpers.FilesHelper
import com.clover.studio.spikamessenger.utils.helpers.MediaHelper
import com.clover.studio.spikamessenger.utils.helpers.PhonebookService
import com.clover.studio.spikamessenger.utils.helpers.Resource
import com.clover.studio.spikamessenger.utils.helpers.TempUri
import com.clover.studio.spikamessenger.utils.helpers.UploadService
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL


fun startMainActivity(fromActivity: Activity) = fromActivity.apply {
    startActivity(Intent(fromActivity as Context, MainActivity::class.java))
    finish()
}

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()
    private lateinit var bindingSetup: ActivityMainBinding
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private var phonebookService: Intent? = null

    private var tempFilesToCreate: MutableList<TempUri> = ArrayList()
    private var uriPairList: MutableList<Pair<Uri, Uri>> = mutableListOf()
    private var thumbnailUris: MutableList<Uri> = ArrayList()
    private var currentMediaLocation: MutableList<Uri> = ArrayList()
    private var uploadFiles: ArrayList<FileData> = ArrayList()
    private var filesSelected: MutableList<Uri> = ArrayList()
    private var unsentMessages: MutableList<Message> = ArrayList()

    private var lastReceivedIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bindingSetup = ActivityMainBinding.inflate(layoutInflater)

        val view = bindingSetup.root
        setContentView(view)

        checkNotificationPermission()
        initializeObservers()
        sendPushTokenToServer()
        startPhonebookService()

        if ((Intent.ACTION_SEND == intent.action) && intent != null) {
            val uri = intent?.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
            if (uri != null) handleReceivedData(intent, uri, arrayListOf())
        } else if (Intent.ACTION_SEND_MULTIPLE == intent.action && intent != null) {
            val multipleUri = intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
            handleReceivedData(intent, null, multipleUri)
        }
    }

    private fun handleReceivedData(intent: Intent, uri: Uri?, multipleUri: ArrayList<Parcelable>?) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (uri != null){
                    if (Const.JsonFields.TEXT_PREFIX == intent.type) {
                        // TODO Handle text being sent
                    } else if (intent.type?.startsWith(Const.JsonFields.IMAGE_PREFIX) == true ||
                        intent.type?.startsWith(Const.JsonFields.VIDEO_PREFIX) == true
                    ) {
                        // Sending single image
                        val fileMimeType = uri.let { Tools.getFileMimeType(applicationContext, it) }
                        if ((fileMimeType?.contains(Const.JsonFields.IMAGE_TYPE) == true ||
                                    fileMimeType?.contains(Const.JsonFields.VIDEO_TYPE) == true) &&
                            !Tools.forbiddenMimeTypes(fileMimeType)
                        ) {
                            MediaHelper.convertMedia(
                                context = applicationContext,
                                uri = uri,
                                fileMimeType = Tools.getFileMimeType(applicationContext, uri),
                                tempFilesToCreate = tempFilesToCreate,
                                uriPairList = uriPairList,
                                thumbnailUris = thumbnailUris,
                                currentMediaLocation = currentMediaLocation
                            )
                        }
                    } else if (intent.type?.startsWith(Const.JsonFields.FILE_PREFIX) == true) {
                        // Sending single file
                        filesSelected.add(uri)
                        tempFilesToCreate.add(TempUri(uri, Const.JsonFields.FILE_TYPE))
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (multipleUri != null){
                    Timber.d("Multiple uris: $multipleUri")
                    multipleUri.forEach {
                        it as Uri
                        Timber.d("It:::::: $it")
                        MediaHelper.convertMedia(
                            context = applicationContext,
                            uri = it,
                            fileMimeType = Tools.getFileMimeType(applicationContext, it),
                            tempFilesToCreate = tempFilesToCreate,
                            uriPairList = uriPairList,
                            thumbnailUris = thumbnailUris,
                            currentMediaLocation = currentMediaLocation
                        )

                    }
                }
            }
            else -> {
                Timber.d("Error")
            }
        }

        val chatSelectorBottomSheet =
            viewModel.getLocalUserId()?.let {
                ChatSelectorBottomSheet(
                    context = this,
                    localId = it,
                    title = getString(R.string.share_message)
                )
            }

        chatSelectorBottomSheet?.setForwardListener(object :
            ChatSelectorBottomSheet.BottomSheetForwardAction {
            override fun forward(userIds: ArrayList<Int>, roomIds: ArrayList<Int>) {

                if (roomIds.isEmpty()){
                    // TODO make new rooms with userIds
                    Timber.d("Empty list")

                } else {
                    sendFile(roomIds)
                    Timber.d("Upload files: $uploadFiles")
                    startUploadService(uploadFiles)
                }
                uploadFiles.clear()
            }
        })

        chatSelectorBottomSheet?.show(
            this.supportFragmentManager,
            ChatSelectorBottomSheet.TAG
        )
    }

    private fun sendFile(roomIds: ArrayList<Int>) {
        if (tempFilesToCreate.isNotEmpty()) {
            for (tempFile in tempFilesToCreate) {
                createTempFileMessage(tempFile.uri, tempFile.type, roomIds.first())
            }

            Timber.d("Temp files: $tempFilesToCreate")
            tempFilesToCreate.clear()

            Timber.d("Thumbnail uris: $thumbnailUris")

            // Crash
            FilesHelper.sendFiles(
                unsentMessages = unsentMessages,
                uploadFiles = uploadFiles,
                temporaryMessages = mutableListOf(),
                filesSelected = filesSelected,
                thumbnailUris =thumbnailUris,
                currentMediaLocation = currentMediaLocation,
                roomId = roomIds.first()
            )
        }
    }

    private fun createTempFileMessage(uri: Uri, type: String, roomId: Int) {
        val tempMessage = viewModel.getLocalUserId()?.let {
            FilesHelper.createTempMessage(
                uri = uri,
                type = type,
                localUserId = it,
                roomId = roomId,
                unsentMessages = unsentMessages
            )
        }

        if (tempMessage != null) {
            Timber.d("Temp created message:$tempMessage")
            unsentMessages.add(tempMessage)
            chatViewModel.storeMessageLocally(tempMessage)
        }
    }

    private fun startPhonebookService() {
        if (ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Timber.d("Starting phonebook service")
            phonebookService = Intent(this, PhonebookService::class.java)
            startService(phonebookService)
        }
    }

    private fun initializeObservers() {
        viewModel.newMessageReceivedListener.observe(this, EventObserver { message ->
            message.responseData?.roomId?.let {
                viewModel.getRoomWithUsers(
                    it,
                    message.responseData
                )
            }
        })

        viewModel.deleteUserListener.observe(this, EventObserver {
            when (it.status) {
                Resource.Status.SUCCESS -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        Tools.clearUserData(this@MainActivity)
                    }
                }

                Resource.Status.ERROR -> Timber.d("Delete user failed")
                else -> Timber.d("Other error")
            }
        })

        viewModel.tokenExpiredListener.observe(this, EventObserver { tokenExpired ->
            if (tokenExpired) {
                DialogError.getInstance(this,
                    getString(R.string.warning),
                    getString(R.string.session_expired),
                    null,
                    getString(R.string.ok),
                    object : DialogInteraction {
                        override fun onFirstOptionClicked() {
                            // Ignore
                        }

                        override fun onSecondOptionClicked() {
                            viewModel.setTokenExpiredFalse()
                            viewModel.removeToken()
                            startOnboardingActivity(this@MainActivity, false)
                        }
                    })
            }
        })

        viewModel.isInTeamMode()
    }

    private fun checkNotificationPermission() {
        notificationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                if (!it) {
                    Timber.d("Couldn't send notifications. No permission granted.")
                }
            }

        if (
            ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // ignore
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) || shouldShowRequestPermissionRationale(
                Manifest.permission.POST_NOTIFICATIONS
            )
        ) {
            // TODO show why permission is needed
        } else {
            if (AppPermissions.hasPostNotificationPermission()) {
                notificationPermissionLauncher.launch(notificationPermission)
            }
        }
    }

    private fun sendPushTokenToServer() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                return@OnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result
            val jsonObject = JsonObject()

            jsonObject.addProperty(Const.JsonFields.PUSH_TOKEN, token)

            viewModel.updatePushToken(jsonObject)
            Timber.d("Token: $token")
        })
    }

    private fun startUploadService(files: ArrayList<FileData>) {
        val intent = Intent(MainApplication.appContext, UploadService::class.java)
        intent.putParcelableArrayListExtra(Const.IntentExtras.FILES_EXTRA, files)
        MainApplication.appContext.startService(intent)
        this.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private lateinit var fileUploadService: UploadService
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as UploadService.UploadServiceBinder
            fileUploadService = binder.getService()
            fileUploadService.setCallbackListener(object : UploadService.FileUploadCallback {
                override fun updateUploadProgressBar(
                    progress: Int,
                    maxProgress: Int,
                    localId: String?
                ) {
                    Timber.d("Uploading")
                }

                override fun uploadingFinished(uploadedFiles: MutableList<FileData>) {
                    Timber.d("Finished")
                    Tools.deleteTemporaryMedia(applicationContext)
                    applicationContext?.cacheDir?.deleteRecursively()
                    uriPairList.clear()
                    uploadedFiles.clear()
                    unsentMessages.clear()
                }
            })
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Timber.d("Service disconnected")
        }
    }

    override fun onStart() {
        super.onStart()
        Timber.d("First SSE launch = ${viewModel.checkIfFirstSSELaunch()}")
        if (viewModel.checkIfFirstSSELaunch()) {
            viewModel.getPushNotificationStream().asLiveData().observe(this) {}
        }

        viewModel.getUnreadCount()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (phonebookService != null) {
            stopService(phonebookService)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        // Handle the new intent when the activity is already running
        if (Intent.ACTION_SEND == intent?.action) {
            val uri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
            if (uri != null) handleReceivedData(intent, uri, arrayListOf())
        } else if (Intent.ACTION_SEND_MULTIPLE == intent?.action) {
            val multipleUri = intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
            handleReceivedData(intent, null, multipleUri)
        }
    }

    override fun onResume() {
        super.onResume()
        lastReceivedIntent = intent
    }
}
