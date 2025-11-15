package com.FEdev.i221279_i220809

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class Login1 : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login1)
        Log.d("ActivityStack", "Login1 onCreate")

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val backBtn = findViewById<ImageView>(R.id.back)
        val signupText = findViewById<TextView>(R.id.signup)
        val switchAccount = findViewById<TextView>(R.id.switchacc)
        val loginBtn = findViewById<Button>(R.id.login)

        backBtn.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        signupText.setOnClickListener {
            startActivity(Intent(this, signup::class.java))
        }

        switchAccount.setOnClickListener {
            startActivity(Intent(this, login2::class.java))
        }

        loginBtn.setOnClickListener {
            startActivity(Intent(this, login2::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ActivityStack", "Login1 onDestroy")
    }
}