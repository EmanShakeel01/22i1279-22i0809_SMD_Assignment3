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

data class StoryUploadRequest(
    val auth_token: String,
    val image_base64: String
)

data class StoryUploadResponse(
    val story_id: Int,
    val timestamp: Long,
    val expires_at: Long
)

data class Story(
    val story_id: Int,
    val user_id: Int,
    val username: String,
    val image_base64: String,
    val timestamp: Long,
    val expires_at: Long
)

data class MyStoriesRequest(
    val auth_token: String
)

data class MyStoriesResponse(
    val stories: List<Story>,
    val count: Int
)

data class UserStoriesRequest(
    val auth_token: String,
    val user_id: Int
)

data class UserStoriesResponse(
    val success: Boolean,
    val message: String,
    val data: StoryData?
)

data class StoryData(
    val stories: List<Story>,
    val count: Int,
    val username: String
)

data class AllStoriesRequest(
    val auth_token: String
)

data class UserStoryPreview(
    val user_id: Int,
    val username: String,
    val preview_image: String,
    val story_count: Int,
    val is_own: Boolean
)

data class AllStoriesResponse(
    val user_stories: List<UserStoryPreview>,
    val total_users: Int
)

data class PostUploadRequest(
    val auth_token: String,
    val image_base64: String,
    val caption: String
)

data class PostUploadResponse(
    val post_id: Int,
    val timestamp: Long
)

data class GetPostsRequest(
    val auth_token: String
)

data class Post(
    val post_id: Int,
    val user_id: Int,
    val username: String,
    val caption: String,
    val image_base64: String,
    val timestamp: Long,
    var like_count: Int,
    var comment_count: Int,
    var is_liked: Boolean
)

data class GetPostsResponse(
    val posts: List<Post>,
    val total: Int
)

data class ToggleLikeRequest(
    val auth_token: String,
    val post_id: Int
)

data class ToggleLikeResponse(
    val is_liked: Boolean,
    val like_count: Int
)

data class AddCommentRequest(
    val auth_token: String,
    val post_id: Int,
    val comment_text: String
)

data class AddCommentResponse(
    val comment_id: Int,
    val username: String,
    val comment_text: String,
    val timestamp: Long
)

data class GetCommentsRequest(
    val auth_token: String,
    val post_id: Int
)

data class Comment(
    val comment_id: Int,
    val user_id: Int,
    val username: String,
    val comment_text: String,
    val timestamp: String
)

data class GetCommentsResponse(
    val comments: List<Comment>,
    val total: Int
)

// Search models
data class SearchUsersRequest(
    val auth_token: String,
    val search_query: String
)

data class SearchUserResult(
    val user_id: Int,
    val username: String,
    val email: String,
    val fullname: String?
)

data class SearchUsersResponse(
    val users: List<SearchUserResult>,
    val total: Int,
    val query: String
)

data class GetUserProfileRequest(
    val auth_token: String,
    val user_id: Int
)

data class UserProfile(
    val user_id: Int,
    val username: String,
    val email: String,
    val fullname: String?,
    val bio: String?,
    val profile_image: String?,
    val post_count: Int
)

data class GetUserProfileResponse(
    val user: UserProfile
)

// Follow Request Models
data class GetFollowRequestsRequest(
    val auth_token: String
)

data class FollowRequestItem(
    val request_id: Int,
    val follower_id: Int,
    val username: String,
    val email: String,
    val created_at: Long
)
data class GetFollowRequestsResponse(
    val requests: List<FollowRequestItem>,
    val total: Int
)
data class SendFollowRequestRequest(
    val auth_token: String,
    val target_user_id: Int
)

data class SendFollowRequestResponse(
    val success: Boolean,
    val message: String
)

// ✅ FIXED: Removed 'status' parameter from the REQUEST
data class CheckFollowStatusRequest(
    val auth_token: String,
    val target_user_id: Int
)

data class CheckFollowStatusResponse(
    val status: String // "none", "pending", "accepted"
)




data class AcceptFollowRequestRequest(
    val auth_token: String,
    val request_id: Int
)

data class RejectFollowRequestRequest(
    val auth_token: String,
    val request_id: Int
)


// Status update request/response
data class UpdateStatusRequest(
    val auth_token: String,
    val status: String // "online" or "offline"
)

data class UpdateStatusResponse(
    val user_id: Int,
    val status: String,
    val last_seen: Long
)

// Get user status
data class GetUserStatusRequest(
    val auth_token: String,
    val target_user_id: Int
)

data class UserStatusData(
    val user_id: Int,
    val username: String,
    val status: String,
    val last_seen: Long?,
    val is_online: Boolean
)

// Get multiple statuses
data class GetMultipleStatusesRequest(
    val auth_token: String,
    val user_ids: List<Int>
)

data class GetMultipleStatusesResponse(
    val statuses: List<UserStatusData>,
    val count: Int
)