package com.clover.studio.spikamessenger.ui.main.chat_details.notes

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.clover.studio.spikamessenger.data.models.entity.Note
import com.clover.studio.spikamessenger.databinding.ItemNoteBinding

class NotesAdapter(
    private val context: Context,
    private val onNoteInteraction: ((note: Note) -> Unit)
) :
    ListAdapter<Note, NotesAdapter.NotesViewHolder>(NoteCallback()) {

    inner class NotesViewHolder(val binding: ItemNoteBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotesViewHolder {
        val binding =
            ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotesViewHolder, position: Int) {
        with(holder) {
            getItem(position).let { noteItem ->
                binding.tvNoteTitle.text = noteItem.title

                itemView.setOnClickListener {
                    noteItem.let {
                        onNoteInteraction.invoke(it)
                    }
                }
            }
        }
    }

    private class NoteCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Note, newItem: Note) =
            oldItem == newItem
    }
}