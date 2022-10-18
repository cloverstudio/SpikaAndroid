package com.clover.studio.exampleapp.data.models.networking

data class SettingsResponse(
    val status: String,
    val data: SettingsData
)

data class SettingsData(
    val settings: List<Settings>
)

data class Settings(
    val id: Int,
    val userId: Int,
    val key: String,
    val value: Boolean
)