package com.FEdev.i221279_i220809

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class signup : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_signup)
        Log.d("ActivityStack", "signup onCreate")

        auth = FirebaseAuth.getInstance()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.signup)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val backBtn = findViewById<ImageView>(R.id.back)
        val createAccBtn = findViewById<Button>(R.id.createacc)
        val emailField = findViewById<EditText>(R.id.email)
        val passwordField = findViewById<EditText>(R.id.password)
        val usernameField = findViewById<EditText>(R.id.username)

        backBtn.setOnClickListener {
            startActivity(Intent(this, login2::class.java))
        }

        createAccBtn.setOnClickListener {
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()
            val username = usernameField.text.toString().trim()

            if (email.isEmpty() || password.isEmpty() || username.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        saveUserToRealtimeDb(uid, email, username)
                        Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, login2::class.java))
                        finish()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Signup failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun saveUserToRealtimeDb(uid: String, email: String, username: String) {
        val key = email.replace(".", ",")
        val userData = mapOf(
            "uid" to uid,
            "email" to email,
            "username" to username
        )

        FirebaseDatabase.getInstance().reference
            .child("users")
            .child(key)
            .setValue(userData)
            .addOnSuccessListener {
                Log.d("Firebase", "✅ User saved to Realtime DB with UID: $uid")
            }
            .addOnFailureListener {
                Log.e("Firebase", "❌ Error saving user: ${it.message}")
            }
    }
}
