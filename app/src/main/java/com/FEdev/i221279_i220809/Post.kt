data class Comment(
    val userId: String = "",
    val username: String = "",
    val text: String = "",
    val timestamp: String = ""
)

data class Post(
    val postId: String = "",
    val userId: String = "",
    val username: String = "",
    val caption: String = "",
    val imageUrl: String = "",
    val likes: MutableMap<String, Boolean> = mutableMapOf()
)
