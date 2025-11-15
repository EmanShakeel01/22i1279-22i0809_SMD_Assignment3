//package com.FEdev.i221279_i220809
//import android.content.Intent
//import android.os.Bundle
//import android.util.Log
//import android.widget.ImageView
//import android.widget.TextView
//import androidx.activity.enableEdgeToEdge
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.view.ViewCompat
//import androidx.core.view.WindowInsetsCompat
//import de.hdodenhof.circleimageview.CircleImageView
//
//class activityyou : AppCompatActivity() {
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        setContentView(R.layout.activity_activityyou)
//        Log.d("ActivityStack", "Activityyoy onCreate")
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
//        val homeNav = findViewById<ImageView>(R.id.nav_home)
//        homeNav.setOnClickListener {
//            val intent = Intent(this, homepage::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//            startActivity(intent)
//        }
//
//        val likeNav = findViewById<ImageView>(R.id.nav_like)
//        likeNav.setOnClickListener {
//            val intent = Intent(this, activitypage::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//            startActivity(intent)
//        }
//        val searchNav = findViewById<ImageView>(R.id.nav_search)
//        searchNav.setOnClickListener {
//            val intent = Intent(this, searchpage::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//            startActivity(intent)
//        }
//        val profileNav = findViewById< CircleImageView>(R.id.nav_profile)
//        profileNav.setOnClickListener {
//            val intent = Intent(this, activityprofile::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//            startActivity(intent)
//        }
//
//        val you = findViewById<TextView>(R.id.tabYou)
//        you.setOnClickListener {
//            val intent = Intent(this, activityyou::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//            startActivity(intent)
//        }
//        val following= findViewById<TextView>(R.id.tabFollowing)
//        following.setOnClickListener {
//            val intent = Intent(this, activitypage::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//            startActivity(intent)
//        }
//        val addpic = findViewById< ImageView>(R.id.add)
//        addpic.setOnClickListener {
//            val intent = Intent(this, selectpicture::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//            startActivity(intent)
//        }
//
//
//
//    }
//
//
//    override fun onDestroy() {
//        super.onDestroy()
//        Log.d("ActivityStack", "Activityyou onDestroy")
//    }
//}

package com.FEdev.i221279_i220809

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import de.hdodenhof.circleimageview.CircleImageView

class activityyou : AppCompatActivity() {

    private lateinit var followRequestsContainer: LinearLayout
    private lateinit var followRequestsHeader: TextView
    private lateinit var emptyRequestsText: TextView
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_activityyou)

        Log.d("ActivityStack", "Activityyou onCreate")

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize follow requests views
        followRequestsContainer = findViewById(R.id.followRequestsContainer)
        followRequestsHeader = findViewById(R.id.followRequestsHeader)

        // Load follow requests
        loadFollowRequests()

        // Bottom Navigation
        val homeNav = findViewById<ImageView>(R.id.nav_home)
        homeNav.setOnClickListener {
            val intent = Intent(this, homepage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
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

        val you = findViewById<TextView>(R.id.tabYou)
        you.setOnClickListener {
            val intent = Intent(this, activityyou::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        val following = findViewById<TextView>(R.id.tabFollowing)
        following.setOnClickListener {
            val intent = Intent(this, activitypage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        val addpic = findViewById<ImageView>(R.id.add)
        addpic.setOnClickListener {
            val intent = Intent(this, selectpicture::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }
    }

    private fun loadFollowRequests() {
        val currentUserId = auth.currentUser?.uid

        if (currentUserId == null) {
            Log.e("ActivityYou", "User not logged in")
            followRequestsHeader.visibility = View.GONE
            return
        }

        FollowRequestManager.getPendingRequests(currentUserId) { requests ->
            runOnUiThread {
                followRequestsContainer.removeAllViews()

                if (requests.isEmpty()) {
                    followRequestsHeader.text = "Follow Requests"

                    // Show "No requests" message
                    val emptyView = TextView(this).apply {
                        text = "No pending follow requests"
                        textSize = 14f
                        setPadding(16, 16, 16, 16)
                        setTextColor(resources.getColor(android.R.color.darker_gray, null))
                    }
                    followRequestsContainer.addView(emptyView)
                } else {
                    followRequestsHeader.text = "Follow Requests (${requests.size})"

                    // Add each follow request
                    requests.forEach { request ->
                        val requestView = createFollowRequestView(request, currentUserId)
                        followRequestsContainer.addView(requestView)
                    }
                }
            }
        }
    }

    private fun createFollowRequestView(request: FollowRequest, currentUserId: String): View {
        val view = LayoutInflater.from(this).inflate(R.layout.item_follow_request_inline, null)

        val profileImage = view.findViewById<CircleImageView>(R.id.requestProfileImage)
        val username = view.findViewById<TextView>(R.id.requestUsername)
        val acceptBtn = view.findViewById<Button>(R.id.acceptButton)
        val rejectBtn = view.findViewById<Button>(R.id.rejectButton)

        // Set data
        username.text = "${request.fromUsername} wants to follow you"
        profileImage.setImageResource(R.drawable.mystory) // Default profile image

        // Accept button
        acceptBtn.setOnClickListener {
            acceptFollowRequest(request, currentUserId)
        }

        // Reject button
        rejectBtn.setOnClickListener {
            rejectFollowRequest(request, currentUserId)
        }

        return view
    }

    private fun acceptFollowRequest(request: FollowRequest, currentUserId: String) {
        FollowRequestManager.acceptFollowRequest(
            request.requestId,
            request.fromUserId,
            currentUserId,
            onSuccess = {
                runOnUiThread {
                    Toast.makeText(this, "Accepted ${request.fromUsername}'s request", Toast.LENGTH_SHORT).show()
                    loadFollowRequests() // Reload the list
                }
            },
            onFailure = { error ->
                runOnUiThread {
                    Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun rejectFollowRequest(request: FollowRequest, currentUserId: String) {
        FollowRequestManager.rejectFollowRequest(
            request.requestId,
            currentUserId,
            onSuccess = {
                runOnUiThread {
                    Toast.makeText(this, "Rejected ${request.fromUsername}'s request", Toast.LENGTH_SHORT).show()
                    loadFollowRequests() // Reload the list
                }
            },
            onFailure = { error ->
                runOnUiThread {
                    Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ActivityStack", "Activityyou onDestroy")
    }

    override fun onResume() {
        super.onResume()
        // Reload requests when returning to this activity
        loadFollowRequests()
    }
}