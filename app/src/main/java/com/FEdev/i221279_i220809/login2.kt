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
import com.google.firebase.database.*

class login2 : AppCompatActivity() {

    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login2)
        Log.d("ActivityStack", "Login2 onCreate")

        database = FirebaseDatabase.getInstance().getReference("users")

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

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

            loginUser(input, password)
        }

        backBtn.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun loginUser(input: String, password: String) {
        val auth = FirebaseAuth.getInstance()

        if (android.util.Patterns.EMAIL_ADDRESS.matcher(input).matches()) {
            // Login via email
            auth.signInWithEmailAndPassword(input, password)
                .addOnSuccessListener {
                    val uid = auth.currentUser?.uid ?: return@addOnSuccessListener
                    ensureUserExistsInRealtimeDb(uid, input)
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Login failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Login via username
            database.orderByChild("username").equalTo(input).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val email = snapshot.children.first().child("email").value.toString()
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener {
                            val uid = auth.currentUser?.uid ?: return@addOnSuccessListener
                            ensureUserExistsInRealtimeDb(uid, email)
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Login failed: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, "Username not found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ✅ Automatically creates or updates user entry in Realtime DB
    private fun ensureUserExistsInRealtimeDb(uid: String, email: String) {
        val key = email.replace(".", ",")
        val userRef = FirebaseDatabase.getInstance().reference.child("users").child(key)

        userRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                // 🔹 Create new DB entry if missing
                val userData = mapOf(
                    "uid" to uid,
                    "email" to email,
                    "username" to email.substringBefore("@")
                )
                userRef.setValue(userData)
                Log.d("Firebase", "✅ Created new user node for $email")
            } else {
                // 🔹 Ensure UID is up-to-date
                userRef.child("uid").setValue(uid)
                Log.d("Firebase", "✅ Updated UID for existing user $email")
            }

            Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, homepage::class.java))
            finish()
        }
    }
}
