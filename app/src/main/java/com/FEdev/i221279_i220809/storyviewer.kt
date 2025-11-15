package com.FEdev.i221279_i220809

import android.animation.ObjectAnimator
import android.content.Intent
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
import com.FEdev.i221279_i220809.models.Story
import com.FEdev.i221279_i220809.models.UserStoriesRequest
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.utils.SessionManager
import kotlinx.coroutines.launch

class storyviewer : AppCompatActivity() {

    private lateinit var storyImage: ImageView
    private lateinit var backButton: ImageView
    private lateinit var usernameText: TextView
    private lateinit var storyCountText: TextView
    private lateinit var storyTimestamp: TextView
    private lateinit var progressBarsContainer: LinearLayout

    private lateinit var sessionManager: SessionManager
    private var storyList: MutableList<Story> = mutableListOf()
    private var currentIndex = 0
    private var progressAnimators: MutableList<ObjectAnimator> = mutableListOf()
    private var viewedUserId: Int = 0
    private var viewedUsername: String = ""

    companion object {
        private const val STORY_DURATION = 5000L // 5 seconds per story
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_storyviewer)

        sessionManager = SessionManager(this)

        storyImage = findViewById(R.id.storyImage)
        backButton = findViewById(R.id.back)
        usernameText = findViewById(R.id.username)
        storyCountText = findViewById(R.id.storyCount)
        storyTimestamp = findViewById(R.id.storyTimestamp)
        progressBarsContainer = findViewById(R.id.progressBarsContainer)

        // Get userId and username from intent
        viewedUserId = intent.getIntExtra("userId", 0)
        viewedUsername = intent.getStringExtra("username") ?: "User"
        usernameText.text = viewedUsername

        backButton.setOnClickListener { goBackToHomepage() }

        storyImage.setOnClickListener {
            stopCurrentProgress()
            if (currentIndex < storyList.size - 1) displayStory(currentIndex + 1)
            else goBackToHomepage()
        }

        loadUserStories(viewedUserId)
    }

    private fun goBackToHomepage() {
        val intent = Intent(this, homepage::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    private fun loadUserStories(userId: Int) {
        val authToken = sessionManager.getAuthToken() ?: run {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (userId <= 0) {
            Toast.makeText(this, "Invalid user", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                val request = UserStoriesRequest(auth_token = authToken, user_id = userId)
                val response = RetrofitClient.apiService.getUserStories(request)
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    if (data != null && data.stories.isNotEmpty()) {
                        storyList = data.stories.toMutableList()
                        usernameText.text = data.username
                        createProgressBars()
                        displayStory(0)
                    } else {
                        Toast.makeText(this@storyviewer, "No stories available", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    Toast.makeText(this@storyviewer, response.body()?.message ?: "Failed to load stories", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e("StoryViewer", "Error: ${e.message}", e)
                Toast.makeText(this@storyviewer, "Network error", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun createProgressBars() {
        progressBarsContainer.removeAllViews()
        progressAnimators.clear()
        for (i in storyList.indices) {
            val progressBar = View(this)
            val params = LinearLayout.LayoutParams(0, 4, 1f)
            params.setMargins(2, 0, 2, 0)
            progressBar.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            progressBarsContainer.addView(progressBar, params)
        }
    }

    private fun displayStory(index: Int) {
        if (index !in storyList.indices) return
        currentIndex = index
        val story = storyList[index]

        try {
            val imageBytes = Base64.decode(story.image_base64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            storyImage.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Log.e("StoryViewer", "Error decoding image: ${e.message}")
            storyImage.setImageResource(R.drawable.placeholder_error)
        }

        storyCountText.text = "${currentIndex + 1} / ${storyList.size}"
        storyTimestamp.text = getTimeAgo(story.timestamp)
        updateProgressBars()
        startProgressAnimation()
    }

    private fun updateProgressBars() {
        for (i in 0 until progressBarsContainer.childCount) {
            val progressBar = progressBarsContainer.getChildAt(i)
            when {
                i < currentIndex -> progressBar.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white))
                i == currentIndex -> progressBar.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white))
                else -> progressBar.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            }
        }
    }

    private fun startProgressAnimation() {
        stopCurrentProgress()
        val currentProgressBar = progressBarsContainer.getChildAt(currentIndex)
        currentProgressBar?.let {
            val animator = ObjectAnimator.ofFloat(it, "scaleX", 0f, 1f)
            animator.duration = STORY_DURATION
            animator.start()
            progressAnimators.add(animator)

            it.postDelayed({
                if (currentIndex < storyList.size - 1) displayStory(currentIndex + 1)
                else goBackToHomepage()
            }, STORY_DURATION)
        }
    }

    private fun stopCurrentProgress() {
        progressAnimators.forEach { it.cancel() }
        progressAnimators.clear()
    }

    private fun getTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffInSeconds = (now - timestamp) / 1000
        return when {
            diffInSeconds < 60 -> "Just now"
            diffInSeconds < 3600 -> "${diffInSeconds / 60}m ago"
            diffInSeconds < 86400 -> "${diffInSeconds / 3600}h ago"
            diffInSeconds < 604800 -> "${diffInSeconds / 86400}d ago"
            else -> java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        }
    }

    override fun onPause() {
        super.onPause()
        stopCurrentProgress()
    }

    override fun onResume() {
        super.onResume()
        if (storyList.isNotEmpty()) startProgressAnimation()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCurrentProgress()
    }
}
