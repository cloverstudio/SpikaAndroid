package com.clover.studio.exampleapp.ui.main.rooms

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.clover.studio.exampleapp.R
import com.clover.studio.exampleapp.data.models.MessageAndRecords
import com.clover.studio.exampleapp.data.models.RoomAndMessageAndRecords
import com.clover.studio.exampleapp.databinding.ItemChatRoomBinding
import com.clover.studio.exampleapp.utils.Const
import com.clover.studio.exampleapp.utils.Tools.getFileUrl
import com.clover.studio.exampleapp.utils.Tools.getRelativeTimeSpan

class RoomsAdapter(
    private val context: Context,
    private val myUserId: String,
    private val onItemClick: ((item: RoomAndMessageAndRecords) -> Unit)
) : ListAdapter<RoomAndMessageAndRecords, RoomsAdapter.RoomsViewHolder>(RoomsDiffCallback()) {
    inner class RoomsViewHolder(val binding: ItemChatRoomBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomsViewHolder {
        val binding =
            ItemChatRoomBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RoomsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RoomsViewHolder, position: Int) {
        with(holder) {
            getItem(position).let { roomItem ->
                var userName = ""
                var userAvatar = ""
                //Timber.d("Room data = $roomItem, ${roomItem.roomWithUsers.room.name}")
                if (Const.JsonFields.PRIVATE == roomItem.roomWithUsers.room.type) {
                    for (roomUser in roomItem.roomWithUsers.users) {
                        if (myUserId != roomUser.id.toString()) {
                            userName = roomUser.displayName.toString()
                            userAvatar = roomUser.avatarUrl.toString()
                            break
                        } else {
                            userName = roomUser.displayName.toString()
                            userAvatar = roomUser.avatarUrl.toString()
                        }
                    }
                } else {
                    userName = roomItem.roomWithUsers.room.name.toString()
                    userAvatar = roomItem.roomWithUsers.room.avatarUrl.toString()
                }
                binding.tvRoomName.text = userName
                Glide.with(context)
                    .load(userAvatar.let { getFileUrl(it) })
                    .into(binding.ivRoomImage)

                if (!roomItem.message.isNullOrEmpty()) {
                    val sortedList = roomItem.message.sortedBy { it.message.createdAt }
                    val lastMessage = sortedList.last().message.body
                    var textUserName = ""

                    if (Const.JsonFields.GROUP == roomItem.roomWithUsers.room.type) {
                        for (user in roomItem.roomWithUsers.users) {
                            if (sortedList.last().message.fromUserId == user.id) {
                                textUserName = user.displayName.toString() + ": "
                                break
                            }
                        }
                    }
                    if (lastMessage?.text.isNullOrEmpty()) {
                        when (sortedList.last().message.type) {
                            Const.JsonFields.CHAT_IMAGE -> binding.tvLastMessage.text =
                                textUserName + context.getString(R.string.image_shared)
                            Const.JsonFields.VIDEO -> binding.tvLastMessage.text =
                                textUserName + context.getString(R.string.video_shared)
                            Const.JsonFields.FILE_TYPE -> binding.tvLastMessage.text =
                                textUserName + context.getString(R.string.file_shared)
                            else -> binding.tvLastMessage.text =
                                textUserName + lastMessage?.text.toString()
                        }
                    } else binding.tvLastMessage.text = textUserName + lastMessage?.text.toString()

                    binding.tvMessageTime.text = roomItem.message.last().message.createdAt?.let {
                        getRelativeTimeSpan(it)
                    }

                    val unreadMessages = ArrayList<MessageAndRecords>()
                    for (messages in sortedList) {
                        if (roomItem.roomWithUsers.room.visitedRoom == null) {
                            unreadMessages.add(messages)
                        } else {
                            if (messages.message.createdAt!! >= roomItem.roomWithUsers.room.visitedRoom!!) {
                                unreadMessages.add(messages)
                            }
                        }
                    }

                    if (unreadMessages.isNotEmpty()) {
                        binding.tvNewMessages.text = unreadMessages.size.toString()
                        binding.tvNewMessages.visibility = View.VISIBLE
                    } else binding.tvNewMessages.visibility = View.GONE
                } else {
                    binding.tvLastMessage.text = ""
                    binding.tvMessageTime.text = ""
                    binding.tvNewMessages.visibility = View.GONE
                }

                itemView.setOnClickListener {
                    roomItem.let {
                        onItemClick.invoke(it)
                    }
                }
            }
        }
    }


    private class RoomsDiffCallback : DiffUtil.ItemCallback<RoomAndMessageAndRecords>() {

        override fun areItemsTheSame(
            oldItem: RoomAndMessageAndRecords,
            newItem: RoomAndMessageAndRecords
        ) =
            oldItem.roomWithUsers.room.roomId == newItem.roomWithUsers.room.roomId

        override fun areContentsTheSame(
            oldItem: RoomAndMessageAndRecords,
            newItem: RoomAndMessageAndRecords
        ) =
            oldItem == newItem
    }
}