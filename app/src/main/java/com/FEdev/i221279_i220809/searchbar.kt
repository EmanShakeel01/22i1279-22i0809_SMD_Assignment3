//package com.FEdev.i221279_i220809
//
//import android.content.Intent
//import android.os.Bundle
//import android.text.Editable
//import android.text.TextWatcher
//import android.util.Log
//import android.view.KeyEvent
//import android.view.View
//import android.view.inputmethod.EditorInfo
//import android.widget.EditText
//import android.widget.LinearLayout
//import android.widget.TextView
//import android.widget.Toast
//import androidx.activity.enableEdgeToEdge
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.view.ViewCompat
//import androidx.core.view.WindowInsetsCompat
//import com.google.firebase.database.*
//import de.hdodenhof.circleimageview.CircleImageView
//
//class searchbar : AppCompatActivity() {
//
//    private lateinit var searchEdit: EditText
//    private lateinit var clearText: TextView
//    private lateinit var searchResultContainer: LinearLayout
//    private lateinit var usernameText: TextView
//    private lateinit var fullnameText: TextView
//    private lateinit var avatarImage: CircleImageView
//    private lateinit var database: DatabaseReference
//
//    private var foundUserEmail: String? = null
//    private var foundUserUid: String? = null
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        setContentView(R.layout.activity_searchbar)
//        Log.d("ActivityStack", "Searchbar onCreate")
//
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
//
//        database = FirebaseDatabase.getInstance().getReference("users")
//
//        // Initialize views
//        searchEdit = findViewById(R.id.searchEdit)
//        clearText = findViewById(R.id.clearText)
//        searchResultContainer = findViewById(R.id.searchresult1)
//        usernameText = searchResultContainer.findViewById<TextView>(R.id.username)
//        fullnameText = searchResultContainer.findViewById<TextView>(R.id.fullname)
//        avatarImage = findViewById(R.id.searchresult)
//
//        // Clear the default text and set hint
//        searchEdit.text.clear()
//        searchEdit.hint = "Search username..."
//
//        // Hide results initially
//        searchResultContainer.visibility = View.GONE
//
//        // Clear button click
//        clearText.setOnClickListener {
//            searchEdit.text.clear()
//            searchResultContainer.visibility = View.GONE
//            foundUserEmail = null
//            foundUserUid = null
//        }
//
//
//        // Search on text change
//        searchEdit.addTextChangedListener(object : TextWatcher {
//            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
//
//            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
//                val query = s.toString().trim()
//                if (query.isNotEmpty()) {
//                    searchUser(query)
//                } else {
//                    searchResultContainer.visibility = View.GONE
//                    foundUserEmail = null
//                    foundUserUid = null
//                }
//            }
//
//            override fun afterTextChanged(s: Editable?) {}
//        })
//
//
//
//        // Search on enter key
//        searchEdit.setOnEditorActionListener { _, actionId, event ->
//            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
//                (event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
//                val query = searchEdit.text.toString().trim()
//                if (query.isNotEmpty()) {
//                    searchUser(query)
//                }
//                true
//            } else {
//                false
//            }
//        }
//
//        // Navigate to profile on click
//        searchResultContainer.setOnClickListener {
//            if (foundUserEmail != null && foundUserUid != null) {
//                val intent = Intent(this, activityprofile2::class.java)
//                intent.putExtra("USER_EMAIL", foundUserEmail)
//                intent.putExtra("USER_UID", foundUserUid)
//                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//                startActivity(intent)
//            }
//        }
//    }
//
//    private fun searchUser(query: String) {
//        // Search through all users in Firebase
//        database.addListenerForSingleValueEvent(object : ValueEventListener {
//            override fun onDataChange(snapshot: DataSnapshot) {
//                var found = false
//
//                // Iterate through all children (email keys)
//                for (emailSnapshot in snapshot.children) {
//                    val username = emailSnapshot.child("username").getValue(String::class.java)
//                    val email = emailSnapshot.child("email").getValue(String::class.java)
//                    val uid = emailSnapshot.child("uid").getValue(String::class.java)
//
//                    // Check if username matches (case-insensitive, partial match)
//                    if (username != null && username.lowercase().contains(query.lowercase())) {
//                        // User found
//                        displaySearchResult(username, email, uid, emailSnapshot.key)
//                        found = true
//                        break // Show only first match
//                    }
//                }
//
//                if (!found) {
//                    // No user found
//                    showNoResults()
//                }
//            }
//
//            override fun onCancelled(error: DatabaseError) {
//                Log.e("SearchBar", "Database error: ${error.message}")
//                Toast.makeText(this@searchbar, "Search error occurred", Toast.LENGTH_SHORT).show()
//            }
//        })
//    }
//
//    private fun displaySearchResult(username: String, email: String?, uid: String?, emailKey: String?) {
//        searchResultContainer.visibility = View.VISIBLE
//        searchResultContainer.isClickable = true
//
//        // Store user info for navigation
//        foundUserEmail = emailKey ?: email
//        foundUserUid = uid
//
//        // Display username
//        fullnameText.text = username
//        usernameText.text = email ?: "No email"
//
//        // You can load profile image here if you add profileImage field to Firebase
//        // For now, it will use the default image from XML
//    }
//
//    private fun showNoResults() {
//        searchResultContainer.visibility = View.VISIBLE
//        searchResultContainer.isClickable = false
//        foundUserEmail = null
//        foundUserUid = null
//
//        fullnameText.text = "Account not found"
//        usernameText.text = "No user matches your search"
//        avatarImage.setImageResource(R.drawable.profile10) // default image
//    }
//
//    override fun onBackPressed() {
//        super.onBackPressed()
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        Log.d("ActivityStack", "Searchbar onDestroy")
//    }
//}


//works with online/offline status

//package com.FEdev.i221279_i220809
//
//import android.content.Intent
//import android.os.Bundle
//import android.text.Editable
//import android.text.TextWatcher
//import android.util.Log
//import android.view.KeyEvent
//import android.view.View
//import android.view.inputmethod.EditorInfo
//import android.widget.EditText
//import android.widget.LinearLayout
//import android.widget.TextView
//import android.widget.Toast
//import androidx.activity.enableEdgeToEdge
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.view.ViewCompat
//import androidx.core.view.WindowInsetsCompat
//import com.google.firebase.database.*
//import de.hdodenhof.circleimageview.CircleImageView
//
//class searchbar : AppCompatActivity() {
//
//    private lateinit var searchEdit: EditText
//    private lateinit var clearText: TextView
//    private lateinit var searchResultContainer: LinearLayout
//    private lateinit var usernameText: TextView
//    private lateinit var fullnameText: TextView
//    private lateinit var avatarImage: CircleImageView
//    private lateinit var database: DatabaseReference
//
//    private var foundUserEmail: String? = null
//    private var foundUserUid: String? = null
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        setContentView(R.layout.activity_searchbar)
//        Log.d("ActivityStack", "Searchbar onCreate")
//
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
//
//        database = FirebaseDatabase.getInstance().getReference("users")
//
//        // Initialize views
//        searchEdit = findViewById(R.id.searchEdit)
//        clearText = findViewById(R.id.clearText)
//        searchResultContainer = findViewById(R.id.searchresult1)
//        usernameText = searchResultContainer.findViewById<TextView>(R.id.username)
//        fullnameText = searchResultContainer.findViewById<TextView>(R.id.fullname)
//        avatarImage = findViewById(R.id.searchresult)
//
//        // Clear the default text and set hint
//        searchEdit.text.clear()
//        searchEdit.hint = "Search username..."
//
//        // Hide results initially
//        searchResultContainer.visibility = View.GONE
//
//        // Clear button click
//        clearText.setOnClickListener {
//            searchEdit.text.clear()
//            searchResultContainer.visibility = View.GONE
//            foundUserEmail = null
//            foundUserUid = null
//        }
//
//        // Search on text change
//        searchEdit.addTextChangedListener(object : TextWatcher {
//            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
//
//            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
//                val query = s.toString().trim()
//                if (query.isNotEmpty()) {
//                    searchUser(query)
//                } else {
//                    searchResultContainer.visibility = View.GONE
//                    foundUserEmail = null
//                    foundUserUid = null
//                }
//            }
//
//            override fun afterTextChanged(s: Editable?) {}
//        })
//
//        // Search on enter key
//        searchEdit.setOnEditorActionListener { _, actionId, event ->
//            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
//                (event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
//                val query = searchEdit.text.toString().trim()
//                if (query.isNotEmpty()) {
//                    searchUser(query)
//                }
//                true
//            } else {
//                false
//            }
//        }
//
//        // Navigate to profile on click
//        searchResultContainer.setOnClickListener {
//            if (foundUserEmail != null && foundUserUid != null) {
//                val intent = Intent(this, activityprofile2::class.java)
//                intent.putExtra("USER_EMAIL", foundUserEmail)
//                intent.putExtra("USER_UID", foundUserUid)
//                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//                startActivity(intent)
//            }
//        }
//    }
//
//    private fun searchUser(query: String) {
//        // Search through all users in Firebase
//        database.addListenerForSingleValueEvent(object : ValueEventListener {
//            override fun onDataChange(snapshot: DataSnapshot) {
//                var found = false
//
//                // Iterate through all children (email keys)
//                for (emailSnapshot in snapshot.children) {
//                    val username = emailSnapshot.child("username").getValue(String::class.java)
//                    val email = emailSnapshot.child("email").getValue(String::class.java)
//                    val uid = emailSnapshot.child("uid").getValue(String::class.java)
//
//                    // Check if username matches (case-insensitive, partial match)
//                    if (username != null && username.lowercase().contains(query.lowercase())) {
//                        // User found
//                        displaySearchResult(username, email, uid, emailSnapshot.key)
//                        found = true
//                        break // Show only first match
//                    }
//                }
//
//                if (!found) {
//                    // No user found
//                    showNoResults()
//                }
//            }
//
//            override fun onCancelled(error: DatabaseError) {
//                Log.e("SearchBar", "Database error: ${error.message}")
//                Toast.makeText(this@searchbar, "Search error occurred", Toast.LENGTH_SHORT).show()
//            }
//        })
//    }
//
//    private fun displaySearchResult(username: String, email: String?, uid: String?, emailKey: String?) {
//        searchResultContainer.visibility = View.VISIBLE
//        searchResultContainer.isClickable = true
//
//        // Store user info for navigation
//        foundUserEmail = emailKey ?: email
//        foundUserUid = uid
//
//        // Display username
//        fullnameText.text = username
//        usernameText.text = email ?: "No email"
//
//        // Show online/offline status
//        if (emailKey != null) {
//            OnlineStatusManager.getUserStatus(emailKey) { isOnline, lastSeen ->
//                runOnUiThread {
//                    val statusIndicator = if (isOnline) " 🟢" else " ⚫"
//                    fullnameText.text = "$username$statusIndicator"
//                }
//            }
//        }
//
//        // You can load profile image here if you add profileImage field to Firebase
//        // For now, it will use the default image from XML
//    }
//
//    private fun showNoResults() {
//        searchResultContainer.visibility = View.VISIBLE
//        searchResultContainer.isClickable = false
//        foundUserEmail = null
//        foundUserUid = null
//
//        fullnameText.text = "Account not found"
//        usernameText.text = "No user matches your search"
//        avatarImage.setImageResource(R.drawable.profile10) // default image
//    }
//
//    override fun onBackPressed() {
//        super.onBackPressed()
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        Log.d("ActivityStack", "Searchbar onDestroy")
//    }
//}




package com.FEdev.i221279_i220809

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.*
import de.hdodenhof.circleimageview.CircleImageView

class searchbar : AppCompatActivity() {

    private lateinit var searchEdit: EditText
    private lateinit var clearText: TextView
    private lateinit var searchResultContainer: LinearLayout
    private lateinit var usernameText: TextView
    private lateinit var fullnameText: TextView
    private lateinit var avatarImage: CircleImageView
    private lateinit var database: DatabaseReference

    private var foundUserEmail: String? = null
    private var foundUserUid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_searchbar)
        Log.d("ActivityStack", "Searchbar onCreate")

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        database = FirebaseDatabase.getInstance().getReference("users")

        // Initialize views
        searchEdit = findViewById(R.id.searchEdit)
        clearText = findViewById(R.id.clearText)
        searchResultContainer = findViewById(R.id.searchresult1)
        usernameText = searchResultContainer.findViewById<TextView>(R.id.username)
        fullnameText = searchResultContainer.findViewById<TextView>(R.id.fullname)
        avatarImage = findViewById(R.id.searchresult)

        // Clear the default text and set hint
        searchEdit.text.clear()
        searchEdit.hint = "Search username..."

        // Hide results initially
        searchResultContainer.visibility = View.GONE

        // Set up click listener for search result BEFORE any other setup
        searchResultContainer.setOnClickListener {
            Log.d("SearchBar", "Search result clicked!")
            if (foundUserEmail != null && foundUserUid != null) {
                Log.d("SearchBar", "Navigating to profile - Email: $foundUserEmail, UID: $foundUserUid")
                val intent = Intent(this@searchbar, activityprofile2::class.java)
                intent.putExtra("USER_EMAIL", foundUserEmail)
                intent.putExtra("USER_UID", foundUserUid)
                startActivity(intent)
            } else {
                Log.e("SearchBar", "Cannot navigate - Email: $foundUserEmail, UID: $foundUserUid")
                Toast.makeText(this@searchbar, "Unable to open profile", Toast.LENGTH_SHORT).show()
            }
        }

        // Clear button click
        clearText.setOnClickListener {
            searchEdit.text.clear()
            searchResultContainer.visibility = View.GONE
            foundUserEmail = null
            foundUserUid = null
        }

        // Search on text change
        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (query.isNotEmpty()) {
                    searchUser(query)
                } else {
                    searchResultContainer.visibility = View.GONE
                    foundUserEmail = null
                    foundUserUid = null
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Search on enter key
        searchEdit.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                val query = searchEdit.text.toString().trim()
                if (query.isNotEmpty()) {
                    searchUser(query)
                }
                true
            } else {
                false
            }
        }
    }

    private fun searchUser(query: String) {
        // Search through all users in Firebase
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var found = false

                // Iterate through all children (email keys)
                for (emailSnapshot in snapshot.children) {
                    val username = emailSnapshot.child("username").getValue(String::class.java)
                    val email = emailSnapshot.child("email").getValue(String::class.java)
                    val uid = emailSnapshot.child("uid").getValue(String::class.java)

                    // Check if username matches (case-insensitive, partial match)
                    if (username != null && username.lowercase().contains(query.lowercase())) {
                        // User found
                        displaySearchResult(username, email, uid, emailSnapshot.key)
                        found = true
                        break // Show only first match
                    }
                }

                if (!found) {
                    // No user found
                    showNoResults()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SearchBar", "Database error: ${error.message}")
                Toast.makeText(this@searchbar, "Search error occurred", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun displaySearchResult(username: String, email: String?, uid: String?, emailKey: String?) {
        // Store user info FIRST before showing UI
        foundUserEmail = emailKey ?: email
        foundUserUid = uid

        Log.d("SearchBar", "Displaying result - Username: $username, Email: $foundUserEmail, UID: $foundUserUid")

        // Update UI
        fullnameText.text = username
        usernameText.text = email ?: "No email"

        // Make container visible and clickable
        searchResultContainer.visibility = View.VISIBLE
        searchResultContainer.isClickable = true
        searchResultContainer.isFocusable = true

        // Show online/offline status
        if (emailKey != null) {
            OnlineStatusManager.getUserStatus(emailKey) { isOnline, lastSeen ->
                runOnUiThread {
                    val statusIndicator = if (isOnline) " 🟢" else " ⚫"
                    fullnameText.text = "$username$statusIndicator"
                }
            }
        }

        // You can load profile image here if you add profileImage field to Firebase
        // For now, it will use the default image from XML
    }

    private fun showNoResults() {
        searchResultContainer.visibility = View.VISIBLE
        searchResultContainer.isClickable = false
        foundUserEmail = null
        foundUserUid = null

        fullnameText.text = "Account not found"
        usernameText.text = "No user matches your search"
        avatarImage.setImageResource(R.drawable.profile10) // default image
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ActivityStack", "Searchbar onDestroy")
    }
}