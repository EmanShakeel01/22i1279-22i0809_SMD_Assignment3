package com.FEdev.i221279_i220809

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.FEdev.i221279_i220809.models.*
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.utils.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.launch

class homepage : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var postRecyclerView: RecyclerView
    private lateinit var postList: ArrayList<Post>
    private lateinit var postAdapter: PostAdapter
    private var storyUsersData: MutableList<UserStoryPreview> = mutableListOf()

    private lateinit var navProfileImage: CircleImageView // 🔥 Added this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_homepage)
        Log.d("ActivityStack", "HomePage onCreate")

        sessionManager = SessionManager(this)
        OnlineStatusManager.initializeStatus(sessionManager)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize navigation profile image
        navProfileImage = findViewById(R.id.nav_profile)

        // ------------------- NAVIGATION BUTTONS -------------------
        setupNavigation()

        // ------------------- STORY SECTION (Web Service) -------------------
        setupMyStoryClick()
        loadStoryUsersFromWebService()

        // ------------------- POSTS FEED (Web Service) -------------------
        setupPostsRecyclerView()
        loadPostsFromWebService()

        // ------------------- LOAD PROFILE PICTURE -------------------
        loadProfilePicture() // 🔥 Load profile pic on create

        // ------------------- INCOMING CALL LISTENER (Firebase) -------------------
        listenForIncomingCalls()
    }

    private fun setupNavigation() {
        val homeNav = findViewById<ImageView>(R.id.nav_home)
        homeNav.setOnClickListener {
            // Already on home, just reload
            recreate()
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

        navProfileImage.setOnClickListener {
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
    }

    // 🔥 NEW: Load profile picture from SessionManager
    private fun loadProfilePicture() {
        try {
            val base64Pic = sessionManager.getProfilePic()

            Log.d("HomePage", "Loading profile picture from SessionManager")

            if (!base64Pic.isNullOrEmpty()) {
                Log.d("HomePage", "Profile pic found, length: ${base64Pic.length}")

                val decodedBytes = Base64.decode(base64Pic, Base64.NO_WRAP)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

                if (bitmap != null) {
                    navProfileImage.setImageBitmap(bitmap)
                    Log.d("HomePage", "✅ Profile picture loaded in nav bar")
                } else {
                    Log.e("HomePage", "❌ Failed to decode bitmap")
                }
            } else {
                Log.d("HomePage", "No saved profile picture")
            }
        } catch (e: Exception) {
            Log.e("HomePage", "❌ Error loading profile picture: ${e.message}", e)
        }
    }

    // ------------------- STORY HANDLING (Web Service) -------------------
    private fun setupMyStoryClick() {
        val myStory = findViewById<CircleImageView>(R.id.story1)
        val addStoryBtn = findViewById<ImageView>(R.id.add_story)

        myStory.setOnClickListener {
            checkUserStoriesFromWebService(openCameraIfNone = false)
        }

        addStoryBtn.setOnClickListener {
            val intent = Intent(this, takepicture::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }
    }

    private fun loadStoryUsersFromWebService() {
        val authToken = sessionManager.getAuthToken()

        if (authToken == null) {
            Log.e("HomePage", "No auth token for loading stories")
            return
        }

        lifecycleScope.launch {
            try {
                val request = AllStoriesRequest(auth_token = authToken)
                val response = RetrofitClient.apiService.getAllStories(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data

                    if (data != null && data.user_stories.isNotEmpty()) {
                        storyUsersData.clear()

                        // Separate own stories from others
                        val ownStories = data.user_stories.filter { it.is_own }
                        val otherStories = data.user_stories.filter { !it.is_own }

                        storyUsersData.addAll(otherStories)

                        Log.d("HomePage", "✅ Loaded ${data.total_users} users with stories")

                        // Update UI with story circles
                        updateStoryNamesUI()

                        // Update "My Story" circle if user has stories
                        if (ownStories.isNotEmpty()) {
                            updateMyStoryCircle(ownStories[0])
                        }
                    } else {
                        Log.d("HomePage", "No stories available")
                    }
                } else {
                    Log.e("HomePage", "❌ Failed to load stories: ${response.body()?.message}")
                }
            } catch (e: Exception) {
                Log.e("HomePage", "❌ Error loading stories: ${e.message}", e)
            }
        }
    }

    private fun updateMyStoryCircle(myStory: UserStoryPreview) {
        val myStoryCircle = findViewById<CircleImageView>(R.id.story1)

        try {
            val imageBytes = Base64.decode(myStory.preview_image, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            if (bitmap != null) {
                myStoryCircle.setImageBitmap(bitmap)
                Log.d("HomePage", "✅ Updated my story preview image")
            }
        } catch (e: Exception) {
            Log.e("HomePage", "Error decoding my story preview: ${e.message}")
        }
    }

    private fun updateStoryNamesUI() {
        // Story 2
        if (storyUsersData.size > 0) {
            val userStory = storyUsersData[0]
            val story2View = findViewById<LinearLayout>(R.id.story2_container)
            val story2Text = story2View.findViewWithTag<TextView>("story_username")
            val story2Image = story2View.findViewWithTag<CircleImageView>("story_image")

            story2Text?.text = userStory.username

            try {
                val imageBytes = Base64.decode(userStory.preview_image, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bitmap != null) {
                    story2Image?.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                Log.e("HomePage", "Error decoding story 2 preview: ${e.message}")
            }

            story2View.setOnClickListener {
                openUserStoryFromWebService(userStory.user_id, userStory.username)
            }
        }

        // Story 3
        if (storyUsersData.size > 1) {
            val userStory = storyUsersData[1]
            val story3View = findViewById<LinearLayout>(R.id.story3_container)
            val story3Text = story3View.findViewWithTag<TextView>("story_username")
            val story3Image = story3View.findViewWithTag<CircleImageView>("story_image")

            story3Text?.text = userStory.username

            try {
                val imageBytes = Base64.decode(userStory.preview_image, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bitmap != null) {
                    story3Image?.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                Log.e("HomePage", "Error decoding story 3 preview: ${e.message}")
            }

            story3View.setOnClickListener {
                openUserStoryFromWebService(userStory.user_id, userStory.username)
            }
        }

        // Story 4
        if (storyUsersData.size > 2) {
            val userStory = storyUsersData[2]
            val story4View = findViewById<LinearLayout>(R.id.story4_container)
            val story4Text = story4View.findViewWithTag<TextView>("story_username")
            val story4Image = story4View.findViewWithTag<CircleImageView>("story_image")

            story4Text?.text = userStory.username

            try {
                val imageBytes = Base64.decode(userStory.preview_image, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bitmap != null) {
                    story4Image?.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                Log.e("HomePage", "Error decoding story 4 preview: ${e.message}")
            }

            story4View.setOnClickListener {
                openUserStoryFromWebService(userStory.user_id, userStory.username)
            }
        }
    }

    private fun openUserStoryFromWebService(userId: Int, username: String) {
        val intent = Intent(this, storyviewer::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        intent.putExtra("userId", userId)
        intent.putExtra("username", username)
        startActivity(intent)
    }

    private fun checkUserStoriesFromWebService(openCameraIfNone: Boolean) {
        val authToken = sessionManager.getAuthToken()

        if (authToken == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val request = MyStoriesRequest(auth_token = authToken)
                val response = RetrofitClient.apiService.getMyStories(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data

                    if (data != null && data.stories.isNotEmpty()) {
                        val intent = Intent(this@homepage, storyviewer2::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                    } else {
                        if (openCameraIfNone) {
                            val intent = Intent(this@homepage, takepicture::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            startActivity(intent)
                        } else {
                            Toast.makeText(this@homepage, "No active story", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    if (openCameraIfNone) {
                        val intent = Intent(this@homepage, takepicture::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                    } else {
                        Toast.makeText(this@homepage, "No active story", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("HomePage", "Error checking stories: ${e.message}")
                Toast.makeText(this@homepage, "Error loading stories", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ------------------- POSTS HANDLING (Web Service) -------------------
    private fun setupPostsRecyclerView() {
        postRecyclerView = findViewById(R.id.postRecyclerView)
        postRecyclerView.layoutManager = LinearLayoutManager(this)
        postRecyclerView.setHasFixedSize(false)

        postList = arrayListOf()
        postAdapter = PostAdapter(postList, sessionManager)
        postRecyclerView.adapter = postAdapter
    }

    private fun loadPostsFromWebService() {
        val authToken = sessionManager.getAuthToken()

        if (authToken == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val request = GetPostsRequest(auth_token = authToken)
                val response = RetrofitClient.apiService.getPosts(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data

                    if (data != null) {
                        postList.clear()
                        postList.addAll(data.posts)
                        postAdapter.notifyDataSetChanged()

                        Log.d("HomePage", "✅ Loaded ${data.total} posts from web service")
                    }
                } else {
                    Log.e("HomePage", "❌ Failed to load posts: ${response.body()?.message}")
                    Toast.makeText(
                        this@homepage,
                        response.body()?.message ?: "Failed to load posts",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("HomePage", "❌ Error loading posts: ${e.message}", e)
                Toast.makeText(
                    this@homepage,
                    "Network error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ------------------- INCOMING CALL LISTENER (Firebase) -------------------
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

    override fun onResume() {
        super.onResume()
        Log.d("HomePage", "onResume - Reloading data")
        // Refresh posts, stories, and profile picture when returning to homepage
        loadPostsFromWebService()
        loadStoryUsersFromWebService()
        loadProfilePicture() // 🔥 Reload profile pic on resume
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ActivityStack", "HomePage onDestroy")
        OnlineStatusManager.cleanup()
    }
}