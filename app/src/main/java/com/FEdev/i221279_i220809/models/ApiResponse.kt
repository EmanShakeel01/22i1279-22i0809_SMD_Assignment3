package com.FEdev.i221279_i220809.models

data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null
)

data class UserData(
    val user_id: Int,
    val email: String,
    val username: String,
    val auth_token: String
)

data class SignupRequest(
    val email: String,
    val username: String,
    val password: String
)

data class LoginRequest(
    val identifier: String,
    val password: String
)

data class SessionRequest(
    val auth_token: String
)