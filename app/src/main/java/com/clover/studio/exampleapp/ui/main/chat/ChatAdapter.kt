package com.clover.studio.exampleapp.ui.main.chat

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.request.target.Target.SIZE_ORIGINAL
import com.clover.studio.exampleapp.R
import com.clover.studio.exampleapp.data.models.*
import com.clover.studio.exampleapp.databinding.ItemMessageMeBinding
import com.clover.studio.exampleapp.databinding.ItemMessageOtherBinding
import com.clover.studio.exampleapp.utils.Const
import com.clover.studio.exampleapp.utils.Tools
import com.clover.studio.exampleapp.utils.Tools.getRelativeTimeSpan
import timber.log.Timber
import java.util.*


private const val VIEW_TYPE_MESSAGE_SENT = 1
private const val VIEW_TYPE_MESSAGE_RECEIVED = 2
private var REACTION = ""
private var reactionMessage: ReactionMessage =
    ReactionMessage(
        "", 0, 0, false, ReactionActive(
            thumbsUp = false,
            heart = false,
            prayingHandsEmoji = false,
            astonishedEmoji = false,
            relievedEmoji = false,
            cryingFaceEmoji = false
        )
    )
//private var reactions = Reactions(0, 0, 0, 0, 0, 0)

class ChatAdapter(
    private val context: Context,
    private val myUserId: Int,
    private val users: List<User>,
    private val chatType: String,
    private val addReaction: ((reaction: ReactionMessage) -> Unit),
) :
    ListAdapter<MessageAndRecords, RecyclerView.ViewHolder>(MessageAndRecordsDiffCallback()) {

    inner class SentMessageHolder(val binding: ItemMessageMeBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class ReceivedMessageHolder(val binding: ItemMessageOtherBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_MESSAGE_SENT) {
            val binding =
                ItemMessageMeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            SentMessageHolder(binding)
        } else {
            val binding =
                ItemMessageOtherBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ReceivedMessageHolder(binding)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)

        return if (message.message.fromUserId == myUserId) {
            VIEW_TYPE_MESSAGE_SENT
        } else {
            VIEW_TYPE_MESSAGE_RECEIVED
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        getItem(position).let { it ->

            val calendar = Calendar.getInstance()
            calendar.timeInMillis = it.message.createdAt!!
            val date = calendar.get(Calendar.DAY_OF_MONTH)

            // View holder for my messages
            // TODO can two view holders use same method for binding if all views are the same?
            if (holder.itemViewType == VIEW_TYPE_MESSAGE_SENT) {
                holder as SentMessageHolder
                when (it.message.type) {
                    Const.JsonFields.TEXT -> {
                        holder.binding.tvMessage.text = it.message.body?.text
                        holder.binding.tvMessage.visibility = View.VISIBLE
                        holder.binding.cvImage.visibility = View.GONE
                        holder.binding.clFileMessage.visibility = View.GONE
                        holder.binding.clVideos.visibility = View.GONE
                    }
                    Const.JsonFields.CHAT_IMAGE -> {
                        holder.binding.tvMessage.visibility = View.GONE
                        holder.binding.cvImage.visibility = View.VISIBLE
                        holder.binding.clFileMessage.visibility = View.GONE
                        holder.binding.clVideos.visibility = View.GONE

                        val imagePath = it.message.body?.file?.path?.let { imagePath ->
                            Tools.getFileUrl(
                                imagePath
                            )
                        }

                        Glide.with(context)
                            .load(imagePath)
                            .override(SIZE_ORIGINAL, SIZE_ORIGINAL)
                            .placeholder(R.drawable.ic_baseline_image_24)
                            .dontTransform()
                            .dontAnimate()
                            .into(holder.binding.ivChatImage)

                        holder.binding.clContainer.setOnClickListener { view ->
                            val action =
                                ChatMessagesFragmentDirections.actionChatMessagesFragment2ToVideoFragment2(
                                    "", imagePath!!
                                )
                            view.findNavController().navigate(action)
                        }
                    }

                    Const.JsonFields.FILE_TYPE -> {
                        holder.binding.tvMessage.visibility = View.GONE
                        holder.binding.cvImage.visibility = View.GONE
                        holder.binding.clFileMessage.visibility = View.VISIBLE
                        holder.binding.clVideos.visibility = View.GONE

                        holder.binding.tvFileTitle.text = it.message.body?.file?.fileName
                        val megabyteText =
                            "${
                                Tools.calculateToMegabyte(it.message.body?.file?.size!!)
                                    .toString()
                            } ${holder.itemView.context.getString(R.string.files_mb_text)}"
                        holder.binding.tvFileSize.text = megabyteText
                        addFiles(it.message, holder.binding.ivFileType)

                        // TODO implement file handling when clicked on in chat
                        /*val filePath = it.message.body.file?.path?.let { filePath ->
                            Tools.getFileUrl(
                                filePath
                            )
                        }*/
                        holder.binding.tvFileTitle.setOnClickListener {

                        }
                    }

                    Const.JsonFields.VIDEO -> {
                        holder.binding.tvMessage.visibility = View.GONE
                        holder.binding.cvImage.visibility = View.GONE
                        holder.binding.clFileMessage.visibility = View.GONE

                        val videoPath = it.message.body?.file?.path?.let { videoPath ->
                            Tools.getFileUrl(
                                videoPath
                            )
                        }

                        Glide.with(context)
                            .load(videoPath)
                            .priority(Priority.HIGH)
                            .dontTransform()
                            .dontAnimate()
                            .placeholder(R.drawable.ic_baseline_videocam_24)
                            .override(SIZE_ORIGINAL, SIZE_ORIGINAL)
                            .into(holder.binding.ivVideoThumbnail)

                        holder.binding.clVideos.visibility = View.VISIBLE
                        holder.binding.ivPlayButton.setImageResource(R.drawable.ic_baseline_play_circle_filled_24)

                        holder.binding.ivPlayButton.setOnClickListener { view ->
                            val action =
                                ChatMessagesFragmentDirections.actionChatMessagesFragment2ToVideoFragment2(
                                    videoPath!!, ""
                                )
                            view.findNavController().navigate(action)
                        }
                    }

                    else -> {
                        holder.binding.tvMessage.visibility = View.VISIBLE
                        holder.binding.cvImage.visibility = View.GONE
                        holder.binding.clFileMessage.visibility = View.GONE
                        holder.binding.clVideos.visibility = View.GONE
                    }
                }

                /* Reactions section: */

                // Listener - remove reaction layouts
                holder.binding.clMessageMe.setOnTouchListener { _, _ ->
                    if (holder.binding.cvReactions.visibility == View.VISIBLE) {
                        holder.binding.cvReactions.visibility = View.GONE
                        //holder.binding.cvMessageOptions.visibility = View.GONE
                    }
                    return@setOnTouchListener true
                }

                // Get reactions from database:
                var reactionText = ""
                var reactions = Reactions(0, 0, 0, 0, 0, 0)
                // Private chat - show only last reaction
                if (chatType == Const.JsonFields.PRIVATE) {
                    try {
                        val last = it.records!!.last().reaction
                        if (!last.isNullOrEmpty()) {
                            holder.binding.tvReactedEmoji.text = last
                            holder.binding.cvReactedEmoji.visibility = View.VISIBLE
                        } else {
                            holder.binding.cvReactedEmoji.visibility = View.GONE
                        }
                    } catch (e: Exception) {
                        Timber.d(e.toString())
                    }

                    // Group chat:
                } else {
                    var myReaction = ""
                    /* TODO: if user changes reaction show only last reaction*/
                    // TODO Matko
                    // Message id: 16861
                    for (record in it.records!!) {
                        if (!record.reaction.isNullOrEmpty()) {
                            if (record.userId != myUserId) {
                                getAllReactions(record.reaction, reactions)
                            } else {
                                // Tmp solution - getting only last sent reaction
                                myReaction = record.reaction
                            }
                        }
                    }

                    if (myReaction.isNotEmpty()) {
                        getAllReactions(myReaction, reactions)
                    }

                    reactionText = getGroupReactions(reactions, "")
                    if (reactionText.isNotEmpty()) {
                        holder.binding.tvReactedEmoji.text = reactionText
                        holder.binding.cvReactedEmoji.visibility = View.VISIBLE
                    } else {
                        holder.binding.cvReactedEmoji.visibility = View.GONE
                    }
                }

                holder.binding.clContainer.setOnLongClickListener { _ ->
                    holder.binding.cvReactions.visibility = View.VISIBLE
                    //val reactionsContainer = holder.binding.cllReactions as ReactionsContainer
                    //holder.binding.cvMessageOptions.visibility = View.VISIBLE
                    Timber.d("here!")
                    if (chatType == Const.JsonFields.PRIVATE) {
                        val privateReactions = Reactions(0, 0, 0, 0, 0, 0)
                        listeners(holder, it.message.id, privateReactions)
                    } else {
                        listeners(holder, it.message.id, reactions)
                    }
                    true
                }

                showDateHeader(position, date, holder.binding.tvSectionHeader, it.message)

                when {
                    it.message.seenCount!! > 0 -> {
                        holder.binding.ivMessageStatus.setImageDrawable(
                            ContextCompat.getDrawable(
                                context,
                                R.drawable.img_seen
                            )
                        )
                    }
                    it.message.totalUserCount == it.message.deliveredCount -> {
                        holder.binding.ivMessageStatus.setImageDrawable(
                            ContextCompat.getDrawable(
                                context,
                                R.drawable.img_done
                            )
                        )
                    }
                    it.message.deliveredCount!! >= 0 -> {
                        holder.binding.ivMessageStatus.setImageDrawable(
                            ContextCompat.getDrawable(
                                context,
                                R.drawable.img_sent
                            )
                        )
                    }
                    else -> {
                        holder.binding.ivMessageStatus.setImageDrawable(
                            ContextCompat.getDrawable(
                                context,
                                R.drawable.img_clock
                            )
                        )
                    }
                }
            } else {
                // View holder for messages from other users
                holder as ReceivedMessageHolder
                when (it.message.type) {
                    Const.JsonFields.TEXT -> {
                        holder.binding.tvMessage.text = it.message.body?.text
                        holder.binding.tvMessage.visibility = View.VISIBLE
                        holder.binding.cvImage.visibility = View.GONE
                        holder.binding.clFileMessage.visibility = View.GONE
                        holder.binding.clVideos.visibility = View.GONE
                    }
                    Const.JsonFields.CHAT_IMAGE -> {
                        holder.binding.tvMessage.visibility = View.GONE
                        holder.binding.clImages.visibility = View.VISIBLE
                        holder.binding.clFileMessage.visibility = View.GONE
                        holder.binding.clVideos.visibility = View.GONE

                        val imagePath = it.message.body?.file?.path?.let { imagePath ->
                            Tools.getFileUrl(
                                imagePath
                            )
                        }

                        Glide.with(context)
                            .load(imagePath)
                            .override(SIZE_ORIGINAL, SIZE_ORIGINAL)
                            .placeholder(R.drawable.ic_baseline_image_24)
                            .dontTransform()
                            .dontAnimate()
                            .into(holder.binding.ivChatImage)

                        holder.binding.clContainer.setOnClickListener { view ->
                            val action =
                                ChatMessagesFragmentDirections.actionChatMessagesFragment2ToVideoFragment2(
                                    "", imagePath!!
                                )
                            view.findNavController().navigate(action)
                        }

                    }

                    Const.JsonFields.VIDEO -> {
                        holder.binding.tvMessage.visibility = View.GONE
                        holder.binding.clImages.visibility = View.GONE
                        holder.binding.clFileMessage.visibility = View.GONE
                        holder.binding.clVideos.visibility = View.VISIBLE

                        val videoPath = it.message.body?.file?.path?.let { videoPath ->
                            Tools.getFileUrl(
                                videoPath
                            )
                        }

                        Glide.with(context)
                            .load(videoPath)
                            .priority(Priority.HIGH)
                            .dontTransform()
                            .dontAnimate()
                            .placeholder(R.drawable.ic_baseline_videocam_24)
                            .override(SIZE_ORIGINAL, SIZE_ORIGINAL)
                            .into(holder.binding.ivVideoThumbnail)

                        holder.binding.clVideos.visibility = View.VISIBLE
                        holder.binding.ivPlayButton.setImageResource(R.drawable.ic_baseline_play_circle_filled_24)

                        holder.binding.ivPlayButton.setOnClickListener { view ->
                            val action =
                                ChatMessagesFragmentDirections.actionChatMessagesFragment2ToVideoFragment2(
                                    videoPath!!, ""
                                )
                            view.findNavController().navigate(action)
                        }
                    }

                    Const.JsonFields.FILE_TYPE -> {
                        holder.binding.tvMessage.visibility = View.GONE
                        holder.binding.cvImage.visibility = View.GONE
                        holder.binding.clFileMessage.visibility = View.VISIBLE
                        holder.binding.clVideos.visibility = View.GONE

                        holder.binding.tvFileTitle.text = it.message.body?.file?.fileName
                        val megabyteText =
                            "${
                                Tools.calculateToMegabyte(it.message.body?.file?.size!!)
                                    .toString()
                            } ${holder.itemView.context.getString(R.string.files_mb_text)}"
                        holder.binding.tvFileSize.text = megabyteText

                        addFiles(it.message, holder.binding.ivFileType)
                    }
                    else -> {
                        holder.binding.tvMessage.visibility = View.VISIBLE
                        holder.binding.cvImage.visibility = View.GONE
                        holder.binding.clFileMessage.visibility = View.GONE
                        holder.binding.clVideos.visibility = View.GONE
                    }
                }

                if (it.message.body?.text.isNullOrEmpty()) {
                    holder.binding.tvMessage.visibility = View.GONE
                    holder.binding.cvImage.visibility = View.VISIBLE

                    Glide.with(context)
                        .load(it.message.body?.file?.path?.let { imagePath ->
                            Tools.getFileUrl(
                                imagePath
                            )
                        })
                        .into(holder.binding.ivChatImage)
                } else {
                    holder.binding.tvMessage.visibility = View.VISIBLE
                    holder.binding.cvImage.visibility = View.GONE
                }

                for (roomUser in users) {
                    if (it.message.fromUserId == roomUser.id) {
                        holder.binding.tvUsername.text = roomUser.displayName
                        Glide.with(context)
                            .load(roomUser.avatarUrl?.let { avatarUrl ->
                                Tools.getFileUrl(
                                    avatarUrl
                                )
                            })
                            .placeholder(context.getDrawable(R.drawable.img_user_placeholder))
                            .into(holder.binding.ivUserImage)
                        break
                    }
                }

                /* Reactions section: */
                // Listener - remove reaction layouts
                holder.binding.clMessageOther.setOnTouchListener { _, _ ->
                    if (holder.binding.cvReactions.visibility == View.VISIBLE) {
                        holder.binding.cvReactions.visibility = View.GONE
                        //holder.binding.cvMessageOptions.visibility = View.GONE
                    }
                    return@setOnTouchListener true
                }

                // Get reactions from database:
                var reactionText = ""
                var reactionId = 0
                val reactions = Reactions(0, 0, 0, 0, 0, 0)
                // Private chat - show only last reaction
                if (chatType == Const.JsonFields.PRIVATE) {
                    try {
                        val last = it.records!!.last().reaction
                        if (!last.isNullOrEmpty()) {
                            reactionId = it.records.last().id
                            Timber.d("last reactionId: $reactionId")
                            Timber.d("last: $last")
                            holder.binding.tvReactedEmoji.text = last
                            holder.binding.cvReactedEmoji.visibility = View.VISIBLE
                        } else {
                            holder.binding.cvReactedEmoji.visibility = View.GONE
                        }
                    } catch (e: Exception) {
                        Timber.d(e.toString())
                    }
                    // Group chat:
                } else {
                    var myReaction = ""
                    /* TODO: if user changes reaction show only last reaction*/
                    // TODO Matko
                    // Message id: 16861
                    for (record in it.records!!) {
                        if (!record.reaction.isNullOrEmpty()) {
                            if (record.userId != myUserId) {
                                getAllReactions(record.reaction.toString(), reactions)
                                //Timber.d("react: ${record.reaction}")
                            } else {
                                // Tmp solution - getting only last sent reaction
                                myReaction = record.reaction
                                reactionId = record.id
                            }
                        }
                    }
                    if (myReaction.isNotEmpty()) {
                        getAllReactions(myReaction, reactions)
                    }

                    Timber.d("reaction id group: $reactionId")

                    reactionText = getGroupReactions(reactions, "")
                    if (reactionText.isNotEmpty()) {
                        holder.binding.tvReactedEmoji.text = getGroupReactions(reactions, "")
                        holder.binding.cvReactedEmoji.visibility = View.VISIBLE
                    } else {
                        holder.binding.cvReactedEmoji.visibility = View.GONE
                    }
                }

                // Send new reaction:
                holder.binding.clContainer.setOnLongClickListener { _ ->
                    holder.binding.cvReactions.visibility = View.VISIBLE
                    if (chatType == Const.JsonFields.PRIVATE) {
                        val privateReactions = Reactions(0, 0, 0, 0, 0, 0)
                        listeners(holder, it.message.id, reactionId, privateReactions)
                    } else {
                        listeners(holder, it.message.id, reactionId, reactions)
                    }

                    true
                }

                showDateHeader(position, date, holder.binding.tvSectionHeader, it.message)

                // TODO - show avatar only on last message and name on first message
                if (position > 0) {
                    try {
                        val nextItem = getItem(position + 1).fromUserId
                        val previousItem = getItem(position - 1).fromUserId

                        val currentItem = it.message.fromUserId
                        //Timber.d("Items : $nextItem, $currentItem ${nextItem == currentItem}")

                        if (previousItem == currentItem) {
                            holder.binding.cvUserImage.visibility = View.INVISIBLE
                        } else {
                            holder.binding.cvUserImage.visibility = View.VISIBLE
                        }

                        if (nextItem == currentItem) {
                            holder.binding.tvUsername.visibility = View.GONE
                        } else {
                            holder.binding.tvUsername.visibility = View.VISIBLE
                        }
                    } catch (ex: IndexOutOfBoundsException) {
                        Tools.checkError(ex)
                        holder.binding.tvUsername.visibility = View.VISIBLE
                        holder.binding.cvUserImage.visibility = View.VISIBLE
                    }
                } else {
                    holder.binding.tvUsername.visibility = View.VISIBLE
                    holder.binding.cvUserImage.visibility = View.VISIBLE
                }
            }
        }
    }

    // Get number of reactions for each one
    private fun getAllReactions(reaction: String, reactions: Reactions) {
        when (reaction) {
            // TODO - Some reactions from web not showing, ask for emoji unicode
            context.getString(R.string.praying_hands_emoji) -> {
                reactions.prayingHandsEmoji++
            }
            context.getString(R.string.thumbs_up_emoji) -> {
                reactions.thumbsUp++
            }
            context.getString(R.string.heart_emoji) -> {
                reactions.heart++
            }
            context.getString(R.string.astonished_emoji) -> {
                reactions.astonishedEmoji++
            }
            context.getString(R.string.disappointed_relieved_emoji) -> {
                reactions.relievedEmoji++
            }
            context.getString(R.string.crying_face_emoji) -> {
                reactions.cryingFaceEmoji++
            }
            else -> {
                Timber.d("not found")
            }
        }
    }

    // Get reaction and number of that reaction
    private fun getGroupReactions(reactions: Reactions, reaction: String): String {
        var reactionText = ""
        if (chatType == Const.JsonFields.PRIVATE) {
            reactionText = reaction
        } else {
            if (reactions.thumbsUp != 0) {
                reactionText += context.getString(R.string.thumbs_up_emoji) + " " + reactions.thumbsUp + " "
            }
            if (reactions.heart != 0) {
                reactionText += context.getString(R.string.heart_emoji) + " " + reactions.heart + " "
            }
            if (reactions.astonishedEmoji != 0) {
                reactionText += context.getString(R.string.astonished_emoji) + " " + reactions.astonishedEmoji + " "
            }
            if (reactions.prayingHandsEmoji != 0) {
                reactionText += context.getString(R.string.praying_hands_emoji) + " " + reactions.prayingHandsEmoji + " "
            }
            if (reactions.cryingFaceEmoji != 0) {
                reactionText += context.getString(R.string.crying_face_emoji) + " " + reactions.cryingFaceEmoji + " "
            }
            if (reactions.relievedEmoji != 0) {
                reactionText += context.getString(R.string.disappointed_relieved_emoji) + " " + reactions.relievedEmoji + " "
            }
        }
        return reactionText
    }

    // TODO - same listeners method for both holders
    private fun listeners(
        holder: ReceivedMessageHolder,
        messageId: Int,
        reactionId: Int,
        reactions: Reactions
    ) {
        // TODO - remove my reaction for new one
        Timber.d("listeners: $reactionId")
        holder.binding.reactions.clEmoji.children.forEach { child ->
            child.setOnClickListener {
                when (child) {
                    holder.binding.reactions.tvThumbsUpEmoji -> {
                        if (reactionMessage.activeReaction!!.thumbsUp) {
                            // Active reaction is already thumbs up -> remove it
                            reactionMessage.clicked = true
                            reactionMessage.activeReaction!!.thumbsUp = false
                        } else {
                            reactionMessage.activeReaction!!.thumbsUp = true
                            REACTION = context.getString(R.string.thumbs_up_emoji)
                            getAllReactions(REACTION, reactions)
                            holder.binding.tvReactedEmoji.text =
                                getGroupReactions(reactions, REACTION)
                        }
                    }
                    holder.binding.reactions.tvHeartEmoji -> {
                        if (reactionMessage.activeReaction!!.heart) {
                            reactionMessage.clicked = true
                            reactionMessage.activeReaction!!.heart = false
                        } else {
                            reactionMessage.activeReaction!!.heart = true
                            REACTION = context.getString(R.string.heart_emoji)
                            getAllReactions(REACTION, reactions)
                            holder.binding.tvReactedEmoji.text =
                                getGroupReactions(reactions, REACTION)
                        }
                    }
                    holder.binding.reactions.tvCryingEmoji -> {
                        if (reactionMessage.activeReaction!!.cryingFaceEmoji) {
                            reactionMessage.clicked = true
                            reactionMessage.activeReaction!!.cryingFaceEmoji = false
                        } else {
                            reactionMessage.activeReaction!!.cryingFaceEmoji = true
                            REACTION = context.getString(R.string.crying_face_emoji)
                            getAllReactions(REACTION, reactions)
                            holder.binding.tvReactedEmoji.text =
                                getGroupReactions(reactions, REACTION)
                        }
                    }
                    holder.binding.reactions.tvAstonishedEmoji -> {
                        if (reactionMessage.activeReaction!!.astonishedEmoji) {
                            reactionMessage.clicked = true
                            reactionMessage.activeReaction!!.astonishedEmoji = false
                        } else {
                            reactionMessage.activeReaction!!.astonishedEmoji = true
                            REACTION = context.getString(R.string.astonished_emoji, REACTION)
                            getAllReactions(REACTION, reactions)
                            holder.binding.tvReactedEmoji.text =
                                getGroupReactions(reactions, REACTION)
                        }
                    }
                    holder.binding.reactions.tvDisappointedRelievedEmoji -> {
                        if (reactionMessage.activeReaction!!.relievedEmoji) {
                            reactionMessage.clicked = true
                            reactionMessage.activeReaction!!.relievedEmoji = false
                        } else {
                            reactionMessage.activeReaction!!.relievedEmoji = true
                            REACTION = context.getString(R.string.disappointed_relieved_emoji)
                            getAllReactions(REACTION, reactions)
                            holder.binding.tvReactedEmoji.text =
                                getGroupReactions(reactions, REACTION)
                        }
                    }
                    holder.binding.reactions.tvPrayingHandsEmoji -> {
                        if (reactionMessage.activeReaction!!.prayingHandsEmoji) {
                            reactionMessage.clicked = true
                            reactionMessage.activeReaction!!.prayingHandsEmoji = false
                        } else {
                            reactionMessage.activeReaction!!.prayingHandsEmoji = true
                            REACTION = context.getString(R.string.praying_hands_emoji)
                            getAllReactions(REACTION, reactions)
                            holder.binding.tvReactedEmoji.text =
                                getGroupReactions(reactions, REACTION)
                        }
                    }
                }

                reactionMessage.reaction = REACTION
                reactionMessage.messageId = messageId
                reactionMessage.reactionId = reactionId
                Timber.d("reactionId: $reactionId")

                if (REACTION.isNotEmpty()) {
                    addReaction.invoke(reactionMessage)
                    if (reactionMessage.clicked) {
                        //holder.binding.cvReactedEmoji.visibility = View.GONE
                        reactionMessage.clicked = false
                    } else {
                        holder.binding.cvReactedEmoji.visibility = View.VISIBLE
                    }
                } else {
                    holder.binding.cvReactedEmoji.visibility = View.GONE
                }
                holder.binding.cvReactions.visibility = View.GONE
                holder.binding.cvMessageOptions.visibility = View.GONE
            }
        }
    }

    private fun listeners(holder: SentMessageHolder, messageId: Int, reactions: Reactions) {
        // TODO - remove my reaction for new one
        holder.binding.reactions.clEmoji.children.forEach { child ->
            child.setOnClickListener {
                when (child) {
                    holder.binding.reactions.tvThumbsUpEmoji -> {
                        REACTION = context.getString(R.string.thumbs_up_emoji)
                        getAllReactions(REACTION, reactions)
                        holder.binding.tvReactedEmoji.text = getGroupReactions(reactions, REACTION)
                    }
                    holder.binding.reactions.tvHeartEmoji -> {
                        REACTION = context.getString(R.string.heart_emoji)
                        getAllReactions(REACTION, reactions)
                        holder.binding.tvReactedEmoji.text = getGroupReactions(reactions, REACTION)
                    }
                    holder.binding.reactions.tvCryingEmoji -> {
                        REACTION = context.getString(R.string.crying_face_emoji)
                        getAllReactions(REACTION, reactions)
                        holder.binding.tvReactedEmoji.text = getGroupReactions(reactions, REACTION)
                    }
                    holder.binding.reactions.tvAstonishedEmoji -> {
                        REACTION = context.getString(R.string.astonished_emoji)
                        getAllReactions(REACTION, reactions)
                        holder.binding.tvReactedEmoji.text = getGroupReactions(reactions, REACTION)
                    }
                    holder.binding.reactions.tvDisappointedRelievedEmoji -> {
                        REACTION = context.getString(R.string.disappointed_relieved_emoji, REACTION)
                        getAllReactions(REACTION, reactions)
                        holder.binding.tvReactedEmoji.text = getGroupReactions(reactions, REACTION)
                    }
                    holder.binding.reactions.tvPrayingHandsEmoji -> {
                        REACTION = context.getString(R.string.praying_hands_emoji)
                        getAllReactions(REACTION, reactions)
                        holder.binding.tvReactedEmoji.text = getGroupReactions(reactions, REACTION)
                    }
                }

                reactionMessage.reaction = REACTION
                reactionMessage.messageId = messageId

                if (REACTION.isNotEmpty()) {
                    addReaction.invoke(reactionMessage)
                    holder.binding.cvReactedEmoji.visibility = View.VISIBLE
                } else {
                    holder.binding.cvReactedEmoji.visibility = View.GONE
                }
                holder.binding.cvReactions.visibility = View.GONE
                holder.binding.cvMessageOptions.visibility = View.GONE
            }
        }
    }


    private fun addFiles(message: Message, ivFileType: ImageView) {
        when (message.body?.file?.fileName?.substringAfterLast(".")) {
            Const.FileExtensions.PDF -> ivFileType.setImageDrawable(
                ResourcesCompat.getDrawable(
                    context.resources,
                    R.drawable.ic_baseline_picture_as_pdf_24,
                    null
                )
            )
            Const.FileExtensions.ZIP, Const.FileExtensions.RAR -> ivFileType.setImageDrawable(
                ResourcesCompat.getDrawable(
                    context.resources,
                    R.drawable.ic_baseline_folder_zip_24,
                    null
                )
            )
            Const.FileExtensions.MP3, Const.FileExtensions.WAW -> ivFileType.setImageDrawable(
                ResourcesCompat.getDrawable(
                    context.resources,
                    R.drawable.ic_baseline_audio_file_24,
                    null
                )
            )
            else -> ivFileType.setImageDrawable(
                ResourcesCompat.getDrawable(
                    context.resources,
                    R.drawable.ic_baseline_insert_drive_file_24,
                    null
                )
            )
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
            return oldItem.message.id == newItem.message.id
        }
    }

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
            view.text =
                message.createdAt?.let {
                    getRelativeTimeSpan(it)
                }
        }
    }
}