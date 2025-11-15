package com.FEdev.i221279_i220809

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.models.SessionRequest
import com.FEdev.i221279_i220809.utils.SessionManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sessionManager = SessionManager(this)

        // Wait 5 seconds (splash screen requirement)
        Handler(Looper.getMainLooper()).postDelayed({
            checkUserSession()
        }, 5000)
    }

    private fun checkUserSession() {
        val authToken = sessionManager.getAuthToken()

        if (authToken != null) {
            // User has a saved session, verify it with server
            lifecycleScope.launch {
                try {
                    val response = RetrofitClient.apiService.checkSession(
                        SessionRequest(authToken)
                    )

                    if (response.isSuccessful && response.body()?.success == true) {
                        val userData = response.body()?.data

                        // Update session data
                        if (userData != null) {
                            sessionManager.saveSession(
                                authToken,
                                userData.user_id,
                                userData.email,
                                userData.username
                            )

                            // Go to homepage - logged in user
                            startActivity(Intent(this@MainActivity, homepage::class.java))
                        }
                    } else {
                        // Session invalid, clear and go to login
                        sessionManager.clearSession()
                        startActivity(Intent(this@MainActivity, Login1::class.java))
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error checking session: ${e.message}")
                    // Network error, clear session and go to login
                    sessionManager.clearSession()
                    startActivity(Intent(this@MainActivity, Login1::class.java))
                }
                finish()
            }
        } else {
            // No session saved, go to login screen - first time or logged out user
            startActivity(Intent(this@MainActivity, Login1::class.java))
            finish()
        }
    }
}