
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
import de.hdodenhof.circleimageview.CircleImageView

class myprofilepage : AppCompatActivity() {
    private lateinit var usernameText: TextView
    private lateinit var fullnameText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_myprofilepage)
        Log.d("ActivityStack", "MyProfilePage onCreate")

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize TextViews with correct IDs from your layout
        usernameText = findViewById(R.id.username)
        fullnameText = findViewById(R.id.fullnameText)

        // Retrieve username and fullname from Intent
        val username = intent.getStringExtra("USERNAME")
        val fullname = intent.getStringExtra("FULLNAME")

        Log.d("MyProfilePage", "Received USERNAME: $username, FULLNAME: $fullname")

        // Display the data
        usernameText.text = username ?: "Unknown User"
        fullnameText.text = fullname ?: "Unknown User"

        val msg = findViewById<Button>(R.id.msg)
        msg.setOnClickListener {
            val intent = Intent(this, inbox::class.java)
            startActivity(intent)
        }

        val homeNav = findViewById<ImageView>(R.id.nav_home)
        homeNav.setOnClickListener {
            val intent = Intent(this, homepage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        val likeNav = findViewById<ImageView>(R.id.nav_like)
        likeNav.setOnClickListener {
            val intent = Intent(this, activitypage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        val searchNav = findViewById<ImageView>(R.id.nav_search)
        searchNav.setOnClickListener {
            val intent = Intent(this, searchpage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        val profileNav = findViewById<CircleImageView>(R.id.nav_profile)
        profileNav.setOnClickListener {
            val intent = Intent(this, activityprofile::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        val addpic = findViewById<ImageView>(R.id.add)
        addpic.setOnClickListener {
            val intent = Intent(this, selectpicture::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        val highlight = findViewById<CircleImageView>(R.id.highlight1)
        highlight.setOnClickListener {
            val intent = Intent(this, highlights::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }
    }

}