//package com.FEdev.i221279_i220809
//
//import android.content.Intent
//import android.os.Bundle
//import android.util.Log
//import android.view.View
//import android.widget.Button
//import android.widget.ImageView
//import android.widget.TextView
//import androidx.activity.enableEdgeToEdge
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.view.ViewCompat
//import androidx.core.view.WindowInsetsCompat
//import de.hdodenhof.circleimageview.CircleImageView
//
//class activityprofile2 : AppCompatActivity() {
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        setContentView(R.layout.myprofilepage2)
//
//        Log.d("ActivityStack", "ActivityProfile2 onCreate")
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
//        val homeNav = findViewById<ImageView>(R.id.nav_home)
//        homeNav.setOnClickListener {
//            val intent = Intent(this, homepage::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//
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
//        val follow = findViewById<Button>(R.id.follow)
//        follow.setOnClickListener {
//            val intent = Intent(this, myprofilepage::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//            startActivity(intent)
//        }
//        val msg = findViewById<Button>(R.id.msg)
//        msg.setOnClickListener {
//            val intent = Intent(this, inbox::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//            startActivity(intent)
//        }
//        val addpic = findViewById< ImageView>(R.id.add)
//        addpic.setOnClickListener {
//            val intent = Intent(this, selectpicture::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//            startActivity(intent)
//        }
//        val highlight = findViewById< CircleImageView>(R.id.highlight1)
//        highlight.setOnClickListener {
//            val intent = Intent(this, highlights::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//            startActivity(intent)
//        }
//
//
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        Log.d("ActivityStack", "ActivityProfile2 onDestroy")
//    }
//}


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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import de.hdodenhof.circleimageview.CircleImageView

class activityprofile2 : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var usernameText: TextView
    private lateinit var fullnameText: TextView
    private lateinit var profileImage: CircleImageView

//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        setContentView(R.layout.myprofilepage2)
//
//        Log.d("ActivityStack", "ActivityProfile2 onCreate")
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
//
//        // Initialize views
//        statusText = findViewById(R.id.statusText)
//        statusDot = findViewById(R.id.statusDot)
//        usernameText = findViewById(R.id.username)
//        fullnameText = findViewById(R.id.fullnameText)
//        profileImage = findViewById(R.id.myprofileimage)
//
//        // Get user info from intent
//        val userEmail = intent.getStringExtra("USER_EMAIL")
//        val userUid = intent.getStringExtra("USER_UID")
//
//        Log.d("ActivityProfile2", "Received USER_EMAIL: $userEmail")
//        Log.d("ActivityProfile2", "Received USER_UID: $userUid")
//
//        if (userEmail != null) {
//            loadUserProfile(userEmail)
//            setupOnlineStatusListener(userEmail)
//        } else {
//            Log.e("ActivityProfile2", "No user email provided!")
//            statusText.text = "User not found"
//            statusDot.visibility = View.GONE
//        }
//
//        // Bottom Navigation
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
//
//        val searchNav = findViewById<ImageView>(R.id.nav_search)
//        searchNav.setOnClickListener {
//            val intent = Intent(this, searchpage::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//            startActivity(intent)
//        }
//
//        val profileNav = findViewById<CircleImageView>(R.id.nav_profile)
//        profileNav.setOnClickListener {
//            val intent = Intent(this, activityprofile::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//            startActivity(intent)
//        }
//
//
//        val follow = findViewById<Button>(R.id.follow)
//        follow.setOnClickListener {
//            val intent = Intent(this, myprofilepage::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//
//            // Pass the username and fullname to myprofilepage
//            intent.putExtra("USERNAME", usernameText.text.toString())
//            intent.putExtra("FULLNAME", fullnameText.text.toString())
//
//            startActivity(intent)
//        }
//
//        val msg = findViewById<Button>(R.id.msg)
//        msg.setOnClickListener {
//            val intent = Intent(this, inbox::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//            startActivity(intent)
//        }
//
//        val addpic = findViewById<ImageView>(R.id.add)
//        addpic.setOnClickListener {
//            val intent = Intent(this, selectpicture::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//            startActivity(intent)
//        }
//
//        val highlight = findViewById<CircleImageView>(R.id.highlight1)
//        highlight.setOnClickListener {
//            val intent = Intent(this, highlights::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//            startActivity(intent)
//        }
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.myprofilepage2)

        Log.d("ActivityStack", "ActivityProfile2 onCreate")
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views
        statusText = findViewById(R.id.statusText)
        statusDot = findViewById(R.id.statusDot)
        usernameText = findViewById(R.id.username)
        fullnameText = findViewById(R.id.fullnameText)
        profileImage = findViewById(R.id.myprofileimage)
        val follow = findViewById<Button>(R.id.follow)

        // Get user info from intent
        val userEmail = intent.getStringExtra("USER_EMAIL")
        val userUid = intent.getStringExtra("USER_UID")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        Log.d("ActivityProfile2", "Received USER_EMAIL: $userEmail")
        Log.d("ActivityProfile2", "Received USER_UID: $userUid")
        Log.d("ActivityProfile2", "Current USER_ID: $currentUserId")

        if (userEmail != null && userUid != null && currentUserId != null) {
            loadUserProfile(userEmail)
            setupOnlineStatusListener(userEmail)
            updateFollowButton(currentUserId, userUid, follow)
        } else {
            Log.e("ActivityProfile2", "Missing user information!")
            statusText.text = "User not found"
            statusDot.visibility = View.GONE
        }

        // Bottom Navigation (keep existing code)
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

        val msg = findViewById<Button>(R.id.msg)
        msg.setOnClickListener {
            val intent = Intent(this, inbox::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        val addpic = findViewById<ImageView>(R.id.add)
        addpic.setOnClickListener {
            val intent = Intent(this, selectpicture::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        val highlight = findViewById<CircleImageView>(R.id.highlight1)
        highlight.setOnClickListener {
            val intent = Intent(this, highlights::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }
    }

    private fun loadUserProfile(userEmail: String) {
        // Load user data from Firebase
        val database = FirebaseDatabase.getInstance()
        val userRef = database.reference.child("users").child(userEmail)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val username = snapshot.child("username").getValue(String::class.java)
                    val fullname = snapshot.child("fullname").getValue(String::class.java)
                    val email = snapshot.child("email").getValue(String::class.java)

                    Log.d("ActivityProfile2", "Loaded username: $username, fullname: $fullname")

                    // Display username at the top (in pink color)
                    usernameText.text = username ?: "Unknown User"

                    // Display the same username in the bio section
                    fullnameText.text = username ?: "Unknown User"

                    // You can load more profile data here (bio, followers, etc.)
                } else {
                    Log.e("ActivityProfile2", "User snapshot does not exist for: $userEmail")
                    usernameText.text = "User Not Found"
                    fullnameText.text = "User Not Found"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ActivityProfile2", "Error loading profile: ${error.message}")
                usernameText.text = "Error loading profile"
                fullnameText.text = "Error loading profile"
            }
        })
    }

    private fun setupOnlineStatusListener(userEmail: String) {
        // Listen to user's online status in real-time
        OnlineStatusManager.listenToUserStatus(userEmail) { isOnline, lastSeen ->
            runOnUiThread {
                updateStatusUI(isOnline, lastSeen)
            }
        }
    }

    private fun updateStatusUI(isOnline: Boolean, lastSeen: Long?) {
        if (isOnline) {
            // User is online
            statusText.text = "Online"
            statusText.setTextColor(getColor(android.R.color.holo_green_dark))
            statusDot.setBackgroundResource(R.drawable.status_dot_online)
            statusDot.visibility = View.VISIBLE

            Log.d("ActivityProfile2", "User is ONLINE")
        } else {
            // User is offline - show last seen
            val lastSeenText = TimeUtils.getLastSeenText(lastSeen)
            statusText.text = lastSeenText
            statusText.setTextColor(getColor(android.R.color.darker_gray))
            statusDot.setBackgroundResource(R.drawable.status_dot_offline)
            statusDot.visibility = View.VISIBLE

            Log.d("ActivityProfile2", "User is OFFLINE - Last seen: $lastSeenText")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ActivityStack", "ActivityProfile2 onDestroy")
    }

    private fun updateFollowButton(currentUserId: String, targetUserId: String, followBtn: Button) {
        FollowRequestManager.getFollowRequestStatus(currentUserId, targetUserId) { status ->
            runOnUiThread {
                when (status) {
                    "following" -> {
                        followBtn.text = "Unfollow"
                        followBtn.setBackgroundColor(getColor(android.R.color.darker_gray))
                        followBtn.setOnClickListener {
                            unfollowUser(currentUserId, targetUserId, followBtn)
                        }
                    }
                    "pending" -> {
                        followBtn.text = "Requested"
                        followBtn.setBackgroundColor(getColor(android.R.color.darker_gray))
                        followBtn.setOnClickListener {
                            cancelFollowRequest(currentUserId, targetUserId, followBtn)
                        }
                    }
                    else -> {
                        followBtn.text = "Follow"
                        followBtn.setBackgroundColor(getColor(R.color.pink))
                        followBtn.setOnClickListener {
                            sendFollowRequest(currentUserId, targetUserId, followBtn)
                        }
                    }
                }
            }
        }
    }

    private fun sendFollowRequest(fromUserId: String, toUserId: String, followBtn: Button) {
        val fromUsername = FirebaseAuth.getInstance().currentUser?.email?.substringBefore("@") ?: "User"
        val toUsername = usernameText.text.toString()

        FollowRequestManager.sendFollowRequest(
            fromUserId,
            fromUsername,
            toUserId,
            toUsername,
            onSuccess = {
                runOnUiThread {
                    Toast.makeText(this, "Follow request sent", Toast.LENGTH_SHORT).show()
                    updateFollowButton(fromUserId, toUserId, followBtn)
                }
            },
            onFailure = { error ->
                runOnUiThread {
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun cancelFollowRequest(fromUserId: String, toUserId: String, followBtn: Button) {
        FollowRequestManager.cancelFollowRequest(
            fromUserId,
            toUserId,
            onSuccess = {
                runOnUiThread {
                    Toast.makeText(this, "Request cancelled", Toast.LENGTH_SHORT).show()
                    updateFollowButton(fromUserId, toUserId, followBtn)
                }
            },
            onFailure = { error ->
                runOnUiThread {
                    Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun unfollowUser(currentUserId: String, targetUserId: String, followBtn: Button) {
        FollowRequestManager.unfollowUser(
            currentUserId,
            targetUserId,
            onSuccess = {
                runOnUiThread {
                    Toast.makeText(this, "Unfollowed", Toast.LENGTH_SHORT).show()
                    updateFollowButton(currentUserId, targetUserId, followBtn)
                }
            },
            onFailure = { error ->
                runOnUiThread {
                    Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

}
