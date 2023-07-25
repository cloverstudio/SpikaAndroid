package com.clover.studio.spikamessenger.ui.main.rooms.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.clover.studio.spikamessenger.data.models.entity.MessageAndRecords
import com.clover.studio.spikamessenger.data.models.entity.User
import com.clover.studio.spikamessenger.databinding.ItemMessageSearchBinding
import com.clover.studio.spikamessenger.utils.Tools

class SearchAdapter(
    private val users: List<User>,
    private val onItemClick: ((roomId: Int) -> Unit)
) : ListAdapter<MessageAndRecords, SearchAdapter.SearchViewHolder>(SearchDiffCallback()) {
    inner class SearchViewHolder(val binding: ItemMessageSearchBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val binding =
            ItemMessageSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SearchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        with(holder) {
            getItem(position).let { messageItem ->
                for (user in users) {
                    if (user.id == messageItem.message.fromUserId) {
                        binding.tvUsername.text = user.displayName
                    }
                }

                binding.tvMessageDate.text = messageItem.message.createdAt?.let {
                    Tools.fullDateFormat(
                        it
                    )
                }
                binding.tvMessageContent.text = messageItem.message.body?.text

                itemView.setOnClickListener {
                    messageItem.let {
                        it.message.roomId?.let { roomId -> onItemClick.invoke(roomId) }
                    }
                }
            }
        }
    }


    private class SearchDiffCallback : DiffUtil.ItemCallback<MessageAndRecords>() {

        override fun areItemsTheSame(
            oldItem: MessageAndRecords,
            newItem: MessageAndRecords
        ) =
            oldItem.message.id == newItem.message.id

        override fun areContentsTheSame(
            oldItem: MessageAndRecords,
            newItem: MessageAndRecords
        ) =
            oldItem == newItem
    }
}