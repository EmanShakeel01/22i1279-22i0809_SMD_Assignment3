package com.FEdev.i221279_i220809

data class FollowRequest(
    val requestId: String = "",
    val fromUserId: String = "",
    val fromUsername: String = "",
    val toUserId: String = "",
    val toUsername: String = "",
    val timestamp: Long = 0L,
    val status: String = "pending" // pending, accepted, rejected
)