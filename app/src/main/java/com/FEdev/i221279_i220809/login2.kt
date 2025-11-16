package com.FEdev.i221279_i220809

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.models.LoginRequest
import com.FEdev.i221279_i220809.utils.SessionManager
import kotlinx.coroutines.launch

class login2 : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login2)

        sessionManager = SessionManager(this)

        val signupText = findViewById<TextView>(R.id.signUpLink)
        val loginBtn = findViewById<Button>(R.id.loginButton)
        val backBtn = findViewById<ImageView>(R.id.back)
        val usernameInput = findViewById<EditText>(R.id.username)
        val passwordInput = findViewById<EditText>(R.id.password)

        signupText.setOnClickListener {
            startActivity(Intent(this, signup::class.java))
        }

        loginBtn.setOnClickListener {
            val input = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (input.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginBtn.isEnabled = false

            lifecycleScope.launch {
                loginUser(input, password, loginBtn)
            }
        }

        backBtn.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private suspend fun loginUser(identifier: String, password: String, loginBtn: Button) {
        try {
            val request = LoginRequest(
                identifier = identifier,
                password = password
            )

            val response = RetrofitClient.apiService.login(request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.data != null) {

                    val userData = body.data

                    // Save session correctly
                    sessionManager.saveSession(
                        userData.auth_token,   // <-- FIXED
                        userData.user_id,
                        userData.email,
                        userData.username
                    )

                    Toast.makeText(this@login2, body.message, Toast.LENGTH_SHORT).show()

                    startActivity(Intent(this@login2, homepage::class.java))
                    finish()

                } else {
                    Toast.makeText(this@login2, body?.message ?: "Login failed", Toast.LENGTH_SHORT).show()
                    loginBtn.isEnabled = true
                }
            } else {
                Toast.makeText(this@login2, "Server error: ${response.code()}", Toast.LENGTH_SHORT).show()
                loginBtn.isEnabled = true
            }
        } catch (e: Exception) {
            Log.e("Login", "Error: ${e.message}")
            Toast.makeText(this@login2, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            loginBtn.isEnabled = true
        }
    }
}
