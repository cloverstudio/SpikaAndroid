package com.clover.studio.spikamessenger.ui.main.create_room

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.clover.studio.spikamessenger.R
import com.clover.studio.spikamessenger.data.models.entity.PrivateGroupChats
import com.clover.studio.spikamessenger.databinding.ItemPeopleSelectedBinding
import com.clover.studio.spikamessenger.utils.Tools

class GroupInformationAdapter(
    private val context: Context,
    private val onItemClick: ((item: PrivateGroupChats) -> Unit)
) :
    ListAdapter<PrivateGroupChats, GroupInformationAdapter.ContactsViewHolder>(ContactsDiffCallback()) {

    inner class ContactsViewHolder(val binding: ItemPeopleSelectedBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactsViewHolder {
        val binding =
            ItemPeopleSelectedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContactsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactsViewHolder, position: Int) {
        with(holder) {
            getItem(position).let { userItem ->
                binding.tvUsername.text =
                    userItem?.name ?: userItem.formattedDisplayName
                binding.tvTitle.text = userItem.telephoneNumber
                Glide.with(context)
                    .load(userItem.avatarFileId.let { Tools.getFilePathUrl(it) })
                    .placeholder(R.drawable.img_user_avatar)
                    .centerCrop()
                    .into(binding.ivUserImage)

                binding.ivRemoveUser.visibility = View.VISIBLE
                binding.ivRemoveUser.setOnClickListener {
                    userItem.let {
                        onItemClick.invoke(it)
                    }
                }
            }
        }
    }

    private class ContactsDiffCallback : DiffUtil.ItemCallback<PrivateGroupChats>() {
        override fun areItemsTheSame(oldItem: PrivateGroupChats, newItem: PrivateGroupChats) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: PrivateGroupChats, newItem: PrivateGroupChats) =
            oldItem == newItem
    }
}
