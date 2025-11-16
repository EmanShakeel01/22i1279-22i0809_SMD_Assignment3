package com.FEdev.i221279_i220809

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.FEdev.i221279_i220809.models.SearchUsersRequest
import com.FEdev.i221279_i220809.models.SearchUserResult
import com.FEdev.i221279_i220809.models.GetMultipleStatusesRequest
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.utils.SessionManager
import kotlinx.coroutines.launch

class chatlist : AppCompatActivity() {

    private lateinit var usersRecycler: RecyclerView
    private lateinit var searchInput: EditText
    private lateinit var adapter: ChatUserAdapter
    private lateinit var sessionManager: SessionManager

    private val allUsers = mutableListOf<ChatUser>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chatlist)

        sessionManager = SessionManager(this)

        findViewById<ImageView>(R.id.back).setOnClickListener { finish() }

        usersRecycler = findViewById(R.id.usersRecycler)
        usersRecycler.layoutManager = LinearLayoutManager(this)
        adapter = ChatUserAdapter(mutableListOf()) { user -> openChat(user) }
        usersRecycler.adapter = adapter

        searchInput = findViewById(R.id.search_message)
        searchInput.setOnFocusChangeListener { _: View, hasFocus: Boolean ->
            if (hasFocus && searchInput.text.isNullOrBlank()) {
                // Clear results when focused with empty text
                adapter.update(emptyList())
            }
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = Unit
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty().trim()
                if (query.length >= 2) {
                    searchUsers(query)
                } else if (query.isEmpty()) {
                    // Clear results when search is empty
                    allUsers.clear()
                    adapter.update(emptyList())
                }
            }
        })

        Log.d("ChatList", "Chat list initialized")
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
                        allUsers.clear()

                        // Convert SearchUserResult to ChatUser
                        val chatUsers = data.users.map { user ->
                            ChatUser(
                                userId = user.user_id,
                                username = user.username,
                                email = user.email,
                                fullname = user.fullname,
                                isOnline = false, // Will be updated from status API
                                lastMessage = null,
                                lastMessageTime = null,
                                unreadCount = 0
                            )
                        }

                        allUsers.addAll(chatUsers)

                        if (chatUsers.isEmpty()) {
                            adapter.update(emptyList())
                            Toast.makeText(
                                this@chatlist,
                                "No users found for \"$query\"",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            // Fetch online statuses for the users
                            fetchUserStatuses(chatUsers)
                        }

                        Log.d("ChatList", "✅ Found ${data.total} users")
                    }
                } else {
                    Log.e("ChatList", "Search failed: ${response.body()?.message}")
                    Toast.makeText(
                        this@chatlist,
                        response.body()?.message ?: "Search failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("ChatList", "Error searching users: ${e.message}", e)
                Toast.makeText(
                    this@chatlist,
                    "Network error",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun fetchUserStatuses(users: List<ChatUser>) {
        val authToken = sessionManager.getAuthToken() ?: return

        if (users.isEmpty()) return

        lifecycleScope.launch {
            try {
                val userIds = users.map { it.userId }
                val request = GetMultipleStatusesRequest(
                    auth_token = authToken,
                    user_ids = userIds
                )

                val response = RetrofitClient.apiService.getMultipleUserStatuses(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val statusData = response.body()?.data?.statuses

                    if (statusData != null) {
                        // Update online status for each user
                        statusData.forEach { status ->
                            allUsers.find { it.userId == status.user_id }?.isOnline = status.is_online
                        }

                        // Update adapter
                        adapter.update(allUsers)

                        Log.d("ChatList", "✅ Updated statuses for ${statusData.size} users")
                    }
                } else {
                    // Even if status fetch fails, show the users
                    adapter.update(allUsers)
                    Log.e("ChatList", "Failed to fetch statuses: ${response.body()?.message}")
                }
            } catch (e: Exception) {
                // Even if status fetch fails, show the users
                adapter.update(allUsers)
                Log.e("ChatList", "Error fetching statuses: ${e.message}", e)
            }
        }
    }

    private fun openChat(user: ChatUser) {
        Log.d("ChatList", "Opening chat with ${user.username} (ID: ${user.userId})")

        val intent = Intent(this, ChatInboxActivity::class.java).apply {
            putExtra("targetUid", user.userId.toString())
            putExtra("targetName", user.username)
            putExtra("targetEmail", user.email)
            putExtra("targetFullname", user.fullname)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ChatList", "Chat list destroyed")
    }
}

