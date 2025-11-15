


package com.FEdev.i221279_i220809

import Post
import android.app.Activity
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import de.hdodenhof.circleimageview.CircleImageView

class PostAdapter(private val postList: ArrayList<Post>) :
    RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    private val listeners = mutableMapOf<String, ValueEventListener>()

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
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        // Check if user can view this post
//        val postUserId = post.userId // You need to add userId field to Post data class
//        if (postUserId != currentUserId) {
//            FollowRequestManager.checkIfFollowing(currentUserId!!, postUserId) { isFollowing ->
//                ( holder.itemView.context as? Activity)?.runOnUiThread {
//                if (!isFollowing) {
//                        // Hide post if not following
//                        holder.itemView.visibility = View.GONE
//                        holder.itemView.layoutParams = RecyclerView.LayoutParams(0, 0)
//                        return@runOnUiThread
//                    } else {
//                        holder.itemView.visibility = View.VISIBLE
//                        holder.itemView.layoutParams = RecyclerView.LayoutParams(
//                            ViewGroup.LayoutParams.MATCH_PARENT,
//                            ViewGroup.LayoutParams.WRAP_CONTENT
//                        )
//                    }
//                }
//            }
//        }



        Log.d("PostAdapter", "=== BIND DEBUG ===")
        Log.d("PostAdapter", "Current User ID: $currentUserId")
        Log.d("PostAdapter", "Current User Email: ${FirebaseAuth.getInstance().currentUser?.email}")
        Log.d("PostAdapter", "Post ID: ${post.postId}")

        val postRef = FirebaseDatabase.getInstance().getReference("posts").child(post.postId)
        val likesRef = postRef.child("likes")

        // --- Username & Caption ---
        holder.username.text = post.username
        holder.caption.text = "${post.username}  ${post.caption}"

        // --- Decode Base64 Image ---
        try {
            val imageBytes = Base64.decode(post.imageUrl, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            holder.postImage.setImageBitmap(bitmap)
        } catch (e: Exception) {
            holder.postImage.setImageResource(R.drawable.postsample)
        }

        // --- Placeholder Profile Picture ---
        holder.profilePic.setImageResource(R.drawable.mystory)

        // Remove old listener if exists to prevent duplicates
        listeners[post.postId]?.let { oldListener ->
            likesRef.removeEventListener(oldListener)
            listeners.remove(post.postId)
        }

        // --- Real-time Like Updates ---
        val likeListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val likeCount = snapshot.childrenCount.toInt()
                val liked = currentUserId != null && snapshot.hasChild(currentUserId)

                Log.d("PostAdapter", "Post ${post.postId}: likeCount=$likeCount, liked=$liked, userId=$currentUserId")

                // Update UI
                holder.btnLike.setImageResource(
                    if (liked) R.drawable.filledheart else R.drawable.like
                )
                holder.likesText.text =
                    if (likeCount == 0) "Be the first to like"
                    else if (likeCount == 1) "Liked by 1 user"
                    else "Liked by $likeCount users"

                // Update local map
                post.likes.clear()
                for (child in snapshot.children) {
                    post.likes[child.key!!] = true
                }

                Log.d("PostAdapter", "Updated likes map for ${post.postId}: ${post.likes.keys}")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("PostAdapter", "Failed to read likes: ${error.message}")
            }
        }

        holder.btnLike.setOnClickListener {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val postId = post.postId
            val postOwnerId = post.userId

            // Reference to likes node
            val likesRef = FirebaseDatabase.getInstance()
                .getReference("posts")
                .child(postId)
                .child("likes")
                .child(currentUserId)

            likesRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // Unlike
                        likesRef.removeValue()
                            .addOnSuccessListener {
                                // Update UI
                                holder.btnLike.setImageResource(R.drawable.like)
                            }
                    } else {
                        // Like
                        likesRef.setValue(true)
                            .addOnSuccessListener {
                                // Update UI
                                holder.btnLike.setImageResource(R.drawable.like)

                                // ✅ Send notification to post owner
                                if (currentUserId != postOwnerId) {
                                    getUsernameAndNotify(currentUserId, postOwnerId, postId)
                                }
                            }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("PostAdapter", "Error: ${error.message}")
                }
            })
        }

        likesRef.addValueEventListener(likeListener)
        listeners[post.postId] = likeListener

        // --- Like Button Click ---
        holder.btnLike.setOnClickListener {
            val uid = FirebaseAuth.getInstance().currentUser?.uid

            Log.d("PostAdapter", "=== CLICK DEBUG ===")
            Log.d("PostAdapter", "Current User ID: $uid")
            Log.d("PostAdapter", "Current User Email: ${FirebaseAuth.getInstance().currentUser?.email}")

            if (uid == null) {
                Log.e("PostAdapter", "ERROR: User is not logged in!")
                Toast.makeText(holder.itemView.context, "Please login first", Toast.LENGTH_LONG).show()
                return@setOnClickListener

            }

            Log.d("PostAdapter", "Like button clicked. Post: ${post.postId}, User: $uid")
            Log.d("PostAdapter", "Current likes before action: ${post.likes.keys}")

            val isLiked = post.likes.containsKey(uid)
            Log.d("PostAdapter", "Is currently liked: $isLiked")

            if (isLiked) {
                // Unlike
                Log.d("PostAdapter", "Removing like from Firebase")
                likesRef.child(uid).removeValue()
                    .addOnSuccessListener {
                        Log.d("PostAdapter", "Successfully removed like")
                    }
                    .addOnFailureListener { e ->
                        Log.e("PostAdapter", "Failed to remove like: ${e.message}")
                    }
                Toast.makeText(holder.itemView.context, "Unliked 💔", Toast.LENGTH_SHORT).show()
            } else {
                // Like
                Log.d("PostAdapter", "Adding like to Firebase")
                likesRef.child(uid).setValue(true)
                    .addOnSuccessListener {
                        Log.d("PostAdapter", "Successfully added like")
                    }
                    .addOnFailureListener { e ->
                        Log.e("PostAdapter", "Failed to add like: ${e.message}")
                    }
                Toast.makeText(holder.itemView.context, "Liked ❤️", Toast.LENGTH_SHORT).show()
            }
        }

        // --- Comment Button Click ---
        holder.btnComment.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, CommentsActivity::class.java)
            intent.putExtra("postId", post.postId)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = postList.size

    // Clean up listeners when adapter is destroyed
    fun cleanup() {
        listeners.forEach { (postId, listener) ->
            FirebaseDatabase.getInstance()
                .getReference("posts")
                .child(postId)
                .child("likes")
                .removeEventListener(listener)
        }
        listeners.clear()
    }

    private fun getUsernameAndNotify(likerId: String, postOwnerId: String, postId: String) {
        val usersRef = FirebaseDatabase.getInstance().getReference("users")

        usersRef.child(likerId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val likerName = snapshot.child("username").getValue(String::class.java)
                        ?: snapshot.child("email").getValue(String::class.java)?.substringBefore("@")
                        ?: "Someone"

                    Notificationhelperfcm.sendLikeNotification(
                        likerId = likerId,
                        likerName = likerName,
                        postOwnerId = postOwnerId,
                        postId = postId
                    )
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("PostAdapter", "Error getting username: ${error.message}")
                }
            })
    }
}