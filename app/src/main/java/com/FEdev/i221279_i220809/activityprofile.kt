package com.FEdev.i221279_i220809

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.models.SessionRequest
import com.FEdev.i221279_i220809.utils.SessionManager
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.launch

class activityprofile : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var drawerLayout: DrawerLayout

    private lateinit var menuBtn: ImageView
    private lateinit var logoutBtn: Button

    private lateinit var usernameText: TextView
    private lateinit var emailText: TextView
    private lateinit var topBarUsername: TextView

    private lateinit var profileImage: CircleImageView
    private lateinit var navProfileImage: CircleImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_activityprofile)

        Log.d("ActivityProfile", "onCreate")

        sessionManager = SessionManager(this)

        // =======================
        // INIT VIEWS
        // =======================
        drawerLayout = findViewById(R.id.drawer_layout)
        menuBtn = findViewById(R.id.menu)
        logoutBtn = findViewById(R.id.btn_logout)

        usernameText = findViewById(R.id.profileUsername)
        emailText = findViewById(R.id.profileEmail)
        topBarUsername = findViewById(R.id.username)

        profileImage = findViewById(R.id.profileimg)
        navProfileImage = findViewById(R.id.nav_profile)

        // =======================
        // MENU BUTTON
        // =======================
        menuBtn.setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        logoutBtn.setOnClickListener {
            performLogout()
        }

        // Load user data + profile picture
        loadUserProfile()
        loadSavedProfilePicture()

        // =======================
        // BOTTOM NAVIGATION
        // =======================
        findViewById<ImageView>(R.id.nav_home).setOnClickListener {
            startActivity(Intent(this, homepage::class.java))
        }
        findViewById<ImageView>(R.id.nav_like).setOnClickListener {
            startActivity(Intent(this, activitypage::class.java))
        }
        findViewById<ImageView>(R.id.nav_search).setOnClickListener {
            startActivity(Intent(this, searchpage::class.java))
        }
        findViewById<Button>(R.id.editingprofile).setOnClickListener {
            startActivity(Intent(this, editprofile::class.java))
        }
        findViewById<ImageView>(R.id.add).setOnClickListener {
            startActivity(Intent(this, selectpicture::class.java))
        }

        findViewById<CircleImageView>(R.id.highlight1)?.setOnClickListener {
            startActivity(Intent(this, highlights::class.java))
        }
    }

    // =======================
    // onResume - Reload profile picture
    // =======================
    override fun onResume() {
        super.onResume()
        Log.d("ActivityProfile", "onResume - Reloading profile picture")
        loadSavedProfilePicture()
    }

    // =======================
    // LOAD USER PROFILE TEXT
    // =======================
    private fun loadUserProfile() {
        val username = sessionManager.getUsername()
        val email = sessionManager.getEmail()

        Log.d("ActivityProfile", "Loading profile: $username, $email")

        if (!username.isNullOrEmpty()) {
            topBarUsername.text = username
            usernameText.text = username
        }

        if (!email.isNullOrEmpty()) {
            emailText.text = email
        }
    }

    // =======================
    // LOAD SAVED PROFILE PIC
    // =======================
    private fun loadSavedProfilePicture() {
        try {
            val base64Pic = sessionManager.getProfilePic()

            Log.d("ActivityProfile", "Loading profile picture from SessionManager")

            if (!base64Pic.isNullOrEmpty()) {
                Log.d("ActivityProfile", "Base64 found, length: ${base64Pic.length}")

                val decodedBytes = Base64.decode(base64Pic, Base64.NO_WRAP)
                Log.d("ActivityProfile", "Decoded bytes: ${decodedBytes.size}")

                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

                if (bitmap != null) {
                    Log.d("ActivityProfile", "✅ Bitmap decoded: ${bitmap.width}x${bitmap.height}")
                    profileImage.setImageBitmap(bitmap)
                    navProfileImage.setImageBitmap(bitmap)
                    Log.d("ActivityProfile", "✅ Profile pictures updated")
                } else {
                    Log.e("ActivityProfile", "❌ Failed to decode bitmap from bytes")
                }
            } else {
                Log.d("ActivityProfile", "No saved profile picture in SessionManager")
            }
        } catch (e: Exception) {
            Log.e("ActivityProfile", "❌ Error loading profile picture: ${e.message}", e)
            e.printStackTrace()
        }
    }

    // =======================
    // LOGOUT
    // =======================
    private fun performLogout() {
        val authToken = sessionManager.getAuthToken()

        if (authToken != null) {
            lifecycleScope.launch {
                try {
                    val response = RetrofitClient.apiService.logout(SessionRequest(authToken))
                    Log.d("ActivityProfile", "Logout response: ${response.isSuccessful}")
                } catch (e: Exception) {
                    Log.e("ActivityProfile", "Logout error: ${e.message}")
                } finally {
                    sessionManager.clearSession()
                    val intent = Intent(this@activityprofile, Login1::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
        } else {
            sessionManager.clearSession()
            startActivity(Intent(this, Login1::class.java))
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ActivityProfile", "onDestroy")
    }
}