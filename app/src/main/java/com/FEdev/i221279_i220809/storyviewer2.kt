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
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.models.MyStoriesRequest
import com.FEdev.i221279_i220809.models.Story
import com.FEdev.i221279_i220809.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class storyviewer2 : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var storyImage: ImageView
    private lateinit var backButton: ImageView
    private lateinit var storyCountText: TextView
    private lateinit var storyTimestamp: TextView
    private lateinit var progressBarsContainer: LinearLayout

    private var storyList: MutableList<Story> = mutableListOf()
    private var currentIndex = 0
    private var progressAnimators: MutableList<ObjectAnimator> = mutableListOf()

    companion object {
        private const val STORY_DURATION = 5000L // 5 seconds
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_storyviewer2)

        Log.d("StoryViewer2", "onCreate")

        sessionManager = SessionManager(this)

        // Initialize views
        storyImage = findViewById(R.id.storyImage)
        backButton = findViewById(R.id.back)
        storyCountText = findViewById(R.id.storyCount)
        storyTimestamp = findViewById(R.id.storyTimestamp)
        progressBarsContainer = findViewById(R.id.progressBarsContainer)

        // Tap to next story
        storyImage.setOnClickListener {
            stopCurrentProgress()
            if (currentIndex < storyList.size - 1) {
                displayStory(currentIndex + 1)
            } else {
                Toast.makeText(this, "Last story", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        // Back button
        backButton.setOnClickListener {
            finish()
        }

        // Load my stories
        loadMyStories()
    }

    private fun loadMyStories() {
        val authToken = sessionManager.getAuthToken()

        if (authToken == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                val request = MyStoriesRequest(auth_token = authToken)
                val response = RetrofitClient.apiService.getMyStories(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data

                    if (data != null && data.stories.isNotEmpty()) {
                        storyList = data.stories.toMutableList()
                        Log.d("StoryViewer2", "✅ Loaded ${storyList.size} stories")

                        // Create progress bars
                        createProgressBars()

                        // Show first story
                        displayStory(0)
                    } else {
                        Toast.makeText(
                            this@storyviewer2,
                            "No stories available",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                } else {
                    Toast.makeText(
                        this@storyviewer2,
                        response.body()?.message ?: "Failed to load stories",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e("StoryViewer2", "❌ Error: ${e.message}", e)
                Toast.makeText(
                    this@storyviewer2,
                    "Network error",
                    Toast.LENGTH_SHORT
                ).show()
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
            progressBar.setBackgroundColor(
                ContextCompat.getColor(this, android.R.color.darker_gray)
            )
            progressBarsContainer.addView(progressBar, params)
        }
    }

    private fun displayStory(index: Int) {
        if (index < 0 || index >= storyList.size) return

        currentIndex = index
        val story = storyList[index]

        // Display image
        try {
            val imageBytes = Base64.decode(story.image_base64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            if (bitmap != null) {
                storyImage.setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            Log.e("StoryViewer2", "Error decoding image: ${e.message}")
        }

        // Update story count
        storyCountText.text = "${currentIndex + 1} / ${storyList.size}"

        // Update timestamp
        storyTimestamp.text = getTimeAgo(story.timestamp)

        // Update progress bars
        updateProgressBars()

        // Start progress animation
        startProgressAnimation()
    }

    private fun updateProgressBars() {
        for (i in 0 until progressBarsContainer.childCount) {
            val progressBar = progressBarsContainer.getChildAt(i)
            when {
                i < currentIndex -> progressBar.setBackgroundColor(
                    ContextCompat.getColor(this, android.R.color.white)
                )
                i == currentIndex -> progressBar.setBackgroundColor(
                    ContextCompat.getColor(this, android.R.color.white)
                )
                else -> progressBar.setBackgroundColor(
                    ContextCompat.getColor(this, android.R.color.darker_gray)
                )
            }
        }
    }

    private fun startProgressAnimation() {
        stopCurrentProgress()

        val currentProgressBar = progressBarsContainer.getChildAt(currentIndex)
        if (currentProgressBar != null) {
            val animator = ObjectAnimator.ofFloat(currentProgressBar, "scaleX", 0f, 1f)
            animator.duration = STORY_DURATION
            animator.start()

            progressAnimators.add(animator)

            currentProgressBar.postDelayed({
                if (currentIndex < storyList.size - 1) {
                    displayStory(currentIndex + 1)
                } else {
                    finish()
                }
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
            else -> "${diffInSeconds / 86400}d ago"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCurrentProgress()
    }

    override fun onPause() {
        super.onPause()
        stopCurrentProgress()
    }

    override fun onResume() {
        super.onResume()
        if (storyList.isNotEmpty()) {
            startProgressAnimation()
        }
    }
}