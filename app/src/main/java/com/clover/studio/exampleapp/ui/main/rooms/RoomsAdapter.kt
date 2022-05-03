package com.clover.studio.exampleapp.ui.main.rooms

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.clover.studio.exampleapp.data.models.ChatRoom
import com.clover.studio.exampleapp.databinding.ItemChatRoomBinding
import com.clover.studio.exampleapp.utils.Const
import com.clover.studio.exampleapp.utils.Tools.getAvatarUrl

class RoomsAdapter(
    private val context: Context,
    private val myUserId: String,
    private val onItemClick: ((item: ChatRoom) -> Unit)
) : ListAdapter<ChatRoom, RoomsAdapter.RoomsViewHolder>(RoomsDiffCallback()) {
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
                if (Const.JsonFields.PRIVATE == roomItem.type) {
                    roomItem.users?.forEach { roomUser ->
                        if (myUserId != roomUser.userId.toString()) {
                            binding.tvRoomName.text = roomUser.user?.displayName
                            Glide.with(context)
                                .load(roomUser.user?.avatarUrl?.let { getAvatarUrl(it) })
                                .into(binding.ivRoomImage)
                        }
                    }
                } else {
                    binding.tvRoomName.text = roomItem.name
                    Glide.with(context)
                        .load(roomItem.avatarUrl?.let { getAvatarUrl(it) })
                        .into(binding.ivRoomImage)
                }

                Glide.with(context).load(roomItem.avatarUrl?.let { getAvatarUrl(it) })
                    .into(binding.ivRoomImage)

                // TODO add last message, new message bubble and time ago text

                itemView.setOnClickListener {
                    roomItem.let {
                        onItemClick.invoke(it)
                    }
                }
            }
        }
    }

    private class RoomsDiffCallback : DiffUtil.ItemCallback<ChatRoom>() {

        override fun areItemsTheSame(oldItem: ChatRoom, newItem: ChatRoom) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ChatRoom, newItem: ChatRoom) =
            oldItem == newItem
    }
}