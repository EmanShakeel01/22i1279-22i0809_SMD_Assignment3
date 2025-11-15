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
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class storyviewer2 : AppCompatActivity() {

    private lateinit var storyImage: ImageView
    private lateinit var backButton: ImageView
    private lateinit var leftTapArea: View
    private lateinit var rightTapArea: View
    private lateinit var storyCountText: TextView
    private lateinit var storyTimestamp: TextView
    private lateinit var progressBarsContainer: LinearLayout

    private var storyList: MutableList<Pair<String, Map<String, Any>>> = mutableListOf()
    private var currentIndex = 0
    private val currentTime = System.currentTimeMillis()
    private var progressAnimators: MutableList<ObjectAnimator> = mutableListOf()

    companion object {
        private const val STORY_DURATION = 5000L // 5 seconds per story
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_storyviewer2)

        Log.d("ActivityStack", "StoryViewer2 onCreate")

        val mainLayout = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views
        storyImage = findViewById(R.id.storyImage)
        backButton = findViewById(R.id.back)
        leftTapArea = findViewById(R.id.leftTapArea)
        rightTapArea = findViewById(R.id.rightTapArea)
        storyCountText = findViewById(R.id.storyCount)
        storyTimestamp = findViewById(R.id.storyTimestamp)
        progressBarsContainer = findViewById(R.id.progressBarsContainer)

        // ✅ Tap anywhere on the story image → move to next story
        storyImage.setOnClickListener {
            stopCurrentProgress()
            if (currentIndex < storyList.size - 1) {
                displayStory(currentIndex + 1)
            } else {
                Toast.makeText(this, "Last story", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, homepage::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            }
        }

        // Back button - go to homepage
        backButton.setOnClickListener {
            val intent = Intent(this, homepage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        // Left tap area - previous story
        leftTapArea.setOnClickListener {
            stopCurrentProgress()
            if (currentIndex > 0) {
                displayStory(currentIndex - 1)
            } else {
                Toast.makeText(this, "First story", Toast.LENGTH_SHORT).show()
            }
        }

        // Right tap area - next story
        rightTapArea.setOnClickListener {
            stopCurrentProgress()
            if (currentIndex < storyList.size - 1) {
                displayStory(currentIndex + 1)
            } else {
                // Last story - go back to homepage
                Toast.makeText(this, "Last story", Toast.LENGTH_SHORT).show()
            }
        }

        // Load all stories
        loadAllStories()
    }

    // ✅ Load ALL stories for current user
    private fun loadAllStories() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Log.e("StoryViewer2", "User not logged in ❌")
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val dbRef = FirebaseDatabase.getInstance().getReference("Stories").child(userId)

        dbRef.get().addOnSuccessListener { snapshot ->
            storyList.clear()

            if (!snapshot.exists()) {
                Log.d("StoryViewer2", "No stories found")
                Toast.makeText(this, "No stories available", Toast.LENGTH_SHORT).show()
                finish()
                return@addOnSuccessListener
            }

            // Fetch all non-expired stories
            for (storySnap in snapshot.children) {
                val expiresAt = storySnap.child("expiresAt").getValue(Long::class.java) ?: 0L

                // Only add non-expired stories
                if (currentTime <= expiresAt) {
                    val storyData = mutableMapOf<String, Any>()
                    storySnap.children.forEach { child ->
                        storyData[child.key ?: ""] = child.value ?: ""
                    }
                    storyList.add(Pair(storySnap.key ?: "", storyData))
                } else {
                    // Delete expired story
                    storySnap.ref.removeValue()
                    Log.d("StoryViewer2", "🗑️ Deleted expired story: ${storySnap.key}")
                }
            }

            // Sort by timestamp (newest first)
            storyList.sortByDescending { (_, data) ->
                (data["timestamp"] as? Long) ?: 0L
            }

            if (storyList.isEmpty()) {
                Toast.makeText(this, "No valid stories", Toast.LENGTH_SHORT).show()
                finish()
                return@addOnSuccessListener
            }

            // Create progress bars
            createProgressBars()

            // Show first story
            currentIndex = 0
            displayStory(0)

        }.addOnFailureListener { e ->
            Log.e("StoryViewer2", "Failed to load stories: ${e.message}")
            Toast.makeText(this, "Failed to load stories", Toast.LENGTH_SHORT).show()
        }
    }

    // ✅ Create progress bars for each story
    private fun createProgressBars() {
        progressBarsContainer.removeAllViews()
        progressAnimators.clear()

        for (i in storyList.indices) {
            val progressBar = View(this)
            val params = LinearLayout.LayoutParams(0, 4, 1f)
            params.setMargins(2, 0, 2, 0)

            // Set background color
            if (i < currentIndex) {
                progressBar.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white))
            } else {
                progressBar.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            }

            progressBarsContainer.addView(progressBar, params)
        }
    }

    // ✅ Display story at given index
    private fun displayStory(index: Int) {
        if (index < 0 || index >= storyList.size) return

        currentIndex = index
        val (storyId, storyData) = storyList[index]
        val base64Image = storyData["imageBase64"] as? String

        if (base64Image != null) {
            try {
                val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bitmap != null) {
                    storyImage.setImageBitmap(bitmap)
                } else {
                    storyImage.setImageResource(R.drawable.placeholder_error)
                }
            } catch (e: Exception) {
                Log.e("StoryViewer2", "Error decoding image: ${e.message}")
                storyImage.setImageResource(R.drawable.placeholder_error)
            }
        }

        // Update story count
        storyCountText.text = "${currentIndex + 1} / ${storyList.size}"

        // Update timestamp
        val timestamp = storyData["timestamp"] as? Long ?: 0L
        storyTimestamp.text = getTimeAgo(timestamp)

        // Update progress bars
        updateProgressBars()

        // Start progress animation
        startProgressAnimation()

        Log.d("StoryViewer2", "Displaying story ${currentIndex + 1}/${storyList.size}")
    }

    // ✅ Update progress bar colors
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

    // ✅ Start progress animation for current story
    private fun startProgressAnimation() {
        stopCurrentProgress()

        val currentProgressBar = progressBarsContainer.getChildAt(currentIndex)
        if (currentProgressBar != null) {
            val animator = ObjectAnimator.ofFloat(currentProgressBar, "scaleX", 0f, 1f)
            animator.duration = STORY_DURATION
            animator.start()

            progressAnimators.add(animator)

            // Auto move to next story after duration
            currentProgressBar.postDelayed({
                if (currentIndex < storyList.size - 1) {
                    displayStory(currentIndex + 1)
                } else {
                    val intent = Intent(this, homepage::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                }
            }, STORY_DURATION)
        }
    }

    // ✅ Stop current progress animation
    private fun stopCurrentProgress() {
        progressAnimators.forEach { it.cancel() }
        progressAnimators.clear()
    }

    // ✅ Format timestamp to "time ago"
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
        Log.d("ActivityStack", "StoryViewer2 onDestroy")
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
