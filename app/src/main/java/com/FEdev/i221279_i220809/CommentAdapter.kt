package com.FEdev.i221279_i220809

import Comment
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.hdodenhof.circleimageview.CircleImageView

class CommentAdapter(private val commentList: ArrayList<Comment>) :
    RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userProfile: CircleImageView = itemView.findViewById(R.id.commentUserProfile)
        val commentText: TextView = itemView.findViewById(R.id.commentText)
        val commentTime: TextView = itemView.findViewById(R.id.commentTime)
        val likeButton: ImageView = itemView.findViewById(R.id.commentLikeBtn)
        val replyButton: TextView = itemView.findViewById(R.id.replyButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = commentList[position]

        // Profile picture placeholder
        holder.userProfile.setImageResource(R.drawable.mystory)

        // Bold username
        val formattedText = SpannableString("${comment.username} ${comment.text}")
        formattedText.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            comment.username.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        holder.commentText.text = formattedText

        // Time
        holder.commentTime.text = getTimeAgo(comment.timestamp.toLongOrNull() ?: 0L)

        // Like & reply placeholders
        holder.likeButton.setOnClickListener { /* future implementation */ }
        holder.replyButton.setOnClickListener { /* future implementation */ }
    }

    override fun getItemCount(): Int = commentList.size

    private fun getTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60000 -> "just now"
            diff < 3600000 -> "${diff / 60000}m"
            diff < 86400000 -> "${diff / 3600000}h"
            diff < 604800000 -> "${diff / 86400000}d"
            else -> "${diff / 604800000}w"
        }
    }
}
