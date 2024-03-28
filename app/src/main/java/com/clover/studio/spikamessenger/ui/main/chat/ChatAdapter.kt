package com.clover.studio.spikamessenger.ui.main.chat

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.text.method.LinkMovementMethod
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.clover.studio.spikamessenger.R
import com.clover.studio.spikamessenger.data.models.entity.Message
import com.clover.studio.spikamessenger.data.models.entity.MessageAndRecords
import com.clover.studio.spikamessenger.data.models.entity.User
import com.clover.studio.spikamessenger.databinding.ItemMessageMeBinding
import com.clover.studio.spikamessenger.databinding.ItemMessageOtherBinding
import com.clover.studio.spikamessenger.databinding.ItemSystemMessageBinding
import com.clover.studio.spikamessenger.ui.main.chat.chat_layouts.AudioLayout
import com.clover.studio.spikamessenger.ui.main.chat.chat_layouts.FileLayout
import com.clover.studio.spikamessenger.ui.main.chat.chat_layouts.ImageLayout
import com.clover.studio.spikamessenger.ui.main.chat.chat_layouts.PreviewLayout
import com.clover.studio.spikamessenger.ui.main.chat.chat_layouts.ReplyLayout
import com.clover.studio.spikamessenger.ui.main.chat.chat_layouts.SystemMessageLayout
import com.clover.studio.spikamessenger.ui.main.chat.chat_layouts.VideoLayout
import com.clover.studio.spikamessenger.utils.Const
import com.clover.studio.spikamessenger.utils.Tools
import com.clover.studio.spikamessenger.utils.Tools.getRelativeTimeSpan
import com.clover.studio.spikamessenger.utils.helpers.ChatAdapterHelper
import com.clover.studio.spikamessenger.utils.helpers.ChatAdapterHelper.setViewsVisibility
import com.clover.studio.spikamessenger.utils.helpers.ChatAdapterHelper.showHideUserInformation
import com.vanniktech.emoji.EmojiTextView
import com.vanniktech.emoji.isOnlyEmojis
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

private const val VIEW_TYPE_MESSAGE_SENT = 1
private const val VIEW_TYPE_MESSAGE_RECEIVED = 2
private const val VIEW_TYPE_SYSTEM_MESSAGE = 3
private var oldPosition = -1
private var firstPlay = true
private var playerListener: Player.Listener? = null

class ChatAdapter(
    private val context: Context,
    private val myUserId: Int,
    private val users: List<User>,
    private var exoPlayer: ExoPlayer,
    private var roomType: String?,
    private val onMessageInteraction: ((event: String, message: MessageAndRecords) -> Unit)
) :
    ListAdapter<MessageAndRecords, ViewHolder>(MessageAndRecordsDiffCallback()) {

    private var selectedPosition: Int = RecyclerView.NO_POSITION

    inner class SentMessageHolder(val binding: ItemMessageMeBinding) :
        ViewHolder(binding.root)

    inner class ReceivedMessageHolder(val binding: ItemMessageOtherBinding) :
        ViewHolder(binding.root)

    inner class SystemMessageHolder(val binding: ItemSystemMessageBinding) :
        ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SYSTEM_MESSAGE -> {
                val binding = ItemSystemMessageBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                SystemMessageHolder(binding)
            }

            VIEW_TYPE_MESSAGE_SENT -> {
                val binding =
                    ItemMessageMeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SentMessageHolder(binding)
            }

            else -> {
                val binding = ItemMessageOtherBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ReceivedMessageHolder(binding)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return when {
            Const.JsonFields.SYSTEM_TYPE == message.message.type -> VIEW_TYPE_SYSTEM_MESSAGE
            message.message.fromUserId == myUserId -> VIEW_TYPE_MESSAGE_SENT
            else -> VIEW_TYPE_MESSAGE_RECEIVED
        }
    }

    private var handler = Handler(Looper.getMainLooper())

    @SuppressLint("ClickableViewAccessibility")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position).let {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = it.message.createdAt!!
            val date = calendar.get(Calendar.DAY_OF_MONTH)

            /** View holder for messages from sender */
            when (holder.itemViewType) {
                VIEW_TYPE_MESSAGE_SENT -> {
                    holder as SentMessageHolder

                    if (selectedPosition != 0 && selectedPosition == position) {
                        animateSelectedMessage(holder.itemView)
                        selectedPosition = 0
                    } else {
                        holder.itemView.setBackgroundColor(
                            ContextCompat.getColor(
                                context,
                                android.R.color.transparent
                            )
                        )
                    }

                    if (playerListener != null) {
                        playerListener = null
                    }

                    // The line below sets each adapter item to be unique (uses more memory)
                    // holder.setIsRecyclable(false)

                    // System messages are displaying time so we don't need this interaction
                    it.message.type?.let { type ->
                        bindMessageTime(
                            tvTime = holder.binding.tvTime,
                            clContainer = holder.binding.clMessage,
                            calendar = calendar,
                            type = type
                        )
                    }

                    /** Message types: */
                    when (it.message.type) {
                        Const.JsonFields.TEXT_TYPE -> {
                            setViewsVisibility(holder.binding.tvMessage, holder)
                            bindText(
                                holder = holder,
                                tvMessage = holder.binding.tvMessage,
                                cvReactedEmoji = holder.binding.cvReactedEmoji,
                                chatMessage = it,
                                sender = true
                            )
                        }

                        Const.JsonFields.IMAGE_TYPE, Const.JsonFields.GIF_TYPE -> {
                            setViewsVisibility(holder.binding.cvMedia, holder)
                            setImageLayout(
                                chatMessage = it,
                                container = holder.binding.flMediaContainer,
                            )
                        }

                        Const.JsonFields.VIDEO_TYPE -> {
                            if (it.message.id < 0) {
                                setViewsVisibility(holder.binding.cvMedia, holder)
                                setImageLayout(
                                    chatMessage = it,
                                    container = holder.binding.flMediaContainer,
                                )
                            } else {
                                setViewsVisibility(holder.binding.cvMedia, holder)
                                setVideoLayout(
                                    chatMessage = it,
                                    container = holder.binding.flMediaContainer,
                                )
                            }
                        }

                        Const.JsonFields.FILE_TYPE -> {
                            setViewsVisibility(holder.binding.cvMedia, holder)
                            setFileLayout(
                                chatMessage = it,
                                container = holder.binding.flMediaContainer,
                                sender = true
                            )
                        }

                        Const.JsonFields.AUDIO_TYPE -> {
                            setViewsVisibility(holder.binding.cvMedia, holder)
                            bindAudio(
                                chatMessage = it,
                                container = holder.binding.flMediaContainer,
                                holder = holder
                            )
                        }

                        else -> {
                            setViewsVisibility(holder.binding.tvMessage, holder)
                        }
                    }

                    /** Other: */

                    /** Show message reply: */
                    if (it.message.replyId != null && it.message.replyId != 0L && it.message.deleted == false) {
                        setViewsVisibility(holder.binding.flReplyMsgContainer, holder)
                        holder.binding.tvMessage.visibility = View.VISIBLE

                        setUpReplyLayout(
                            chatMessage = it,
                            parentContainer = holder.binding.clContainer,
                            replyContainer = holder.binding.flReplyMsgContainer,
                            sender = true
                        )
                    }

                    /** Show message preview: */
                    if (it.message.body?.thumbnailData != null && it.message.body.thumbnailData?.title?.isNotEmpty() == true && it.message.deleted == false) {
                        holder.binding.flPreviewMsgContainer.visibility = View.VISIBLE
                        holder.binding.tvMessage.visibility = View.VISIBLE

                        setUpPreviewLayout(
                            chatMessage = it,
                            previewContainer = holder.binding.flPreviewMsgContainer,
                            sender = true
                        )
                    } else {
                        holder.binding.flPreviewMsgContainer.visibility = View.GONE
                        holder.binding.flPreviewMsgContainer.removeAllViews()
                    }

                    /** Show edited/forwarded layout: */
                    holder.binding.tvMessageAction.visibility = View.GONE
                    if (it.message.deleted == false){
                        if (it.message.isForwarded){
                            holder.binding.tvMessageAction.text = context.getString(R.string.forwarded)
                            holder.binding.tvMessageAction.visibility = View.VISIBLE
                        } else {
                            if (it.message.createdAt != it.message.modifiedAt){
                                holder.binding.tvMessageAction.text = context.getString(R.string.edited)
                                holder.binding.tvMessageAction.visibility = View.VISIBLE
                            }
                        }
                    }

                    /** Show reactions: */
                    holder.binding.cvReactedEmoji.visibility = View.GONE
                    if (it.message.deleted != null && !it.message.deleted) {
                        ChatAdapterHelper.bindReactions(
                            chatMessage = it,
                            tvReactedEmoji = holder.binding.tvReactedEmoji,
                            cvReactedEmoji = holder.binding.cvReactedEmoji
                        )
                    }

                    holder.binding.cvReactedEmoji.setOnClickListener { _ ->
                        onMessageInteraction.invoke(Const.UserActions.SHOW_MESSAGE_REACTIONS, it)
                    }

                    /** Send new reaction: */
                    sendReaction(it, holder.binding.clContainer, holder.absoluteAdapterPosition)

                    /** Show date header: */
                    showDateHeader(position, date, holder.binding.tvSectionHeader, it.message)

                    ChatAdapterHelper.showMessageStatus(it, holder.binding.ivMessageStatus)

                    addMargins(position, true, holder.binding.clMessage)

                }

                VIEW_TYPE_MESSAGE_RECEIVED -> {
                    /** View holder for messages from other users */
                    holder as ReceivedMessageHolder

                    if (selectedPosition != 0 && selectedPosition == position) {
                        animateSelectedMessage(holder.itemView)
                        selectedPosition = 0
                    } else {
                        holder.itemView.setBackgroundColor(
                            ContextCompat.getColor(
                                context,
                                android.R.color.transparent
                            )
                        )
                    }

                    if (playerListener != null) {
                        playerListener = null
                    }

                    // The line below sets each adapter item to be unique (uses more memory)
                    // holder.setIsRecyclable(false)
                    // System messages are displaying time so we don't need this interaction
                    it.message.type?.let { type ->
                        bindMessageTime(
                            tvTime = holder.binding.tvTime,
                            clContainer = holder.binding.clMessage,
                            calendar = calendar,
                            type = type
                        )
                    }

                    /** Message types: */
                    when (it.message.type) {
                        Const.JsonFields.TEXT_TYPE -> {
                            setViewsVisibility(holder.binding.tvMessage, holder)
                            bindText(
                                holder = holder,
                                tvMessage = holder.binding.tvMessage,
                                cvReactedEmoji = holder.binding.cvReactedEmoji,
                                chatMessage = it,
                                sender = false,
                            )
                        }

                        Const.JsonFields.IMAGE_TYPE -> {
                            setViewsVisibility(holder.binding.cvMedia, holder)
                            setImageLayout(
                                chatMessage = it,
                                container = holder.binding.flMediaContainer,
                            )
                        }

                        Const.JsonFields.VIDEO_TYPE -> {
                            setViewsVisibility(holder.binding.cvMedia, holder)
                            setVideoLayout(
                                chatMessage = it,
                                container = holder.binding.flMediaContainer,
                            )
                        }

                        Const.JsonFields.FILE_TYPE -> {
                            setViewsVisibility(holder.binding.cvMedia, holder)
                            setFileLayout(
                                chatMessage = it,
                                container = holder.binding.flMediaContainer,
                                sender = false
                            )
                        }

                        Const.JsonFields.AUDIO_TYPE -> {
                            setViewsVisibility(holder.binding.cvMedia, holder)
                            bindAudio(
                                chatMessage = it,
                                container = holder.binding.flMediaContainer,
                                holder = holder
                            )
                        }

                        else -> {
                            setViewsVisibility(holder.binding.tvMessage, holder)
                        }
                    }

                    /** Other: */
                    if (it.message.replyId != null && it.message.replyId != 0L && it.message.deleted == false) {
                        setViewsVisibility(holder.binding.flReplyMsgContainer, holder)
                        holder.binding.tvMessage.visibility = View.VISIBLE

                        setUpReplyLayout(
                            chatMessage = it,
                            parentContainer = holder.binding.clContainer,
                            replyContainer = holder.binding.flReplyMsgContainer,
                            sender = false
                        )
                    }

                    /** Show message preview: */
                    if (it.message.body?.thumbnailData != null && it.message.body.thumbnailData?.title?.isNotEmpty() == true && it.message.deleted == false) {
                        holder.binding.tvMessage.visibility = View.VISIBLE
                        holder.binding.flPreviewMsgContainer.visibility = View.VISIBLE

                        setUpPreviewLayout(
                            chatMessage = it,
                            previewContainer = holder.binding.flPreviewMsgContainer,
                            sender = false
                        )
                    } else {
                        holder.binding.flPreviewMsgContainer.visibility = View.GONE
                        holder.binding.flPreviewMsgContainer.removeAllViews()
                    }

                    /** Show edited/forwarded layout: */
                    holder.binding.tvMessageAction.visibility = View.GONE
                    if (it.message.deleted == false){
                        if (it.message.isForwarded){
                            holder.binding.tvMessageAction.text = context.getString(R.string.forwarded)
                            holder.binding.tvMessageAction.visibility = View.VISIBLE
                        } else {
                            if (it.message.createdAt != it.message.modifiedAt){
                                holder.binding.tvMessageAction.text = context.getString(R.string.edited)
                                holder.binding.tvMessageAction.visibility = View.VISIBLE
                            }
                        }
                    }

                    /** Show user names and avatars in group chat */
                    if (Const.JsonFields.PRIVATE == roomType) {
                        holder.binding.ivUserImage.visibility = View.GONE
                        holder.binding.tvUsername.visibility = View.GONE
                    } else {
                        val roomUser = users.find { user -> user.id == it.message.fromUserId }
                        if (roomUser != null) {
                            holder.binding.tvUsername.text = roomUser.formattedDisplayName
                            val userPath = roomUser.avatarFileId?.let { fileId ->
                                Tools.getFilePathUrl(fileId)
                            }
                            Glide.with(context)
                                .load(userPath)
                                .dontTransform()
                                .placeholder(R.drawable.img_user_avatar)
                                .error(R.drawable.img_user_avatar)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .into(holder.binding.ivUserImage)

                            holder.binding.tvUsername.visibility = View.VISIBLE
                            holder.binding.ivUserImage.apply {
                                visibility = View.VISIBLE
                                setOnClickListener { _ ->
                                    onMessageInteraction.invoke(
                                        Const.UserActions.NAVIGATE_TO_USER_DETAILS,
                                        it
                                    )
                                }
                            }
                        } else {
                            // User probably doesn't exist in the room anymore
                            holder.binding.tvUsername.text =
                                context.getString(R.string.removed_group_user)

                            Glide.with(context)
                                .load(R.drawable.img_user_avatar)
                                .dontTransform()
                                .placeholder(R.drawable.img_user_avatar)
                                .error(R.drawable.img_user_avatar)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .into(holder.binding.ivUserImage)

                            holder.binding.tvUsername.visibility = View.VISIBLE
                            holder.binding.ivUserImage.apply {
                                visibility = View.VISIBLE
                                setOnClickListener { }
                            }
                        }
                    }

                    /** Show reactions: */
                    holder.binding.cvReactedEmoji.visibility = View.GONE
                    if (it.message.deleted != null && !it.message.deleted) {
                        ChatAdapterHelper.bindReactions(
                            chatMessage = it,
                            tvReactedEmoji = holder.binding.tvReactedEmoji,
                            cvReactedEmoji = holder.binding.cvReactedEmoji
                        )
                    }

                    /** Send new reaction: */
                    sendReaction(it, holder.binding.clContainer, holder.absoluteAdapterPosition)

                    holder.binding.cvReactedEmoji.setOnClickListener { _ ->
                        onMessageInteraction.invoke(Const.UserActions.SHOW_MESSAGE_REACTIONS, it)
                    }

                    /** Show date header: */
                    showDateHeader(position, date, holder.binding.tvSectionHeader, it.message)

                    /** Show username and avatar only once in multiple consecutive messages */
                    if (roomType != Const.JsonFields.PRIVATE) {
                        showHideUserInformation(position, holder, currentList)
                    }

                    addMargins(position, false, holder.binding.clMessage)
                }

                VIEW_TYPE_SYSTEM_MESSAGE -> {
                    holder as SystemMessageHolder
                    bindSystemMessage(holder.binding.tvSystemMessage, it)
                }

                else -> Timber.d("Error")
            }
        }

    }

    private fun setUpReplyLayout(
        chatMessage: MessageAndRecords,
        parentContainer: ConstraintLayout,
        replyContainer: FrameLayout,
        sender: Boolean
    ) {
        val reply = ReplyLayout(context)
        reply.setReplyLayoutListener(object : ReplyLayout.ReplyLayoutListener {
            override fun replyLayoutClick() {
                onMessageInteraction.invoke(Const.UserActions.MESSAGE_REPLY, chatMessage)
            }
        })
        reply.bindReply(
            context = context,
            chatMessage = chatMessage,
            users = users,
            clContainer = parentContainer,
            sender = sender,
            roomType = roomType
        )
        replyContainer.addView(reply)
    }

    private fun setUpPreviewLayout(
        chatMessage: MessageAndRecords,
        previewContainer: FrameLayout,
        sender: Boolean
    ) {
        val preview = PreviewLayout(context)
        preview.setPreviewLayoutListener(object : PreviewLayout.PreviewLayoutListener {
            override fun previewLayoutClicked() {
                onMessageInteraction.invoke(Const.UserActions.MESSAGE_PREVIEW, chatMessage)
            }
        })
        preview.bindPreview(
            context = context,
            chatMessage = chatMessage,
            sender = sender
        )
        previewContainer.addView(preview)
    }

    private fun setFileLayout(
        chatMessage: MessageAndRecords,
        container: FrameLayout,
        sender: Boolean
    ) {
        val fileLayout = FileLayout(context)
        fileLayout.setFileLayoutListener(object :
            FileLayout.FileLayoutListener {
            override fun downloadFile() {
                onMessageInteraction.invoke(Const.UserActions.DOWNLOAD_FILE, chatMessage)
            }

            override fun resendFile() {
                onMessageInteraction(Const.UserActions.RESEND_MESSAGE, chatMessage)
            }

            override fun cancelFileUpload() {
                onMessageInteraction(Const.UserActions.CANCEL_UPLOAD, chatMessage)
            }
        })

        fileLayout.bindFile(chatMessage = chatMessage, sender = sender)
        container.addView(fileLayout)
    }

    private fun setVideoLayout(
        chatMessage: MessageAndRecords,
        container: FrameLayout
    ) {
        val video = VideoLayout(context)
        video.setVideoLayoutListener(object : VideoLayout.VideoLayoutListener {
            override fun mediaNavigation() {
                onMessageInteraction(Const.UserActions.NAVIGATE_TO_MEDIA_FRAGMENT, chatMessage)
            }
        })
        video.bindVideo(chatMessage = chatMessage)
        container.addView(video)
    }

    private fun setImageLayout(
        chatMessage: MessageAndRecords,
        container: FrameLayout
    ) {
        val image = ImageLayout(context)
        image.setImageLayoutListener(object : ImageLayout.ImageLayoutListener {
            override fun imageNavigation() {
                onMessageInteraction(Const.UserActions.NAVIGATE_TO_MEDIA_FRAGMENT, chatMessage)
            }

            override fun imageResend() {
                onMessageInteraction(Const.UserActions.RESEND_MESSAGE, chatMessage)
            }

            override fun imageCancelUpload() {
                onMessageInteraction(Const.UserActions.CANCEL_UPLOAD, chatMessage)
            }

            override fun imageOptions() {
                onMessageInteraction(Const.UserActions.MESSAGE_ACTION, chatMessage)
            }
        })
        image.bindImage(chatMessage = chatMessage.message)
        container.addView(image)
    }

    private fun addMargins(position: Int, sender: Boolean, clMessage: ConstraintLayout) {
        val layoutParams = clMessage.layoutParams as ViewGroup.MarginLayoutParams

        if (position != currentList.size - 1) {
            val type = getItemViewType(position + 1)
            val margin =
                if (sender && type == VIEW_TYPE_MESSAGE_RECEIVED || !sender && type == VIEW_TYPE_MESSAGE_SENT) {
                    context.resources.getDimensionPixelSize(R.dimen.sixteen_dp_margin)
                } else {
                    context.resources.getDimensionPixelSize(R.dimen.four_dp_margin)
                }
            layoutParams.topMargin = margin
        }
        clMessage.layoutParams = layoutParams
    }

    private fun bindMessageTime(
        calendar: Calendar,
        tvTime: TextView,
        clContainer: ConstraintLayout,
        type: String
    ) {
        tvTime.visibility = View.GONE
        tvTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(
            calendar.timeInMillis
        ).toString()

        if (Const.JsonFields.SYSTEM_TYPE != type) {
            clContainer.setOnClickListener {
                tvTime.visibility = if (tvTime.visibility == View.VISIBLE) {
                    View.GONE
                } else {
                    View.VISIBLE
                }

            }
        } else {
            clContainer.setOnClickListener(null)
        }
    }

    fun setSelectedPosition(position: Int) {
        selectedPosition = position
        notifyItemChanged(position)
    }

    /** A method that sets the color of the selected message to the selected_message color and
     *  then changes its alpha value to change from that color to transparent
     *  (alpha from 255 to 0)*/
    private fun animateSelectedMessage(itemView: View) {
        val alphaAnimator = ObjectAnimator.ofInt(255, 0)
        alphaAnimator.duration = 2000

        alphaAnimator.addUpdateListener { animator ->
            val alpha = animator.animatedValue as Int
            val backgroundDrawable =
                ColorDrawable(ContextCompat.getColor(context, R.color.selected_message))
            backgroundDrawable.alpha = alpha
            itemView.background = backgroundDrawable
        }

        alphaAnimator.start()
    }

    /** Methods that bind different types of messages: */
    private fun bindText(
        holder: ViewHolder,
        tvMessage: EmojiTextView,
        cvReactedEmoji: CardView,
        chatMessage: MessageAndRecords,
        sender: Boolean
    ) {
        val messageText = chatMessage.message.body?.text.toString()
        if (messageText.isOnlyEmojis()) {
            tvMessage.setEmojiSize(Tools.getEmojiSize(messageText))
        } else {
            tvMessage.background = AppCompatResources.getDrawable(
                context,
                if (sender) R.drawable.bg_message_send else R.drawable.bg_message_received
            )
            tvMessage.setEmojiSize(0)
        }
        tvMessage.text = messageText

        tvMessage.apply {
            if (chatMessage.message.deleted == true || chatMessage.message.body?.text == context.getString(
                    R.string.deleted_message
                ) || chatMessage.message.type == Const.JsonFields.SYSTEM_TYPE
            ) {
                text = context.getString(R.string.message_deleted_text)
                cvReactedEmoji.visibility = View.GONE
                background =
                    AppCompatResources.getDrawable(
                        context,
                        if (sender) R.drawable.bg_deleted_msg_send else R.drawable.bg_deleted_msg_received
                    )
                setOnLongClickListener { true }
            } else {
                setOnLongClickListener {
                    chatMessage.message.messagePosition = holder.absoluteAdapterPosition
                    onMessageInteraction.invoke(Const.UserActions.MESSAGE_ACTION, chatMessage)
                    true
                }
                movementMethod = LinkMovementMethod.getInstance()

                setOnClickListener {
                    if (chatMessage.message.deliveredCount == -1) {
                        onMessageInteraction.invoke(Const.UserActions.RESEND_MESSAGE, chatMessage)
                    }
                }
            }
        }
    }

    private fun bindSystemMessage(tvSystemMessage: TextView, msg: MessageAndRecords) {
        tvSystemMessage.text = SystemMessageLayout(context).bindSystemMessage(
            msg = msg,
            users = users
        )
    }

    private fun bindAudio(
        chatMessage: MessageAndRecords,
        container: FrameLayout,
        holder: ViewHolder,
    ) {
        val audio = AudioLayout(context)
        audio.bindAudio(chatMessage = chatMessage)

        val audioPath = chatMessage.message.body?.file?.id?.let { audioPath ->
            Tools.getFilePathUrl(
                audioPath
            )
        }

        val mediaItem: MediaItem = MediaItem.fromUri(Uri.parse(audioPath))
        exoPlayer.clearMediaItems()

        audio.setProgress(0)

        val runnable = object : Runnable {
            override fun run() {
                audio.setProgress(exoPlayer.currentPosition.toInt())
                audio.setDuration(Tools.convertDurationMillis(exoPlayer.currentPosition))
                handler.postDelayed(this, 100)
            }
        }

        playerListener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> audio.setMaxProgress(exoPlayer.duration.toInt())
                    Player.STATE_ENDED -> {
                        audio.setPlayVisibility(View.VISIBLE)

                        firstPlay = true
                        exoPlayer.pause()
                        exoPlayer.clearMediaItems()
                        handler.removeCallbacks(runnable)

                        audio.setDuration(context.getString(R.string.audio_duration))
                        audio.setPlayImage(R.drawable.img_play_audio_button)

                    }

                    else -> Timber.d("Other state: $state")
                }
            }
        }

        exoPlayer.addListener(playerListener!!)

        audio.setupAudioLayoutListener(object : AudioLayout.AudioLayoutListener {
            override fun audioPlayClicked() {
                if (!exoPlayer.isPlaying) {
                    if (oldPosition != holder.absoluteAdapterPosition) {
                        firstPlay = true
                        exoPlayer.stop()
                        exoPlayer.clearMediaItems()

                        audio.setDuration(context.getString(R.string.audio_duration))

                        handler.removeCallbacks(runnable)
                        notifyItemChanged(oldPosition)
                        oldPosition = holder.absoluteAdapterPosition
                    }
                    if (firstPlay) {
                        exoPlayer.prepare()
                        exoPlayer.setMediaItem(mediaItem)
                    }
                    exoPlayer.play()
                    handler.postDelayed(runnable, 0)

                    audio.setPlayImage(R.drawable.img_pause_audio_button)
                } else {
                    audio.setPlayImage(R.drawable.img_play_audio_button)

                    exoPlayer.pause()
                    firstPlay = false
                    handler.removeCallbacks(runnable)
                }
            }

            override fun audioSeekBarPressed(progress: Int) {
                exoPlayer.seekTo(progress.toLong())
            }

            override fun audioResend() {
                onMessageInteraction(Const.UserActions.RESEND_MESSAGE, chatMessage)
            }

            override fun audioCancelUpload() {
                onMessageInteraction(Const.UserActions.CANCEL_UPLOAD, chatMessage)
            }
        })
        container.addView(audio)
    }

    /** A method that sends a reaction to a message */
    private fun sendReaction(
        chatMessage: MessageAndRecords?,
        clContainer: ConstraintLayout,
        position: Int
    ) {
        clContainer.setOnLongClickListener {
            chatMessage!!.message.messagePosition = position
            onMessageInteraction.invoke(Const.UserActions.MESSAGE_ACTION, chatMessage)
            true
        }
    }

    /** A method that displays a date header - eg Yesterday, Two days ago, Now*/
    private fun showDateHeader(
        position: Int,
        date: Int,
        view: TextView,
        message: Message
    ) {
        if (position >= 0 && currentList.size - 1 > position) {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = getItem(position + 1).message.createdAt!!
            val previousDate = calendar.get(Calendar.DAY_OF_MONTH)

            if (date != previousDate) {
                view.visibility = View.VISIBLE
            } else view.visibility = View.GONE

            view.text = message.createdAt?.let {
                DateUtils.getRelativeTimeSpanString(
                    it, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS
                )
            }

        } else {
            view.visibility = View.VISIBLE
            val time = message.createdAt?.let {
                getRelativeTimeSpan(it)
            }

            if (time == context.getString(R.string.zero_minutes_ago) ||
                time == context.getString(R.string.in_zero_minutes)
            ) {
                view.text = context.getString(R.string.now)
            } else {
                view.text = time
            }
        }
    }

    private class MessageAndRecordsDiffCallback : DiffUtil.ItemCallback<MessageAndRecords>() {
        override fun areItemsTheSame(
            oldItem: MessageAndRecords,
            newItem: MessageAndRecords
        ): Boolean {
            return oldItem.message.id == newItem.message.id
        }

        override fun areContentsTheSame(
            oldItem: MessageAndRecords,
            newItem: MessageAndRecords
        ): Boolean {
            return oldItem == newItem
        }
    }
}
