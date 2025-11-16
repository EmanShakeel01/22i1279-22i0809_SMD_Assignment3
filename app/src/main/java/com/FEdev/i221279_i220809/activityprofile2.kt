package com.FEdev.i221279_i220809

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.FEdev.i221279_i220809.models.CheckFollowStatusRequest
import com.FEdev.i221279_i220809.models.GetUserProfileRequest
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.utils.SessionManager
import com.google.firebase.auth.FirebaseAuth
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.launch

class activityprofile2 : AppCompatActivity() {

    private lateinit var statusText: android.widget.TextView
    private lateinit var statusDot: View
    private lateinit var usernameText: android.widget.TextView
    private lateinit var fullnameText: android.widget.TextView
    private lateinit var bioText: android.widget.TextView
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

        // Get intent data
        targetUserId = intent.getIntExtra("USER_ID", 0)
        targetUsername = intent.getStringExtra("USERNAME") ?: ""
        val userEmail = intent.getStringExtra("USER_EMAIL")
        val userUid = intent.getStringExtra("USER_UID")

        Log.d("ActivityProfile2", "Target User ID: $targetUserId")
        Log.d("ActivityProfile2", "Target Username: $targetUsername")

        if (targetUsername.isNotEmpty()) {
            usernameText.text = targetUsername
            fullnameText.text = targetUsername
        }

        setupNavigation()
        setupButtons()

        if (targetUserId > 0) {
            Log.d("ActivityProfile2", "Loading profile from web service...")
            loadUserProfileFromWebService(targetUserId)
        } else if (userEmail != null) {
            Log.d("ActivityProfile2", "Loading profile from Firebase...")
            loadUserProfileFromFirebase(userEmail)
            setupOnlineStatusListener(userEmail)
        } else {
            Log.e("ActivityProfile2", "❌ No valid user data provided!")
            usernameText.text = "Unknown User"
            fullnameText.text = "No data available"
            Toast.makeText(this, "Invalid user data", Toast.LENGTH_SHORT).show()
        }
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

        // Follow button behavior depends on whether this is a web-user or Firebase legacy user
        if (targetUserId > 0) {
            followBtn.visibility = View.VISIBLE
            followBtn.text = "Follow"
            followBtn.setBackgroundColor(getColor(R.color.pink))
            followBtn.setOnClickListener {
                handleFollowClick()
            }
            checkFollowStatus()
        } else {
            val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
            val userUid = intent.getStringExtra("USER_UID")
            if (currentUserUid != null && userUid != null) {
                updateFollowButton(currentUserUid, userUid)
            } else {
                followBtn.visibility = View.GONE
            }
        }
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
    // Network: send follow request
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
                val request = com.FEdev.i221279_i220809.models.SendFollowRequestRequest(
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
                    Toast.makeText(this@activityprofile2, "Error: ${e.message}", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    // ---------------------------
    // Network: check follow status
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

                        // NOW CORRECT (no more nested 'data.data')
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
                runOnUiThread {
                    followBtn.text = "Follow"
                    followBtn.setBackgroundColor(getColor(R.color.pink))
                }
            }
        }
    }


    // ---------------------------
    // Firebase legacy follow button setup
    // ---------------------------
    private fun updateFollowButton(currentUserUid: String, userUid: String) {
        if (currentUserUid == userUid) {
            followBtn.visibility = View.GONE
            return
        }

        followBtn.visibility = View.VISIBLE
        followBtn.isEnabled = true
        followBtn.text = "Follow"
        followBtn.setBackgroundColor(getColor(R.color.pink))

        // Optional: implement Firebase reads to update button state if you store follow info there.
    }

    // ---------------------------
    // Load profile from web
    // ---------------------------
    private fun loadUserProfileFromWebService(userId: Int) {
        Log.d("ActivityProfile2", "📡 Starting API request...")

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
                Log.d("ActivityProfile2", "Response successful: ${response.isSuccessful}")

                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string() ?: "No error body"
                    Log.e("ActivityProfile2", "Error body: $errorBody")
                    runOnUiThread { showError("Server error: ${response.code()}") }
                    return@launch
                }

                val apiResponse = response.body()
                Log.d("ActivityProfile2", "Response body: $apiResponse")

                if (apiResponse == null) {
                    runOnUiThread { showError("Empty response from server") }
                    return@launch
                }

                if (!apiResponse.success) {
                    runOnUiThread { showError(apiResponse.message) }
                    return@launch
                }

                if (apiResponse.data == null) {
                    runOnUiThread { showError("No user data returned") }
                    return@launch
                }

                val user = apiResponse.data.user
                Log.d("ActivityProfile2", "✅ Got user: ${user.username}")

                runOnUiThread { displayUserProfile(user) }

            } catch (e: com.google.gson.JsonSyntaxException) {
                Log.e("ActivityProfile2", "❌ JSON Parse Error: ${e.message}", e)
                runOnUiThread { showError("Server returned invalid data") }
            } catch (e: java.net.UnknownHostException) {
                Log.e("ActivityProfile2", "❌ Network Error: Cannot reach server", e)
                runOnUiThread { showError("Cannot connect to server") }
            } catch (e: Exception) {
                Log.e("ActivityProfile2", "❌ Exception: ${e.message}", e)
                runOnUiThread { showError("Error: ${e.javaClass.simpleName}") }
            }
        }
    }

    private fun displayUserProfile(user: com.FEdev.i221279_i220809.models.UserProfile) {
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

    // ---------------------------
    // Firebase fallback loader
    // ---------------------------
    private fun loadUserProfileFromFirebase(userEmail: String) {
        val database = com.google.firebase.database.FirebaseDatabase.getInstance()
        val userRef = database.reference.child("users").child(userEmail)

        userRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (snapshot.exists()) {
                    val username = snapshot.child("username").getValue(String::class.java)
                    usernameText.text = username ?: "Unknown User"
                    fullnameText.text = username ?: "Unknown User"
                    targetUsername = username ?: "Unknown User"
                    Log.d("ActivityProfile2", "✅ Firebase loaded: $username")
                } else {
                    showError("User not found in Firebase")
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e("ActivityProfile2", "❌ Firebase error: ${error.message}")
                showError("Firebase error: ${error.message}")
            }
        })
    }

    private fun setupOnlineStatusListener(userEmail: String) {
        OnlineStatusManager.listenToUserStatus(userEmail) { isOnline, lastSeen ->
            runOnUiThread {
                statusText.visibility = View.VISIBLE
                statusDot.visibility = View.VISIBLE
                if (isOnline) {
                    statusText.text = "Online"
                    statusText.setTextColor(getColor(android.R.color.holo_green_dark))
                    statusDot.setBackgroundResource(R.drawable.status_dot_online)
                } else {
                    val lastSeenText = TimeUtils.getLastSeenText(lastSeen)
                    statusText.text = lastSeenText
                    statusText.setTextColor(getColor(android.R.color.darker_gray))
                    statusDot.setBackgroundResource(R.drawable.status_dot_offline)
                }
            }
        }
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