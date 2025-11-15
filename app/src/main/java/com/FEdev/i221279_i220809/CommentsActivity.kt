package com.FEdev.i221279_i220809

import Comment
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class CommentsActivity : AppCompatActivity() {

    private lateinit var dbRef: DatabaseReference
    private lateinit var userDbRef: DatabaseReference
    private lateinit var commentList: ArrayList<Comment>
    private lateinit var adapter: CommentAdapter
    private lateinit var postId: String
    private var currentUsername: String = "Anonymous"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comments)

        // ✅ Get postId from intent
        postId = intent.getStringExtra("postId") ?: run {
            Toast.makeText(this, "Invalid post", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // ✅ Firebase references
        dbRef = FirebaseDatabase.getInstance()
            .getReference("posts")
            .child(postId)
            .child("comments")

        userDbRef = FirebaseDatabase.getInstance().getReference("users")

        // ✅ UI Elements
        val backButton = findViewById<ImageView>(R.id.backButton)
        val commentRecyclerView = findViewById<RecyclerView>(R.id.commentRecyclerView)
        val commentInput = findViewById<EditText>(R.id.commentInput)
        val sendButton = findViewById<ImageView>(R.id.sendCommentBtn)

        backButton.setOnClickListener { finish() }

        // ✅ Setup RecyclerView
        commentRecyclerView.layoutManager = LinearLayoutManager(this)
        commentList = ArrayList()
        adapter = CommentAdapter(commentList)
        commentRecyclerView.adapter = adapter

        // ✅ Hide send button initially
        sendButton.visibility = View.GONE

        // ✅ Show/hide send button dynamically
        commentInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                sendButton.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // ✅ Fetch current username from "users"
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val emailKey = currentUser.email?.replace(".", ",")
            if (emailKey != null) {
                userDbRef.child(emailKey)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            currentUsername = snapshot.child("username").getValue(String::class.java)
                                ?: "Anonymous"
                            Log.d("CommentDebug", "Fetched username: $currentUsername")
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("CommentDebug", "Failed to fetch username: ${error.message}")
                        }
                    })
            }
        }

        // ✅ Load comments in real time
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                commentList.clear()
                for (commentSnap in snapshot.children) {
                    val comment = commentSnap.getValue(Comment::class.java)
                    if (comment != null) {
                        commentList.add(comment)
                    }
                }

                adapter.notifyDataSetChanged()

                if (commentList.isNotEmpty()) {
                    commentRecyclerView.scrollToPosition(commentList.size - 1)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@CommentsActivity, "Failed to load comments", Toast.LENGTH_SHORT).show()
            }
        })

        // ✅ Add comment
        sendButton.setOnClickListener {
            val text = commentInput.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "Comment cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val commentId = dbRef.push().key ?: return@setOnClickListener

            val comment = Comment(
                userId = uid,
                username = currentUsername,
                text = text,
                timestamp = System.currentTimeMillis().toString()
            )

            dbRef.child(commentId).setValue(comment)
                .addOnSuccessListener {
                    commentInput.text.clear()
                    Toast.makeText(this, "Comment added", Toast.LENGTH_SHORT).show()

                    // ✅ Get post owner and send notification
                    getPostOwnerAndNotify(postId, uid, currentUsername, text)
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to add comment: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun getPostOwnerAndNotify(
        postId: String,
        commenterId: String,
        commenterName: String,
        commentText: String
    ) {
        val postsRef = FirebaseDatabase.getInstance().getReference("posts")

        postsRef.child(postId).child("userId")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val postOwnerId = snapshot.getValue(String::class.java) ?: return

                    // Send notification
                    Notificationhelperfcm.sendCommentNotification(
                        commenterId = commenterId,
                        commenterName = commenterName,
                        postOwnerId = postOwnerId,
                        postId = postId,
                        commentText = commentText
                    )
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("CommentsActivity", "Error getting post owner: ${error.message}")
                }
            })
    }

}
