package com.FEdev.i221279_i220809

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import de.hdodenhof.circleimageview.CircleImageView

class activitypage : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activityfollowing)
        Log.d("ActivityStack", "Following onCreate")
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
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
        val profileNav = findViewById< CircleImageView>(R.id.nav_profile)
        profileNav.setOnClickListener {
            val intent = Intent(this, activityprofile::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }
        val addpic = findViewById< ImageView>(R.id.add)
        addpic.setOnClickListener {
            val intent = Intent(this, selectpicture::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        val you = findViewById<TextView>(R.id.You)
        you.setOnClickListener {
            val intent = Intent(this, activityyou::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }
        val following= findViewById<TextView>(R.id.following)
        following.setOnClickListener {
            val intent = Intent(this, activitypage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }


    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ActivityStack", "Following onDestroy")
    }
}

