package com.FEdev.i221279_i220809

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
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

class activityyou : AppCompatActivity() {

    private lateinit var followRequestsContainer: LinearLayout
    private lateinit var followRequestsHeader: TextView
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_activityyou)

        Log.d("ActivityYou", "onCreate")

        sessionManager = SessionManager(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views
        followRequestsContainer = findViewById(R.id.followRequestsContainer)
        followRequestsHeader = findViewById(R.id.followRequestsHeader)

        // Load follow requests
        loadFollowRequests()

        // Setup navigation
        setupNavigation()
    }

    private fun loadFollowRequests() {
        val authToken = sessionManager.getAuthToken()

        if (authToken == null) {
            Log.e("ActivityYou", "Not logged in")
            showNoRequests("Please login to see follow requests")
            return
        }

        Log.d("ActivityYou", "Loading follow requests with token: ${authToken.take(10)}...")

        lifecycleScope.launch {
            try {
                val request = GetFollowRequestsRequest(authToken)
                val response = RetrofitClient.apiService.getFollowRequests(request)

                Log.d("ActivityYou", "Response code: ${response.code()}")

                // Log raw response for debugging
                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string()
                    Log.e("ActivityYou", "Error response: $errorBody")
                    runOnUiThread {
                        showNoRequests("Server error: ${response.code()}")
                    }
                    return@launch
                }

                val body = response.body()
                Log.d("ActivityYou", "Response body: $body")

                if (body?.success == true) {
                    val requestsData = body.data
                    val requestsList = requestsData?.requests ?: emptyList()

                    Log.d("ActivityYou", "✅ Loaded ${requestsList.size} requests")

                    runOnUiThread {
                        displayFollowRequests(requestsList)
                    }
                } else {
                    Log.e("ActivityYou", "❌ Failed: ${body?.message}")
                    runOnUiThread {
                        showNoRequests(body?.message ?: "Failed to load requests")
                    }
                }
            } catch (e: com.google.gson.JsonSyntaxException) {
                Log.e("ActivityYou", "❌ JSON Parse Error: ${e.message}", e)
                runOnUiThread {
                    showNoRequests("Server returned invalid data")
                }
            } catch (e: Exception) {
                Log.e("ActivityYou", "❌ Exception: ${e.message}", e)
                e.printStackTrace()
                runOnUiThread {
                    showNoRequests("Error: ${e.message}")
                }
            }
        }
    }

    private fun displayFollowRequests(requests: List<FollowRequestItem>) {
        followRequestsContainer.removeAllViews()

        if (requests.isEmpty()) {
            showNoRequests("No pending follow requests")
            return
        }

        followRequestsHeader.text = "Follow Requests (${requests.size})"
        followRequestsHeader.visibility = View.VISIBLE

        requests.forEach { request ->
            val requestView = createFollowRequestView(request)
            followRequestsContainer.addView(requestView)
        }
    }

    private fun showNoRequests(message: String) {
        followRequestsHeader.text = "Follow Requests"
        followRequestsHeader.visibility = View.VISIBLE
        followRequestsContainer.removeAllViews()

        val emptyView = TextView(this).apply {
            text = message
            textSize = 14f
            setPadding(32, 32, 32, 32)
            setTextColor(resources.getColor(android.R.color.darker_gray, null))
        }
        followRequestsContainer.addView(emptyView)
    }

    private fun createFollowRequestView(request: FollowRequestItem): View {
        val view = LayoutInflater.from(this).inflate(
            R.layout.item_follow_request_inline,
            followRequestsContainer,
            false
        )

        val profileImage = view.findViewById<CircleImageView>(R.id.requestProfileImage)
        val username = view.findViewById<TextView>(R.id.requestUsername)
        val acceptBtn = view.findViewById<Button>(R.id.acceptButton)
        val rejectBtn = view.findViewById<Button>(R.id.rejectButton)

        // Set data
        username.text = "${request.username} wants to follow you"
        profileImage.setImageResource(R.drawable.mystory)

        // Calculate time ago
        val timeAgo = getTimeAgo(request.created_at)
        val timestampText = view.findViewById<TextView>(R.id.requestTimestamp)
        timestampText?.text = timeAgo

        // Accept button
        acceptBtn.setOnClickListener {
            acceptFollowRequest(request)
        }

        // Reject button
        rejectBtn.setOnClickListener {
            rejectFollowRequest(request)
        }

        // Click on profile to view
        profileImage.setOnClickListener {
            openUserProfile(request.follower_id, request.username)
        }

        username.setOnClickListener {
            openUserProfile(request.follower_id, request.username)
        }

        return view
    }

    private fun acceptFollowRequest(request: FollowRequestItem) {
        val authToken = sessionManager.getAuthToken() ?: return

        lifecycleScope.launch {
            try {
                val acceptRequest = AcceptFollowRequestRequest(authToken, request.request_id)
                val response = RetrofitClient.apiService.acceptFollowRequest(acceptRequest)

                if (response.isSuccessful && response.body()?.success == true) {
                    runOnUiThread {
                        Toast.makeText(
                            this@activityyou,
                            "Accepted ${request.username}'s request",
                            Toast.LENGTH_SHORT
                        ).show()
                        loadFollowRequests() // Reload list
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@activityyou,
                            "Failed to accept request",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ActivityYou", "Error accepting request: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(
                        this@activityyou,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun rejectFollowRequest(request: FollowRequestItem) {
        val authToken = sessionManager.getAuthToken() ?: return

        lifecycleScope.launch {
            try {
                val rejectRequest = RejectFollowRequestRequest(authToken, request.request_id)
                val response = RetrofitClient.apiService.rejectFollowRequest(rejectRequest)

                if (response.isSuccessful && response.body()?.success == true) {
                    runOnUiThread {
                        Toast.makeText(
                            this@activityyou,
                            "Rejected ${request.username}'s request",
                            Toast.LENGTH_SHORT
                        ).show()
                        loadFollowRequests() // Reload list
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@activityyou,
                            "Failed to reject request",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ActivityYou", "Error rejecting request: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(
                        this@activityyou,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun openUserProfile(userId: Int, username: String) {
        val intent = Intent(this, activityprofile2::class.java)
        intent.putExtra("USER_ID", userId)
        intent.putExtra("USERNAME", username)
        startActivity(intent)
    }

    private fun getTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis() / 1000
        val diff = now - timestamp

        return when {
            diff < 60 -> "Just now"
            diff < 3600 -> "${diff / 60}m ago"
            diff < 86400 -> "${diff / 3600}h ago"
            diff < 604800 -> "${diff / 86400}d ago"
            else -> "${diff / 604800}w ago"
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

        findViewById<TextView>(R.id.tabYou).setOnClickListener {
            // Already on this page
        }

        findViewById<TextView>(R.id.tabFollowing).setOnClickListener {
            startActivity(Intent(this, activitypage::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }

        findViewById<ImageView>(R.id.add).setOnClickListener {
            startActivity(Intent(this, selectpicture::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload requests when returning to this activity
        loadFollowRequests()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ActivityYou", "onDestroy")
    }
}