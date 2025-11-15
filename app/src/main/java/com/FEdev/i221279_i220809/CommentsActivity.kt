package com.FEdev.i221279_i220809

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.FEdev.i221279_i220809.models.AddCommentRequest
import com.FEdev.i221279_i220809.models.Comment
import com.FEdev.i221279_i220809.models.GetCommentsRequest
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.utils.SessionManager
import kotlinx.coroutines.launch

class CommentsActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var commentList: ArrayList<Comment>
    private lateinit var adapter: CommentAdapter
    private var postId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comments)

        sessionManager = SessionManager(this)

        // Get postId from intent
        postId = intent.getIntExtra("postId", 0)
        if (postId == 0) {
            Toast.makeText(this, "Invalid post", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // UI Elements
        val backButton = findViewById<ImageView>(R.id.backButton)
        val commentRecyclerView = findViewById<RecyclerView>(R.id.commentRecyclerView)
        val commentInput = findViewById<EditText>(R.id.commentInput)
        val sendButton = findViewById<ImageView>(R.id.sendCommentBtn)

        backButton.setOnClickListener { finish() }

        // Setup RecyclerView
        commentRecyclerView.layoutManager = LinearLayoutManager(this)
        commentList = ArrayList()
        adapter = CommentAdapter(commentList)
        commentRecyclerView.adapter = adapter

        // Hide send button initially
        sendButton.visibility = View.GONE

        // Show/hide send button dynamically
        commentInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                sendButton.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Load comments
        loadComments()

        // Add comment
        sendButton.setOnClickListener {
            val text = commentInput.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "Comment cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            addComment(text, commentInput)
        }
    }

    private fun loadComments() {
        val authToken = sessionManager.getAuthToken()

        if (authToken == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                val request = GetCommentsRequest(
                    auth_token = authToken,
                    post_id = postId
                )

                val response = RetrofitClient.apiService.getComments(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data

                    if (data != null) {
                        commentList.clear()
                        commentList.addAll(data.comments)
                        adapter.notifyDataSetChanged()

                        // Scroll to bottom if there are comments
                        if (commentList.isNotEmpty()) {
                            findViewById<RecyclerView>(R.id.commentRecyclerView)
                                .scrollToPosition(commentList.size - 1)
                        }

                        Log.d("CommentsActivity", "Loaded ${data.total} comments")
                    }
                } else {
                    Toast.makeText(
                        this@CommentsActivity,
                        response.body()?.message ?: "Failed to load comments",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("CommentsActivity", "Error loading comments: ${e.message}", e)
                Toast.makeText(
                    this@CommentsActivity,
                    "Network error",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun addComment(text: String, commentInput: EditText) {
        val authToken = sessionManager.getAuthToken()

        if (authToken == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val request = AddCommentRequest(
                    auth_token = authToken,
                    post_id = postId,
                    comment_text = text
                )

                val response = RetrofitClient.apiService.addComment(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data

                    if (data != null) {
                        // Add new comment to list
                        val newComment = Comment(
                            comment_id = data.comment_id,
                            user_id = 0, // Not returned by API, but not needed for display
                            username = data.username,
                            comment_text = data.comment_text,
                            timestamp = data.timestamp.toString()
                        )

                        commentList.add(newComment)
                        adapter.notifyItemInserted(commentList.size - 1)

                        // Scroll to new comment
                        findViewById<RecyclerView>(R.id.commentRecyclerView)
                            .scrollToPosition(commentList.size - 1)

                        // Clear input
                        commentInput.text.clear()

                        Toast.makeText(
                            this@CommentsActivity,
                            "Comment added",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@CommentsActivity,
                        response.body()?.message ?: "Failed to add comment",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("CommentsActivity", "Error adding comment: ${e.message}", e)
                Toast.makeText(
                    this@CommentsActivity,
                    "Network error",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}