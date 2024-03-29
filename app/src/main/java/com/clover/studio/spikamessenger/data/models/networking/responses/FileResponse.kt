package com.clover.studio.spikamessenger.data.models.networking.responses

data class FileResponse(
    val status: String?,
    val data: FileData?
)

data class FileData(
    val uploadedChunks: List<Int>?,
    val file: FileInfo?
)

data class FileInfo(
    val id: Int,
    val fileName: String?,
    val size: Long?,
    val mimeType: String?,
    val type: String?,
    val relationId: Int?,
    val clientId: String?,
    val path: String?,
    val createdAt: Long?
)