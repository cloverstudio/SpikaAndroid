package com.clover.studio.exampleapp.data.models.networking

import com.clover.studio.exampleapp.data.models.entity.Note

data class NotesResponse(
    val status: String,
    val data: NoteData
)

data class NoteData(
    val notes: List<Note>
)