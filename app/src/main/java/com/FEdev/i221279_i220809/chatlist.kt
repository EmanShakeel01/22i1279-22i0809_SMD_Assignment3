package com.FEdev.i221279_i220809

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.FEdev.i221279_i220809.models.*
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.utils.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class chatlist : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var backButton: ImageView
    private lateinit var clearSearchButton: ImageView
    private lateinit var threadsRecycler: RecyclerView
    private lateinit var searchResultsRecycler: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var emptyStateView: TextView
    private lateinit var loadingIndicator: ProgressBar

    private lateinit var sessionManager: SessionManager
    private lateinit var threadsAdapter: ChatThreadAdapter
    private lateinit var searchAdapter: SearchResultsAdapter

    private val chatThreads = mutableListOf<ChatThread>()
    private val searchResults = mutableListOf<SearchUserResult>()

    private var autoRefreshJob: Job? = null
    private var isSearching = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chatlist)

        Log.d("ChatList", "=== ChatList onCreate ===")

        sessionManager = SessionManager(this)

        initViews()
        setupRecyclers()
        setupSearch()
        setupSwipeRefresh()

        loadChatThreads()
        startAutoRefresh()

        Log.d("ChatList", "Chat list initialized")
    }

    private fun initViews() {
        searchInput = findViewById(R.id.search_message)
        backButton = findViewById(R.id.back)
        clearSearchButton = findViewById(R.id.clearSearch)
        threadsRecycler = findViewById(R.id.threadsRecycler)
        searchResultsRecycler = findViewById(R.id.searchResultsRecycler)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        emptyStateView = findViewById(R.id.emptyStateText)
        loadingIndicator = findViewById(R.id.loadingIndicator) ?: ProgressBar(this).apply {
            visibility = View.GONE
        }

        backButton.setOnClickListener { finish() }
        clearSearchButton.setOnClickListener {
            searchInput.text.clear()
            hideSearch()
        }

        searchResultsRecycler.visibility = View.GONE
        clearSearchButton.visibility = View.GONE
    }

    private fun setupRecyclers() {
        threadsAdapter = ChatThreadAdapter(chatThreads) { thread ->
            openChat(thread)
        }
        threadsRecycler.layoutManager = LinearLayoutManager(this)
        threadsRecycler.adapter = threadsAdapter

        searchAdapter = SearchResultsAdapter(searchResults) { user ->
            openChatWithUser(user)
        }
        searchResultsRecycler.layoutManager = LinearLayoutManager(this)
        searchResultsRecycler.adapter = searchAdapter
    }

    private fun setupSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = Unit
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty().trim()

                if (query.isEmpty()) {
                    hideSearch()
                } else {
                    clearSearchButton.visibility = View.VISIBLE
                    if (query.length >= 2) {
                        showSearch()
                        searchUsers(query)
                    }
                }
            }
        })

        searchInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && searchInput.text.toString().trim().length >= 2) {
                showSearch()
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            loadChatThreads()
        }

        swipeRefresh.setColorSchemeResources(
            R.color.pink,
            R.color.red,
            R.color.darkbrown
        )
    }

    // ==================== LOAD CHAT THREADS ====================

    private fun loadChatThreads() {
        val authToken = sessionManager.getAuthToken()

        Log.d("ChatList", "=== Loading Chat Threads ===")
        Log.d("ChatList", "Auth token: ${authToken?.take(10)}...")

        if (authToken == null) {
            Log.e("ChatList", "❌ No auth token")
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            swipeRefresh.isRefreshing = false
            return
        }

        if (!swipeRefresh.isRefreshing) {
            loadingIndicator.visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            try {
                val request = GetChatThreadsRequest(auth_token = authToken)

                Log.d("ChatList", "📡 Making API call to get_chat_threads.php...")

                val response = RetrofitClient.apiService.getChatThreads(request)

                Log.d("ChatList", "Response code: ${response.code()}")
                Log.d("ChatList", "Response successful: ${response.isSuccessful}")

                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string()
                    Log.e("ChatList", "❌ Server error: $errorBody")
                    runOnUiThread {
                        Toast.makeText(
                            this@chatlist,
                            "Failed to load chats",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                val body = response.body()
                Log.d("ChatList", "Response body success: ${body?.success}")
                Log.d("ChatList", "Response body message: ${body?.message}")

                if (body?.success == true) {
                    val data = body.data

                    if (data != null) {
                        Log.d("ChatList", "✅ Received ${data.threads.size} threads")

                        runOnUiThread {
                            chatThreads.clear()
                            chatThreads.addAll(data.threads)
                            threadsAdapter.notifyDataSetChanged()

                            if (chatThreads.isEmpty()) {
                                emptyStateView.visibility = View.VISIBLE
                                emptyStateView.text = "No conversations yet\nSearch for users to start chatting"
                                threadsRecycler.visibility = View.GONE
                            } else {
                                emptyStateView.visibility = View.GONE
                                threadsRecycler.visibility = View.VISIBLE
                            }
                        }
                    } else {
                        Log.e("ChatList", "❌ Response data is null")
                    }
                } else {
                    Log.e("ChatList", "❌ API returned success=false: ${body?.message}")
                    runOnUiThread {
                        Toast.makeText(
                            this@chatlist,
                            body?.message ?: "Failed to load chats",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: com.google.gson.JsonSyntaxException) {
                Log.e("ChatList", "❌ JSON Parse Error: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(
                        this@chatlist,
                        "Server returned invalid data",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("ChatList", "❌ Error loading threads: ${e.message}", e)
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(
                        this@chatlist,
                        "Network error",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                runOnUiThread {
                    swipeRefresh.isRefreshing = false
                    loadingIndicator.visibility = View.GONE
                }
            }
        }
    }

    // ==================== AUTO REFRESH ====================

    private fun startAutoRefresh() {
        autoRefreshJob = lifecycleScope.launch {
            while (true) {
                delay(10000) // Refresh every 10 seconds
                if (!isSearching && !swipeRefresh.isRefreshing) {
                    loadChatThreads()
                }
            }
        }
    }

    // ==================== SEARCH USERS ====================

    private fun searchUsers(query: String) {
        val authToken = sessionManager.getAuthToken()

        if (authToken == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        isSearching = true

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
                        searchAdapter.notifyDataSetChanged()

                        if (searchResults.isEmpty()) {
                            emptyStateView.visibility = View.VISIBLE
                            emptyStateView.text = "No users found for \"$query\""
                            searchResultsRecycler.visibility = View.GONE
                        } else {
                            emptyStateView.visibility = View.GONE
                            searchResultsRecycler.visibility = View.VISIBLE

                            fetchUserStatuses()
                        }

                        Log.d("ChatList", "✅ Found ${data.total} users")
                    }
                } else {
                    Log.e("ChatList", "Search failed: ${response.body()?.message}")
                }
            } catch (e: Exception) {
                Log.e("ChatList", "Error searching users: ${e.message}", e)
            } finally {
                isSearching = false
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

                val response = RetrofitClient.apiService.getMultipleUserStatuses(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val statusData = response.body()?.data?.statuses

                    if (statusData != null) {
                        val statusMap = statusData.associate {
                            it.user_id to it.is_online
                        }

                        searchAdapter.updateStatuses(statusMap)
                        Log.d("ChatList", "✅ Updated statuses for ${statusData.size} users")
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatList", "Error fetching statuses: ${e.message}", e)
            }
        }
    }

    // ==================== NAVIGATION ====================

    private fun openChat(thread: ChatThread) {
        Log.d("ChatList", "=== Opening chat ===")
        Log.d("ChatList", "Other user: ${thread.other_username}")
        Log.d("ChatList", "Other user ID: ${thread.other_user_id}")

        val intent = Intent(this, ChatInboxActivity::class.java).apply {
            putExtra("targetUserId", thread.other_user_id)
            putExtra("targetName", thread.other_username)
            putExtra("targetEmail", thread.other_email)
        }
        startActivity(intent)
    }

    private fun openChatWithUser(user: SearchUserResult) {
        Log.d("ChatList", "=== Opening new chat ===")
        Log.d("ChatList", "User: ${user.username}")
        Log.d("ChatList", "User ID: ${user.user_id}")

        val intent = Intent(this, ChatInboxActivity::class.java).apply {
            putExtra("targetUserId", user.user_id)
            putExtra("targetName", user.username)
            putExtra("targetEmail", user.email)
            putExtra("targetFullname", user.fullname)
        }
        startActivity(intent)

        hideSearch()
    }

    // ==================== SHOW/HIDE SEARCH ====================

    private fun showSearch() {
        threadsRecycler.visibility = View.GONE
        searchResultsRecycler.visibility = View.VISIBLE
        emptyStateView.visibility = View.GONE
    }

    private fun hideSearch() {
        searchInput.text.clear()
        searchResults.clear()
        searchAdapter.notifyDataSetChanged()

        searchResultsRecycler.visibility = View.GONE
        clearSearchButton.visibility = View.GONE

        if (chatThreads.isEmpty()) {
            emptyStateView.visibility = View.VISIBLE
            emptyStateView.text = "No conversations yet\nSearch for users to start chatting"
            threadsRecycler.visibility = View.GONE
        } else {
            emptyStateView.visibility = View.GONE
            threadsRecycler.visibility = View.VISIBLE
        }

        searchInput.clearFocus()
    }

    // ==================== LIFECYCLE ====================

    override fun onResume() {
        super.onResume()
        Log.d("ChatList", "onResume - Reloading threads")
        loadChatThreads()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoRefreshJob?.cancel()
        Log.d("ChatList", "Chat list destroyed")
    }
}