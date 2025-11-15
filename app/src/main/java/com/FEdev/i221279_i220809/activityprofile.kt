package com.FEdev.i221279_i220809

import android.content.Intent
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_activityprofile)

        Log.d("ActivityProfile", "=== onCreate started ===")

        sessionManager = SessionManager(this)

        // Initialize views
        try {
            drawerLayout = findViewById(R.id.drawer_layout)
            menuBtn = findViewById(R.id.menu)
            logoutBtn = findViewById(R.id.btn_logout)
            usernameText = findViewById(R.id.profileUsername)
            emailText = findViewById(R.id.profileEmail)
            topBarUsername = findViewById(R.id.username)

            Log.d("ActivityProfile", "✅ All views initialized successfully")
            Log.d("ActivityProfile", "DrawerLayout: $drawerLayout")
            Log.d("ActivityProfile", "MenuBtn: $menuBtn")
            Log.d("ActivityProfile", "LogoutBtn: $logoutBtn")
        } catch (e: Exception) {
            Log.e("ActivityProfile", "❌ Error initializing views: ${e.message}", e)
        }

        // Menu button toggles drawer
        menuBtn.setOnClickListener {
            Log.d("ActivityProfile", "🔘 Menu button CLICKED!")
            Log.d("ActivityProfile", "Drawer open status: ${drawerLayout.isDrawerOpen(GravityCompat.START)}")

            try {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    Log.d("ActivityProfile", "Closing drawer...")
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    Log.d("ActivityProfile", "Opening drawer...")
                    drawerLayout.openDrawer(GravityCompat.START)
                    Log.d("ActivityProfile", "Drawer opened successfully")
                }
            } catch (e: Exception) {
                Log.e("ActivityProfile", "❌ Error toggling drawer: ${e.message}", e)
                Toast.makeText(this, "Error opening menu: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Add drawer listener for debugging
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: android.view.View, slideOffset: Float) {
                Log.d("ActivityProfile", "Drawer sliding: $slideOffset")
            }

            override fun onDrawerOpened(drawerView: android.view.View) {
                Log.d("ActivityProfile", "✅ Drawer OPENED")
            }

            override fun onDrawerClosed(drawerView: android.view.View) {
                Log.d("ActivityProfile", "✅ Drawer CLOSED")
            }

            override fun onDrawerStateChanged(newState: Int) {
                Log.d("ActivityProfile", "Drawer state changed: $newState")
            }
        })

        // Logout button
        logoutBtn.setOnClickListener {
            Log.d("ActivityProfile", "🔘 Logout button CLICKED!")
            drawerLayout.closeDrawer(GravityCompat.START)
            performLogout()
        }

        // Load profile
        loadUserProfile()

        // Bottom navigation
        findViewById<ImageView>(R.id.nav_home)?.setOnClickListener {
            startActivity(Intent(this, homepage::class.java))
        }
        findViewById<ImageView>(R.id.nav_like)?.setOnClickListener {
            startActivity(Intent(this, activitypage::class.java))
        }
        findViewById<ImageView>(R.id.nav_search)?.setOnClickListener {
            startActivity(Intent(this, searchpage::class.java))
        }
        findViewById<Button>(R.id.editingprofile)?.setOnClickListener {
            startActivity(Intent(this, editprofile::class.java))
        }
        findViewById<ImageView>(R.id.add)?.setOnClickListener {
            startActivity(Intent(this, selectpicture::class.java))
        }
        findViewById<CircleImageView>(R.id.highlight1)?.setOnClickListener {
            startActivity(Intent(this, highlights::class.java))
        }

        Log.d("ActivityProfile", "=== onCreate completed ===")
    }

    private fun loadUserProfile() {
        val username = sessionManager.getUsername()
        val email = sessionManager.getEmail()

        Log.d("ActivityProfile", "Loading profile - Username: $username, Email: $email")

        if (!username.isNullOrEmpty() && !email.isNullOrEmpty()) {
            topBarUsername.text = username
            usernameText.text = username
            emailText.text = email
            Log.d("ActivityProfile", "✅ Profile loaded successfully")
        } else {
            topBarUsername.text = "User"
            usernameText.text = "Unknown"
            emailText.text = "No email"
            Log.e("ActivityProfile", "❌ Failed to load profile data")
        }
    }

    private fun performLogout() {
        Log.d("ActivityProfile", "=== performLogout started ===")

        val authToken = sessionManager.getAuthToken()
        Log.d("ActivityProfile", "Auth token exists: ${authToken != null}")

        if (authToken != null) {
            Toast.makeText(this, "Logging out...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                try {
                    Log.d("ActivityProfile", "Making logout API call...")
                    val response = RetrofitClient.apiService.logout(SessionRequest(authToken))

                    Log.d("ActivityProfile", "Response: ${response.isSuccessful}, ${response.body()?.success}")

                    if (response.isSuccessful && response.body()?.success == true) {
                        Log.d("ActivityProfile", "✅ Logout successful from server")
                    }
                } catch (e: Exception) {
                    Log.e("ActivityProfile", "❌ Logout error: ${e.message}", e)
                } finally {
                    sessionManager.clearSession()
                    Log.d("ActivityProfile", "✅ Session cleared")

                    val intent = Intent(this@activityprofile, Login1::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
        } else {
            Log.e("ActivityProfile", "❌ No auth token")
            sessionManager.clearSession()
            val intent = Intent(this, Login1::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}