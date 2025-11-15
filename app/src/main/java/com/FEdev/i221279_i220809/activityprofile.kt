
package com.FEdev.i221279_i220809

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import de.hdodenhof.circleimageview.CircleImageView

class activityprofile : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var dbRef: DatabaseReference
    private lateinit var usernameText: TextView
    private lateinit var emailText: TextView
    private lateinit var topBarUsername: TextView  // ✅ Add this line

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_activityprofile)
        Log.d("ActivityStack", "Activityprofile onCreate")

        // ✅ Drawer setup
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val sidebarLayout = findViewById<LinearLayout>(R.id.sidebar)
        val menuBtn = findViewById<ImageView>(R.id.menu)

        auth = FirebaseAuth.getInstance()
        dbRef = FirebaseDatabase.getInstance().getReference("users")

        // ✅ Open sidebar
        menuBtn.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // ✅ Sidebar button actions
        val logoutBtn = findViewById<Button>(R.id.btn_logout)
        logoutBtn?.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, Login1::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        // ✅ Initialize text views
        usernameText = findViewById(R.id.profileUsername)
        emailText = findViewById(R.id.profileEmail)
        topBarUsername = findViewById(R.id.username)  // ✅ initialize top bar username

        loadUserProfile()

        // ✅ Apply window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // ✅ Bottom Navigation
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

        val edit = findViewById<Button>(R.id.editingprofile)
        edit.setOnClickListener {
            val intent = Intent(this, editprofile::class.java)
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

    // ✅ Fetch user data from Firebase Realtime Database
    private fun loadUserProfile() {
        val user = auth.currentUser
        if (user != null) {
            val emailKey = user.email?.replace(".", ",")
            if (emailKey != null) {
                dbRef.child(emailKey).get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot.exists()) {
                            val username = snapshot.child("username").value.toString()
                            val email = snapshot.child("email").value.toString()

                            usernameText.text = username
                            emailText.text = email
                            topBarUsername.text = username   // ✅ set top bar name also
                        } else {
                            Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to load profile: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ActivityStack", "ActivityProfile onDestroy")
    }
}
