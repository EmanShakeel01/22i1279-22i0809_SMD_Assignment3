package com.FEdev.i221279_i220809.network

import com.FEdev.i221279_i220809.models.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("signup.php")
    suspend fun signup(@Body request: SignupRequest): Response<ApiResponse<UserData>>

    @POST("login.php")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<UserData>>

    @POST("check_session.php")
    suspend fun checkSession(@Body request: SessionRequest): Response<ApiResponse<UserData>>

    @POST("logout.php")
    suspend fun logout(@Body request: SessionRequest): Response<ApiResponse<Any>>
}