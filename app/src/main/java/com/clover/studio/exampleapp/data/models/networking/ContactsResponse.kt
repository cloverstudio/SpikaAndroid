package com.clover.studio.exampleapp.data.models.networking

import com.clover.studio.exampleapp.data.models.User

data class ContactResponse(
    val status: String,
    val data: Data?
)

data class Data(
    val list: List<User>?,
    val count: Int?,
    val limit: Int?
)