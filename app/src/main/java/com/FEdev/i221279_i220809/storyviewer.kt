package com.FEdev.i221279_i220809

import android.animation.ObjectAnimator
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.models.UserStoriesRequest
import com.FEdev.i221279_i220809.models.Story
import com.FEdev.i221279_i220809.utils.SessionManager
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class storyviewer : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var storyImage: ImageView
    private lateinit var backButton: ImageView
    private lateinit var profileImage: CircleImageView
    private lateinit var username: TextView
    private lateinit var storyCountText: TextView
    private lateinit var storyTimestamp: TextView
    private lateinit var progressBarsContainer: LinearLayout

    private var storyList: MutableList<Story> = mutableListOf()
    private var currentIndex = 0
    private var progressAnimators: MutableList<ObjectAnimator> = mutableListOf()
    private var viewedUserId: Int = 0
    private var viewedUsername: String = ""

    companion object {
        private const val STORY_DURATION = 5000L // 5 seconds per story
        private const val TAG = "StoryViewer"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_storyviewer)

        Log.d(TAG, "========================================")
        Log.d(TAG, "=== StoryViewer onCreate ===")
        Log.d(TAG, "========================================")

        sessionManager = SessionManager(this)

        // Get user ID and username from intent
        viewedUserId = intent.getIntExtra("userId", 0)
        viewedUsername = intent.getStringExtra("username") ?: "User"

        Log.d(TAG, "Intent data received:")
        Log.d(TAG, "  - userId: $viewedUserId")
        Log.d(TAG, "  - username: $viewedUsername")

        // Initialize views
        initializeViews()

        // Set username from intent
        username.text = viewedUsername
        Log.d(TAG, "Username TextView set to: $viewedUsername")

        // Setup click listeners
        setupClickListeners()

        // Validate and load stories
        if (viewedUserId > 0) {
            Log.d(TAG, "Valid userId, loading stories...")
            loadUserStories(viewedUserId)
        } else {
            Log.e(TAG, "❌ INVALID USER ID: $viewedUserId")
            Toast.makeText(this, "Invalid user", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun initializeViews() {
        try {
            storyImage = findViewById(R.id.storyImage)
            backButton = findViewById(R.id.back)
            profileImage = findViewById(R.id.profileImage)
            username = findViewById(R.id.username)
            storyCountText = findViewById(R.id.storyCount)
            storyTimestamp = findViewById(R.id.storyTimestamp)
            progressBarsContainer = findViewById(R.id.progressBarsContainer)

            Log.d(TAG, "✅ All views initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing views: ${e.message}", e)
            Toast.makeText(this, "Error loading viewer", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupClickListeners() {
        // Back button
        backButton.setOnClickListener {
            Log.d(TAG, "Back button clicked")
            finish()
        }

        // Tap anywhere on story to go to next
        storyImage.setOnClickListener {
            Log.d(TAG, "Story image tapped")
            stopCurrentProgress()
            if (currentIndex < storyList.size - 1) {
                Log.d(TAG, "Moving to next story: ${currentIndex + 1}")
                displayStory(currentIndex + 1)
            } else {
                Log.d(TAG, "Last story reached")
                Toast.makeText(this, "Last story", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun loadUserStories(userId: Int) {
        val authToken = sessionManager.getAuthToken()

        Log.d(TAG, "----------------------------------------")
        Log.d(TAG, "Loading stories for user ID: $userId")
        Log.d(TAG, "Auth token exists: ${authToken != null}")
        Log.d(TAG, "Auth token: ${authToken?.take(20)}...")
        Log.d(TAG, "----------------------------------------")

        if (authToken == null) {
            Log.e(TAG, "❌ NO AUTH TOKEN - User not logged in")
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (userId <= 0) {
            Log.e(TAG, "❌ INVALID USER ID: $userId")
            Toast.makeText(this, "Invalid user", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                Log.d(TAG, "Creating API request...")
                val request = UserStoriesRequest(
                    auth_token = authToken,
                    user_id = userId
                )

                Log.d(TAG, "Request details:")
                Log.d(TAG, "  - auth_token: ${authToken.take(20)}...")
                Log.d(TAG, "  - user_id: $userId")

                Log.d(TAG, "Making API call to get_user_stories.php...")
                val response = RetrofitClient.apiService.getUserStories(request)

                Log.d(TAG, "========================================")
                Log.d(TAG, "API Response received:")
                Log.d(TAG, "  - Response code: ${response.code()}")
                Log.d(TAG, "  - Is successful: ${response.isSuccessful}")
                Log.d(TAG, "  - Response body: ${response.body()}")
                Log.d(TAG, "  - Error body: ${response.errorBody()?.string()}")
                Log.d(TAG, "========================================")

                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data

                    Log.d(TAG, "API Success!")
                    Log.d(TAG, "  - Data is null: ${data == null}")
                    Log.d(TAG, "  - Stories count: ${data?.stories?.size ?: 0}")
                    Log.d(TAG, "  - Username from API: ${data?.username}")

                    if (data != null && data.stories.isNotEmpty()) {
                        storyList = data.stories.toMutableList()
                        username.text = data.username

                        Log.d(TAG, "✅ Successfully loaded ${storyList.size} stories")
                        Log.d(TAG, "Story details:")
                        storyList.forEachIndexed { index, story ->
                            Log.d(TAG, "  Story $index:")
                            Log.d(TAG, "    - ID: ${story.story_id}")
                            Log.d(TAG, "    - User: ${story.username}")
                            Log.d(TAG, "    - Timestamp: ${story.timestamp}")
                            Log.d(TAG, "    - Image length: ${story.image_base64.length}")
                        }

                        // Create progress bars
                        createProgressBars()

                        // Show first story
                        displayStory(0)
                    } else {
                        Log.d(TAG, "⚠️ No stories in response")
                        Toast.makeText(
                            this@storyviewer,
                            "No stories available",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                } else {
                    val errorMsg = response.body()?.message ?: "Unknown error"
                    Log.e(TAG, "❌ API FAILED:")
                    Log.e(TAG, "  - Success flag: ${response.body()?.success}")
                    Log.e(TAG, "  - Message: $errorMsg")
                    Log.e(TAG, "  - Response code: ${response.code()}")

                    Toast.makeText(
                        this@storyviewer,
                        "Failed: $errorMsg",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌❌❌ EXCEPTION OCCURRED ❌❌❌")
                Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
                Log.e(TAG, "Exception message: ${e.message}")
                Log.e(TAG, "Stack trace:")
                e.printStackTrace()

                Toast.makeText(
                    this@storyviewer,
                    "Network error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun createProgressBars() {
        progressBarsContainer.removeAllViews()
        progressAnimators.clear()

        Log.d(TAG, "Creating ${storyList.size} progress bars")

        for (i in storyList.indices) {
            val progressBar = View(this)
            val params = LinearLayout.LayoutParams(0, 4, 1f)
            params.setMargins(2, 0, 2, 0)
            progressBar.setBackgroundColor(
                ContextCompat.getColor(this, android.R.color.darker_gray)
            )
            progressBarsContainer.addView(progressBar, params)
        }

        Log.d(TAG, "✅ Progress bars created")
    }

    private fun displayStory(index: Int) {
        if (index < 0 || index >= storyList.size) {
            Log.e(TAG, "❌ Invalid story index: $index (total: ${storyList.size})")
            return
        }

        currentIndex = index
        val story = storyList[index]

        Log.d(TAG, "========================================")
        Log.d(TAG, "Displaying story ${index + 1} of ${storyList.size}")
        Log.d(TAG, "  - Story ID: ${story.story_id}")
        Log.d(TAG, "  - Username: ${story.username}")
        Log.d(TAG, "  - Timestamp: ${story.timestamp}")
        Log.d(TAG, "========================================")

        // Display image
        try {
            Log.d(TAG, "Decoding image...")
            Log.d(TAG, "  - Base64 length: ${story.image_base64.length}")

            // Clean base64 string (remove newlines and carriage returns)
            val cleanBase64 = story.image_base64
                .replace("\n", "")
                .replace("\r", "")
                .replace(" ", "")

            Log.d(TAG, "  - Cleaned length: ${cleanBase64.length}")

            val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            Log.d(TAG, "  - Decoded bytes: ${imageBytes.size}")

            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            if (bitmap != null) {
                Log.d(TAG, "  - Bitmap size: ${bitmap.width}x${bitmap.height}")
                storyImage.setImageBitmap(bitmap)
                Log.d(TAG, "✅ Story image displayed successfully")
            } else {
                Log.e(TAG, "❌ Bitmap is NULL after decoding")
                storyImage.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error decoding image:")
            Log.e(TAG, "  - Exception: ${e.javaClass.simpleName}")
            Log.e(TAG, "  - Message: ${e.message}")
            e.printStackTrace()
            storyImage.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        // Update story count
        storyCountText.text = "${currentIndex + 1} / ${storyList.size}"
        Log.d(TAG, "Story count updated: ${storyCountText.text}")

        // Update timestamp
        val timeAgo = getTimeAgo(story.timestamp)
        storyTimestamp.text = timeAgo
        Log.d(TAG, "Timestamp updated: $timeAgo")

        // Update progress bars
        updateProgressBars()

        // Start progress animation
        startProgressAnimation()
    }

    private fun updateProgressBars() {
        Log.d(TAG, "Updating progress bars (current: $currentIndex)")

        for (i in 0 until progressBarsContainer.childCount) {
            val progressBar = progressBarsContainer.getChildAt(i)
            when {
                i < currentIndex -> {
                    // Already viewed - white
                    progressBar.setBackgroundColor(
                        ContextCompat.getColor(this, android.R.color.white)
                    )
                }
                i == currentIndex -> {
                    // Currently viewing - will animate
                    progressBar.setBackgroundColor(
                        ContextCompat.getColor(this, android.R.color.white)
                    )
                }
                else -> {
                    // Not viewed yet - gray
                    progressBar.setBackgroundColor(
                        ContextCompat.getColor(this, android.R.color.darker_gray)
                    )
                }
            }
        }

        Log.d(TAG, "✅ Progress bars updated")
    }

    private fun startProgressAnimation() {
        stopCurrentProgress()

        val currentProgressBar = progressBarsContainer.getChildAt(currentIndex)
        if (currentProgressBar != null) {
            Log.d(TAG, "Starting progress animation for story $currentIndex")

            val animator = ObjectAnimator.ofFloat(currentProgressBar, "scaleX", 0f, 1f)
            animator.duration = STORY_DURATION
            animator.start()

            progressAnimators.add(animator)

            // Auto-advance to next story after duration
            currentProgressBar.postDelayed({
                Log.d(TAG, "Animation completed for story $currentIndex")
                if (currentIndex < storyList.size - 1) {
                    Log.d(TAG, "Auto-advancing to next story")
                    displayStory(currentIndex + 1)
                } else {
                    Log.d(TAG, "Last story reached, closing viewer")
                    finish()
                }
            }, STORY_DURATION)
        } else {
            Log.e(TAG, "❌ Progress bar is NULL for index: $currentIndex")
        }
    }

    private fun stopCurrentProgress() {
        if (progressAnimators.isNotEmpty()) {
            Log.d(TAG, "Stopping ${progressAnimators.size} animations")
            progressAnimators.forEach { it.cancel() }
            progressAnimators.clear()
        }
    }

    private fun getTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffInSeconds = (now - timestamp) / 1000

        return when {
            diffInSeconds < 60 -> "Just now"
            diffInSeconds < 3600 -> "${diffInSeconds / 60}m ago"
            diffInSeconds < 86400 -> "${diffInSeconds / 3600}h ago"
            diffInSeconds < 604800 -> "${diffInSeconds / 86400}d ago"
            else -> {
                val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                dateFormat.format(Date(timestamp))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCurrentProgress()
        Log.d(TAG, "========================================")
        Log.d(TAG, "=== StoryViewer onDestroy ===")
        Log.d(TAG, "========================================")
    }

    override fun onPause() {
        super.onPause()
        stopCurrentProgress()
        Log.d(TAG, "=== onPause - animations stopped ===")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "=== onResume ===")
        if (storyList.isNotEmpty()) {
            Log.d(TAG, "Resuming animation for story $currentIndex")
            startProgressAnimation()
        }
    }
}