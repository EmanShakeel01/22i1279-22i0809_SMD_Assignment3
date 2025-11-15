package com.FEdev.i221279_i220809

data class Message2(
    val id: String = System.currentTimeMillis().toString(),
    var text: String? = null,
    val imageUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isSentByMe: Boolean = true,
    val senderName: String? = null,
    val senderAvatar: Int? = null,
    var isEdited: Boolean = false
)