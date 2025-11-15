package com.FEdev.i221279_i220809

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import android.Manifest
class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }

        // ✅ Initialize FCM and get token
        initializeFCM()

        // ✅ Adjust layout for system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // ✅ Initialize Firebase (important!)
        FirebaseApp.initializeApp(this)

        // ✅ Initialize Firebase Auth & Database with your DB URL
        auth = FirebaseAuth.getInstance()
        database =
            FirebaseDatabase.getInstance("https://i1279-22i0809-assignment2-default-rtdb.firebaseio.com/")

        // ✅ Delay for splash/loading effect
        Handler(Looper.getMainLooper()).postDelayed({
            checkUserStatus()
        }, 2000)
    }

    private fun checkUserStatus() {
        val user = auth.currentUser

        if (user != null) {
            val ref = database.getReference("users").child(user.uid)
            ref.get().addOnSuccessListener {
                if (it.exists()) {
                    startActivity(Intent(this, homepage::class.java))
                } else {
                    startActivity(Intent(this, login2::class.java))
                }
                finish()
            }.addOnFailureListener {
                startActivity(Intent(this, Login1::class.java))
                finish()
            }
        } else {
            startActivity(Intent(this, Login1::class.java))
            finish()
        }
    }

    private val NOTIFICATION_PERMISSION_CODE = 1001
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
    }
    private fun initializeFCM() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("MainActivity", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result
            Log.d("MainActivity", "FCM Token: $token")

            // Save token to Firebase Database
            saveFCMToken(token)
        }

        // Subscribe to topic for global notifications (optional)
        FirebaseMessaging.getInstance().subscribeToTopic("all_users")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("MainActivity", "Subscribed to topic: all_users")
                }
            }
    }

    private fun saveFCMToken(token: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseDatabase.getInstance()
            .getReference("users")
            .child(userId)
            .child("fcmToken")
            .setValue(token)
            .addOnSuccessListener {
                Log.d("MainActivity", "FCM token saved successfully")
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Failed to save FCM token: ${e.message}")
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            NOTIFICATION_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("MainActivity", "Notification permission granted")
                } else {
                    Log.d("MainActivity", "Notification permission denied")
                    Toast.makeText(
                        this,
                        "Notification permission is required for alerts",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }



}
