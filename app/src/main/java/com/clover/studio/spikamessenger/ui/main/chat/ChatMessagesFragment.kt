package com.clover.studio.spikamessenger.ui.main.chat

import android.Manifest
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.os.Parcelable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.clover.studio.spikamessenger.BuildConfig
import com.clover.studio.spikamessenger.MainApplication
import com.clover.studio.spikamessenger.R
import com.clover.studio.spikamessenger.data.models.FileData
import com.clover.studio.spikamessenger.data.models.FileMetadata
import com.clover.studio.spikamessenger.data.models.JsonMessage
import com.clover.studio.spikamessenger.data.models.entity.Message
import com.clover.studio.spikamessenger.data.models.entity.MessageAndRecords
import com.clover.studio.spikamessenger.data.models.entity.MessageRecords
import com.clover.studio.spikamessenger.data.models.entity.User
import com.clover.studio.spikamessenger.data.models.junction.RoomWithUsers
import com.clover.studio.spikamessenger.databinding.FragmentChatMessagesBinding
import com.clover.studio.spikamessenger.ui.main.chat.bottom_sheets.ChatBottomSheet
import com.clover.studio.spikamessenger.ui.main.chat.bottom_sheets.CustomReactionBottomSheet
import com.clover.studio.spikamessenger.ui.main.chat.bottom_sheets.DetailsBottomSheet
import com.clover.studio.spikamessenger.ui.main.chat.bottom_sheets.ForwardBottomSheet
import com.clover.studio.spikamessenger.ui.main.chat.bottom_sheets.MediaBottomSheet
import com.clover.studio.spikamessenger.ui.main.chat.bottom_sheets.ReactionsBottomSheet
import com.clover.studio.spikamessenger.ui.main.startMainActivity
import com.clover.studio.spikamessenger.utils.Const
import com.clover.studio.spikamessenger.utils.EventObserver
import com.clover.studio.spikamessenger.utils.MessageSwipeController
import com.clover.studio.spikamessenger.utils.Tools
import com.clover.studio.spikamessenger.utils.Tools.getFileMimeType
import com.clover.studio.spikamessenger.utils.dialog.ChooserDialog
import com.clover.studio.spikamessenger.utils.dialog.DialogError
import com.clover.studio.spikamessenger.utils.extendables.BaseFragment
import com.clover.studio.spikamessenger.utils.extendables.DialogInteraction
import com.clover.studio.spikamessenger.utils.helpers.ColorHelper
import com.clover.studio.spikamessenger.utils.helpers.FilesHelper
import com.clover.studio.spikamessenger.utils.helpers.FilesHelper.downloadFile
import com.clover.studio.spikamessenger.utils.helpers.MessageHelper
import com.clover.studio.spikamessenger.utils.helpers.Resource
import com.clover.studio.spikamessenger.utils.helpers.UploadService
import com.giphy.sdk.core.models.Media
import com.giphy.sdk.ui.GPHContentType
import com.giphy.sdk.ui.GPHSettings
import com.giphy.sdk.ui.Giphy
import com.giphy.sdk.ui.themes.GPHTheme
import com.giphy.sdk.ui.views.GiphyDialogFragment
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.vanniktech.emoji.EmojiPopup
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL


private const val SCROLL_DISTANCE_NEGATIVE = -300
private const val SCROLL_DISTANCE_POSITIVE = 300
private const val MIN_HEIGHT_DIFF = 150
private const val ROTATION_ON = 45f
private const val ROTATION_OFF = 0f
private const val NEW_MESSAGE_ANIMATION_DURATION = 300L

data class TempUri(
    val uri: Uri,
    val type: String,
)

@AndroidEntryPoint
class ChatMessagesFragment : BaseFragment(), ServiceConnection {
    private val viewModel: ChatViewModel by activityViewModels()
    private lateinit var bindingSetup: FragmentChatMessagesBinding

    private var messageSearchId: Int? = 0

    private var roomWithUsers: RoomWithUsers? = null
    private var user: User? = null
    private var messagesRecords: MutableList<MessageAndRecords> = mutableListOf()
    private var unsentMessages: MutableList<Message> = ArrayList()
    private lateinit var storedMessage: Message
    private var localUserId: Int = 0

    private lateinit var chatAdapter: ChatAdapter
    private var itemTouchHelper: ItemTouchHelper? = null
    private var valueAnimator: ValueAnimator? = null

    private var currentMediaLocation: MutableList<Uri> = ArrayList()
    private var filesSelected: MutableList<Uri> = ArrayList()
    private var thumbnailUris: MutableList<Uri> = ArrayList()
    private var tempFilesToCreate: MutableList<TempUri> = ArrayList()
    private var uploadFiles: ArrayList<FileData> = ArrayList()
    private var selectedFiles: MutableList<Uri> = ArrayList()
    private var uriPairList: MutableList<Pair<Uri, Uri>> = mutableListOf()
    private var temporaryMessages: MutableList<Message> = mutableListOf()

    private lateinit var fileUploadService: UploadService
    private var bound = false

    private var photoImageUri: Uri? = null
    private var isFetching = false
    private var directory: File? = null

    private lateinit var storagePermission: ActivityResultLauncher<String>
    private var exoPlayer: ExoPlayer? = null

    private var shouldScroll: Boolean = false
    private var listState: Parcelable? = null
    private var scrollYDistance = 0
    private var heightDiff = 0
    private var scrollToPosition = 0

    private var avatarFileId = 0L
    private var userName = ""

    private var isEditing = false
    private var editingMessage: Message? = null
    private var repliedMessage: Message? = null
    private var originalText = ""
    private var editedMessageId = 0
    private var replyId = 0L
    private var replyPosition = 0
    private var replySearchId: Int? = 0

    private lateinit var emojiPopup: EmojiPopup
    private var replyContainer: ReplyContainer? = null
    private var giphyDialog: GiphyDialogFragment? = null

    private var navOptionsBuilder: NavOptions? = null

    private val chooseFileContract =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
            if (!it.isNullOrEmpty()) {
                for (uri in it) {
                    selectedFiles.add(uri)
                    activity?.contentResolver?.takePersistableUriPermission(
                        uri,
                        FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                handleUserSelectedFile(selectedFiles)
            }
        }

    private val chooseImageContract =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
            if (!it.isNullOrEmpty()) {
                for (uri in it) {
                    selectedFiles.add(uri)
                    activity?.contentResolver?.takePersistableUriPermission(
                        uri,
                        FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                handleUserSelectedFile(selectedFiles)
            } else {
                Timber.d("Gallery error")
            }
        }

    private val takePhotoContract =
        registerForActivityResult(ActivityResultContracts.TakePicture()) {
            if (it) {
                if (photoImageUri != null) {
                    selectedFiles.add(photoImageUri!!)
                    handleUserSelectedFile(selectedFiles)
                } else {
                    Timber.d("Photo error")
                }
            } else Timber.d("Photo error")
        }

    private fun chooseFile() {
        chooseFileContract.launch(arrayOf(Const.JsonFields.FILE))
    }

    private fun chooseImage() {
        chooseImageContract.launch(arrayOf(Const.JsonFields.FILE))
    }

    private fun takePhoto() {
        photoImageUri = FileProvider.getUriForFile(
            requireContext(),
            BuildConfig.APPLICATION_ID + ".fileprovider",
            Tools.createImageFile(
                (requireActivity())
            )
        )
        takePhotoContract.launch(photoImageUri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreate(savedInstanceState)
        bindingSetup = FragmentChatMessagesBinding.inflate(layoutInflater)

        // Code below will handle an issue with system keyboard squeezing the background image
        // when opened. We are setting the background programmatically instead of using the
        // background resource in XML
        val drawable = ContextCompat.getDrawable(
            requireContext(),
            TypedValue().apply {
                activity?.theme?.resolveAttribute(
                    R.attr.backgroundPrimary,
                    this,
                    true
                )
            }.resourceId
        )
        activity?.window?.setBackgroundDrawable(drawable)

        // Giphy
        Giphy.configure(requireContext(), BuildConfig.GIPHY_API_KEY)
        Tools.giphyTheme(requireContext())
        val settings = GPHSettings(theme = GPHTheme.Custom)
        giphyDialog = GiphyDialogFragment.newInstance(settings)

        postponeEnterTransition()

        roomWithUsers = if (viewModel.roomWithUsers.value != null) {
            viewModel.roomWithUsers.value
        } else {
            activity?.intent!!.getParcelableExtra(Const.IntentExtras.ROOM_ID_EXTRA)
        }

        emojiPopup = EmojiPopup(
            rootView = bindingSetup.root,
            editText = bindingSetup.etMessage,
            theming = Tools.setEmojiViewTheme(requireContext())
        )

        replyContainer = ReplyContainer(requireContext(), null)
        navOptionsBuilder = Tools.createCustomNavOptions()

        return bindingSetup.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        if (listState != null) {
            shouldScroll = true
        }

        editingMessage?.let { handleMessageEdit(it) }
        repliedMessage?.let { handleMessageReply(it) }

        localUserId = viewModel.getLocalUserId()!!
        messageSearchId = viewModel.searchMessageId.value

        setUpAdapter()
        initializeObservers()
        initViews()
        initListeners()
        checkStoragePermission()
    }

    private fun initViews() = with(bindingSetup) {
        directory = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        clRoomExit.visibility =
            if (roomWithUsers?.room?.roomExit == true || roomWithUsers?.room?.deleted == true) {
                View.VISIBLE
            } else {
                View.GONE
            }

        if (Const.JsonFields.PRIVATE == roomWithUsers?.room?.type) {
            user =
                roomWithUsers?.users?.firstOrNull { user -> user.id.toString() != localUserId.toString() }
            avatarFileId = user?.avatarFileId ?: 0
            userName = user?.formattedDisplayName.toString()
        } else {
            avatarFileId = roomWithUsers?.room?.avatarFileId ?: 0
            userName = roomWithUsers?.room?.name.toString()
        }

        setUserName(userName = userName)
        setUserAvatar(avatarFileId = avatarFileId)

        if (Const.JsonFields.PRIVATE == roomWithUsers?.room?.type) {
            user =
                roomWithUsers?.users?.firstOrNull { user -> user.id.toString() != localUserId.toString() }
            avatarFileId = user?.avatarFileId ?: 0
            userName = user?.formattedDisplayName.toString()
            chatHeader.tvTitle.text = user?.telephoneNumber
        } else {
            avatarFileId = roomWithUsers?.room?.avatarFileId ?: 0
            userName = roomWithUsers?.room?.name.toString()
            chatHeader.tvTitle.text =
                getString(R.string.members_number, roomWithUsers?.users?.size.toString())
        }

        // Clear notifications for this room
        roomWithUsers?.room?.roomId?.let {
            NotificationManagerCompat.from(requireContext())
                .cancel(it)
        }
    }

    private fun setUserAvatar(avatarFileId: Long) = with(bindingSetup.chatHeader) {
        Glide.with(this@ChatMessagesFragment)
            .load(avatarFileId.let { Tools.getFilePathUrl(it) })
            .placeholder(
                roomWithUsers?.room?.type?.let { Tools.getPlaceholderImage(it) }?.let {
                    AppCompatResources.getDrawable(
                        requireContext(),
                        it
                    )
                }
            )
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(ivUserImage)
    }

    private fun setUserName(userName: String) {
        bindingSetup.chatHeader.tvChatName.text = userName
    }

    private fun initListeners() = with(bindingSetup) {
        chatHeader.clHeader.setOnClickListener {
            if (Const.JsonFields.PRIVATE == roomWithUsers?.room?.type) {
                roomWithUsers?.let { roomUsers ->
                    roomUsers.users.firstOrNull { user -> user.id != localUserId }?.let {
                        handleChatNavigation(it)
                    }
                }
            } else {
                val action =
                    ChatMessagesFragmentDirections.actionChatMessagesFragmentToChatDetailsFragment(
                        roomWithUsers = roomWithUsers!!
                    )
                findNavController().navigate(action, navOptionsBuilder)
            }
        }

        chatHeader.ivArrowBack.setOnClickListener {
            onBackArrowPressed()
        }

        val listOptions = mutableListOf(
            getString(R.string.choose_from_gallery) to { chooseImage() },
            getString(R.string.take_photo) to { takePhoto() },
            getString(R.string.cancel) to {}
        )

        ivCamera.setOnClickListener {
            ChooserDialog.getInstance(
                context = requireContext(),
                listChooseOptions = listOptions.map { it.first }.toMutableList(),
                object : DialogInteraction {
                    override fun onOptionClicked(optionName: String) {
                        listOptions.find { it.first == optionName }?.second?.invoke()
                    }
                }
            )
        }

        ivBtnEmoji.setOnClickListener {
            emojiPopup.toggle()
            emojiPopup.dismiss()
            ivAdd.rotation = ROTATION_OFF
        }

        ivGif.setOnClickListener {
            giphyDialog?.show(
                requireActivity().supportFragmentManager,
                Const.Giphy.GIPHY_BOTTOM_SHEET_TAG
            )
            giphyDialog?.gifSelectionListener = object : GiphyDialogFragment.GifSelectionListener {
                override fun didSearchTerm(term: String) {
                    // Ignore
                }

                override fun onDismissed(selectedContentType: GPHContentType) {
                    // Ignore
                }

                override fun onGifSelected(
                    media: Media,
                    searchTerm: String?,
                    selectedContentType: GPHContentType
                ) {
                    val gifUrl = media.images.original?.gifUrl.toString()
                    handleGifClick(requireContext(), gifUrl)
                }
            }
        }

        etMessage.setOnClickListener {
            if (emojiPopup.isShowing) emojiPopup.dismiss()
        }

        // This listener is for keyboard opening
        rvChat.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom) {
                if (sendingScrollVisibility()) {
                    rvChat.smoothScrollToPosition(0)
                }
            }
        }

        root.viewTreeObserver.addOnGlobalLayoutListener {
            heightDiff = root.rootView.height - root.height
        }

        cvNewMessages.setOnClickListener {
            rvChat.scrollToPosition(0)
            cvNewMessages.visibility = View.INVISIBLE
            scrollYDistance = 0
            viewModel.clearMessages()
        }

        cvBottomArrow.setOnClickListener {
            rvChat.scrollToPosition(0)
            cvBottomArrow.visibility = View.INVISIBLE
            scrollYDistance = 0
        }

        etMessage.addTextChangedListener {
            if (!isEditing) {
                if (it?.isNotEmpty() == true) {
                    showSendButton()
                    ivAdd.rotation = ROTATION_OFF
                } else {
                    hideSendButton()
                }
            } else {
                tvSave.visibility = View.VISIBLE
                ivButtonSend.visibility = View.INVISIBLE
                ivCamera.visibility = View.INVISIBLE
            }
        }

        ivButtonSend.setOnClickListener {
            vTransparent.visibility = View.GONE
            if (etMessage.text?.trim().toString().isNotEmpty()) {
                createTempTextMessage()
                sendMessage()
            }
            etMessage.setText("")
            hideSendButton()
            replyContainer?.closeBottomSheet()
            clSendingArea.setBackgroundColor(resources.getColor(android.R.color.transparent, null))
        }

        btnUnblock.setOnClickListener {
            DialogError.getInstance(requireContext(),
                getString(R.string.unblock_user),
                getString(R.string.unblock_description),
                getString(R.string.cancel),
                getString(R.string.yes),
                object : DialogInteraction {
                    override fun onSecondOptionClicked() {
                        roomWithUsers?.users!!.firstOrNull { user -> user.id != localUserId }
                            ?.let { user -> viewModel.deleteBlockForSpecificUser(user.id) }
                    }
                })
        }

        tvSave.setOnClickListener {
            if (originalText != bindingSetup.etMessage.text.toString()) {
                editMessage()
            }
            resetEditingFields()
        }

        ivAdd.setOnClickListener {
            if (!isEditing) {
                val mediaBottomSheet = MediaBottomSheet(requireContext())
                mediaBottomSheet.show(
                    requireActivity().supportFragmentManager,
                    MediaBottomSheet.TAG
                )
                mediaBottomSheet.setActionListener(object :
                    MediaBottomSheet.BottomSheetMediaAction {
                    override fun chooseFileAction() {
                        chooseFile()
                    }
                })
            } else {
                resetEditingFields()
            }
        }
    }

    fun handleGifClick(context: Context, urlString: String) {
        Toast.makeText(context, getString(R.string.preparing_gifs), Toast.LENGTH_LONG).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(urlString)
                val connection = withContext(Dispatchers.IO) {
                    url.openConnection()
                } as HttpURLConnection

                connection.doInput = true
                connection.connect()

                val inputStream: InputStream = connection.inputStream

                val gifName = "gif_${System.currentTimeMillis()}.gif"
                val file = saveFile(context, inputStream, gifName)

                selectedFiles.add(Uri.fromFile(file))
                handleUserSelectedFile(selectedFiles)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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


    private fun initializeObservers() {
        viewModel.messageSendListener.observe(viewLifecycleOwner, EventObserver {
            when (it.status) {
                Resource.Status.SUCCESS -> {
                    Timber.d("Message send success")
                    if (unsentMessages.isNotEmpty()) {
                        val message =
                            unsentMessages.find { msg -> msg.localId == it.responseData?.data?.message?.localId }
                        unsentMessages.remove(message)
                    }
                }

                Resource.Status.ERROR -> Timber.d("Message send fail: $it")
                else -> Timber.d("Other error")
            }
        })

        viewModel.getMessageAndRecords(roomId = roomWithUsers!!.room.roomId)
            .observe(viewLifecycleOwner) {
                when (it.status) {
                    Resource.Status.SUCCESS -> {
                        messagesRecords.clear()
                        if (it.responseData?.isNotEmpty() == true) {
                            it.responseData.forEach { msg ->
                                messagesRecords.add(msg)
                            }

                            chatAdapter.submitList(messagesRecords.toList())
                            updateSwipeController()

                            if (listState == null && scrollYDistance == 0) {
                                bindingSetup.rvChat.scrollToPosition(0)
                            }
                        } else chatAdapter.submitList(messagesRecords.toList())

                        chatAdapter.notifyItemRangeChanged(0, messagesRecords.size)

                        (view?.parent as? ViewGroup)?.doOnPreDraw {
                            startPostponedEnterTransition()
                        }

                        if (listState != null && shouldScroll) {
                            bindingSetup.rvChat.layoutManager?.onRestoreInstanceState(listState)
                            shouldScroll = false
                        }

                        // If the reply is reply id != 0, it means that the search for the reply message was started and
                        // was not found in the first set of 20 messages.
                        // Other messages should be searched.
                        if (replyPosition != 0) {
                            if (messagesRecords.firstOrNull { messageAndRecords -> messageAndRecords.message.id == replySearchId } != null) {
                                val position =
                                    messagesRecords.indexOfFirst { messageAndRecords -> messageAndRecords.message.id == replySearchId }
                                scrollToPosition = position
                                if (position != -1) {
                                    bindingSetup.rvChat.smoothScrollToPosition(position)
                                    chatAdapter.setSelectedPosition(position)
                                }

                                replySearchId = 0
                                viewModel.searchMessageId.value = 0
                            } else {
                                viewModel.fetchNextSet(roomWithUsers?.room!!.roomId)
                            }
                        }

                        // If messageSearchId is not 0, it means the user navigated via message
                        // search. For now, we will just fetch next sets of data until we find
                        // the correct message id in the adapter to navigate to.
                        if (messageSearchId != 0) {
                            if (messagesRecords.firstOrNull { messageAndRecords -> messageAndRecords.message.id == messageSearchId } != null) {
                                val position =
                                    messagesRecords.indexOfFirst { messageAndRecords -> messageAndRecords.message.id == messageSearchId }
                                scrollToPosition = position
                                if (position != -1) {
                                    bindingSetup.rvChat.smoothScrollToPosition(position)
                                    chatAdapter.setSelectedPosition(position)
                                }

                                messageSearchId = 0
                                viewModel.searchMessageId.value = 0
                            } else {
                                viewModel.fetchNextSet(roomWithUsers?.room!!.roomId)
                            }
                        }
                    }

                    Resource.Status.LOADING -> Timber.d("Message get loading")
                    else -> Timber.d("Message get error")
                }
            }

        viewModel.messagesReceived.observe(viewLifecycleOwner) { messages ->
            val receivedMessages = messages.filter {
                it.roomId == roomWithUsers?.room!!.roomId
                        && it.fromUserId != localUserId
            }
            if (receivedMessages.isNotEmpty()) {
                showNewMessage(receivedMessages.size)
                // Notify backend of messages seen
                viewModel.sendMessagesSeen(roomWithUsers?.room!!.roomId)
            }
        }

        if (Const.JsonFields.PRIVATE == roomWithUsers?.room?.type) {
            viewModel.blockedUserListListener().observe(viewLifecycleOwner) {
                if (it?.isNotEmpty() == true) {
                    viewModel.fetchBlockedUsersLocally(it)
                }
            }

            viewModel.blockedListListener.observe(viewLifecycleOwner, EventObserver {
                when (it.status) {
                    Resource.Status.SUCCESS -> {
                        Timber.d("Blocked users fetched successfully")
                        if (it.responseData != null) {
                            val containsElement =
                                roomWithUsers!!.users.any { user -> it.responseData.find { blockedUser -> blockedUser.id == user.id } != null }
                            if (Const.JsonFields.PRIVATE == roomWithUsers!!.room.type) {
                                if (containsElement) {
                                    bindingSetup.llContactBlocked.visibility = View.VISIBLE
                                } else bindingSetup.llContactBlocked.visibility = View.GONE
                            }
                        } else bindingSetup.llContactBlocked.visibility = View.GONE
                    }

                    Resource.Status.ERROR -> {
                        Timber.d("Failed to fetch blocked users")
                        context?.let { context ->
                            DialogError.getInstance(
                                context,
                                getString(R.string.error),
                                it.message,
                                null,
                                getString(R.string.ok),
                                object : DialogInteraction {
                                    override fun onSecondOptionClicked() {
                                        // Ignore
                                    }
                                })
                        }
                    }

                    else -> Timber.d("Other error")
                }
            })
        }

        viewModel.roomNameUpdated.observe(viewLifecycleOwner, EventObserver {
            if (it != null) setUserName(userName = it)
            roomWithUsers?.room?.name = it
        })

        viewModel.roomAvatarUploaded.observe(viewLifecycleOwner, EventObserver {
            if (it != 0L) setUserAvatar(avatarFileId = it)
            roomWithUsers?.room?.avatarFileId = it
        })

        viewModel.roomNumberUpdated.observe(viewLifecycleOwner) {
            if (it != 0) bindingSetup.chatHeader.tvTitle.text =
                getString(R.string.members_number, it.toString())
        }

        viewModel.forwardListener.observe(viewLifecycleOwner, EventObserver {
            if (it.responseData?.data?.messages?.first()?.roomId != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    val room = it.responseData.data.messages.first().roomId?.let { it1 ->
                        viewModel.getRoomUsers(roomId = it1)
                    }

                    if (room != null && room.room.roomId != roomWithUsers?.room?.roomId) {
                        startChatScreenActivity(
                            requireActivity(),
                            room,
                            0
                        )
                        requireActivity().finish()
                    }
                }
            }
        })

        viewModel.sendMessagesSeen(roomId = roomWithUsers!!.room.roomId)
        viewModel.updateUnreadCount(roomId = roomWithUsers!!.room.roomId)
    }


    private fun showNewMessage(messagesSize: Int) = with(bindingSetup) {
        valueAnimator?.end()
        valueAnimator?.removeAllUpdateListeners()
        cvBottomArrow.visibility = View.INVISIBLE

        // If we received message and keyboard is open:
        if (heightDiff >= MIN_HEIGHT_DIFF && scrollYDistance > SCROLL_DISTANCE_POSITIVE) {
            scrollYDistance -= heightDiff
        }

        if (!(sendingScrollVisibility())) {
            cvNewMessages.visibility = View.VISIBLE

            if (messagesSize == 1 && cvBottomArrow.visibility == View.INVISIBLE) {
                tvNewMessage.text =
                    getString(R.string.new_messages, messagesSize.toString(), "").trim()

                valueAnimator = ValueAnimator.ofInt(cvBottomArrow.width, tvNewMessage.width).apply {
                    addUpdateListener { valueAnimator ->
                        val layoutParams = cvNewMessages.layoutParams
                        layoutParams.width = valueAnimator.animatedValue as Int
                        cvNewMessages.layoutParams = layoutParams
                    }
                    duration = NEW_MESSAGE_ANIMATION_DURATION
                }
                valueAnimator?.start()

            } else {
                tvNewMessage.text =
                    getString(R.string.new_messages, messagesSize.toString(), "s").trim()
            }
        } else if (messagesSize > 0) {
            rvChat.scrollToPosition(0)
            viewModel.clearMessages()
        }
        return@with
    }

    private fun sendingScrollVisibility(): Boolean {
        return ((scrollYDistance <= 0) && (scrollYDistance > SCROLL_DISTANCE_NEGATIVE) || (scrollYDistance >= 0) && (scrollYDistance < SCROLL_DISTANCE_POSITIVE))
    }

    private fun showBottomArrow() = with(bindingSetup) {
        if (heightDiff >= MIN_HEIGHT_DIFF && scrollYDistance > SCROLL_DISTANCE_POSITIVE) {
            scrollYDistance -= heightDiff
        }
        // If we are somewhere up
        if (!sendingScrollVisibility()) {
            if (cvNewMessages.visibility == View.VISIBLE) {
                fadeOutArrow()
            } else {
                fadeInArrow()
            }
        } else {
            fadeOutArrow()
        }
    }

    private fun fadeInArrow() = with(bindingSetup) {
        if (cvBottomArrow.visibility == View.INVISIBLE) {
            cvBottomArrow.visibility = View.VISIBLE
            val fadeInAnimation = AlphaAnimation(0f, 1f)
            fadeInAnimation.duration = NEW_MESSAGE_ANIMATION_DURATION
            cvBottomArrow.startAnimation(fadeInAnimation)
        }
    }

    private fun fadeOutArrow() = with(bindingSetup) {
        if (cvBottomArrow.visibility == View.VISIBLE) {
            val fadeOutAnimation = AlphaAnimation(1f, 0f)
            fadeOutAnimation.duration = NEW_MESSAGE_ANIMATION_DURATION
            fadeOutAnimation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {
                }

                override fun onAnimationEnd(animation: Animation?) {
                    cvBottomArrow.visibility = View.INVISIBLE
                }

                override fun onAnimationRepeat(animation: Animation?) {
                }
            })
            cvBottomArrow.startAnimation(fadeOutAnimation)
        }
    }

    private fun setUpAdapter() = with(bindingSetup) {
        exoPlayer = ExoPlayer.Builder(requireContext()).build()

        chatAdapter = ChatAdapter(
            requireContext(),
            localUserId,
            roomWithUsers!!.users,
            exoPlayer!!,
            roomWithUsers!!.room.type,
            onMessageInteraction = { event, message ->
                if (!roomWithUsers!!.room.deleted && !roomWithUsers!!.room.roomExit) {
                    run {
                        when (event) {
                            Const.UserActions.DOWNLOAD_FILE -> handleDownloadFile(message)
                            Const.UserActions.CANCEL_UPLOAD -> handleUploadCancel(message.message)
                            Const.UserActions.MESSAGE_ACTION -> handleMessageAction(message)
                            Const.UserActions.MESSAGE_REPLY -> handleMessageReplyClick(message)
                            Const.UserActions.RESEND_MESSAGE -> handleMessageResend(message)
                            Const.UserActions.SHOW_MESSAGE_REACTIONS -> handleShowReactions(message)
                            Const.UserActions.NAVIGATE_TO_MEDIA_FRAGMENT -> handleMediaNavigation(
                                message
                            )

                            Const.UserActions.NAVIGATE_TO_USER_DETAILS -> handleUserDetailsNavigation(
                                message.message
                            )

                            else -> Timber.d("No other action currently")
                        }
                    }
                }
            }
        )

        val linearLayoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, true)
        rvChat.apply {
            adapter = chatAdapter
            itemAnimator = null
            linearLayoutManager.stackFromEnd = true
            layoutManager = linearLayoutManager
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    scrollYDistance += dy

                    val lastVisiblePosition = linearLayoutManager.findLastVisibleItemPosition()
                    val totalItemCount = linearLayoutManager.itemCount
                    if (lastVisiblePosition == totalItemCount - 1 && !isFetching) {
                        Timber.d("Fetching next batch of data")
                        viewModel.fetchNextSet(roomWithUsers!!.room.roomId)
                        isFetching = true
                    } else if (lastVisiblePosition != totalItemCount - 1) {
                        isFetching = false // Reset the flag when user scrolls away from the bottom
                    }
                    showBottomArrow()
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    // This condition checks if the RecyclerView is at the bottom
                    if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                        cvNewMessages.visibility = View.INVISIBLE
                        cvBottomArrow.visibility = View.INVISIBLE
                        scrollYDistance = 0
                        viewModel.clearMessages()
                    }
                }
            })
        }

        setUpAdapterDataObserver()
    }

    private fun setUpAdapterDataObserver() = with(bindingSetup) {
        val adapterDataObserver = object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                scrollToBottom(positionStart, itemCount)
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                scrollToBottom(positionStart, itemCount)
            }
        }
        chatAdapter.registerAdapterDataObserver(adapterDataObserver)
    }

    private fun scrollToBottom(positionStart: Int, itemCount: Int) = with(bindingSetup) {
        val scrollingIndex = positionStart + itemCount - 1
        if (scrollingIndex >= 0) {
            if (sendingScrollVisibility()) {
                rvChat.smoothScrollToPosition(0)
                scrollYDistance = 0
                cvBottomArrow.visibility = View.INVISIBLE
            } else {
                cvBottomArrow.visibility = View.VISIBLE
            }
        }
    }

    private fun updateSwipeController() {
        itemTouchHelper?.attachToRecyclerView(null)
        val messageSwipeController =
            MessageSwipeController(
                requireContext(),
                messagesRecords,
                onSwipeAction = { action, position ->
                    when (action) {
                        Const.UserActions.ACTION_RIGHT -> {
                            handleMessageReply(messagesRecords[position].message)
                        }

                        Const.UserActions.ACTION_LEFT -> {
                            messagesRecords.find { it.message.id == messagesRecords[position].message.id }
                                ?.let {
                                    DetailsBottomSheet(
                                        context = requireContext(),
                                        roomWithUsers = roomWithUsers!!,
                                        messagesRecords = it,
                                        localUserId = localUserId
                                    )
                                }?.show(
                                    requireActivity().supportFragmentManager,
                                    DetailsBottomSheet.TAG
                                )
                        }
                    }
                })

        itemTouchHelper = ItemTouchHelper(messageSwipeController)
        if (!roomWithUsers!!.room.deleted && !roomWithUsers!!.room.roomExit) itemTouchHelper!!.attachToRecyclerView(
            bindingSetup.rvChat
        )
    }

    private fun handleMessageReply(message: Message) = with(bindingSetup) {
        if (isEditing) {
            resetEditingFields()
        }

        flReplyContainer.removeAllViews()
        flReplyContainer.addView(replyContainer)

        replyContainer?.setReplyContainerListener(object : ReplyContainer.ReplyContainerListener {
            override fun closeSheet() {
                clSendingArea.setBackgroundColor(
                    resources.getColor(
                        android.R.color.transparent,
                        null
                    )
                )
                // We need to reset replyId in this case, otherwise it will just persist on the
                // next message you're sending.
                replyId = 0L
            }
        })

        repliedMessage = message
        replyId = message.id.toLong()

        roomWithUsers?.let { roomWithUsers ->
            repliedMessage?.let { repliedMessage ->
                replyContainer?.setReactionContainer(repliedMessage, roomWithUsers)
            }
        }

        clSendingArea.setBackgroundColor(ColorHelper.getSecondAdditionalColor(requireContext()))
    }

    private fun handleMessageReplyClick(msg: MessageAndRecords) {
        replySearchId = msg.message.referenceMessage?.id
        replyPosition =
            messagesRecords.indexOfFirst { it.message.id == msg.message.referenceMessage?.id }

        if (replyPosition == -1) {
            viewModel.fetchNextSet(roomWithUsers!!.room.roomId)
        } else {
            bindingSetup.rvChat.smoothScrollToPosition(replyPosition)
            chatAdapter.setSelectedPosition(replyPosition)
        }
    }

    private fun handleMessageResend(message: MessageAndRecords) {
        val listOptions = mutableListOf(
            getString(R.string.resend_message) to { resendMessage(message = message.message) },
            getString(R.string.cancel) to { }
        )

        ChooserDialog.getInstance(
            context = requireContext(),
            listChooseOptions = listOptions.map { it.first }.toMutableList(),
            object : DialogInteraction {
                override fun onOptionClicked(optionName: String) {
                    listOptions.find { it.first == optionName }?.second?.invoke()
                }
            }
        )
    }

    private fun handleShowReactions(msg: MessageAndRecords) {
        val reactionsBottomSheet =
            ReactionsBottomSheet(requireContext(), msg, roomWithUsers!!, localUserId)
        reactionsBottomSheet.setReactionListener(object : ReactionsBottomSheet.ReactionsAction {
            override fun deleteReaction(reaction: MessageRecords?) {
                reaction?.id?.let { viewModel.deleteReaction(it) }
            }
        })
        reactionsBottomSheet.show(
            requireActivity().supportFragmentManager,
            ReactionsBottomSheet.TAG
        )
    }

    private fun handleMessageAction(msg: MessageAndRecords) = with(bindingSetup) {
        if (msg.message.deleted == null || msg.message.deleted == true) {
            return
        }

        itemTouchHelper?.attachToRecyclerView(null)

        hideKeyboard(root)

        val bottomSheet = ChatBottomSheet(msg.message, localUserId)
        bottomSheet.setActionListener(object : ChatBottomSheet.BottomSheetAction {
            override fun actionCopy() {
                Tools.handleCopyAction(msg.message.body?.text.toString())
            }

            override fun actionClose() {
                rotationAnimation()
            }

            override fun actionDelete() {
                showDeleteMessageDialog(msg.message)
            }

            override fun actionEdit() {
                replyContainer?.let {
                    if (it.isReplyBottomSheetVisible()) {
                        it.closeBottomSheet()
                    }
                }
                editingMessage = msg.message
                editingMessage?.let { handleMessageEdit(it) }
            }

            override fun actionReply() {
                repliedMessage = msg.message
                repliedMessage?.let { handleMessageReply(it) }
            }

            override fun actionDetails() {
                messagesRecords.find { it.message.id == msg.message.id }?.let {
                    DetailsBottomSheet(
                        context = requireContext(),
                        roomWithUsers = roomWithUsers!!,
                        messagesRecords = it,
                        localUserId = localUserId
                    )
                }?.show(
                    requireActivity().supportFragmentManager,
                    DetailsBottomSheet.TAG
                )
            }

            override fun actionDownload() {
                downloadFile(
                    context = requireContext(),
                    message = msg.message
                )
            }

            override fun actionReaction(reaction: String) {
                if (reaction.isNotEmpty()) {
                    msg.message.reaction = reaction
                    addReaction(msg.message)
                    chatAdapter.notifyItemChanged(msg.message.messagePosition)
                }
            }

            override fun actionAddCustomReaction() {
                val customReactionBottomSheet =
                    CustomReactionBottomSheet(context = requireContext())
                customReactionBottomSheet.setCustomReactionListener(object :
                    CustomReactionBottomSheet.BottomSheetCustomReactionListener {
                    override fun addCustomReaction(emoji: String) {
                        msg.message.reaction = emoji
                        addReaction(msg.message)
                    }
                })
                customReactionBottomSheet.show(
                    requireActivity().supportFragmentManager,
                    CustomReactionBottomSheet.TAG
                )
            }

            override fun actionForward() {
                val forwardBottomSheet = ForwardBottomSheet(context = requireContext(), localUserId)
                forwardBottomSheet.setForwardListener(object :
                    ForwardBottomSheet.BottomSheetForwardAction {
                    override fun forward(userIds: ArrayList<Int>, roomIds: ArrayList<Int>) {
                        val jsonObject = JsonObject()

                        val rooms = JsonArray()
                        roomIds.forEach { room ->
                            rooms.add(room)
                        }

                        val users = JsonArray()
                        userIds.forEach { user ->
                            users.add(user)
                        }

                        val message = JsonArray()
                        message.add(msg.message.id)

                        jsonObject.add(Const.JsonFields.ROOM_IDS, rooms)
                        jsonObject.add(Const.JsonFields.USER_IDS, users)
                        // For now we only send one message
                        jsonObject.add(Const.JsonFields.MESSAGE_IDS, message)

                        Timber.d("Json object: $jsonObject")

                        viewModel.forwardMessage(jsonObject, roomIds.size == 1 || userIds.size == 1)
                    }
                })
                forwardBottomSheet.show(
                    requireActivity().supportFragmentManager,
                    ForwardBottomSheet.TAG
                )
            }

            override fun sheetDismissed() {
                updateSwipeController()
            }
        })
        bottomSheet.show(requireActivity().supportFragmentManager, ChatBottomSheet.TAG)
    }

    private fun handleDownloadFile(message: MessageAndRecords) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            when {
                context?.let {
                    ContextCompat.checkSelfPermission(
                        it,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                } == PackageManager.PERMISSION_GRANTED -> {
                    downloadFile(requireContext(), message.message)
                }

                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    // TODO show why permission is needed
                }

                else -> {
                    storedMessage = message.message
                    storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        } else downloadFile(requireContext(), message.message)
    }

    private fun handleUploadCancel(message: Message) {
        DialogError.getInstance(requireActivity(),
            getString(R.string.warning),
            getString(R.string.cancel_upload),
            getString(R.string.back),
            getString(R.string.ok),
            object : DialogInteraction {
                override fun onFirstOptionClicked() {
                    // Ignore
                }

                override fun onSecondOptionClicked() {
                    viewModel.cancelUploadFile(messageId = message.localId.toString())
                    viewModel.deleteLocalMessage(message = message)
                    viewModel.updateUnreadCount(roomId = roomWithUsers!!.room.roomId)
                }
            })
    }

    private fun handleMediaNavigation(chatMessage: MessageAndRecords) {
        val action =
            ChatMessagesFragmentDirections.actionChatMessagesFragmentToMediaFragment(
                message = chatMessage.message,
                localUserId = localUserId,
                roomWithUsers = roomWithUsers
            )
        findNavController().navigate(action, navOptionsBuilder)
    }

    private fun handleUserDetailsNavigation(message: Message) {
        roomWithUsers?.let { roomWithUsers ->
            roomWithUsers.users.firstOrNull { roomUser ->
                roomUser.id == message.fromUserId
            }?.let { user ->
                handleChatNavigation(user)
            }
        }
    }

    private fun handleChatNavigation(user: User) {
        val privateGroupUser = Tools.transformUserToPrivateGroupChat(user)
        val bundle =
            bundleOf(
                Const.Navigation.USER_PROFILE to privateGroupUser,
                Const.Navigation.ROOM_ID to roomWithUsers?.room?.roomId,
                Const.Navigation.ROOM_DATA to roomWithUsers?.room
            )
        findNavController().navigate(
            R.id.action_chatMessagesFragment_to_contactDetailsFragment,
            bundle,
            navOptionsBuilder
        )
    }

    private fun handleMessageEdit(message: Message) = with(bindingSetup) {
        isEditing = true
        originalText = message.body?.text.toString()
        editedMessageId = message.id
        etMessage.setText(message.body?.text.toString())
        ivAdd.rotation = ROTATION_ON
    }

    private fun addReaction(message: Message) {
        val jsonObject = JsonObject()
        jsonObject.addProperty(Const.Networking.MESSAGE_ID, message.id)
        jsonObject.addProperty(Const.JsonFields.TYPE, Const.JsonFields.REACTION)
        jsonObject.addProperty(Const.JsonFields.REACTION, message.reaction)
        viewModel.sendReaction(jsonObject)
    }

    private fun resetEditingFields() = with(bindingSetup) {
        editedMessageId = 0
        isEditing = false
        originalText = ""
        ivAdd.rotation = ROTATION_OFF
        tvSave.visibility = View.GONE
        ivCamera.visibility = View.VISIBLE
        etMessage.text!!.clear()
        etMessage.setText("")
    }

    private fun rotationAnimation() = with(bindingSetup) {
        vTransparent.visibility = View.GONE
        ivAdd.rotation = ROTATION_OFF
    }

    private fun showSendButton() = with(bindingSetup) {
        ivCamera.visibility = View.INVISIBLE
        ivButtonSend.visibility = View.VISIBLE
        clTyping.updateLayoutParams<ConstraintLayout.LayoutParams> {
            endToStart = ivButtonSend.id
        }
        ivAdd.rotation = ROTATION_OFF
    }

    private fun hideSendButton() = with(bindingSetup) {
        ivCamera.visibility = View.VISIBLE
        ivButtonSend.visibility = View.GONE
        clTyping.updateLayoutParams<ConstraintLayout.LayoutParams> {
            endToStart = ivCamera.id
        }
        ivAdd.rotation = ROTATION_OFF
    }

    private fun showDeleteMessageDialog(message: Message) {
        val listOptions = mutableListOf(
            getString(R.string.delete_for_everyone) to {
                deleteMessage(
                    message,
                    Const.UserActions.DELETE_MESSAGE_ALL
                )
            },
            getString(R.string.delete_for_me) to {
                deleteMessage(
                    message,
                    Const.UserActions.DELETE_MESSAGE_ME
                )
            },
            getString(R.string.cancel) to { }
        )

        ChooserDialog.getInstance(
            context = requireContext(),
            listChooseOptions = listOptions.map { it.first }.toMutableList(),
            object : DialogInteraction {
                override fun onOptionClicked(optionName: String) {
                    listOptions.find { it.first == optionName }?.second?.invoke()
                }
            }
        )
    }

    private fun deleteMessage(message: Message, target: String) {
        viewModel.deleteMessage(message.id, target)
        val deletedMessage =
            messagesRecords.firstOrNull { it.message.localId == message.localId }
        chatAdapter.notifyItemChanged(messagesRecords.indexOf(deletedMessage))
    }

    private fun editMessage() {
        if (editedMessageId != 0) {
            val jsonObject = JsonObject()
            jsonObject.addProperty(
                Const.JsonFields.TEXT_TYPE,
                bindingSetup.etMessage.text.toString().trim()
            )
            viewModel.editMessage(editedMessageId, jsonObject)
        }
    }

    private fun resendMessage(message: Message) {
        when (message.type) {
            Const.JsonFields.TEXT_TYPE -> {
                try {
                    sendMessage(
                        text = message.body?.text!!,
                        localId = message.localId.toString(),
                    )
                } catch (e: Exception) {
                    Timber.d("Send message exception: $e")
                }
            }

            Const.JsonFields.GIF_TYPE -> {
                val resendUri = message.originalUri?.toUri() ?: return
                selectedFiles.add(resendUri)
                handleUserSelectedFile(selectedFiles)

                viewModel.deleteLocalMessage(message)
            }

            Const.JsonFields.FILE_TYPE, Const.JsonFields.AUDIO_TYPE -> {
                val resendUri = message.body?.file?.uri?.toUri() ?: return
                selectedFiles.add(resendUri)
                handleUserSelectedFile(selectedFiles)

                viewModel.deleteLocalMessage(message)
            }

            Const.JsonFields.IMAGE_TYPE, Const.JsonFields.VIDEO_TYPE -> {
                val resendUri = message.originalUri?.toUri() ?: return
                selectedFiles.add(resendUri)
                handleUserSelectedFile(selectedFiles)

                viewModel.deleteLocalMessage(message)
            }
        }
    }

    private fun sendMessage() {
        try {
            sendMessage(
                text = bindingSetup.etMessage.text.toString().trim(),
                localId = unsentMessages.first().localId!!,
            )
        } catch (e: Exception) {
            Timber.d("Send message exception: $e")
        }
    }

    private fun sendMessage(
        text: String,
        localId: String,
    ) {
        val jsonMessage = JsonMessage(
            msgText = text,
            mimeType = Const.JsonFields.TEXT_TYPE,
            fileId = 0,
            thumbId = 0,
            roomId = roomWithUsers?.room?.roomId,
            localId = localId,
            replyId = replyId
        )

        val jsonObject = jsonMessage.messageToJson()

        viewModel.sendMessage(jsonObject, localId)

        if (replyId != 0L) {
            replyId = 0L
        }
    }

    private fun createTempTextMessage() {
        val tempMessage = roomWithUsers?.let {
            MessageHelper.createTempTextMessage(
                text = bindingSetup.etMessage.text.toString().trim(),
                roomId = it.room.roomId,
                localUserId = localUserId,
                unsentMessages = unsentMessages
            )
        }

        tempMessage?.let {
            unsentMessages.add(0, tempMessage)
            viewModel.storeMessageLocally(tempMessage)
        }
    }

    /** Files uploading */
    private fun handleUserSelectedFile(selectedFilesUris: MutableList<Uri>) {
        selectedFiles.forEach { uri ->
            val fileMimeType = getFileMimeType(requireContext(), uri)
            when {
                fileMimeType.contains(Const.JsonFields.GIF) -> {
                    filesSelected.add(uri)
                    tempFilesToCreate.add(TempUri(uri, fileMimeType))
                }

                fileMimeType.contains(Const.JsonFields.IMAGE_TYPE) ||
                        fileMimeType.contains(Const.JsonFields.VIDEO_TYPE) &&
                        !Tools.forbiddenMimeTypes(fileMimeType) -> {
                    convertMedia(uri, fileMimeType)
                }

                else -> {
                    filesSelected.add(uri)
                    tempFilesToCreate.add(TempUri(uri, Const.JsonFields.FILE_TYPE))
                }
            }
        }

        sendFile()

        if (bound) {
            CoroutineScope(Dispatchers.Default).launch {
                fileUploadService.uploadItems(uploadFiles)
                uploadFiles.clear()
            }
        } else {
            startUploadService()
        }

        selectedFilesUris.clear()
        tempFilesToCreate.clear()
    }

    private fun convertMedia(uri: Uri, fileMimeType: String) {
        val thumbnailUri: Uri
        val fileUri: Uri

        if (fileMimeType.contains(Const.JsonFields.VIDEO_TYPE)) {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(context, uri)

            val duration =
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
            val bitRate =
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLong() ?: 0

            if (Tools.getVideoSize(duration, bitRate)) {
                Toast.makeText(context, getString(R.string.video_error), Toast.LENGTH_LONG).show()
                return
            }

            val bitmap = mmr.frameAtTime
            val fileName = "VIDEO-${System.currentTimeMillis()}.mp4"
            val file =
                File(context?.getExternalFilesDir(Environment.DIRECTORY_MOVIES), fileName)

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
            thumbnailUri = Tools.convertBitmapToUri(requireActivity(), thumbnail)
            tempFilesToCreate.add(TempUri(thumbnailUri, Const.JsonFields.VIDEO_TYPE))
            uriPairList.add(Pair(uri, thumbnailUri))

            mmr.release()
        } else {
            val bitmap =
                Tools.handleSamplingAndRotationBitmap(requireActivity(), uri, false)
            fileUri = Tools.convertBitmapToUri(requireActivity(), bitmap!!)

            val thumbnail =
                Tools.handleSamplingAndRotationBitmap(requireActivity(), fileUri, true)
            thumbnailUri = Tools.convertBitmapToUri(requireActivity(), thumbnail!!)
            tempFilesToCreate.add(TempUri(thumbnailUri, Const.JsonFields.IMAGE_TYPE))
        }

        uriPairList.add(Pair(uri, thumbnailUri))
        thumbnailUris.add(thumbnailUri)
        currentMediaLocation.add(fileUri)
    }

    private fun sendFile() {
        if (tempFilesToCreate.isNotEmpty()) {
            tempFilesToCreate.forEach { tempFile ->
                createTempFileMessage(tempFile.uri, tempFile.type)
            }
            tempFilesToCreate.clear()

            if (temporaryMessages.isNotEmpty()) {
                for (unsentMessage in temporaryMessages) {
                    if (Const.JsonFields.IMAGE_TYPE == unsentMessage.type ||
                        Const.JsonFields.VIDEO_TYPE == unsentMessage.type
                    ) {
                        // Send thumbnail
                        uploadFiles(
                            isThumbnail = true,
                            uri = thumbnailUris.first(),
                            localId = unsentMessage.localId!!,
                            metadata = unsentMessage.body?.file?.metaData
                        )
                        // Send original image
                        uploadFiles(
                            isThumbnail = false,
                            uri = currentMediaLocation.first(),
                            localId = unsentMessage.localId,
                            metadata = unsentMessage.body?.file?.metaData
                        )
                        currentMediaLocation.removeFirst()
                        thumbnailUris.removeFirst()
                    } else if (filesSelected.isNotEmpty()) {
                        // Send file or gif
                        val uri = if (Const.JsonFields.GIF_TYPE == unsentMessage.type) {
                            unsentMessage.localId?.let {
                                Tools.renameGif(
                                    filesSelected.first(),
                                    it
                                )
                            } ?: filesSelected.first()
                        } else {
                            filesSelected.first()
                        }

                        unsentMessage.localId?.let {
                            uploadFiles(
                                isThumbnail = false,
                                uri = uri,
                                localId = it,
                                metadata = null
                            )
                        }

                        filesSelected.removeFirst()
                    }
                }
                temporaryMessages.clear()
            }
        }
    }

    private fun createTempFileMessage(uri: Uri, type: String) {
        val tempMessage = roomWithUsers?.room?.let {
            FilesHelper.createTempFile(
                uri = uri,
                type = type,
                localUserId = localUserId,
                roomId = it.roomId,
                unsentMessages = unsentMessages
            )
        }

        if (tempMessage != null) {
            temporaryMessages.add(tempMessage)
            unsentMessages.add(tempMessage)
            viewModel.storeMessageLocally(tempMessage)
        }
    }

    private fun uploadFiles(
        isThumbnail: Boolean,
        uri: Uri,
        localId: String,
        metadata: FileMetadata?,
    ) {
        val uploadData: MutableList<FileData> = ArrayList()
        roomWithUsers?.let {
            FilesHelper.uploadFile(
                isThumbnail = isThumbnail,
                uri = uri,
                localId = localId,
                roomId = it.room.roomId,
                metadata = metadata
            )?.let { fileData ->
                uploadData.add(
                    fileData
                )
            }
            uploadFiles.addAll(uploadData)
        }
    }

    private fun checkStoragePermission() {
        storagePermission =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                if (it) {
                    context?.let { context -> downloadFile(context, storedMessage) }
                } else {
                    Timber.d("Couldn't download file. No permission granted.")
                }
            }
    }

    private fun onBackArrowPressed() {
        viewModel.updateUnreadCount(roomId = roomWithUsers!!.room.roomId)
        activity?.onBackPressedDispatcher?.onBackPressed()
        activity?.finish()

        startMainActivity(requireActivity())
    }

    override fun onResume() {
        super.onResume()
        viewModel.getBlockedUsersList()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (exoPlayer != null) {
            exoPlayer!!.release()
        }
        viewModel.unregisterSharedPrefsReceiver()

        if (bound) {
            requireActivity().unbindService(serviceConnection)
        }
        bound = false
    }

    override fun onPause() {
        super.onPause()
        listState = bindingSetup.rvChat.layoutManager?.onSaveInstanceState()
    }

    /** Upload service */
    private fun startUploadService() {
        val intent = Intent(MainApplication.appContext, UploadService::class.java)
        MainApplication.appContext.startService(intent)
        activity?.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private val serviceConnection = this
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        bound = true

        val binder = service as UploadService.UploadServiceBinder
        fileUploadService = binder.getService()
        fileUploadService.setCallbackListener(object : UploadService.FileUploadCallback {
            override fun updateUploadProgressBar(
                progress: Int,
                maxProgress: Int,
                localId: String?
            ) {
                val message = messagesRecords.firstOrNull { it.message.localId == localId }
                message!!.message.uploadProgress = (progress * 100) / maxProgress

                if (isVisible || isResumed) {
                    requireActivity().runOnUiThread {
                        chatAdapter.notifyItemChanged(
                            messagesRecords.indexOf(
                                message
                            )
                        )
                    }
                }
            }

            override fun uploadingFinished(uploadedFiles: MutableList<FileData>) {
                Tools.deleteTemporaryMedia(requireContext())
                context?.cacheDir?.deleteRecursively()

                if (uploadedFiles.isNotEmpty()) {
                    uploadedFiles.forEach { item ->
                        if (Resource.Status.ERROR == item.messageStatus || Resource.Status.LOADING == item.messageStatus ||
                            item.messageStatus == null
                        ) {
                            if (!item.isThumbnail) {
                                viewModel.updateMessages(
                                    messageStatus = Resource.Status.ERROR.toString(),
                                    localId = item.localId.toString()
                                )

                                if (item.fileUri.toString().contains(Const.JsonFields.GIF)) {
                                    viewModel.updateLocalUri(
                                        localId = item.localId.toString(),
                                        uri = item.fileUri.toString()
                                    )
                                }
                            } else {
                                val resendUri = uriPairList.find { it.second == item.fileUri }
                                viewModel.updateLocalUri(
                                    localId = item.localId.toString(),
                                    uri = resendUri?.first.toString(),
                                )
                            }
                        } else {
                            uriPairList.removeIf { it.second == item.fileUri }
                        }
                    }
                    uriPairList.clear()
                    uploadedFiles.clear()
                    unsentMessages.clear()
                    return
                }
            }
        })

        CoroutineScope(Dispatchers.Default).launch {
            fileUploadService.uploadItems(uploadFiles)
            uploadFiles.clear()
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Timber.d("Service disconnected")
    }
}
