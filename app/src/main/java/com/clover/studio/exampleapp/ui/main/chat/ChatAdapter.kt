package com.clover.studio.exampleapp.ui.main.chat

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
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.clover.studio.exampleapp.R
import com.clover.studio.exampleapp.data.models.Message
import com.clover.studio.exampleapp.data.models.User
import com.clover.studio.exampleapp.databinding.ItemMessageMeBinding
import com.clover.studio.exampleapp.databinding.ItemMessageOtherBinding
import com.clover.studio.exampleapp.utils.Const
import com.clover.studio.exampleapp.utils.Tools
import com.clover.studio.exampleapp.utils.Tools.getRelativeTimeSpan
import timber.log.Timber
import java.util.*


private const val VIEW_TYPE_MESSAGE_SENT = 1
private const val VIEW_TYPE_MESSAGE_RECEIVED = 2

class ChatAdapter(
    private val context: Context,
    private val myUserId: Int,
    private val users: List<User>
) :
    ListAdapter<Message, RecyclerView.ViewHolder>(MessageDiffCallback()) {


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

        return if (message.fromUserId == myUserId) {
            VIEW_TYPE_MESSAGE_SENT
        } else {
            VIEW_TYPE_MESSAGE_RECEIVED
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        getItem(position).let { it ->
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = it.createdAt!!
            val date = calendar.get(Calendar.DAY_OF_MONTH)

            // View holder for my messages

            // TODO can two view holders use same method for binding if all views are the same?
            if (holder.itemViewType == VIEW_TYPE_MESSAGE_SENT) {
                holder as SentMessageHolder
                when (it.type) {
                    Const.JsonFields.TEXT -> {
                        holder.binding.tvMessage.text = it.body?.text
                        holder.binding.tvMessage.visibility = View.VISIBLE
                        holder.binding.ivChatImage.visibility = View.GONE
                        holder.binding.clFileMessage.visibility = View.GONE
                        holder.binding.vvVideo.visibility = View.GONE
                    }
                    Const.JsonFields.CHAT_IMAGE -> {
                        holder.binding.tvMessage.visibility = View.GONE
                        holder.binding.ivChatImage.visibility = View.VISIBLE
                        holder.binding.clFileMessage.visibility = View.GONE
                        holder.binding.vvVideo.visibility = View.GONE

                        Glide.with(context)
                            .load(it.body?.file?.path?.let { imagePath ->
                                Tools.getAvatarUrl(
                                    imagePath
                                )
                            })
                            .into(holder.binding.ivChatImage)

                    }

                    Const.JsonFields.FILE_TYPE -> {
                        holder.binding.tvMessage.visibility = View.GONE
                        holder.binding.ivChatImage.visibility = View.GONE
                        holder.binding.clFileMessage.visibility = View.VISIBLE
                        holder.binding.vvVideo.visibility = View.GONE

                        holder.binding.tvFileTitle.text = it.body?.file?.fileName
                        val megabyteText =
                            Tools.calculateToMegabyte(it.body?.file?.size!!).toString() + " MB"
                        holder.binding.tvFileSize.text = megabyteText

                        addFiles(it, holder.binding.ivFileType)
                    }

                    Const.JsonFields.VIDEO -> {
                        holder.binding.tvMessage.visibility = View.GONE
                        holder.binding.ivChatImage.visibility = View.GONE
                        holder.binding.clFileMessage.visibility = View.GONE

                        holder.binding.clVideos.visibility = View.VISIBLE

                        val videoPath = it.body?.file?.path?.let { videoPath ->
                            Tools.getVideoUrl(
                                videoPath
                            )
                        }

                        Glide.with(context)
                            .load(videoPath)
                            .into(holder.binding.vvVideo)

                        holder.binding.ivPlayButton.setOnClickListener { view ->
                            val action =
                                ChatMessagesFragmentDirections.actionChatMessagesFragment2ToVideoFragment2(
                                    videoPath!!
                                )
                            view.findNavController().navigate(action)
                        }
                    }

                    else -> {
                        holder.binding.tvMessage.visibility = View.VISIBLE
                        holder.binding.ivChatImage.visibility = View.GONE
                        holder.binding.clFileMessage.visibility = View.GONE
                    }
                }

                showDateHeader(position, date, holder.binding.tvSectionHeader, it)

                when {
                    it.seenCount!! > 0 -> {
                        holder.binding.ivMessageStatus.setImageDrawable(
                            ContextCompat.getDrawable(
                                context,
                                R.drawable.img_seen
                            )
                        )
                    }
                    it.totalUserCount == it.deliveredCount -> {
                        holder.binding.ivMessageStatus.setImageDrawable(
                            ContextCompat.getDrawable(
                                context,
                                R.drawable.img_done
                            )
                        )
                    }
                    it.deliveredCount!! >= 0 -> {
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
                when (it.type) {
                    Const.JsonFields.TEXT -> {
                        holder.binding.tvMessage.text = it.body?.text
                        holder.binding.tvMessage.visibility = View.VISIBLE
                        holder.binding.ivChatImage.visibility = View.GONE
                        holder.binding.clFileMessage.visibility = View.GONE
                    }
                    Const.JsonFields.CHAT_IMAGE -> {
                        holder.binding.tvMessage.visibility = View.GONE
                        holder.binding.ivChatImage.visibility = View.VISIBLE
                        holder.binding.clFileMessage.visibility = View.GONE

                        Glide.with(context)
                            .load(it.body?.file?.path?.let { imagePath ->
                                Tools.getAvatarUrl(
                                    imagePath
                                )
                            })
                            .into(holder.binding.ivChatImage)
                    }

                    Const.JsonFields.VIDEO -> {
                        holder.binding.tvMessage.visibility = View.GONE
                        holder.binding.ivChatImage.visibility = View.GONE
                        holder.binding.clFileMessage.visibility = View.GONE

                        holder.binding.clVideos.visibility = View.VISIBLE


                        val videoPath = it.body?.file?.path?.let { videoPath ->
                            Tools.getVideoUrl(
                                videoPath
                            )
                        }

                        Glide.with(context)
                            .load(videoPath)
                            .into(holder.binding.vvVideo)


                        holder.binding.ivPlayButton.setOnClickListener { view ->
                            val action =
                                ChatMessagesFragmentDirections.actionChatMessagesFragment2ToVideoFragment2(
                                    videoPath!!
                                )
                            view.findNavController().navigate(action)
                        }
                    }


                    Const.JsonFields.FILE_TYPE -> {
                        holder.binding.tvMessage.visibility = View.GONE
                        holder.binding.ivChatImage.visibility = View.GONE
                        holder.binding.clFileMessage.visibility = View.VISIBLE
                        holder.binding.vvVideo.visibility = View.GONE

                        holder.binding.tvFileTitle.text = it.body?.file?.fileName
                        val megabyteText =
                            Tools.calculateToMegabyte(it.body?.file?.size!!).toString() + " MB"
                        holder.binding.tvFileSize.text = megabyteText

                        addFiles(it, holder.binding.ivFileType)
                    }
                    else -> {
                        holder.binding.tvMessage.visibility = View.VISIBLE
                        holder.binding.ivChatImage.visibility = View.GONE
                        holder.binding.clFileMessage.visibility = View.GONE
                    }
                }

                if (it.body?.text.isNullOrEmpty()) {
                    holder.binding.tvMessage.visibility = View.GONE
                    holder.binding.ivChatImage.visibility = View.VISIBLE

                    Glide.with(context)
                        .load(it.body?.file?.path?.let { imagePath -> Tools.getAvatarUrl(imagePath) })
                        .into(holder.binding.ivChatImage)
                } else {
                    holder.binding.tvMessage.visibility = View.VISIBLE
                    holder.binding.ivChatImage.visibility = View.GONE
                }

                for (roomUser in users) {
                    if (it.fromUserId == roomUser.id) {
                        holder.binding.tvUsername.text = roomUser.displayName
                        Glide.with(context)
                            .load(roomUser.avatarUrl?.let { avatarUrl ->
                                Tools.getAvatarUrl(
                                    avatarUrl
                                )
                            })
                            .into(holder.binding.ivUserImage)
                        break
                    }
                }

                showDateHeader(position, date, holder.binding.tvSectionHeader, it)

                if (position > 0) {
                    try {
                        val nextItem = getItem(position + 1).fromUserId

                        val currentItem = it.fromUserId
                        Timber.d("Items : $nextItem, $currentItem ${nextItem == currentItem}")

                        if (nextItem == currentItem) {
                            holder.binding.tvUsername.visibility = View.GONE
                        } else {
                            holder.binding.tvUsername.visibility = View.VISIBLE
                        }
                    } catch (ex: IndexOutOfBoundsException) {
                        Tools.checkError(ex)
                        holder.binding.tvUsername.visibility = View.VISIBLE
                    }
                } else {
                    holder.binding.tvUsername.visibility = View.VISIBLE
                }
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

    private class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {

        override fun areItemsTheSame(oldItem: Message, newItem: Message) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Message, newItem: Message) =
            oldItem == newItem
    }

    private fun showDateHeader(
        position: Int,
        date: Int,
        view: TextView,
        message: Message
    ) {
        if (position >= 0 && currentList.size - 1 > position) {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = getItem(position + 1).createdAt!!
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
