package com.FEdev.i221279_i220809.network

import com.FEdev.i221279_i220809.models.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    // ==================== AUTH ENDPOINTS ====================

    @POST("signup.php")
    suspend fun signup(@Body request: SignupRequest): Response<ApiResponse<UserData>>

    @POST("login.php")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<UserData>>

    @POST("check_session.php")
    suspend fun checkSession(@Body request: SessionRequest): Response<ApiResponse<UserData>>

    @POST("logout.php")
    suspend fun logout(@Body request: SessionRequest): Response<ApiResponse<Any>>

    // ==================== STORY ENDPOINTS ====================

    @POST("upload_story.php")
    suspend fun uploadStory(@Body request: StoryUploadRequest): Response<ApiResponse<StoryUploadResponse>>

    @POST("get_my_stories.php")
    suspend fun getMyStories(@Body request: MyStoriesRequest): Response<ApiResponse<MyStoriesResponse>>

    @POST("get_users_stories.php")
    suspend fun getUserStories(@Body request: UserStoriesRequest): Response<UserStoriesResponse>

    @POST("get_all_stories.php")
    suspend fun getAllStories(@Body request: AllStoriesRequest): Response<ApiResponse<AllStoriesResponse>>

    // ==================== POST ENDPOINTS ====================

    @POST("upload_post.php")
    suspend fun uploadPost(@Body request: PostUploadRequest): Response<ApiResponse<PostUploadResponse>>

    @POST("get_posts.php")
    suspend fun getPosts(@Body request: GetPostsRequest): Response<ApiResponse<GetPostsResponse>>

    @POST("toggle_like.php")
    suspend fun toggleLike(@Body request: ToggleLikeRequest): Response<ApiResponse<ToggleLikeResponse>>

    // ==================== COMMENT ENDPOINTS ====================

    @POST("add_comment.php")
    suspend fun addComment(@Body request: AddCommentRequest): Response<ApiResponse<AddCommentResponse>>

    @POST("get_comments.php")
    suspend fun getComments(@Body request: GetCommentsRequest): Response<ApiResponse<GetCommentsResponse>>

    // ==================== SEARCH ENDPOINTS ====================

    @POST("search_users.php")
    suspend fun searchUsers(@Body request: SearchUsersRequest): Response<ApiResponse<SearchUsersResponse>>

    // ==================== USER PROFILE ENDPOINTS ====================

    @POST("get_user_profile.php")
    suspend fun getUserProfile(@Body request: GetUserProfileRequest): Response<ApiResponse<GetUserProfileResponse>>

    // ==================== FOLLOW REQUEST ENDPOINTS ====================

    @POST("send_follow_request.php")
    suspend fun sendFollowRequest(@Body request: SendFollowRequestRequest): Response<ApiResponse<Map<String, Any>>>

    @POST("check_follow_status.php")
    suspend fun checkFollowStatus(@Body request: CheckFollowStatusRequest): Response<ApiResponse<CheckFollowStatusResponse>>

    @POST("get_follow_requests.php")
    suspend fun getFollowRequests(@Body request: GetFollowRequestsRequest): Response<ApiResponse<GetFollowRequestsResponse>>

    @POST("accept_follow_request.php")
    suspend fun acceptFollowRequest(@Body request: AcceptFollowRequestRequest): Response<ApiResponse<Map<String, String>>>

    @POST("reject_follow_request.php")
    suspend fun rejectFollowRequest(@Body request: RejectFollowRequestRequest): Response<ApiResponse<Map<String, String>>>

    @POST("update_status.php")
    suspend fun updateStatus(@Body request: UpdateStatusRequest): Response<ApiResponse<Any>>

    @POST("get_user_status.php")
    suspend fun getUserStatus(@Body request: GetUserStatusRequest): Response<ApiResponse<UserStatusData>>

    @POST("get_multiple_statuses.php")
    suspend fun getMultipleUserStatuses(@Body request: GetMultipleStatusesRequest): Response<ApiResponse<MultipleStatusesData>>
    @POST("update_profile_picture.php")
    suspend fun updateProfilePicture(@Body request: UpdateProfilePictureRequest): Response<UpdateProfilePictureResponse>


// ==================== ADD TO ApiService.kt ====================

    @POST("send_message.php")
    suspend fun sendMessage(@Body request: SendMessageRequest): Response<ApiResponse<SendMessageResponse>>

    @POST("get_messages.php")
    suspend fun getMessages(@Body request: GetMessagesRequest): Response<ApiResponse<GetMessagesResponse>>

    @POST("edit_message.php")
    suspend fun editMessage(@Body request: EditMessageRequest): Response<ApiResponse<EditMessageResponse>>

    @POST("delete_message.php")
    suspend fun deleteMessage(@Body request: DeleteMessageRequest): Response<ApiResponse<DeleteMessageResponse>>

    @POST("toggle_vanish_mode.php")
    suspend fun toggleVanishMode(@Body request: ToggleVanishModeRequest): Response<ApiResponse<ToggleVanishModeResponse>>

    @POST("clear_vanish_messages.php")
    suspend fun clearVanishMessages(@Body request: ClearVanishMessagesRequest): Response<ApiResponse<ClearVanishMessagesResponse>>



}