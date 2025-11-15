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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class chatlist : AppCompatActivity() {

    private lateinit var usersRecycler: RecyclerView
    private lateinit var searchInput: EditText
    private lateinit var adapter: ChatUserAdapter

    private val rtdb = FirebaseDatabase.getInstance(
        "https://i1279-22i0809-assignment2-default-rtdb.firebaseio.com/"
    ).reference

    private val allUsers = mutableListOf<User>()
    private val conversationsMap = mutableMapOf<String, ConversationData>()

    private var currentUid: String = ""
    private var usersListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chatlist)

        currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        Log.d("ChatList", "Current UID: $currentUid")

        findViewById<ImageView>(R.id.back).setOnClickListener { finish() }

        usersRecycler = findViewById(R.id.usersRecycler)
        usersRecycler.layoutManager = LinearLayoutManager(this)
        adapter = ChatUserAdapter(mutableListOf()) { user -> openChat(user) }
        usersRecycler.adapter = adapter

        searchInput = findViewById(R.id.search_message)
        searchInput.setOnFocusChangeListener { _: View, hasFocus: Boolean ->
            if (hasFocus && searchInput.text.isNullOrBlank()) filterUsers("")
        }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = Unit
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty()
                filterUsers(query)
            }
        })

        attachUsersListener()
        listenToConversations()
    }

    override fun onDestroy() {
        super.onDestroy()
        usersListener?.let { rtdb.child("users").removeEventListener(it) }
    }

    private fun attachUsersListener() {
        val ref = rtdb.child("users")
        usersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allUsers.clear()

                for (child in snapshot.children) {
                    val uid = child.child("uid").getValue(String::class.java)?.trim() ?: ""
                    val email = child.child("email").getValue(String::class.java)?.trim() ?: ""
                    val username = child.child("username").getValue(String::class.java)?.trim() ?: ""

                    if (uid.isEmpty() || uid == currentUid) continue

                    val displayName = if (username.isNotEmpty()) username else email.substringBefore("@")

                    allUsers.add(
                        User(
                            uid = uid,
                            name = displayName,
                            email = email,
                            avatarUrl = null,
                            nameLower = displayName.lowercase()
                        )
                    )
                }

                Log.d("ChatList", "Total users loaded: ${allUsers.size}")
                filterUsers(searchInput.text?.toString().orEmpty())
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatList", "Firebase error: ${error.message}")
                Toast.makeText(this@chatlist, "Error loading users: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
        ref.addValueEventListener(usersListener as ValueEventListener)
    }

    private fun listenToConversations() {
        rtdb.child("chats").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                conversationsMap.clear()

                for (chatSnapshot in snapshot.children) {
                    val chatId = chatSnapshot.key ?: continue

                    // Extract the other user's UID from chatId (format: uid1_uid2)
                    val parts = chatId.split("_")
                    if (parts.size != 2) continue

                    val otherUid = when {
                        parts[0] == currentUid -> parts[1]
                        parts[1] == currentUid -> parts[0]
                        else -> continue
                    }

                    // Get the last message from this conversation
                    val messagesSnapshot = chatSnapshot.child("messages")
                    if (messagesSnapshot.exists()) {
                        var lastMsg: Message? = null
                        var latestTimestamp = 0L

                        for (msgSnapshot in messagesSnapshot.children) {
                            val msg = msgSnapshot.getValue(Message::class.java)
                            if (msg != null && msg.timestamp > latestTimestamp) {
                                lastMsg = msg
                                latestTimestamp = msg.timestamp
                            }
                        }

                        if (lastMsg != null) {
                            conversationsMap[otherUid] = ConversationData(
                                lastMessage = lastMsg.messageText ?: "[Image]",
                                timestamp = lastMsg.timestamp,
                                unread = lastMsg.senderId != currentUid
                            )
                        }
                    }
                }

                Log.d("ChatList", "Conversations loaded: ${conversationsMap.size}")
                filterUsers(searchInput.text?.toString().orEmpty())
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatList", "Error loading conversations: ${error.message}")
            }
        })
    }

    private fun filterUsers(query: String) {
        val q = query.trim().lowercase()

        val filteredUsers = if (q.isBlank()) {
            allUsers
        } else {
            allUsers.filter { u ->
                u.nameLower.contains(q) || (u.email?.lowercase()?.contains(q) == true)
            }
        }

        // Sort users: those with conversations first, sorted by most recent
        val sorted = filteredUsers.sortedByDescending { user ->
            conversationsMap[user.uid]?.timestamp ?: 0L
        }

        Log.d("ChatList", "Filtered results: ${sorted.size} users")
        adapter.update(sorted)
    }

    private fun openChat(user: User) {
        Log.d("ChatList", "Opening chat with ${user.name} (${user.uid})")
        val intent = Intent(this, ChatInboxActivity::class.java).apply {
            putExtra("targetUid", user.uid)
            putExtra("targetName", user.name)
            putExtra("targetAvatarUrl", user.avatarUrl)
        }
        startActivity(intent)
    }
}

data class ConversationData(
    val lastMessage: String,
    val timestamp: Long,
    val unread: Boolean
)