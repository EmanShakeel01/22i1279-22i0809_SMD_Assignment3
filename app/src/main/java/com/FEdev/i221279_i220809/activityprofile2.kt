package com.FEdev.i221279_i220809

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.FEdev.i221279_i220809.models.*
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.utils.SessionManager
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.launch

class activityprofile2 : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var usernameText: TextView
    private lateinit var fullnameText: TextView
    private lateinit var bioText: TextView
    private lateinit var profileImage: CircleImageView

    private lateinit var sessionManager: SessionManager
    private lateinit var followBtn: Button
    private lateinit var msgBtn: Button

    private var targetUserId: Int = 0
    private var targetUsername: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.myprofilepage2)

        Log.d("ActivityProfile2", "=== onCreate START ===")

        sessionManager = SessionManager(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()

        // Get user data from intent
        targetUserId = intent.getIntExtra("USER_ID", 0)
        targetUsername = intent.getStringExtra("USERNAME") ?: ""

        Log.d("ActivityProfile2", "Target User ID: $targetUserId")
        Log.d("ActivityProfile2", "Target Username: $targetUsername")

        if (targetUserId > 0) {
            // Display username immediately
            if (targetUsername.isNotEmpty()) {
                usernameText.text = targetUsername
                fullnameText.text = targetUsername
            }

            // Load full profile from API
            loadUserProfileFromWebService(targetUserId)

            // Setup buttons
            setupButtons()
        } else {
            Log.e("ActivityProfile2", "❌ No valid user ID provided!")
            usernameText.text = "Unknown User"
            fullnameText.text = "No data available"
            Toast.makeText(this, "Invalid user data", Toast.LENGTH_SHORT).show()
        }

        setupNavigation()
    }

    private fun initViews() {
        try {
            statusText = findViewById(R.id.statusText)
            statusDot = findViewById(R.id.statusDot)
            usernameText = findViewById(R.id.username)
            fullnameText = findViewById(R.id.fullnameText)
            bioText = findViewById(R.id.bioText)
            profileImage = findViewById(R.id.myprofileimage)

            followBtn = findViewById(R.id.follow)
            msgBtn = findViewById(R.id.msg)

            statusText.visibility = View.GONE
            statusDot.visibility = View.GONE

            Log.d("ActivityProfile2", "✅ All views initialized")
        } catch (e: Exception) {
            Log.e("ActivityProfile2", "❌ Error initializing views: ${e.message}", e)
        }
    }

    private fun setupButtons() {
        // Message button
        msgBtn.setOnClickListener {
            if (targetUserId > 0) {
                Log.d("ActivityProfile2", "Opening chat for: $targetUsername")
                try {
                    val intent = Intent(this, ChatInboxActivity::class.java)
                    intent.putExtra("targetUserId", targetUserId)
                    intent.putExtra("targetName", targetUsername)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Chat not available", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Cannot start chat", Toast.LENGTH_SHORT).show()
            }
        }

        // Follow button
        followBtn.visibility = View.VISIBLE
        followBtn.text = "Follow"
        followBtn.setBackgroundColor(getColor(R.color.pink))
        followBtn.setOnClickListener {
            handleFollowClick()
        }

        // Check current follow status
        checkFollowStatus()
    }

    private fun handleFollowClick() {
        val currentButtonText = followBtn.text.toString()

        when (currentButtonText) {
            "Follow" -> sendFollowRequest()
            "Requested" -> Toast.makeText(this, "Request already sent", Toast.LENGTH_SHORT).show()
            "Following" -> Toast.makeText(this, "Unfollow feature coming soon", Toast.LENGTH_SHORT).show()
            else -> sendFollowRequest()
        }
    }

    // ---------------------------
    // API: Send follow request
    // ---------------------------
    private fun sendFollowRequest() {
        val authToken = sessionManager.getAuthToken()

        if (authToken == null) {
            Toast.makeText(this, "Please login", Toast.LENGTH_SHORT).show()
            return
        }

        if (targetUserId <= 0) {
            Toast.makeText(this, "Invalid user", Toast.LENGTH_SHORT).show()
            return
        }

        followBtn.isEnabled = false

        lifecycleScope.launch {
            try {
                val request = SendFollowRequestRequest(
                    auth_token = authToken,
                    target_user_id = targetUserId
                )

                Log.d("ActivityProfile2", "Sending follow request to user: $targetUserId")

                val response = RetrofitClient.apiService.sendFollowRequest(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    runOnUiThread {
                        followBtn.text = "Requested"
                        followBtn.setBackgroundColor(getColor(android.R.color.darker_gray))
                        followBtn.isEnabled = true
                        Toast.makeText(
                            this@activityprofile2,
                            "Follow request sent to $targetUsername",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    Log.d("ActivityProfile2", "✅ Follow request sent successfully")
                } else {
                    val errorMsg = response.body()?.message ?: "Failed to send request"
                    Log.e("ActivityProfile2", "❌ Follow request failed: $errorMsg")
                    runOnUiThread {
                        followBtn.isEnabled = true
                        followBtn.text = "Follow"
                        Toast.makeText(this@activityprofile2, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ActivityProfile2", "❌ Error sending follow request: ${e.message}", e)
                runOnUiThread {
                    followBtn.isEnabled = true
                    followBtn.text = "Follow"
                    Toast.makeText(this@activityprofile2, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ---------------------------
    // API: Check follow status
    // ---------------------------
    private fun checkFollowStatus() {
        val authToken = sessionManager.getAuthToken()

        if (authToken == null || targetUserId <= 0) {
            followBtn.text = "Follow"
            followBtn.setBackgroundColor(getColor(R.color.pink))
            return
        }

        lifecycleScope.launch {
            try {
                val request = CheckFollowStatusRequest(
                    auth_token = authToken,
                    target_user_id = targetUserId
                )

                val response = RetrofitClient.apiService.checkFollowStatus(request)

                if (response.isSuccessful) {
                    val body = response.body()

                    if (body != null && body.success) {
                        val followStatus = body.data?.status ?: "none"

                        runOnUiThread {
                            when (followStatus) {
                                "accepted" -> {
                                    followBtn.text = "Following"
                                    followBtn.setBackgroundColor(getColor(android.R.color.darker_gray))
                                }
                                "pending" -> {
                                    followBtn.text = "Requested"
                                    followBtn.setBackgroundColor(getColor(android.R.color.darker_gray))
                                }
                                else -> {
                                    followBtn.text = "Follow"
                                    followBtn.setBackgroundColor(getColor(R.color.pink))
                                }
                            }
                        }
                    } else {
                        runOnUiThread {
                            followBtn.text = "Follow"
                            followBtn.setBackgroundColor(getColor(R.color.pink))
                        }
                    }
                } else {
                    runOnUiThread {
                        followBtn.text = "Follow"
                        followBtn.setBackgroundColor(getColor(R.color.pink))
                    }
                }

            } catch (e: Exception) {
                Log.e("ActivityProfile2", "Error checking follow status: ${e.message}", e)
                runOnUiThread {
                    followBtn.text = "Follow"
                    followBtn.setBackgroundColor(getColor(R.color.pink))
                }
            }
        }
    }

    // ---------------------------
    // API: Load user profile
    // ---------------------------
    private fun loadUserProfileFromWebService(userId: Int) {
        Log.d("ActivityProfile2", "📡 Loading profile from API...")

        val authToken = sessionManager.getAuthToken()

        if (authToken == null) {
            Log.e("ActivityProfile2", "❌ No auth token")
            showError("Not logged in")
            return
        }

        lifecycleScope.launch {
            try {
                val request = GetUserProfileRequest(auth_token = authToken, user_id = userId)

                Log.d("ActivityProfile2", "Calling API with user_id=$userId")

                val response = RetrofitClient.apiService.getUserProfile(request)

                Log.d("ActivityProfile2", "Response code: ${response.code()}")

                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string() ?: "No error body"
                    Log.e("ActivityProfile2", "Error body: $errorBody")
                    runOnUiThread { showError("Server error: ${response.code()}") }
                    return@launch
                }

                val apiResponse = response.body()

                if (apiResponse == null || !apiResponse.success || apiResponse.data == null) {
                    runOnUiThread { showError("Failed to load profile") }
                    return@launch
                }

                val user = apiResponse.data.user
                Log.d("ActivityProfile2", "✅ Got user: ${user.username}")

                runOnUiThread { displayUserProfile(user) }

            } catch (e: Exception) {
                Log.e("ActivityProfile2", "❌ Exception: ${e.message}", e)
                runOnUiThread { showError("Error: ${e.javaClass.simpleName}") }
            }
        }
    }

    private fun displayUserProfile(user: UserProfile) {
        Log.d("ActivityProfile2", "📝 Displaying profile: ${user.username}")
        try {
            usernameText.text = user.username
            fullnameText.text = user.fullname ?: user.username
            bioText.text = user.bio ?: "No bio yet"
            targetUsername = user.username

            statusText.text = user.email
            statusText.visibility = View.VISIBLE
            statusDot.visibility = View.GONE

            Toast.makeText(this, "Profile loaded", Toast.LENGTH_SHORT).show()
            Log.d("ActivityProfile2", "✅ Profile displayed successfully")
        } catch (e: Exception) {
            Log.e("ActivityProfile2", "❌ Error displaying: ${e.message}", e)
            showError("Error displaying profile")
        }
    }

    private fun showError(message: String) {
        Log.e("ActivityProfile2", "Showing error: $message")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        bioText.text = message
        statusText.text = "Error loading profile"
        statusText.visibility = View.VISIBLE
    }

    private fun setupNavigation() {
        findViewById<ImageView>(R.id.nav_home).setOnClickListener {
            startActivity(Intent(this, homepage::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }

        findViewById<ImageView>(R.id.nav_like).setOnClickListener {
            startActivity(Intent(this, activitypage::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }

        findViewById<ImageView>(R.id.nav_search).setOnClickListener {
            startActivity(Intent(this, searchpage::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }

        findViewById<CircleImageView>(R.id.nav_profile).setOnClickListener {
            startActivity(Intent(this, activityprofile::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }

        findViewById<ImageView>(R.id.add).setOnClickListener {
            startActivity(Intent(this, selectpicture::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }

        findViewById<CircleImageView>(R.id.highlight1).setOnClickListener {
            startActivity(Intent(this, highlights::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ActivityProfile2", "=== onDestroy ===")
    }
}