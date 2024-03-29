package com.clover.studio.spikamessenger.ui.main.chat

import android.Manifest
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.Target
import com.clover.studio.spikamessenger.BuildConfig
import com.clover.studio.spikamessenger.MainApplication
import com.clover.studio.spikamessenger.R
import com.clover.studio.spikamessenger.data.models.FileData
import com.clover.studio.spikamessenger.data.models.FileMetadata
import com.clover.studio.spikamessenger.data.models.JsonMessage
import com.clover.studio.spikamessenger.data.models.entity.Message
import com.clover.studio.spikamessenger.data.models.entity.MessageAndRecords
import com.clover.studio.spikamessenger.data.models.entity.MessageBody
import com.clover.studio.spikamessenger.data.models.entity.MessageRecords
import com.clover.studio.spikamessenger.data.models.entity.User
import com.clover.studio.spikamessenger.data.models.junction.RoomWithUsers
import com.clover.studio.spikamessenger.databinding.FragmentChatMessagesBinding
import com.clover.studio.spikamessenger.ui.ReactionContainer
import com.clover.studio.spikamessenger.ui.ReactionsContainer
import com.clover.studio.spikamessenger.utils.Const
import com.clover.studio.spikamessenger.utils.EventObserver
import com.clover.studio.spikamessenger.utils.MessageSwipeController
import com.clover.studio.spikamessenger.utils.Tools
import com.clover.studio.spikamessenger.utils.Tools.getFileMimeType
import com.clover.studio.spikamessenger.utils.dialog.ChooserDialog
import com.clover.studio.spikamessenger.utils.dialog.DialogError
import com.clover.studio.spikamessenger.utils.extendables.BaseFragment
import com.clover.studio.spikamessenger.utils.extendables.DialogInteraction
import com.clover.studio.spikamessenger.utils.helpers.FilesHelper
import com.clover.studio.spikamessenger.utils.helpers.FilesHelper.getUniqueRandomId
import com.clover.studio.spikamessenger.utils.helpers.Resource
import com.clover.studio.spikamessenger.utils.helpers.UploadService
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.gson.JsonObject
import com.vanniktech.emoji.EmojiPopup
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.io.File

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
class ChatMessagesFragment : BaseFragment(), ChatOnBackPressed {
    private val viewModel: ChatViewModel by activityViewModels()
    private lateinit var bindingSetup: FragmentChatMessagesBinding

    private var messageSearchId: Int? = 0

    private lateinit var roomWithUsers: RoomWithUsers
    private var user: User? = null
    private var messagesRecords: MutableList<MessageAndRecords> = mutableListOf()
    private var unsentMessages: MutableList<Message> = ArrayList()
    private lateinit var storedMessage: Message
    private var localUserId: Int = 0

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var detailsMessageAdapter: MessageDetailsAdapter
    private lateinit var messageReactionAdapter: MessageReactionAdapter
    var itemTouchHelper: ItemTouchHelper? = null
    private var valueAnimator: ValueAnimator? = null

    private var currentMediaLocation: MutableList<Uri> = ArrayList()
    private var filesSelected: MutableList<Uri> = ArrayList()
    private var thumbnailUris: MutableList<Uri> = ArrayList()
    private var tempFilesToCreate: MutableList<TempUri> = ArrayList()
    private var uploadFiles: ArrayList<FileData> = ArrayList()
    private var selectedFiles: MutableList<Uri> = ArrayList()
    private var uriPairList: MutableList<Pair<Uri, Uri>> = mutableListOf()

    private lateinit var fileUploadService: UploadService

    private var photoImageUri: Uri? = null
    private var isFetching = false
    private var directory: File? = null

    private var isAdmin = false
    private var listState: Parcelable? = null

    private lateinit var bottomSheetBehaviour: BottomSheetBehavior<ConstraintLayout>
    private lateinit var bottomSheetMessageActions: BottomSheetBehavior<ConstraintLayout>
    private lateinit var bottomSheetReplyAction: BottomSheetBehavior<ConstraintLayout>
    private lateinit var bottomSheetDetailsAction: BottomSheetBehavior<ConstraintLayout>
    private lateinit var bottomSheetReactionsAction: BottomSheetBehavior<ConstraintLayout>
    private lateinit var bottomSheets: List<BottomSheetBehavior<ConstraintLayout>>

    private lateinit var storagePermission: ActivityResultLauncher<String>
    private var exoPlayer: ExoPlayer? = null
    private var shouldScroll: Boolean = false

    private var avatarFileId = 0L
    private var userName = ""
    private var isEditing = false
    private var originalText = ""
    private var editedMessageId = 0
    private var replyId: Long? = 0L
    private lateinit var emojiPopup: EmojiPopup

    private var scrollYDistance = 0
    private var heightDiff = 0

    private var scrollToPosition = 0

    private val chooseFileContract =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
            if (!it.isNullOrEmpty()) {
                for (uri in it) {
                    selectedFiles.add(uri)
                }
                handleUserSelectedFile(selectedFiles)
            }
        }

    private val chooseImageContract =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
            if (!it.isNullOrEmpty()) {
                for (uri in it) {
                    selectedFiles.add(uri)
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
            context!!,
            BuildConfig.APPLICATION_ID + ".fileprovider",
            Tools.createImageFile(
                (activity!!)
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

        if (listState != null) {
            shouldScroll = true
        }
        bindingSetup = FragmentChatMessagesBinding.inflate(layoutInflater)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        bottomSheetBehaviour = BottomSheetBehavior.from(bindingSetup.bottomSheet.root)
        bottomSheetMessageActions = BottomSheetBehavior.from(bindingSetup.messageActions.root)
        bottomSheetReplyAction = BottomSheetBehavior.from(bindingSetup.replyAction.root)
        bottomSheetDetailsAction = BottomSheetBehavior.from(bindingSetup.detailsAction.root)
        bottomSheetReactionsAction = BottomSheetBehavior.from(bindingSetup.reactionsDetails.root)

        bottomSheets = listOf(
            bottomSheetReactionsAction,
            bottomSheetBehaviour,
            bottomSheetDetailsAction,
            bottomSheetMessageActions
        )

        localUserId = viewModel.getLocalUserId()!!
        messageSearchId = (activity as ChatScreenActivity).searchMessageId

        emojiPopup = EmojiPopup(bindingSetup.root, bindingSetup.etMessage)

        viewModel.getSingleRoomData((activity as ChatScreenActivity).roomId)

        checkStoragePermission()

        viewModel.roomDataListener.observe(viewLifecycleOwner, EventObserver {
            when (it.status) {
                Resource.Status.SUCCESS -> {
                    roomWithUsers = it.responseData?.roomWithUsers!!

                    if (Const.JsonFields.PRIVATE == roomWithUsers.room.type) {
                        user =
                            roomWithUsers.users.firstOrNull { user -> user.id.toString() != localUserId.toString() }
                    }

                    if (roomWithUsers.room.roomExit || roomWithUsers.room.deleted) {
                        bindingSetup.clRoomExit.visibility = View.VISIBLE
                    } else {
                        bindingSetup.clRoomExit.visibility = View.GONE
                        setUpMessageDetailsAdapter()
                        setUpMessageReactionAdapter()
                        checkIsUserAdmin()
                    }
                    initializeObservers()
                    initViews()
                    initListeners()
                    setUpAdapter()

                    // Clear notifications for this room
                    NotificationManagerCompat.from(requireContext())
                        .cancel(roomWithUsers.room.roomId)
                }

                Resource.Status.ERROR -> Toast.makeText(
                    activity,
                    getString(R.string.failed_to_load_room_data),
                    Toast.LENGTH_SHORT
                ).show()

                else -> Toast.makeText(
                    activity,
                    getString(R.string.other_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        return bindingSetup.root
    }

    private fun initViews() {
        directory = context!!.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (Const.JsonFields.PRIVATE == roomWithUsers.room.type) {
            avatarFileId = user?.avatarFileId ?: 0
            userName = user?.formattedDisplayName.toString()
        } else {
            avatarFileId = roomWithUsers.room.avatarFileId ?: 0
            userName = roomWithUsers.room.name.toString()
        }

        setAvatarAndName(avatarFileId, userName)

        if (roomWithUsers.room.roomExit || roomWithUsers.room.deleted) {
            bindingSetup.chatHeader.ivVideoCall.setImageResource(R.drawable.img_video_call_disabled)
            bindingSetup.chatHeader.ivCallUser.setImageResource(R.drawable.img_call_user_disabled)
            bindingSetup.chatHeader.ivVideoCall.isEnabled = false
            bindingSetup.chatHeader.ivCallUser.isEnabled = false
        }

        bindingSetup.chatHeader.tvTitle.text = roomWithUsers.room.type
    }

    private fun setAvatarAndName(avatarFileId: Long, userName: String) {
        bindingSetup.chatHeader.tvChatName.text = userName
        Glide.with(this)
            .load(avatarFileId.let { Tools.getFilePathUrl(it) })
            .placeholder(
                AppCompatResources.getDrawable(
                    requireContext(),
                    R.drawable.img_user_placeholder
                )
            )
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(bindingSetup.chatHeader.ivUserImage)
    }

    private fun checkIsUserAdmin() {
        isAdmin = roomWithUsers.users.any { user ->
            user.id == localUserId && viewModel.isUserAdmin(roomWithUsers.room.roomId, user.id)
        }
    }

    private fun initListeners() = with(bindingSetup) {
        chatHeader.clHeader.setOnClickListener {
            if (Const.JsonFields.PRIVATE == roomWithUsers.room.type) {
                val bundle =
                    bundleOf(
                        Const.Navigation.USER_PROFILE to roomWithUsers.users.firstOrNull { user -> user.id != localUserId },
                        Const.Navigation.ROOM_ID to roomWithUsers.room.roomId,
                    )
                findNavController().navigate(
                    R.id.action_chatMessagesFragment_to_contactDetailsFragment,
                    bundle
                )
            } else {
                val action =
                    ChatMessagesFragmentDirections.actionChatMessagesFragmentToChatDetailsFragment(
                        roomWithUsers,
                        isAdmin
                    )
                findNavController().navigate(action)
            }
        }

        chatHeader.ivArrowBack.setOnClickListener {
            onBackArrowPressed()
        }

        ivCamera.setOnClickListener {
            ChooserDialog.getInstance(context!!,
                getString(R.string.placeholder_title),
                null,
                getString(R.string.choose_from_gallery),
                getString(R.string.take_photo),
                object : DialogInteraction {
                    override fun onFirstOptionClicked() {
                        chooseImage()
                    }

                    override fun onSecondOptionClicked() {
                        takePhoto()
                    }
                })
        }

        bindingSetup.ivBtnEmoji.setOnClickListener {
            emojiPopup.toggle()
            emojiPopup.dismiss()
            emojiPopup.isShowing
            ivAdd.rotation = ROTATION_OFF
        }

        etMessage.setOnClickListener {
            if (emojiPopup.isShowing) {
                emojiPopup.dismiss()
            }
        }

        // This listener is for keyboard opening
        rvChat.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom) {
                if ((scrollYDistance <= 0) && (scrollYDistance > SCROLL_DISTANCE_NEGATIVE)
                    || (scrollYDistance >= 0) && (scrollYDistance < SCROLL_DISTANCE_POSITIVE)
                ) {
                    bindingSetup.rvChat.smoothScrollToPosition(0)
                }
            }
        }

        root.viewTreeObserver.addOnGlobalLayoutListener {
            heightDiff = bindingSetup.root.rootView.height - bindingSetup.root.height
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
                    bindingSetup.ivAdd.rotation = ROTATION_OFF
                } else {
                    hideSendButton()
                }
            }
        }

        ivButtonSend.setOnClickListener {
            vHideTyping.visibility = View.GONE
            vTransparent.visibility = View.GONE
            if (bindingSetup.etMessage.text?.trim().toString().isNotEmpty()) {
                createTempTextMessage()
                sendMessage()
            }
            etMessage.setText("")
            hideSendButton()
            bottomSheetReplyAction.state = BottomSheetBehavior.STATE_COLLAPSED
            bindingSetup.clBottomReplyAction.visibility = View.GONE
        }

        tvUnblock.setOnClickListener {
            DialogError.getInstance(requireContext(),
                getString(R.string.unblock_user),
                getString(R.string.unblock_description, bindingSetup.chatHeader.tvChatName.text),
                getString(R.string.no),
                getString(R.string.unblock),
                object : DialogInteraction {
                    override fun onSecondOptionClicked() {
                        roomWithUsers.users.firstOrNull { user -> user.id != localUserId }
                            ?.let { user -> viewModel.deleteBlockForSpecificUser(user.id) }
                    }
                })
        }

        tvSave.setOnClickListener {
            editMessage()
            resetEditingFields()
        }

        initBottomSheetsListeners()
    }

    private fun initBottomSheetsListeners() = with(bindingSetup) {
        // Initial visibility of bottom sheets
        clBottomSheet.visibility = View.GONE
        clBottomMessageActions.visibility = View.GONE
        clDetailsAction.visibility = View.GONE
        clBottomReplyAction.visibility = View.GONE
        clReactionsDetails.visibility = View.GONE

        // Bottom sheet listeners
        ivAdd.setOnClickListener {
            if (bottomSheetReplyAction.state == BottomSheetBehavior.STATE_EXPANDED) {
                replyId = 0L
                bottomSheetReplyAction.state = BottomSheetBehavior.STATE_COLLAPSED
                clBottomReplyAction.visibility = View.GONE
            }
            if (!isEditing) {
                if (bottomSheetBehaviour.state != BottomSheetBehavior.STATE_EXPANDED) {
                    ivAdd.rotation = ROTATION_ON
                    bottomSheetBehaviour.state = BottomSheetBehavior.STATE_EXPANDED
                    vTransparent.visibility = View.VISIBLE
                    clBottomSheet.visibility = View.VISIBLE
                }
            } else {
                resetEditingFields()
            }
        }

        bottomSheet.btnFiles.setOnClickListener {
            chooseFile()
            rotationAnimation()
        }

        messageActions.ivRemove.setOnClickListener {
            closeMessageSheet()
        }

        reactionsDetails.ivRemove.setOnClickListener {
            bottomSheetReactionsAction.state = BottomSheetBehavior.STATE_COLLAPSED
            bindingSetup.clReactionsDetails.visibility = View.GONE
        }

        replyAction.ivRemove.setOnClickListener {
            if (bottomSheetReplyAction.state == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetReplyAction.state = BottomSheetBehavior.STATE_COLLAPSED
                bindingSetup.clBottomReplyAction.visibility = View.GONE
                replyId = 0L
            }
        }

        detailsAction.ivRemove.setOnClickListener {
            if (bottomSheetDetailsAction.state == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetDetailsAction.state = BottomSheetBehavior.STATE_COLLAPSED
                bindingSetup.clDetailsAction.visibility = View.GONE
                bindingSetup.vTransparent.visibility = View.GONE
            }
        }

        bottomSheet.ivRemove.setOnClickListener {
            bottomSheetBehaviour.state = BottomSheetBehavior.STATE_COLLAPSED
            bindingSetup.clBottomSheet.visibility = View.GONE
            rotationAnimation()
        }

        bottomSheet.btnLibrary.setOnClickListener {
            chooseImage()
            rotationAnimation()
        }

        bottomSheet.btnLocation.setOnClickListener {
            rotationAnimation()
        }

        bottomSheet.btnContact.setOnClickListener {
            rotationAnimation()
        }


        val bottomSheetBehaviorCallback =
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    // Ignore
                }

                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                        bindingSetup.vTransparent.visibility = View.GONE
                    }
                    if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                        bindingSetup.vTransparent.visibility = View.VISIBLE
                    }

                }
            }

        val bottomSheetBehaviorCallbackMessageAction =
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    // Ignore
                }

                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (bottomSheetDetailsAction.state == BottomSheetBehavior.STATE_COLLAPSED) {
                        if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                            bindingSetup.vTransparent.visibility = View.GONE
                        }
                    }
                    if (bottomSheetReplyAction.state == BottomSheetBehavior.STATE_EXPANDED) {
                        if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                            bindingSetup.vTransparent.visibility = View.VISIBLE
                        }
                    }
                }
            }

        val bottomSheetBehaviorCallbackReactionDetails =
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    // Ignore
                }

                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                        bindingSetup.vTransparent.visibility = View.GONE
                        val childNUmber = bindingSetup.reactionsDetails.llReactions.childCount
                        if (childNUmber != 0) {
                            bindingSetup.reactionsDetails.llReactions.removeViews(
                                1,
                                childNUmber - 1
                            )
                        }
                    }
                    if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                        bindingSetup.vTransparent.visibility = View.VISIBLE
                    }
                }
            }

        bottomSheetMessageActions.addBottomSheetCallback(bottomSheetBehaviorCallbackMessageAction)
        bottomSheetDetailsAction.addBottomSheetCallback(bottomSheetBehaviorCallback)
        bottomSheetBehaviour.addBottomSheetCallback(bottomSheetBehaviorCallback)
        bottomSheetReplyAction.addBottomSheetCallback(bottomSheetBehaviorCallback)
        bottomSheetReactionsAction.addBottomSheetCallback(bottomSheetBehaviorCallbackReactionDetails)
    }

    private fun closeMessageSheet() {
        bottomSheetMessageActions.state = BottomSheetBehavior.STATE_COLLAPSED
        bindingSetup.clBottomMessageActions.visibility = View.GONE
        bindingSetup.vTransparent.visibility = View.GONE
    }

    private fun initializeObservers() {
        viewModel.messageSendListener.observe(viewLifecycleOwner, EventObserver {
            senderScroll()
            when (it.status) {
                Resource.Status.SUCCESS -> {
                    if (unsentMessages.isNotEmpty()) {
                        val message =
                            unsentMessages.find { msg -> msg.localId == it.responseData?.data?.message?.localId }
                        unsentMessages.remove(message)
                    }
                }

                Resource.Status.ERROR -> {
                    Timber.d("Message send fail: $it")
                }

                else -> Timber.d("Other error")
            }
        })

        viewModel.getMessageAndRecords(roomId = roomWithUsers.room.roomId)
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

                        if (listState != null && shouldScroll) {
                            bindingSetup.rvChat.layoutManager?.onRestoreInstanceState(listState)
                            shouldScroll = false
                        }

                        if (messageSearchId != 0) {
                            if (messagesRecords.firstOrNull { messageAndRecords -> messageAndRecords.message.id == messageSearchId } != null) {
                                val position =
                                    messagesRecords.indexOfFirst { messageAndRecords -> messageAndRecords.message.id == messageSearchId }
                                scrollToPosition = position
                                if (position != -1) {
                                    bindingSetup.rvChat.smoothScrollToPosition(position)
                                }
                                messageSearchId = 0
                            } else {
                                viewModel.fetchNextSet(roomWithUsers.room.roomId)
                            }
                        }
                    }

                    Resource.Status.LOADING -> {
                    }

                    else -> {
                        Timber.d("Message get error")
                    }
                }
                senderScroll()
            }

        viewModel.messagesReceived.observe(viewLifecycleOwner) { messages ->
            val receivedMessages = messages.filter {
                it.roomId == roomWithUsers.room.roomId
                        && it.fromUserId != localUserId
            }
            if (receivedMessages.isNotEmpty()) {
                showNewMessage(receivedMessages.size)
            }
        }

        if (Const.JsonFields.PRIVATE == roomWithUsers.room.type) {
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
                                roomWithUsers.users.any { user -> it.responseData.find { blockedUser -> blockedUser.id == user.id } != null }
                            if (Const.JsonFields.PRIVATE == roomWithUsers.room.type) {
                                if (containsElement) {
                                    bindingSetup.clContactBlocked.visibility = View.VISIBLE
                                } else bindingSetup.clContactBlocked.visibility = View.GONE
                            }
                        } else bindingSetup.clContactBlocked.visibility = View.GONE
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

        viewModel.roomInfoUpdated.observe(viewLifecycleOwner, EventObserver {
            when (it.status) {
                Resource.Status.SUCCESS -> {
                    if (it.responseData?.data?.room?.roomId == roomWithUsers.room.roomId) {
                        val avatarFile = it.responseData.data.room.avatarFileId ?: 0L
                        val roomName = it.responseData.data.room.name ?: ""
                        roomWithUsers.room.apply {
                            avatarFileId = avatarFile
                            name = roomName
                        }
                        setAvatarAndName(avatarFile, roomName)
                    }
                }

                Resource.Status.ERROR -> Timber.d("Error while updating room data")
                else -> Timber.d("Other error")
            }
        })

        viewModel.sendMessagesSeen(roomId = roomWithUsers.room.roomId)

        viewModel.updateUnreadCount(roomId = roomWithUsers.room.roomId)
    }

    private fun senderScroll() {
        if ((scrollYDistance <= 0) && (scrollYDistance > SCROLL_DISTANCE_NEGATIVE)
            || (scrollYDistance >= 0) && (scrollYDistance < SCROLL_DISTANCE_POSITIVE)
        ) {
            scrollToPosition()
            bindingSetup.cvBottomArrow.visibility = View.INVISIBLE
        } else {
            bindingSetup.cvBottomArrow.visibility = View.VISIBLE
        }
    }

    private fun showNewMessage(messagesSize: Int) {
        valueAnimator?.end()
        valueAnimator?.removeAllUpdateListeners()
        bindingSetup.cvBottomArrow.visibility = View.INVISIBLE

        // If we received message and keyboard is open:
        if (heightDiff >= MIN_HEIGHT_DIFF && scrollYDistance > SCROLL_DISTANCE_POSITIVE) {
            scrollYDistance -= heightDiff
        }

        if (!((scrollYDistance <= 0) && (scrollYDistance > SCROLL_DISTANCE_NEGATIVE)
                    || (scrollYDistance >= 0) && (scrollYDistance < SCROLL_DISTANCE_POSITIVE))
        ) {

            bindingSetup.cvNewMessages.visibility = View.VISIBLE

            if (messagesSize == 1 && bindingSetup.cvNewMessages.visibility == View.INVISIBLE) {
                bindingSetup.tvNewMessage.text =
                    getString(R.string.new_messages, messagesSize.toString(), "").trim()

                val startWidth = bindingSetup.ivBottomArrow.width
                val endWidth = (bindingSetup.tvNewMessage.width + bindingSetup.ivBottomArrow.width)

                valueAnimator = ValueAnimator.ofInt(startWidth, endWidth).apply {
                    addUpdateListener { valueAnimator ->
                        val layoutParams = bindingSetup.cvNewMessages.layoutParams
                        layoutParams.width = valueAnimator.animatedValue as Int
                        bindingSetup.cvNewMessages.layoutParams = layoutParams
                    }
                    duration = NEW_MESSAGE_ANIMATION_DURATION
                }
                valueAnimator?.start()

            } else {
                bindingSetup.tvNewMessage.text =
                    getString(R.string.new_messages, messagesSize.toString(), "s").trim()
            }
        } else if (messagesSize > 0) {
            bindingSetup.rvChat.scrollToPosition(0)
            viewModel.clearMessages()
        }
        return
    }

    private fun showBottomArrow() {
        if (heightDiff >= MIN_HEIGHT_DIFF && scrollYDistance > SCROLL_DISTANCE_POSITIVE) {
            scrollYDistance -= heightDiff
        }
        // If we are somewhere up
        if (!((scrollYDistance <= 0) && (scrollYDistance > SCROLL_DISTANCE_NEGATIVE)
                    || (scrollYDistance >= 0) && (scrollYDistance < SCROLL_DISTANCE_POSITIVE))
        ) {
            if (bindingSetup.cvNewMessages.visibility == View.VISIBLE) {
                bindingSetup.cvBottomArrow.visibility = View.INVISIBLE
            } else {
                bindingSetup.cvBottomArrow.visibility = View.VISIBLE
            }
        } else {
            bindingSetup.cvBottomArrow.visibility = View.INVISIBLE
        }
    }

    private fun scrollToPosition() {
        bindingSetup.rvChat.scrollToPosition(0)
        scrollYDistance = 0
        return
    }

    private fun setUpAdapter() {
        exoPlayer = ExoPlayer.Builder(this.context!!).build()
        chatAdapter = ChatAdapter(
            context!!,
            localUserId,
            roomWithUsers.users,
            exoPlayer!!,
            roomWithUsers.room.type,
            onMessageInteraction = { event, message ->
                if (!roomWithUsers.room.deleted && !roomWithUsers.room.roomExit) {
                    run {
                        when (event) {
                            Const.UserActions.DOWNLOAD_FILE -> handleDownloadFile(message)
                            Const.UserActions.DOWNLOAD_CANCEL -> handleDownloadCancelFile(message.message)
                            Const.UserActions.MESSAGE_ACTION -> handleMessageAction(message)
                            Const.UserActions.MESSAGE_REPLY -> handleMessageReplyClick(message)
                            Const.UserActions.RESEND_MESSAGE -> handleMessageResend(message)
                            Const.UserActions.SHOW_MESSAGE_REACTIONS -> handleShowReactions(
                                message
                            )

                            Const.UserActions.NAVIGATE_TO_MEDIA_FRAGMENT -> handleMediaNavigation(
                                message
                            )

                            else -> Timber.d("No other action currently")
                        }
                    }
                }
            }
        )
        val layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, true)
        bindingSetup.rvChat.itemAnimator = null
        bindingSetup.rvChat.adapter = chatAdapter
        layoutManager.stackFromEnd = true
        bindingSetup.rvChat.layoutManager = layoutManager

        bindingSetup.rvChat.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                scrollYDistance += dy

                val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
                val totalItemCount = layoutManager.itemCount
                if (lastVisiblePosition == totalItemCount - 1 && !isFetching) {
                    Timber.d("Fetching next batch of data")
                    viewModel.fetchNextSet(roomWithUsers.room.roomId)
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
                    bindingSetup.cvNewMessages.visibility = View.INVISIBLE
                    bindingSetup.cvBottomArrow.visibility = View.INVISIBLE
                    scrollYDistance = 0
                    viewModel.clearMessages()
                }
            }
        })
    }

    private fun updateSwipeController() {
        itemTouchHelper?.attachToRecyclerView(null)
        val messageSwipeController =
            MessageSwipeController(
                context!!,
                messagesRecords,
                onSwipeAction = { action, position ->
                    when (action) {
                        Const.UserActions.ACTION_RIGHT -> {
                            bottomSheetReplyAction.state = BottomSheetBehavior.STATE_EXPANDED
                            bindingSetup.clBottomReplyAction.visibility = View.VISIBLE
                            handleMessageReply(messagesRecords[position].message)
                        }

                        Const.UserActions.ACTION_LEFT -> {
                            bottomSheetDetailsAction.state = BottomSheetBehavior.STATE_EXPANDED
                            bindingSetup.clDetailsAction.visibility = View.VISIBLE
                            getDetailsList(messagesRecords[position].message)
                        }
                    }
                })

        itemTouchHelper = ItemTouchHelper(messageSwipeController)
        itemTouchHelper!!.attachToRecyclerView(bindingSetup.rvChat)
    }

    private fun setUpMessageDetailsAdapter() {
        detailsMessageAdapter = MessageDetailsAdapter(
            context!!,
            roomWithUsers,
        )
        val layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        bindingSetup.detailsAction.rvReactionsDetails.adapter = detailsMessageAdapter
        bindingSetup.detailsAction.rvReactionsDetails.layoutManager = layoutManager
        bindingSetup.detailsAction.rvReactionsDetails.itemAnimator = null
    }

    private fun setUpMessageReactionAdapter() {
        messageReactionAdapter = MessageReactionAdapter(
            context!!,
            roomWithUsers,
        )
        val layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        bindingSetup.reactionsDetails.rvReactionsDetails.adapter = messageReactionAdapter
        bindingSetup.reactionsDetails.rvReactionsDetails.layoutManager = layoutManager
        bindingSetup.reactionsDetails.rvReactionsDetails.itemAnimator = null
    }

    private fun handleShowReactions(messageRecords: MessageAndRecords) {
        bindingSetup.vTransparent.visibility = View.VISIBLE
        bottomSheetReactionsAction.state = BottomSheetBehavior.STATE_EXPANDED
        bindingSetup.clReactionsDetails.visibility = View.VISIBLE
        bindingSetup.reactionsDetails.tvAllReactions.setBackgroundResource(R.drawable.bg_reaction_selected)

        val reactionsList = messageRecords.records!!.filter { it.reaction != null }
            .sortedByDescending { it.createdAt }
        // Default view - all reactions:
        messageReactionAdapter.submitList(reactionsList)

        // Group same reactions
        val reactionList = reactionsList.groupBy { it.reaction }.mapValues { it.value.size }

        // Add reaction views:
        if (reactionList.isNotEmpty()) {
            for (reaction in reactionList) {
                val reactionView = ReactionContainer(
                    requireActivity(),
                    null,
                    reaction.key.toString(),
                    reaction.value.toString()
                )
                bindingSetup.reactionsDetails.llReactions.addView(reactionView)
            }
        }

        // Set listeners to reaction views and submit new, filtered list of reactions to adapter
        var currentlySelectedTextView: View? = null

        for (child in bindingSetup.reactionsDetails.llReactions.children) {
            child.setOnClickListener { view ->
                // Remove / add backgrounds for views
                if (view != currentlySelectedTextView) {
                    currentlySelectedTextView?.background = null
                    view.setBackgroundResource(R.drawable.bg_reaction_selected)
                    currentlySelectedTextView = view
                }

                val childIndex = bindingSetup.reactionsDetails.llReactions.indexOfChild(view)
                if (childIndex == 0) {
                    messageReactionAdapter.submitList(reactionsList)
                } else {
                    bindingSetup.reactionsDetails.tvAllReactions.background = null
                    val reactionView: ReactionContainer =
                        bindingSetup.reactionsDetails.llReactions.getChildAt(childIndex) as ReactionContainer
                    val reactionText = reactionView.showReaction()
                    messageReactionAdapter.submitList(reactionsList.filter { it.reaction == reactionText })
                }
            }
        }
    }

    private fun handleMessageReplyClick(msg: MessageAndRecords) {
        val position =
            messagesRecords.indexOfFirst { it.message.createdAt == msg.message.body?.referenceMessage?.createdAt }
        if (position != -1) {
            bindingSetup.rvChat.scrollToPosition(position)
        }
    }

    private fun handleMessageResend(message: MessageAndRecords) {
        ChooserDialog.getInstance(requireContext(),
            getString(R.string.resend),
            null,
            getString(R.string.resend_message),
            "",
            object : DialogInteraction {
                override fun onFirstOptionClicked() {
                    resendMessage(message.message)
                }

                override fun onSecondOptionClicked() {
                    // Ignore
                }
            })

    }

    private fun handleMessageAction(msg: MessageAndRecords) = with(bindingSetup) {
        if (msg.message.deleted == null || msg.message.deleted == true) {
            return
        }

        val reactionsContainer = ReactionsContainer(requireContext(), null)
        messageActions.reactionsContainer.addView(reactionsContainer)
        bottomSheetMessageActions.state = BottomSheetBehavior.STATE_EXPANDED
        clBottomMessageActions.visibility = View.VISIBLE
        vTransparent.visibility = View.VISIBLE

        val localId = viewModel.getLocalUserId()
        val fromUserId = msg.message.fromUserId

        messageActions.tvDelete.visibility =
            if (fromUserId == localId) View.VISIBLE else View.GONE

        messageActions.tvEdit.visibility =
            if (fromUserId == localId && Const.JsonFields.TEXT_TYPE == msg.message.type) View.VISIBLE else View.GONE

        messageActions.tvCopy.visibility =
            if (Const.JsonFields.TEXT_TYPE == msg.message.type) View.VISIBLE else View.GONE

        reactionsContainer.setButtonListener(object : ReactionsContainer.AddReaction {
            override fun addReaction(reaction: String) {
                if (reaction.isNotEmpty()) {
                    msg.message.reaction = reaction
                    addReaction(msg.message)
                    chatAdapter.notifyItemChanged(msg.message.messagePosition)
                    closeMessageSheet()
                }
            }
        })

        messageActions.tvDelete.setOnClickListener {
            closeMessageSheet()
            showDeleteMessageDialog(msg.message)
        }

        messageActions.tvEdit.setOnClickListener {
            closeMessageSheet()
            handleMessageEdit(msg.message)
        }

        messageActions.tvReply.setOnClickListener {
            closeMessageSheet()
            bottomSheetReplyAction.state = BottomSheetBehavior.STATE_EXPANDED
            clBottomReplyAction.visibility = View.VISIBLE
            handleMessageReply(msg.message)
        }

        messageActions.tvDetails.setOnClickListener {
            bottomSheetMessageActions.state = BottomSheetBehavior.STATE_COLLAPSED
            clBottomMessageActions.visibility = View.GONE
            getDetailsList(msg.message)
            bottomSheetDetailsAction.state = BottomSheetBehavior.STATE_EXPANDED
            clDetailsAction.visibility = View.VISIBLE
        }

        messageActions.tvCopy.setOnClickListener {
            val clipboard =
                requireContext().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("", msg.message.body?.text.toString())
            clipboard.setPrimaryClip(clip)
            bottomSheetMessageActions.state = BottomSheetBehavior.STATE_COLLAPSED
            clBottomMessageActions.visibility = View.GONE
            Toast.makeText(
                requireContext(),
                getString(R.string.text_copied),
                Toast.LENGTH_SHORT
            )
                .show()
        }
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
                    Tools.downloadFile(requireContext(), message.message)
                }

                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    // TODO show why permission is needed
                }

                else -> {
                    storedMessage = message.message
                    storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        } else Tools.downloadFile(requireContext(), message.message)
    }

    private fun handleDownloadCancelFile(message: Message) {
        DialogError.getInstance(activity!!,
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
                    viewModel.updateUnreadCount(roomId = roomWithUsers.room.roomId)
                }
            })
    }

    private fun handleMediaNavigation(chatMessage: MessageAndRecords) {
        val mediaInfo: String = if (chatMessage.message.fromUserId == localUserId) {
            requireContext().getString(
                R.string.you_sent_on,
                Tools.fullDateFormat(chatMessage.message.createdAt!!)
            )
        } else {
            val userName =
                roomWithUsers.users.firstOrNull { it.id == chatMessage.message.fromUserId }!!.formattedDisplayName
            requireContext().getString(
                R.string.user_sent_on,
                userName,
                Tools.fullDateFormat(chatMessage.message.createdAt!!)
            )
        }

        val action =
            ChatMessagesFragmentDirections.actionChatMessagesFragmentToVideoFragment(
                mediaInfo = mediaInfo,
                message = chatMessage.message
            )

        findNavController().navigate(action)
    }

    private fun handleMessageReply(message: Message) {
        bindingSetup.vTransparent.visibility = View.VISIBLE
        replyId = message.id.toLong()
        val backgroundResId = if (message.fromUserId == localUserId) {
            R.drawable.bg_message_send
        } else {
            R.drawable.bg_message_received
        }
        bindingSetup.replyAction.clReplyContainer.setBackgroundResource(backgroundResId)

        val user = roomWithUsers.users.firstOrNull {
            it.id == message.fromUserId
        }
        bindingSetup.replyAction.tvUsername.text = user!!.formattedDisplayName

        when (message.type) {
            Const.JsonFields.IMAGE_TYPE, Const.JsonFields.VIDEO_TYPE -> {
                bindingSetup.replyAction.tvMessage.visibility = View.GONE
                bindingSetup.replyAction.ivReplyImage.visibility = View.VISIBLE
                if (Const.JsonFields.IMAGE_TYPE == message.type) {
                    bindingSetup.replyAction.tvReplyMedia.text = getString(
                        R.string.media,
                        context!!.getString(R.string.photo)
                    )
                    bindingSetup.replyAction.tvReplyMedia.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.img_camera_reply,
                        0,
                        0,
                        0
                    )
                }
                if (Const.JsonFields.VIDEO_TYPE == message.type) {
                    bindingSetup.replyAction.tvReplyMedia.text = getString(
                        R.string.media,
                        context!!.getString(R.string.video)
                    )
                    bindingSetup.replyAction.tvReplyMedia.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.img_video_reply,
                        0,
                        0,
                        0
                    )
                }
                val mediaPath = Tools.getMediaFile(this.context!!, message)
                bindingSetup.replyAction.tvReplyMedia.visibility = View.VISIBLE
                Glide.with(this)
                    .load(mediaPath)
                    .override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                    .placeholder(R.drawable.img_image_placeholder)
                    .dontTransform()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(bindingSetup.replyAction.ivReplyImage)
            }

            Const.JsonFields.AUDIO_TYPE -> {
                bindingSetup.replyAction.tvMessage.visibility = View.GONE
                bindingSetup.replyAction.tvReplyMedia.visibility = View.VISIBLE
                bindingSetup.replyAction.ivReplyImage.visibility = View.GONE
                bindingSetup.replyAction.tvReplyMedia.text =
                    getString(R.string.media, context!!.getString(R.string.audio))
                bindingSetup.replyAction.tvReplyMedia.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.img_audio_reply,
                    0,
                    0,
                    0
                )
            }

            Const.JsonFields.FILE_TYPE -> {
                bindingSetup.replyAction.tvMessage.visibility = View.GONE
                bindingSetup.replyAction.ivReplyImage.visibility = View.GONE
                bindingSetup.replyAction.tvReplyMedia.visibility = View.VISIBLE
                bindingSetup.replyAction.tvReplyMedia.text =
                    getString(R.string.media, context!!.getString(R.string.file))
                bindingSetup.replyAction.tvReplyMedia.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.img_file_reply,
                    0,
                    0,
                    0
                )
            }

            else -> {
                bindingSetup.replyAction.ivReplyImage.visibility = View.GONE
                bindingSetup.replyAction.tvReplyMedia.visibility = View.GONE
                bindingSetup.replyAction.tvMessage.visibility = View.VISIBLE
                val replyText = message.body?.text
                bindingSetup.replyAction.tvMessage.text = replyText
                bindingSetup.replyAction.tvReplyMedia.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    0,
                    0,
                    0
                )
            }
        }
    }

    private fun handleMessageEdit(message: Message) {
        isEditing = true
        originalText = message.body?.text.toString()
        editedMessageId = message.id
        bindingSetup.etMessage.setText(message.body?.text)
        bindingSetup.ivAdd.rotation = ROTATION_ON

        bindingSetup.etMessage.addTextChangedListener {
            if (isEditing) {
                if (!originalText.equals(it)) {
                    bindingSetup.tvSave.visibility = View.VISIBLE
                    bindingSetup.ivCamera.visibility = View.INVISIBLE
                    bindingSetup.ivMicrophone.visibility = View.INVISIBLE
                } else {
                    bindingSetup.tvSave.visibility = View.GONE
                    bindingSetup.ivCamera.visibility = View.VISIBLE
                    bindingSetup.ivMicrophone.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun getDetailsList(detailsMessage: Message) {
        val senderId = detailsMessage.fromUserId

        /* Adding a message record for the sender so that it can be sent to the adapter */
        val senderMessageRecord = MessageRecords(
            id = 0,
            messageId = detailsMessage.id,
            userId = detailsMessage.fromUserId!!,
            type = Const.JsonFields.SENT,
            reaction = null,
            modifiedAt = detailsMessage.modifiedAt,
            createdAt = detailsMessage.createdAt!!,
            null
        )

        /* In the messageDetails list, we save message records for a specific message,
         remove reactions from those records(because we only need the seen and delivered types),
         remove the sender from the seen/delivered list and sort the list so that first we see
         seen and then delivered. */
        val messageDetails =
            messagesRecords.filter { it.message.id == detailsMessage.id }
                .flatMap { it.records!! }
                .filter { Const.JsonFields.REACTION != it.type }
                .filter { it.userId != detailsMessage.fromUserId }
                .sortedByDescending { it.type }
                .toMutableList()

        /* Then we add the sender of the message to the first position of the messageDetails list
        * so that we can display it in the RecyclerView */
        messageDetails.add(0, senderMessageRecord)

        /* If the room type is a group and the current user is not the sender, remove it from the list.*/
        if ((Const.JsonFields.GROUP == roomWithUsers.room.type) && (senderId != localUserId)) {
            val filteredMessageDetails =
                messageDetails.filter { it.userId != localUserId }.toMutableList()
            detailsMessageAdapter.submitList(ArrayList(filteredMessageDetails))
        } else {
            detailsMessageAdapter.submitList(ArrayList(messageDetails))
        }
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
        ivMicrophone.visibility = View.VISIBLE
        etMessage.text!!.clear()
        etMessage.setText("")
    }

    private fun rotationAnimation() = with(bindingSetup) {
        bottomSheetBehaviour.state = BottomSheetBehavior.STATE_COLLAPSED
        clBottomSheet.visibility = View.GONE
        vTransparent.visibility = View.GONE
        ivAdd.rotation = ROTATION_OFF
    }

    private fun showSendButton() = with(bindingSetup) {
        ivCamera.visibility = View.INVISIBLE
        ivMicrophone.visibility = View.INVISIBLE
        ivButtonSend.visibility = View.VISIBLE
        clTyping.updateLayoutParams<ConstraintLayout.LayoutParams> {
            endToStart = bindingSetup.ivButtonSend.id
        }
        ivAdd.rotation = ROTATION_OFF
    }

    private fun hideSendButton() = with(bindingSetup) {
        ivCamera.visibility = View.VISIBLE
        ivMicrophone.visibility = View.VISIBLE
        ivButtonSend.visibility = View.GONE
        clTyping.updateLayoutParams<ConstraintLayout.LayoutParams> {
            endToStart = bindingSetup.ivCamera.id
        }
        ivAdd.rotation = ROTATION_OFF
    }

    private fun showDeleteMessageDialog(message: Message) {
        ChooserDialog.getInstance(requireContext(),
            getString(R.string.delete),
            null,
            getString(R.string.delete_for_everyone),
            getString(R.string.delete_for_me),
            object : DialogInteraction {
                override fun onFirstOptionClicked() {
                    deleteMessage(message, Const.UserActions.DELETE_MESSAGE_ALL)
                }

                override fun onSecondOptionClicked() {
                    deleteMessage(message, Const.UserActions.DELETE_MESSAGE_ME)
                }
            })
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

            Const.JsonFields.FILE_TYPE, Const.JsonFields.AUDIO_TYPE -> {
                val resendMessage = message.body?.file?.uri?.toUri()
                if (resendMessage != null) {
                    selectedFiles.add(resendMessage)
                    handleUserSelectedFile(selectedFiles)
                }
                viewModel.deleteLocalMessage(message)
            }

            Const.JsonFields.IMAGE_TYPE, Const.JsonFields.VIDEO_TYPE -> {
                if (message.originalUri != null) {
                    selectedFiles.add(message.originalUri!!.toUri())
                    handleUserSelectedFile(selectedFiles)
                    viewModel.deleteLocalMessage(message)
                } else {
                    Toast.makeText(context, "Something went wrong", Toast.LENGTH_LONG)
                }
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
            text,
            Const.JsonFields.TEXT_TYPE,
            0,
            0,
            roomWithUsers.room.roomId,
            localId,
            replyId
        )

        val jsonObject = jsonMessage.messageToJson()
        Timber.d("Message object: $jsonObject")
        viewModel.sendMessage(jsonObject, localId)

        if (replyId != 0L) {
            replyId = 0L
        }
    }

    private fun createTempTextMessage() {
        val messageBody =
            MessageBody(null, bindingSetup.etMessage.text.toString().trim(), 1, 1, null, null)

        val tempMessage = Tools.createTemporaryMessage(
            getUniqueRandomId(unsentMessages),
            localUserId,
            roomWithUsers.room.roomId,
            Const.JsonFields.TEXT_TYPE,
            messageBody
        )

        Timber.d("Temporary message: $tempMessage")
        unsentMessages.add(0, tempMessage)
        viewModel.storeMessageLocally(tempMessage)
    }

    /** Files uploading */
    private fun handleUserSelectedFile(selectedFilesUris: MutableList<Uri>) {
        bindingSetup.ivCamera.visibility = View.GONE

        for (uri in selectedFilesUris) {
            val fileMimeType = getFileMimeType(context, uri)
            if ((fileMimeType?.contains(Const.JsonFields.IMAGE_TYPE) == true ||
                        fileMimeType?.contains(Const.JsonFields.VIDEO_TYPE) == true) &&
                (!fileMimeType.contains(Const.JsonFields.SVG_TYPE) &&
                        !fileMimeType.contains(Const.JsonFields.AVI_TYPE))
            ) {
                convertMedia(uri, fileMimeType)
            } else {
                filesSelected.add(uri)
                tempFilesToCreate.add(TempUri(uri, Const.JsonFields.FILE_TYPE))
            }
        }

        sendFile()
        startUploadService(uploadFiles)

        uploadFiles.clear()
        selectedFilesUris.clear()
        tempFilesToCreate.clear()
    }

    private fun convertMedia(uri: Uri, fileMimeType: String?) {
        val thumbnailUri: Uri
        val fileUri: Uri

        if (fileMimeType?.contains(Const.JsonFields.VIDEO_TYPE) == true) {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(context, uri)
            val bitmap = mmr.frameAtTime

            val fileName = "VIDEO-${System.currentTimeMillis()}.mp4"
            val file = File(context?.getExternalFilesDir(Environment.DIRECTORY_MOVIES), fileName)

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
            thumbnailUri = Tools.convertBitmapToUri(activity!!, thumbnail)
            tempFilesToCreate.add(TempUri(thumbnailUri, Const.JsonFields.VIDEO_TYPE))
            uriPairList.add(Pair(uri, thumbnailUri))
        } else {
            val bitmap =
                Tools.handleSamplingAndRotationBitmap(activity!!, uri, false)
            fileUri = Tools.convertBitmapToUri(activity!!, bitmap!!)

            val thumbnail =
                Tools.handleSamplingAndRotationBitmap(activity!!, fileUri, true)
            thumbnailUri = Tools.convertBitmapToUri(activity!!, thumbnail!!)
            tempFilesToCreate.add(TempUri(thumbnailUri, Const.JsonFields.IMAGE_TYPE))
        }

        uriPairList.add(Pair(uri, thumbnailUri))
        thumbnailUris.add(thumbnailUri)
        currentMediaLocation.add(fileUri)
    }

    private fun sendFile() {
        if (tempFilesToCreate.isNotEmpty()) {
            for (tempFile in tempFilesToCreate) {
                createTempFileMessage(tempFile.uri, tempFile.type)
            }
            tempFilesToCreate.clear()

            if (unsentMessages.isNotEmpty()) {
                for (unsentMessage in unsentMessages) {
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
                        // Send file
                        uploadFiles(
                            isThumbnail = false,
                            uri = filesSelected.first(),
                            localId = unsentMessage.localId!!,
                            metadata = null
                        )
                        filesSelected.removeFirst()
                    }
                }
            }
        }
    }

    private fun createTempFileMessage(uri: Uri, type: String) {
        val tempMessage = FilesHelper.createTempFile(
            uri = uri,
            type = type,
            localUserId = localUserId,
            roomId = roomWithUsers.room.roomId,
            unsentMessages = unsentMessages
        )

        unsentMessages.add(tempMessage)
        viewModel.storeMessageLocally(tempMessage)
    }

    private fun uploadFiles(
        isThumbnail: Boolean,
        uri: Uri,
        localId: String,
        metadata: FileMetadata?,
    ) {
        val uploadData: MutableList<FileData> = ArrayList()
        uploadData.add(
            FilesHelper.uploadFile(
                isThumbnail,
                uri,
                localId,
                roomWithUsers.room.roomId,
                metadata
            )
        )
        uploadFiles.addAll(uploadData)
    }

    /** Upload service */
    private fun startUploadService(files: ArrayList<FileData>) {
        val intent = Intent(MainApplication.appContext, UploadService::class.java)
        intent.putParcelableArrayListExtra(Const.IntentExtras.FILES_EXTRA, files)
        MainApplication.appContext.startService(intent)
        activity?.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

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
                    val message = messagesRecords.firstOrNull { it.message.localId == localId }
                    message!!.message.uploadProgress = (progress * 100) / maxProgress

                    if (isVisible || isResumed) {
                        activity!!.runOnUiThread {
                            chatAdapter.notifyItemChanged(
                                messagesRecords.indexOf(
                                    message
                                )
                            )
                        }
                    }
                }

                override fun uploadingFinished(uploadedFiles: MutableList<FileData>) {
                    Tools.deleteTemporaryMedia(context!!)
                    context?.cacheDir?.deleteRecursively()

                    if (uploadedFiles.isNotEmpty()) {
                        uploadedFiles.forEach { item ->
                            if (item.messageStatus == Resource.Status.ERROR ||
                                item.messageStatus == Resource.Status.LOADING ||
                                item.messageStatus == null
                            ) {
                                if (!item.isThumbnail) {
                                    viewModel.updateMessages(
                                        messageStatus = Resource.Status.ERROR.toString(),
                                        localId = item.localId.toString()
                                    )
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
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Timber.d("Service disconnected")
        }
    }

    private fun checkStoragePermission() {
        storagePermission =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                if (it) {
                    context?.let { context -> Tools.downloadFile(context, storedMessage) }
                } else {
                    Timber.d("Couldn't download file. No permission granted.")
                }
            }
    }

    private fun onBackArrowPressed() {
        viewModel.updateUnreadCount(roomId = roomWithUsers.room.roomId)
        activity!!.finish()
    }

    override fun onBackPressed(): Boolean {
        for (bottomSheet in bottomSheets) {
            if (bottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
                return false
            }
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        for (bottomSheet in bottomSheets) {
            bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        bindingSetup.clBottomMessageActions.visibility = View.GONE
        bindingSetup.clBottomSheet.visibility = View.GONE
        bindingSetup.clDetailsAction.visibility = View.GONE
        bindingSetup.clBottomReplyAction.visibility = View.GONE
        bindingSetup.clReactionsDetails.visibility = View.GONE

        viewModel.getBlockedUsersList()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (exoPlayer != null) {
            exoPlayer!!.release()
        }
        viewModel.unregisterSharedPrefsReceiver()
    }

    override fun onPause() {
        super.onPause()
        Timber.d("List state store = ${bindingSetup.rvChat.layoutManager?.onSaveInstanceState()}")
        listState = bindingSetup.rvChat.layoutManager?.onSaveInstanceState()
    }
}
