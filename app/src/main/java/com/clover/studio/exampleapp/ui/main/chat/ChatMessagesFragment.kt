package com.clover.studio.exampleapp.ui.main.chat

import android.Manifest
import android.app.DownloadManager
import android.content.ContentResolver
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.get
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.clover.studio.exampleapp.R
import com.clover.studio.exampleapp.data.models.Message
import com.clover.studio.exampleapp.data.models.MessageAndRecords
import com.clover.studio.exampleapp.data.models.MessageBody
import com.clover.studio.exampleapp.data.models.ReactionMessage
import com.clover.studio.exampleapp.data.models.junction.RoomWithUsers
import com.clover.studio.exampleapp.databinding.FragmentChatMessagesBinding
import com.clover.studio.exampleapp.ui.ImageSelectedContainer
import com.clover.studio.exampleapp.utils.*
import com.clover.studio.exampleapp.utils.dialog.ChooserDialog
import com.clover.studio.exampleapp.utils.dialog.DialogError
import com.clover.studio.exampleapp.utils.extendables.BaseFragment
import com.clover.studio.exampleapp.utils.extendables.DialogInteraction
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.gson.JsonObject
import com.vanniktech.emoji.EmojiPopup
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject


/*fun startChatScreenActivity(fromActivity: Activity, roomData: String) =
    fromActivity.apply {
        val intent = Intent(fromActivity as Context, ChatScreenActivity::class.java)
        intent.putExtra(Const.Navigation.ROOM_DATA, roomData)
        startActivity(intent)
    }
*/
private const val SCROLL_DISTANCE_NEGATIVE = -300
private const val SCROLL_DISTANCE_POSITIVE = 300
private const val ROTATION_ON = 45f
private const val ROTATION_OFF = 0f

enum class UploadMimeTypes {
    IMAGE, VIDEO, FILE, MESSAGE
}

@AndroidEntryPoint
class ChatMessagesFragment : BaseFragment(), ChatOnBackPressed {
    private val viewModel: ChatViewModel by activityViewModels()
    private lateinit var roomWithUsers: RoomWithUsers
    private lateinit var bindingSetup: FragmentChatMessagesBinding
    private lateinit var chatAdapter: ChatAdapter
    private var messagesRecords: MutableList<MessageAndRecords> = mutableListOf()
    private var unsentMessages: MutableList<Message> = ArrayList()

    private var currentPhotoLocation: MutableList<Uri> = ArrayList()
    private var currentVideoLocation: MutableList<Uri> = ArrayList()

    private var filesSelected: MutableList<Uri> = ArrayList()

    private var thumbnailUris: MutableList<Uri> = ArrayList()
    private var photoImageUri: Uri? = null
    private var isAdmin = false
    private var uploadIndex = 0
    private var uploadInProgress = false
    private lateinit var bottomSheetBehaviour: BottomSheetBehavior<ConstraintLayout>

    private val layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, true)

    private var avatarUrl = ""
    private var userName = ""
    private var firstEnter = true
    private var isEditing = false
    private var originalText = ""
    private var editedMessageId = 0
    private lateinit var emojiPopup: EmojiPopup
    private lateinit var storagePermission: ActivityResultLauncher<String>
    private lateinit var storedMessage: Message
    private var oldPosition = 0
    private var scrollYDistance = 0
    private var sent = false
    private var heightDiff = 0

    @Inject
    lateinit var uploadDownloadManager: UploadDownloadManager

    private val chooseFileContract =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
            bindingSetup.llImagesContainer.removeAllViews()
            if (it != null) {
                for (uri in it) {
                    displayFileInContainer(uri)
                    activity!!.runOnUiThread { showSendButton() }
                    filesSelected.add(uri)
                }
            }
        }

    private val chooseImageContract =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
            bindingSetup.llImagesContainer.removeAllViews()
            if (it != null) {
                for (uri in it) {
                    getImageOrVideo(uri)
                }
            } else {
                Timber.d("Gallery error")
            }
        }

    private val takePhotoContract =
        registerForActivityResult(ActivityResultContracts.TakePicture()) {
            if (it) {
                if (photoImageUri != null) {
                    convertImageToBitmap(photoImageUri)
                } else {
                    Timber.d("Photo error")
                }
            } else Timber.d("Photo error")
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreate(savedInstanceState)
        bindingSetup = FragmentChatMessagesBinding.inflate(layoutInflater)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        bottomSheetBehaviour = BottomSheetBehavior.from(bindingSetup.bottomSheet.root)

        roomWithUsers = (activity as ChatScreenActivity?)!!.roomWithUsers!!

        emojiPopup = EmojiPopup(bindingSetup.root, bindingSetup.etMessage)

        checkStoragePermission()
        setUpAdapter()
        initViews()
        initializeObservers()
        checkIsUserAdmin()

        return bindingSetup.root
    }

    private fun setAvatarAndName(avatarUrl: String, userName: String) {
        bindingSetup.tvChatName.text = userName
        Glide.with(this)
            .load(avatarUrl.let { Tools.getFileUrl(it) })
            .placeholder(context?.getDrawable(R.drawable.img_user_placeholder))
            .into(bindingSetup.ivUserImage)
    }

    private fun checkIsUserAdmin() {
        for (user in roomWithUsers.users) {
            isAdmin = user.id == viewModel.getLocalUserId() && viewModel.isUserAdmin(
                roomWithUsers.room.roomId,
                user.id
            )
            if (isAdmin) break
        }
    }

    private fun initializeObservers() {
        viewModel.messageSendListener.observe(viewLifecycleOwner, EventObserver {
            when (it) {
                ChatStatesEnum.MESSAGE_SENT -> {
                    bindingSetup.etMessage.setText("")
                    viewModel.deleteLocalMessages(unsentMessages)
                    unsentMessages.clear()
                }
                ChatStatesEnum.MESSAGE_SEND_FAIL -> Timber.d("Message send fail")
                else -> Timber.d("Other error")
            }
        })

        viewModel.getMessagesListener.observe(viewLifecycleOwner, EventObserver {
            when (it) {
                is MessagesFetched -> {
                    viewModel.deleteLocalMessages(unsentMessages)
                    unsentMessages.clear()
                }
                is MessageFetchFail -> Timber.d("Failed to fetch messages")
                else -> Timber.d("Other error")
            }
        })

        /*viewModel.getMessagesTimestampListener.observe(viewLifecycleOwner, EventObserver {
            when (it) {
                is MessagesTimestampFetched -> {
                    Timber.d("Messages timestamp fetched")
                    messages = it.messages as MutableList<Message>
                    //chatAdapter.submitList(it.messages)
                }
                is MessageTimestampFetchFail -> Timber.d("Failed to fetch messages timestamp")
                else -> Timber.d("Other error")
            }
        })*/

        viewModel.sendMessageDeliveredListener.observe(viewLifecycleOwner, EventObserver {
            when (it) {
                ChatStatesEnum.MESSAGE_DELIVERED -> {
                    Timber.d("Messages delivered")
                    unsentMessages.clear()

                }
                ChatStatesEnum.MESSAGE_DELIVER_FAIL -> Timber.d("Failed to deliver messages")
                else -> Timber.d("Other error")
            }
        })
        viewModel.getChatRoomAndMessageAndRecordsById(roomWithUsers.room.roomId)
            .observe(viewLifecycleOwner) {
                messagesRecords.clear()
                if (it.message?.isNotEmpty() == true) {
                    it.message.forEach { msg ->
                        messagesRecords.add(msg)
                    }
                    messagesRecords.sortByDescending { messages -> messages.message.createdAt }
                    // messagesRecords.toList -> for DiffUtil class
                    chatAdapter.submitList(messagesRecords.toList())

                    if (oldPosition != messagesRecords.size) {
                        showNewMessage()
                    }

                    if (firstEnter) {
                        oldPosition = messagesRecords.size
                        bindingSetup.rvChat.scrollToPosition(0)
                        firstEnter = false
                    }
                }
            }
    }

    private fun showNewMessage() {
        // If we send message
        if (sent) {
            scrollToPosition()
        } else {
            // If we received message and keyboard is open:
            if (heightDiff >= 150 && scrollYDistance > SCROLL_DISTANCE_POSITIVE) {
                scrollYDistance -= heightDiff
                Timber.d("heig: $heightDiff, scroll: $scrollYDistance")
                Timber.d("scroll: $scrollYDistance")
            }
            // We need to check where we are in recycler view:
            // If we are somewhere bottom
            if ((scrollYDistance <= 0) && (scrollYDistance > SCROLL_DISTANCE_NEGATIVE)
                || (scrollYDistance >= 0) && (scrollYDistance < SCROLL_DISTANCE_POSITIVE)
            ) {
                scrollToPosition()
            }
            // If we are somewhere up in chat, show new message dialog
            else {
                bindingSetup.cvNewMessages.visibility = View.VISIBLE
                val newMessages = messagesRecords.size - oldPosition
                if (newMessages == 1) {
                    bindingSetup.tvNewMessage.text =
                        getString(R.string.new_messages, newMessages.toString(), "")
                } else {
                    bindingSetup.tvNewMessage.text =
                        getString(R.string.new_messages, newMessages.toString(), "s")
                }
            }
        }
        sent = false
        return
    }

    private fun scrollToPosition() {
        //TimeUnit.MILLISECONDS.sleep(200)
        oldPosition = messagesRecords.size
        bindingSetup.rvChat.smoothScrollToPosition(0)
        scrollYDistance = 0
    }

    private fun setUpAdapter() {
        chatAdapter = ChatAdapter(
            context!!,
            viewModel.getLocalUserId()!!,
            roomWithUsers.users,
            roomWithUsers.room.type!!,
            addReaction = { addMessageReaction(it) },
            onMessageInteraction = { event, message ->
                run {
                    when (event) {
                        Const.UserActions.DELETE -> showDeleteMessageDialog(message)
                        Const.UserActions.EDIT -> handleMessageEdit(message)
                        Const.UserActions.DOWNLOAD_FILE -> handleDownloadFile(message)
                        else -> Timber.d("No other action currently")
                    }
                }
            }
        )
        bindingSetup.rvChat.adapter = chatAdapter
        layoutManager.stackFromEnd = true
        bindingSetup.rvChat.layoutManager = layoutManager

        // Add callback for item swipe handling
        val simpleItemTouchCallback: ItemTouchHelper.SimpleCallback = object :
            ItemTouchHelper.SimpleCallback(
                0,
                ItemTouchHelper.RIGHT
            ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                // ignore
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDir: Int) {
                // Get swiped message text and add to message EditText
                // After that, return item to correct position
                val position = viewHolder.absoluteAdapterPosition
                bindingSetup.etMessage.setText(messagesRecords[position].message.body?.text)
                chatAdapter.notifyItemChanged(position)
            }
        }

        val itemTouchHelper = ItemTouchHelper(simpleItemTouchCallback)
        itemTouchHelper.attachToRecyclerView(bindingSetup.rvChat)

        // Notify backend of messages seen
        viewModel.sendMessagesSeen(roomWithUsers.room.roomId)

        // Update room visited
        roomWithUsers.room.visitedRoom = System.currentTimeMillis()
        viewModel.updateRoomVisitedTimestamp(roomWithUsers.room)
    }

    private fun addMessageReaction(reaction: ReactionMessage) {
        // POST reaction to server:
        // Timber.d("reactions: ${reaction.reaction}, ${reaction.messageId}")
        /*if (!reaction.clicked) {*/
        val jsonObject = JsonObject()
        jsonObject.addProperty(Const.Networking.MESSAGE_ID, reaction.messageId)
        jsonObject.addProperty(Const.JsonFields.TYPE, Const.JsonFields.REACTION)
        jsonObject.addProperty(Const.JsonFields.REACTION, reaction.reaction)
        viewModel.sendReaction(jsonObject)
        /*} else {
            // Remove reaction
            val jsonObject = JsonObject()
            jsonObject.addProperty(Const.Networking.MESSAGE_ID, reaction.messageId)
            jsonObject.addProperty(Const.JsonFields.TYPE, Const.JsonFields.REACTION)
            if (roomWithUsers.room.type == Const.JsonFields.PRIVATE){
                viewModel.deleteAllReactions(reaction.messageId)
            } else {
                viewModel.deleteReaction(reaction.reactionId, reaction.userId)
            }
        }*/
    }

    private fun initViews() {
        if (Const.JsonFields.PRIVATE == roomWithUsers.room.type) {
            for (user in roomWithUsers.users) {
                if (user.id.toString() != viewModel.getLocalUserId().toString()) {
                    avatarUrl = user.avatarUrl.toString()
                    userName = user.displayName.toString()
                    break
                } else {
                    avatarUrl = user.avatarUrl.toString()
                    userName = user.displayName.toString()
                }
            }
        } else {
            avatarUrl = roomWithUsers.room.avatarUrl.toString()
            userName = roomWithUsers.room.name.toString()
        }
        setAvatarAndName(avatarUrl, userName)

        bindingSetup.clHeader.setOnClickListener {
            val action =
                ChatMessagesFragmentDirections.actionChatMessagesFragmentToChatDetailsFragment(
                    roomWithUsers.room.roomId,
                    isAdmin
                )
            findNavController().navigate(action)
        }

        bindingSetup.ivArrowBack.setOnClickListener {
            onBackArrowPressed()
        }

        bindingSetup.bottomSheet.btnFiles.setOnClickListener {
            chooseFile()
            rotationAnimation()
        }

        bindingSetup.ivCamera.setOnClickListener {
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

        // Emoji section:
        bindingSetup.ivBtnEmoji.setOnClickListener {
            emojiPopup.toggle() // Toggles visibility of the Popup.
            emojiPopup.dismiss() // Dismisses the Popup.
            emojiPopup.isShowing // Returns true when Popup is showing.
            bindingSetup.ivAdd.rotation = ROTATION_OFF
        }

        bindingSetup.etMessage.setOnClickListener {
            if (emojiPopup.isShowing) {
                emojiPopup.dismiss()
            }
        }

        bindingSetup.rvChat.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom) {
                bindingSetup.rvChat.smoothScrollToPosition(0)
            }
        }

        bindingSetup.rvChat.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                //Timber.d("scroll distance $dy")
                Timber.d("scroll y $scrollYDistance")
                scrollYDistance += dy
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                    Timber.d("-----")
                    bindingSetup.cvNewMessages.visibility = View.GONE
                    oldPosition = messagesRecords.size
                    scrollYDistance = 0
                }
            }
        })


        bindingSetup.root.viewTreeObserver.addOnGlobalLayoutListener {
            heightDiff = bindingSetup.root.rootView.height - bindingSetup.root.height
            Timber.d("height diff: $heightDiff")
            // IF height diff is more then 150, consider keyboard as visible.
        }

        bindingSetup.cvNewMessages.setOnClickListener {
            bindingSetup.rvChat.scrollToPosition(0)
            bindingSetup.cvNewMessages.visibility = View.GONE
            scrollYDistance = 0
            oldPosition = messagesRecords.size
        }

        bindingSetup.etMessage.addTextChangedListener {
            if (!isEditing) {
                if (it?.isNotEmpty() == true) {
                    showSendButton()
                    bindingSetup.ivAdd.rotation = ROTATION_OFF
                } else {
                    hideSendButton()
                }
            }
        }

        // TODO add send message button and handle UI when message is being entered
        // Change required field after work has been done

        bindingSetup.tvTitle.text = roomWithUsers.room.type

        bindingSetup.ivButtonSend.setOnClickListener {
            if (currentPhotoLocation.isNotEmpty()) {
                uploadImage()

            } else if (filesSelected.isNotEmpty()) {
                uploadFile(filesSelected[0])

            } else if (currentVideoLocation.isNotEmpty()) {
                uploadVideo()

            } else {
                createTempMessage()
                sendMessage()
            }
            sent = true
            hideSendButton()
        }

        bindingSetup.ivAdd.setOnClickListener {
            if (!isEditing) {
                if (bottomSheetBehaviour.state != BottomSheetBehavior.STATE_EXPANDED) {
                    bindingSetup.ivAdd.rotation = ROTATION_ON
                    bottomSheetBehaviour.state = BottomSheetBehavior.STATE_EXPANDED
                    bindingSetup.vTransparent.visibility = View.VISIBLE
                }
            } else {
                resetEditingFields()
            }
        }

        bindingSetup.bottomSheet.ivRemove.setOnClickListener {
            bottomSheetBehaviour.state = BottomSheetBehavior.STATE_COLLAPSED
            rotationAnimation()
        }

        bindingSetup.bottomSheet.btnLibrary.setOnClickListener {
            chooseImage()
            rotationAnimation()
        }

        bindingSetup.bottomSheet.btnLocation.setOnClickListener {
            rotationAnimation()
        }
        bindingSetup.bottomSheet.btnContact.setOnClickListener {
            rotationAnimation()
        }

        bindingSetup.tvSave.setOnClickListener {
            editMessage()
            resetEditingFields()
        }
    }

    private fun resetEditingFields() {
        editedMessageId = 0
        isEditing = false
        originalText = ""
        bindingSetup.etMessage.setText("")
        bindingSetup.ivAdd.rotation = ROTATION_OFF
        bindingSetup.tvSave.visibility = View.GONE
        bindingSetup.ivCamera.visibility = View.VISIBLE
        bindingSetup.ivMicrophone.visibility = View.VISIBLE
    }

    private fun rotationAnimation() {
        bottomSheetBehaviour.state = BottomSheetBehavior.STATE_COLLAPSED
        bindingSetup.vTransparent.visibility = View.GONE
        bindingSetup.ivAdd.rotation = ROTATION_OFF
    }

    private fun hideSendButton() {
        bindingSetup.ivCamera.visibility = View.VISIBLE
        bindingSetup.ivMicrophone.visibility = View.VISIBLE
        bindingSetup.ivButtonSend.visibility = View.GONE
        bindingSetup.clTyping.updateLayoutParams<ConstraintLayout.LayoutParams> {
            endToStart = bindingSetup.ivCamera.id
        }
        bindingSetup.ivAdd.rotation = ROTATION_OFF
    }

    private fun showSendButton() {
        bindingSetup.ivCamera.visibility = View.INVISIBLE
        bindingSetup.ivMicrophone.visibility = View.INVISIBLE
        bindingSetup.ivButtonSend.visibility = View.VISIBLE
        bindingSetup.clTyping.updateLayoutParams<ConstraintLayout.LayoutParams> {
            endToStart = bindingSetup.ivButtonSend.id
        }
        bindingSetup.ivAdd.rotation = ROTATION_OFF
    }

    private fun showDeleteMessageDialog(message: Message) {
        ChooserDialog.getInstance(requireContext(),
            null,
            null,
            getString(R.string.delete_for_everyone),
            getString(R.string.delete_for_me),
            object : DialogInteraction {
                override fun onFirstOptionClicked() {
                    deleteMessage(message.id, Const.UserActions.DELETE_MESSAGE_ALL)
                }

                override fun onSecondOptionClicked() {
                    deleteMessage(message.id, Const.UserActions.DELETE_MESSAGE_ME)
                }
            })
    }

    private fun deleteMessage(messageId: Int, target: String) {
        viewModel.deleteMessage(messageId, target)
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
                    // Show save button
                    bindingSetup.tvSave.visibility = View.VISIBLE
                    bindingSetup.ivCamera.visibility = View.INVISIBLE
                    bindingSetup.ivMicrophone.visibility = View.INVISIBLE
                } else {
                    // Hide save button
                    bindingSetup.tvSave.visibility = View.GONE
                    bindingSetup.ivCamera.visibility = View.VISIBLE
                    bindingSetup.ivMicrophone.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun editMessage() {
        if (editedMessageId != 0) {
            val jsonObject = JsonObject()
            jsonObject.addProperty(Const.JsonFields.TEXT, bindingSetup.etMessage.text.toString())

            viewModel.editMessage(editedMessageId, jsonObject)
        }
    }

    private fun sendMessage() {
        sendMessage(mimeType = UploadMimeTypes.MESSAGE, 0, 0)
    }

    private fun sendMessage(
        mimeType: UploadMimeTypes,
        fileId: Long,
        thumbId: Long
    ) {
        val jsonObject = JsonObject()
        val innerObject = JsonObject()
        innerObject.addProperty(
            Const.JsonFields.TEXT,
            bindingSetup.etMessage.text.toString()
        )
        if (UploadMimeTypes.IMAGE == mimeType) {
            innerObject.addProperty(Const.JsonFields.FILE_ID, fileId)
            innerObject.addProperty(Const.JsonFields.THUMB_ID, thumbId)
            jsonObject.addProperty(Const.JsonFields.TYPE, Const.JsonFields.CHAT_IMAGE)
        } else if (UploadMimeTypes.FILE == mimeType) {
            innerObject.addProperty(Const.JsonFields.FILE_ID, fileId)
            jsonObject.addProperty(Const.JsonFields.TYPE, Const.JsonFields.FILE_TYPE)
        } else if (UploadMimeTypes.VIDEO == mimeType) {
            innerObject.addProperty(Const.JsonFields.FILE_ID, fileId)
            innerObject.addProperty(Const.JsonFields.THUMB_ID, thumbId)
            jsonObject.addProperty(Const.JsonFields.TYPE, Const.JsonFields.VIDEO)
        } else jsonObject.addProperty(Const.JsonFields.TYPE, Const.JsonFields.TEXT)

        jsonObject.addProperty(Const.JsonFields.ROOM_ID, roomWithUsers.room.roomId)
        jsonObject.add(Const.JsonFields.BODY, innerObject)

        viewModel.sendMessage(jsonObject)
    }

    private fun createTempMessage() {
        val tempMessage = Message(
            0,
            viewModel.getLocalUserId(),
            0,
            0,
            0,
            -1,
            0,
            roomWithUsers.room.roomId,
            Const.JsonFields.TEXT,
            MessageBody(bindingSetup.etMessage.text.toString(), 1, 1, null, null),
            System.currentTimeMillis(),
            null,
            null
        )

        unsentMessages.add(tempMessage)
        viewModel.storeMessageLocally(tempMessage)
    }

    private fun onBackArrowPressed() {
        if (uploadInProgress) {
            showUploadError()
        } else {
            // Update room visited
            roomWithUsers.room.visitedRoom = System.currentTimeMillis()
            viewModel.updateRoomVisitedTimestamp(roomWithUsers.room)
            //
            activity!!.finish()
        }
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
            "com.clover.studio.exampleapp.fileprovider",
            Tools.createImageFile(
                (activity!!)
            )
        )
        takePhotoContract.launch(photoImageUri)
    }

    private fun uploadThumbnail(messageBody: MessageBody, index: Int) {
        uploadMedia(messageBody, true, thumbnailUris[index], UploadMimeTypes.IMAGE)
    }

    private fun uploadVideoThumbnail(messageBody: MessageBody, index: Int) {
        uploadMedia(messageBody, true, thumbnailUris[index], UploadMimeTypes.VIDEO)
    }

    private fun uploadImage() {
        val messageBody = MessageBody("", 0, 0, null, null)
        uploadThumbnail(messageBody, uploadIndex)
    }

    private fun uploadVideo() {
        val messageBody = MessageBody("", 0, 0, null, null)
        uploadVideoThumbnail(messageBody, uploadIndex)
    }

    private fun uploadFile(uri: Uri) {
        uploadInProgress = true
        val messageBody = MessageBody("", 0, 0, null, null)
        val inputStream =
            activity!!.contentResolver.openInputStream(uri)

        var fileName = ""
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)

        val cr = activity!!.contentResolver
        cr.query(uri, projection, null, null, null)?.use { metaCursor ->
            if (metaCursor.moveToFirst()) {
                fileName = metaCursor.getString(0)
            }
        }

        Tools.fileName = fileName

        val fileStream = Tools.copyStreamToFile(
            activity!!,
            inputStream!!,
            activity!!.contentResolver.getType(uri)!!
        )
        val uploadPieces =
            if ((fileStream.length() % CHUNK_SIZE).toInt() != 0)
                fileStream.length() / CHUNK_SIZE + 1
            else fileStream.length() / CHUNK_SIZE
        var progress = 0
        val imageContainer = bindingSetup.llImagesContainer[uploadIndex] as ImageSelectedContainer

        imageContainer.setMaxProgress(uploadPieces.toInt())
        Timber.d("File upload start")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                uploadDownloadManager.uploadFile(
                    activity!!,
                    uri,
                    Const.JsonFields.FILE,
                    Const.JsonFields.FILE_TYPE,
                    uploadPieces,
                    fileStream,
                    false,
                    object : FileUploadListener {
                        override fun filePieceUploaded() {
                            try {
                                if (progress <= uploadPieces) {
                                    imageContainer.setUploadProgress(progress)
                                    progress++
                                } else progress = 0
                            } catch (ex: Exception) {
                                Timber.d("Video upload failed on piece")
                                uploadIndex = 0
                                filesSelected.clear()
                                uploadInProgress = false
                            }
                        }

                        override fun fileUploadError(description: String) {
                            try {
                                activity!!.runOnUiThread {
                                    if (imageContainer.childCount > 0) {
                                        imageContainer.removeViewAt(0)
                                    }
                                    uploadIndex++
                                    if (uploadIndex < filesSelected.size) {
                                        uploadFile(filesSelected[uploadIndex])
                                    } else {
                                        uploadIndex = 0
                                        filesSelected.clear()
                                    }

                                    Toast.makeText(
                                        activity!!.baseContext,
                                        getString(R.string.failed_file_upload),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    uploadInProgress = false
                                }
                            } catch (ex: Exception) {
                                Timber.d("Video upload failed on error")
                                uploadIndex = 0
                                filesSelected.clear()
                                uploadInProgress = false
                            }
                        }

                        override fun fileUploadVerified(path: String, thumbId: Long, fileId: Long) {
                            try {
                                activity!!.runOnUiThread {
                                    Timber.d("Successfully sent file")
                                    if (imageContainer.childCount > 0) {
                                        imageContainer.removeViewAt(0)
                                    }

                                    if (fileId > 0) messageBody.fileId = fileId
                                    sendMessage(
                                        UploadMimeTypes.FILE,
                                        messageBody.fileId!!,
                                        0
                                    )

                                    uploadIndex++
                                    if (uploadIndex < filesSelected.size) {
                                        uploadFile(filesSelected[uploadIndex])
                                    } else {
                                        uploadIndex = 0
                                        filesSelected.clear()
                                        uploadInProgress = false
                                    }
                                }
                                // update room data
                            } catch (ex: Exception) {
                                Timber.d("Video upload failed on verify")
                                uploadIndex = 0
                                filesSelected.clear()
                                uploadInProgress = false
                            }
                        }
                    })
            } catch (ex: Exception) {
                uploadIndex = 0
                filesSelected.clear()
                uploadInProgress = false
            }
        }
    }

    /**
     * One method used for uploading images and video files. They can be discerned by the
     * mediaType field send to the constructor.
     *
     * @param messageBody MessageBody object that will be filed with required data
     * @param isThumbnail Declare if a thumbnail is being sent or a media file
     * @param uri Uri of the media/thumbnail file
     * @param mediaType Type of file being send to the server (Image, Video, Message, File)
     */
    private fun uploadMedia(
        messageBody: MessageBody,
        isThumbnail: Boolean,
        uri: Uri,
        mediaType: UploadMimeTypes
    ) {
        uploadInProgress = true
        val inputStream =
            activity!!.contentResolver.openInputStream(uri)

        val fileStream = Tools.copyStreamToFile(
            activity!!,
            inputStream!!,
            activity!!.contentResolver.getType(uri)!!
        )
        val uploadPieces =
            if ((fileStream.length() % CHUNK_SIZE).toInt() != 0)
                fileStream.length() / CHUNK_SIZE + 1
            else fileStream.length() / CHUNK_SIZE
        var progress = 0

        val imageContainer = bindingSetup.llImagesContainer[uploadIndex] as ImageSelectedContainer
        imageContainer.setMaxProgress(uploadPieces.toInt())

        val mimeType = if (mediaType == UploadMimeTypes.IMAGE) {
            Const.JsonFields.IMAGE
        } else {
            Const.JsonFields.VIDEO
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                uploadDownloadManager.uploadFile(
                    activity!!,
                    uri,
                    mimeType,
                    Const.JsonFields.AVATAR,
                    uploadPieces,
                    fileStream,
                    isThumbnail,
                    object : FileUploadListener {
                        override fun filePieceUploaded() {
                            try {
                                if (progress <= uploadPieces) {
                                    imageContainer.setUploadProgress(progress)
                                    progress++
                                } else progress = 0
                            } catch (ex: Exception) {
                                Timber.d("File upload failed on piece")
                                resetUploadFields(mimeType)
                            }
                        }

                        override fun fileUploadError(description: String) {
                            try {
                                activity!!.runOnUiThread {
                                    if (imageContainer.childCount > 0) {
                                        imageContainer.removeViewAt(0)
                                    }
                                    uploadIndex++

                                    if (mimeType == Const.JsonFields.IMAGE) {
                                        if (uploadIndex < currentPhotoLocation.size) {
                                            uploadImage()
                                        } else {
                                            resetUploadFields(mimeType)
                                        }
                                    } else {
                                        if (uploadIndex < currentVideoLocation.size) {
                                            uploadVideo()
                                        } else {
                                            resetUploadFields(mimeType)
                                        }
                                    }

                                    Toast.makeText(
                                        activity!!.baseContext,
                                        getString(R.string.failed_file_upload),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    uploadInProgress = false
                                    context?.cacheDir?.deleteRecursively()
                                }
                            } catch (ex: Exception) {
                                Timber.d("File upload failed on error")
                                resetUploadFields(mimeType)
                            }
                        }

                        override fun fileUploadVerified(path: String, thumbId: Long, fileId: Long) {
                            try {
                                activity!!.runOnUiThread {
                                    if (!isThumbnail) {
                                        if (fileId > 0) messageBody.fileId = fileId

                                        sendMessage(
                                            mediaType,
                                            messageBody.fileId!!,
                                            messageBody.thumbId!!
                                        )

                                        imageContainer.hideProgressScreen()
                                        // TODO think about changing this... Index changes for other views when removed
                                        if (imageContainer.childCount > 0) {
                                            imageContainer.removeViewAt(0)
                                        }
                                        uploadIndex++
                                        if (mimeType == Const.JsonFields.IMAGE && uploadIndex < currentPhotoLocation.size) {
                                            uploadImage()
                                        } else if (mimeType == Const.JsonFields.VIDEO && uploadIndex < currentVideoLocation.size) {
                                            uploadVideo()
                                        } else {
                                            resetUploadFields(mimeType)
                                        }
                                    } else {
                                        if (thumbId > 0) messageBody.thumbId = thumbId
                                        if (mimeType == Const.JsonFields.IMAGE) {
                                            uploadMedia(
                                                messageBody,
                                                false,
                                                currentPhotoLocation[uploadIndex],
                                                UploadMimeTypes.IMAGE
                                            )
                                        } else {
                                            uploadMedia(
                                                messageBody,
                                                false,
                                                currentVideoLocation[uploadIndex],
                                                UploadMimeTypes.VIDEO
                                            )
                                        }
                                    }
                                }
                                // update room data
                            } catch (ex: Exception) {
                                Timber.d("File upload failed on verified")
                                resetUploadFields(mimeType)
                            }
                        }
                    })
            } catch (ex: Exception) {
                resetUploadFields(mimeType)
            }
        }
    }

    private fun resetUploadFields(mimeType: String) {
        uploadIndex = 0
        if (mimeType == Const.JsonFields.IMAGE) {
            currentPhotoLocation.clear()
        } else {
            currentVideoLocation.clear()
        }
        thumbnailUris.clear()
        uploadInProgress = false
        context?.cacheDir?.deleteRecursively()
    }

    private fun showUploadError() {
        DialogError.getInstance(activity!!,
            getString(R.string.warning),
            getString(R.string.upload_in_progress),
            getString(R.string.back),
            getString(R.string.ok),
            object : DialogInteraction {
                override fun onFirstOptionClicked() {
                    // ignore
                }

                override fun onSecondOptionClicked() {
                    // Update room visited
                    roomWithUsers.room.visitedRoom = System.currentTimeMillis()
                    viewModel.updateRoomVisitedTimestamp(roomWithUsers.room)
                    //
                    activity!!.finish()
                }
            })
    }

    private fun displayFileInContainer(uri: Uri) {
        val imageSelected = ImageSelectedContainer(activity!!, null)
        var fileName = ""
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)

        val cr = activity!!.contentResolver
        cr.query(uri, projection, null, null, null)?.use { metaCursor ->
            if (metaCursor.moveToFirst()) {
                fileName = metaCursor.getString(0)
            }
        }

        imageSelected.setFile(cr.getType(uri)!!, fileName)
        imageSelected.setButtonListener(object : ImageSelectedContainer.RemoveImageSelected {
            override fun removeImage() {
                bindingSetup.llImagesContainer.removeView(imageSelected)
                bindingSetup.ivAdd.rotation = ROTATION_OFF
            }
        })
        bindingSetup.llImagesContainer.addView(imageSelected)
    }

    private fun getImageOrVideo(uri: Uri) {
        val cR: ContentResolver = context!!.contentResolver
        val mime = MimeTypeMap.getSingleton()
        val type = mime.getExtensionFromMimeType(cR.getType(uri))

        if (type.equals(Const.FileExtensions.MP4)) {
            convertVideo(uri)
        } else {
            convertImageToBitmap(uri)
        }
    }

    private fun convertVideo(videoUri: Uri) {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(context, videoUri)
        val bitmap = mmr.frameAtTime

        val imageSelected = ImageSelectedContainer(activity!!, null)
        bitmap.let { imageBitmap -> imageSelected.setImage(imageBitmap!!) }
        bindingSetup.llImagesContainer.addView(imageSelected)

        activity!!.runOnUiThread { showSendButton() }
        imageSelected.setButtonListener(object :
            ImageSelectedContainer.RemoveImageSelected {
            override fun removeImage() {
                bindingSetup.llImagesContainer.removeView(imageSelected)
                bindingSetup.ivAdd.rotation = ROTATION_OFF
            }
        })
        val thumbnail =
            ThumbnailUtils.extractThumbnail(bitmap, bitmap!!.width, bitmap.height)
        val thumbnailUri = Tools.convertBitmapToUri(activity!!, thumbnail)

        thumbnailUris.add(thumbnailUri)
        currentVideoLocation.add(videoUri)
    }

    private fun convertImageToBitmap(imageUri: Uri?) {
        val bitmap =
            Tools.handleSamplingAndRotationBitmap(activity!!, imageUri)
        val bitmapUri = Tools.convertBitmapToUri(activity!!, bitmap!!)

        val imageSelected = ImageSelectedContainer(context!!, null)
        bitmap.let { imageBitmap -> imageSelected.setImage(imageBitmap) }
        bindingSetup.llImagesContainer.addView(imageSelected)

        activity!!.runOnUiThread { showSendButton() }
        imageSelected.setButtonListener(object :
            ImageSelectedContainer.RemoveImageSelected {
            override fun removeImage() {
                bindingSetup.llImagesContainer.removeView(imageSelected)
                bindingSetup.ivAdd.rotation = ROTATION_OFF
            }
        })
        val thumbnail =
            ThumbnailUtils.extractThumbnail(bitmap, bitmap.width, bitmap.height)
        val thumbnailUri = Tools.convertBitmapToUri(activity!!, thumbnail)

        // Create thumbnail for the image which will also be sent to the backend
        thumbnailUris.add(thumbnailUri)
        currentPhotoLocation.add(bitmapUri)
    }

    private fun checkStoragePermission() {
        storagePermission =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                if (it) {
                    downloadFile(storedMessage)
                } else {
                    Timber.d("Couldn't download file. No permission granted.")
                }
            }
    }

    private fun handleDownloadFile(message: Message) {
        when {
            context?.let {
                ContextCompat.checkSelfPermission(
                    it,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            } == PackageManager.PERMISSION_GRANTED -> {
                downloadFile(message)
            }

            shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                // TODO show why permission is needed
            }

            else -> {
                storedMessage = message
                storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

    }

    private fun downloadFile(message: Message) {
        try {
            val tmp = Tools.getFileUrl(message.body!!.file!!.path)
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
                context!!.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(context, "File is downloading", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Timber.d("$e")
        }
    }

    override fun onBackPressed(): Boolean {
        return if (uploadInProgress) {
            showUploadError()
            false
        } else true
    }
}