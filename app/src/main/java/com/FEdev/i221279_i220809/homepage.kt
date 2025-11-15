package com.FEdev.i221279_i220809

import Post
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import de.hdodenhof.circleimageview.CircleImageView

class homepage : AppCompatActivity() {

    private lateinit var postRecyclerView: RecyclerView
    private lateinit var postList: ArrayList<Post>
    private lateinit var dbRef: DatabaseReference
    private lateinit var postAdapter: PostAdapter
    private var valueEventListener: ValueEventListener? = null
    private var storyUsersData: MutableList<Pair<String, String>> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_homepage)
        Log.d("ActivityStack", "HomePage onCreate")
        OnlineStatusManager.initializeStatus()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // ------------------- NAVIGATION BUTTONS -------------------
        val homeNav = findViewById<ImageView>(R.id.nav_home)
        homeNav.setOnClickListener {
            startActivity(Intent(this, homepage::class.java))
            finish()
        }

        val likeNav = findViewById<ImageView>(R.id.nav_like)
        likeNav.setOnClickListener {
            val intent = Intent(this, activitypage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        val searchNav = findViewById<ImageView>(R.id.nav_search)
        searchNav.setOnClickListener {
            val intent = Intent(this, searchpage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        val profileNav = findViewById<CircleImageView>(R.id.nav_profile)
        profileNav.setOnClickListener {
            val intent = Intent(this, activityprofile::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        val addpic = findViewById<ImageView>(R.id.add)
        addpic.setOnClickListener {
            val intent = Intent(this, selectpicture::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        val camera = findViewById<ImageView>(R.id.camera1)
        camera.setOnClickListener {
            val intent = Intent(this, takepicture::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        val message = findViewById<ImageView>(R.id.message1)
        message.setOnClickListener {
            val intent = Intent(this, chatlist::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        // ------------------- STORY SECTION -------------------
        setupMyStoryClick()
        loadStoryUsers()

        // ------------------- POSTS FEED (Firebase) -------------------
        postRecyclerView = findViewById(R.id.postRecyclerView)
        postRecyclerView.layoutManager = LinearLayoutManager(this)
        postRecyclerView.setHasFixedSize(false)

        postList = arrayListOf()
        postAdapter = PostAdapter(postList)
        postRecyclerView.adapter = postAdapter

        dbRef = FirebaseDatabase.getInstance().getReference("Posts")
        loadPostsFromFirebase()

        // ------------------- INCOMING CALL LISTENER -------------------
        listenForIncomingCalls()
    }

    // ------------------- STORY HANDLING -------------------
    private fun setupMyStoryClick() {
        val myStory = findViewById<CircleImageView>(R.id.story1)
        val addStoryBtn = findViewById<ImageView>(R.id.add_story)

        myStory.setOnClickListener {
            checkUserStories(openCameraIfNone = false)
        }

        addStoryBtn.setOnClickListener {
            val intent = Intent(this, takepicture::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }
    }

    private fun loadStoryUsers() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val storiesRef = FirebaseDatabase.getInstance().getReference("Stories")
        val usersRef = FirebaseDatabase.getInstance().getReference("users")

        storiesRef.get().addOnSuccessListener { storiesSnapshot ->
            storyUsersData.clear()

            for (storySnap in storiesSnapshot.children) {
                val userId = storySnap.key ?: continue
                if (userId != currentUserId) {
                    storyUsersData.add(Pair(userId, "Loading..."))
                }
            }

            usersRef.get().addOnSuccessListener { usersSnapshot ->
                for (i in storyUsersData.indices) {
                    val userId = storyUsersData[i].first

                    for (userSnap in usersSnapshot.children) {
                        val uid = userSnap.child("uid").getValue(String::class.java)
                        if (uid == userId) {
                            val username = userSnap.child("username").getValue(String::class.java) ?: "User"
                            storyUsersData[i] = Pair(userId, username)
                            Log.d("StoryUsers", "Found user: $username with ID: $userId")
                            break
                        }
                    }
                }

                updateStoryNamesUI()
            }
        }.addOnFailureListener { e ->
            Log.e("StoryUsersError", "Failed to load stories: ${e.message}")
        }
    }

    private fun updateStoryNamesUI() {
        if (storyUsersData.size > 0) {
            val (userId, username) = storyUsersData[0]
            val story2View = findViewById<LinearLayout>(R.id.story2_container)
            val story2Text = story2View.findViewWithTag<TextView>("story_username")
            story2Text?.text = username
            story2View.setOnClickListener { openUserStory(userId, username) }
        }

        if (storyUsersData.size > 1) {
            val (userId, username) = storyUsersData[1]
            val story3View = findViewById<LinearLayout>(R.id.story3_container)
            val story3Text = story3View.findViewWithTag<TextView>("story_username")
            story3Text?.text = username
            story3View.setOnClickListener { openUserStory(userId, username) }
        }

        if (storyUsersData.size > 2) {
            val (userId, username) = storyUsersData[2]
            val story4View = findViewById<LinearLayout>(R.id.story4_container)
            val story4Text = story4View.findViewWithTag<TextView>("story_username")
            story4Text?.text = username
            story4View.setOnClickListener { openUserStory(userId, username) }
        }
    }

    private fun openUserStory(userId: String, username: String) {
        val intent = Intent(this, storyviewer::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        intent.putExtra("userId", userId)
        intent.putExtra("username", username)
        startActivity(intent)
    }

    private fun checkUserStories(openCameraIfNone: Boolean) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val storiesRef = FirebaseDatabase.getInstance().getReference("Stories").child(userId)

        storiesRef.get().addOnSuccessListener { snapshot ->
            val currentTime = System.currentTimeMillis()
            var hasValidStory = false

            for (storySnap in snapshot.children) {
                val expiresAt = storySnap.child("expiresAt").getValue(Long::class.java) ?: 0L
                if (currentTime <= expiresAt) {
                    hasValidStory = true
                    break
                }
            }

            if (hasValidStory) {
                val intent = Intent(this, storyviewer2::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
            } else if (openCameraIfNone) {
                val intent = Intent(this, takepicture::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
            } else {
                Toast.makeText(this, "No active story", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Log.e("CheckStories", "Error: ${it.message}")
        }
    }

    // ------------------- POSTS HANDLING -------------------
    private fun loadPostsFromFirebase() {
        valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                postList.clear()
                if (snapshot.exists()) {
                    for (postSnap in snapshot.children) {
                        val post = postSnap.getValue(Post::class.java)
                        post?.let {
                            postList.add(it)
                            Log.d("PostLoading", "Post loaded: ${it.username}")
                        }
                    }
                    postList.reverse()
                    Log.d("PostLoading", "TOTAL POSTS: ${postList.size}")
                    postAdapter.notifyDataSetChanged()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseError", "Error: ${error.message}")
            }
        }
        dbRef.addValueEventListener(valueEventListener!!)
    }

    // ------------------- INCOMING CALL LISTENER -------------------
    private fun listenForIncomingCalls() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val callsRef = FirebaseDatabase.getInstance().getReference("calls")

        callsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val calleeId = snapshot.child("calleeId").getValue(String::class.java)
                val status = snapshot.child("status").getValue(String::class.java)
                val type = snapshot.child("type").getValue(String::class.java) ?: "video"
                val channel = snapshot.key ?: return

                if (calleeId == uid && status == "ringing") {
                    Log.d("IncomingCall", "Incoming $type call detected for user: $uid")

                    val intent = Intent(this@homepage, IncomingCallActivity::class.java).apply {
                        putExtra("CHANNEL_NAME", channel)
                        putExtra("IS_VIDEO", type == "video")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e("IncomingCall", "Error listening for calls: ${error.message}")
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ActivityStack", "HomePage onDestroy")
        valueEventListener?.let { dbRef.removeEventListener(it) }
        OnlineStatusManager.cleanup()
    }
}