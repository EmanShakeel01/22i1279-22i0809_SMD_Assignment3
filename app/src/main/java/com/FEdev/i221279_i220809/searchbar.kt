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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.FEdev.i221279_i220809.models.SearchUsersRequest
import com.FEdev.i221279_i220809.models.SearchUserResult
import com.FEdev.i221279_i220809.models.GetMultipleStatusesRequest
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.utils.SessionManager
import kotlinx.coroutines.launch

class searchbar : AppCompatActivity() {

    private lateinit var searchEdit: EditText
    private lateinit var clearText: TextView
    private lateinit var searchResultsRecycler: RecyclerView
    private lateinit var noResultsText: TextView
    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: SearchResultsAdapter
    private val searchResults = mutableListOf<SearchUserResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_searchbar)
        Log.d("ActivityStack", "Searchbar onCreate")

        sessionManager = SessionManager(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views
        searchEdit = findViewById(R.id.searchEdit)
        clearText = findViewById(R.id.clearText)
        searchResultsRecycler = findViewById(R.id.searchResultsRecycler)
        noResultsText = findViewById(R.id.noResultsText)

        // Setup RecyclerView
        setupRecyclerView()

        // Clear the default text and set hint
        searchEdit.text.clear()
        searchEdit.hint = "Search username..."

        // Hide results initially
        searchResultsRecycler.visibility = View.GONE
        noResultsText.visibility = View.GONE

        // Clear button click
        clearText.setOnClickListener {
            searchEdit.text.clear()
            searchResults.clear()
            adapter.notifyDataSetChanged()
            searchResultsRecycler.visibility = View.GONE
            noResultsText.visibility = View.GONE
        }

        // Search on text change
        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (query.length >= 2) {
                    searchUsers(query)
                } else {
                    searchResults.clear()
                    adapter.notifyDataSetChanged()
                    searchResultsRecycler.visibility = View.GONE
                    noResultsText.visibility = View.GONE
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
                    searchUsers(query)
                }
                true
            } else {
                false
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = SearchResultsAdapter(searchResults) { user ->
            openUserProfile(user)
        }
        searchResultsRecycler.layoutManager = LinearLayoutManager(this)
        searchResultsRecycler.adapter = adapter
    }

    private fun searchUsers(query: String) {
        val authToken = sessionManager.getAuthToken()
        if (authToken == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val request = SearchUsersRequest(
                    auth_token = authToken,
                    search_query = query
                )

                val response = RetrofitClient.apiService.searchUsers(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    if (data != null) {
                        searchResults.clear()
                        searchResults.addAll(data.users)

                        if (searchResults.isEmpty()) {
                            searchResultsRecycler.visibility = View.GONE
                            noResultsText.visibility = View.VISIBLE
                            noResultsText.text = "No users found for \"$query\""
                        } else {
                            // First update the list without statuses
                            adapter.notifyDataSetChanged()
                            searchResultsRecycler.visibility = View.VISIBLE
                            noResultsText.visibility = View.GONE

                            // Then fetch and update statuses
                            fetchUserStatuses()
                        }

                        Log.d("SearchBar", "✅ Found ${data.total} users")
                    }
                } else {
                    Log.e("SearchBar", "Search failed: ${response.body()?.message}")
                    Toast.makeText(
                        this@searchbar,
                        response.body()?.message ?: "Search failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("SearchBar", "Error searching users: ${e.message}", e)
                Toast.makeText(
                    this@searchbar,
                    "Network error",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun fetchUserStatuses() {
        val authToken = sessionManager.getAuthToken() ?: return
        if (searchResults.isEmpty()) return

        lifecycleScope.launch {
            try {
                val userIds = searchResults.map { it.user_id }
                val request = GetMultipleStatusesRequest(
                    auth_token = authToken,
                    user_ids = userIds
                )

                Log.d("SearchBar", "📡 Fetching statuses for ${userIds.size} users: $userIds")

                val response = RetrofitClient.apiService.getMultipleUserStatuses(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val statusData = response.body()?.data?.statuses
                    if (statusData != null) {
                        val statusMap = statusData.associate { it.user_id to it.is_online }

                        Log.d("SearchBar", "✅ Received statuses: $statusMap")

                        // Update adapter with statuses
                        adapter.updateStatuses(statusMap)

                        Log.d("SearchBar", "✅ Updated statuses for ${statusData.size} users")
                    } else {
                        Log.w("SearchBar", "⚠️ Status data is null")
                    }
                } else {
                    Log.e("SearchBar", "❌ Failed to fetch statuses: ${response.body()?.message}")
                }
            } catch (e: Exception) {
                Log.e("SearchBar", "❌ Error fetching statuses: ${e.message}", e)
            }
        }
    }

    private fun openUserProfile(user: SearchUserResult) {
        Log.d("SearchBar", "Opening profile for: ${user.username} (ID: ${user.user_id})")
        val intent = Intent(this, activityprofile2::class.java)
        intent.putExtra("USER_ID", user.user_id)
        intent.putExtra("USERNAME", user.username)
        intent.putExtra("EMAIL", user.email)
        intent.putExtra("FULLNAME", user.fullname)
        startActivity(intent)
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ActivityStack", "Searchbar onDestroy")
    }
}