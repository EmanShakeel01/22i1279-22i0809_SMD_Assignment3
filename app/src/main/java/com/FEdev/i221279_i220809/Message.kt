package com.FEdev.i221279_i220809

data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val messageText: String? = null,
    val messageType: String = "text",
    val imageUrl: String? = null,
    val postContent: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val edited: Boolean = false
)
