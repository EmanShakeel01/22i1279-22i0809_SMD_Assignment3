package com.FEdev.i221279_i220809

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.FEdev.i221279_i220809.models.Post
import com.FEdev.i221279_i220809.models.ToggleLikeRequest
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.utils.SessionManager
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PostAdapter(
    private val postList: ArrayList<Post>,
    private val sessionManager: SessionManager
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val username: TextView = itemView.findViewById(R.id.username)
        val caption: TextView = itemView.findViewById(R.id.caption)
        val postImage: ImageView = itemView.findViewById(R.id.post_image)
        val profilePic: CircleImageView = itemView.findViewById(R.id.post_profile_pic)
        val btnLike: ImageView = itemView.findViewById(R.id.btnlike)
        val btnComment: ImageView = itemView.findViewById(R.id.btncomment)
        val likesText: TextView = itemView.findViewById(R.id.likes)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = postList[position]
        val context = holder.itemView.context

        // Set username and caption
        holder.username.text = post.username
        holder.caption.text = "${post.username}  ${post.caption}"

        // Decode and display image
        try {
            val imageBytes = Base64.decode(post.image_base64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            if (bitmap != null) {
                holder.postImage.setImageBitmap(bitmap)
            } else {
                holder.postImage.setImageResource(R.drawable.postsample)
            }
        } catch (e: Exception) {
            Log.e("PostAdapter", "Error decoding image: ${e.message}")
            holder.postImage.setImageResource(R.drawable.postsample)
        }

        // Profile picture placeholder
        holder.profilePic.setImageResource(R.drawable.mystory)

        // Update like button and text
        updateLikeUI(holder, post)

        // Like button click
        holder.btnLike.setOnClickListener {
            toggleLike(holder, post, position)
        }

        // Comment button click
        holder.btnComment.setOnClickListener {
            val intent = Intent(context, CommentsActivity::class.java)
            intent.putExtra("postId", post.post_id)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = postList.size

    private fun updateLikeUI(holder: PostViewHolder, post: Post) {
        // Update like icon
        holder.btnLike.setImageResource(
            if (post.is_liked) R.drawable.filledheart else R.drawable.like
        )

        // Update like text
        holder.likesText.text = when (post.like_count) {
            0 -> "Be the first to like"
            1 -> "Liked by 1 user"
            else -> "Liked by ${post.like_count} users"
        }
    }

    private fun toggleLike(holder: PostViewHolder, post: Post, position: Int) {
        val authToken = sessionManager.getAuthToken()

        if (authToken == null) {
            Toast.makeText(holder.itemView.context, "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

        // Optimistic UI update
        val wasLiked = post.is_liked
        val originalLikeCount = post.like_count

        post.is_liked = !wasLiked
        post.like_count = if (post.is_liked) post.like_count + 1 else post.like_count - 1
        updateLikeUI(holder, post)

        // Make API call
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = ToggleLikeRequest(
                    auth_token = authToken,
                    post_id = post.post_id
                )

                val response = RetrofitClient.apiService.toggleLike(request)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        val data = response.body()?.data
                        if (data != null) {
                            // Update with server response
                            post.is_liked = data.is_liked
                            post.like_count = data.like_count
                            updateLikeUI(holder, post)
                            notifyItemChanged(position)

                            val message = if (data.is_liked) "Liked ❤️" else "Unliked 💔"
                            Toast.makeText(holder.itemView.context, message, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // Revert on failure
                        post.is_liked = wasLiked
                        post.like_count = originalLikeCount
                        updateLikeUI(holder, post)

                        Toast.makeText(
                            holder.itemView.context,
                            response.body()?.message ?: "Failed to update like",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Revert on error
                    post.is_liked = wasLiked
                    post.like_count = originalLikeCount
                    updateLikeUI(holder, post)

                    Log.e("PostAdapter", "Error toggling like: ${e.message}", e)
                    Toast.makeText(
                        holder.itemView.context,
                        "Network error",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun updatePost(position: Int, updatedPost: Post) {
        if (position in postList.indices) {
            postList[position] = updatedPost
            notifyItemChanged(position)
        }
    }
}