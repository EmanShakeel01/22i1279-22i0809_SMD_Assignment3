package com.FEdev.i221279_i220809

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.models.SignupRequest
import kotlinx.coroutines.launch

class signup : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val backBtn = findViewById<ImageView>(R.id.back)
        val createAccBtn = findViewById<Button>(R.id.createacc)
        val emailField = findViewById<EditText>(R.id.email)
        val passwordField = findViewById<EditText>(R.id.password)
        val usernameField = findViewById<EditText>(R.id.username)

        backBtn.setOnClickListener {
            finish()
        }

        createAccBtn.setOnClickListener {
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()
            val username = usernameField.text.toString().trim()

            if (email.isEmpty() || password.isEmpty() || username.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Disable button to prevent double-click
            createAccBtn.isEnabled = false

            lifecycleScope.launch {
                try {
                    val request = SignupRequest(email, username, password)
                    val response = RetrofitClient.apiService.signup(request)

                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body?.success == true) {
                            Toast.makeText(
                                this@signup,
                                body.message,
                                Toast.LENGTH_SHORT
                            ).show()

                            // Navigate to login
                            startActivity(Intent(this@signup, login2::class.java))
                            finish()
                        } else {
                            Toast.makeText(
                                this@signup,
                                body?.message ?: "Signup failed",
                                Toast.LENGTH_SHORT
                            ).show()
                            createAccBtn.isEnabled = true
                        }
                    } else {
                        Toast.makeText(
                            this@signup,
                            "Server error: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                        createAccBtn.isEnabled = true
                    }
                } catch (e: Exception) {
                    Log.e("Signup", "Error: ${e.message}")
                    Toast.makeText(
                        this@signup,
                        "Network error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    createAccBtn.isEnabled = true
                }
            }
        }
    }
}