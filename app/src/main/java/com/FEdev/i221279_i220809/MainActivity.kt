package com.FEdev.i221279_i220809

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.FEdev.i221279_i220809.models.SessionRequest
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.utils.SessionManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sessionManager = SessionManager(this)

        // Show splash for 5 sec
        Handler(Looper.getMainLooper()).postDelayed({
            checkUserSession()
        }, 5000)
    }

    private fun checkUserSession() {
        val token = sessionManager.getAuthToken()

        if (token == null) {
            // No saved session → go login
            startActivity(Intent(this, Login1::class.java))
            finish()
            return
        }

        // Validate session with backend
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.checkSession(SessionRequest(token))

                if (response.isSuccessful && response.body()?.success == true) {

                    val user = response.body()?.data

                    if (user != null) {
                        // Save updated session
                        sessionManager.saveSession(
                            token,
                            user.user_id,
                            user.email,
                            user.username
                        )

                        // Go to home
                        startActivity(Intent(this@MainActivity, homepage::class.java))
                        finish()
                    } else {
                        sessionManager.clearSession()
                        startActivity(Intent(this@MainActivity, Login1::class.java))
                        finish()
                    }

                } else {
                    // Invalid session → force login
                    sessionManager.clearSession()
                    startActivity(Intent(this@MainActivity, Login1::class.java))
                    finish()
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Session check failed: ${e.message}")

                sessionManager.clearSession()
                startActivity(Intent(this@MainActivity, Login1::class.java))
                finish()
            }
        }
    }
}
