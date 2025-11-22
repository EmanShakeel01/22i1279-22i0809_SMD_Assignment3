package com.FEdev.i221279_i220809

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.FEdev.i221279_i220809.models.*
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.utils.SessionManager
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity to display screenshot notifications
 * Shows when other users take screenshots of chats
 */
class ScreenshotNotificationsActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var adapter: ScreenshotNotificationAdapter

    private val notifications = mutableListOf<ScreenshotNotification>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screenshot_notifications)

        sessionManager = SessionManager(this)

        // Initialize views
        recyclerView = findViewById(R.id.notificationsRecycler)
        emptyStateText = findViewById(R.id.emptyStateText)

        val backButton = findViewById<ImageView>(R.id.backButton)
        backButton.setOnClickListener { finish() }

        // Setup RecyclerView
        adapter = ScreenshotNotificationAdapter(notifications)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Load notifications
        loadNotifications()
    }

    private fun loadNotifications() {
        val authToken = sessionManager.getAuthToken()

        if (authToken == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                val request = GetScreenshotNotificationsRequest(
                    auth_token = authToken,
                    mark_as_read = true // Mark as read when viewing
                )

                val response = RetrofitClient.apiService.getScreenshotNotifications(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data

                    if (data != null) {
                        notifications.clear()
                        notifications.addAll(data.notifications)
                        adapter.notifyDataSetChanged()

                        if (notifications.isEmpty()) {
                            recyclerView.visibility = View.GONE
                            emptyStateText.visibility = View.VISIBLE
                            emptyStateText.text = "No screenshot alerts"
                        } else {
                            recyclerView.visibility = View.VISIBLE
                            emptyStateText.visibility = View.GONE
                        }

                        Log.d("ScreenshotNotifications", "✅ Loaded ${data.total} notifications")
                    }
                } else {
                    Toast.makeText(
                        this@ScreenshotNotificationsActivity,
                        response.body()?.message ?: "Failed to load notifications",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("ScreenshotNotifications", "❌ Error: ${e.message}", e)
                Toast.makeText(
                    this@ScreenshotNotificationsActivity,
                    "Network error",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Adapter for screenshot notifications
     */
    inner class ScreenshotNotificationAdapter(
        private val notifications: List<ScreenshotNotification>
    ) : RecyclerView.Adapter<ScreenshotNotificationAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val profileImage: CircleImageView = view.findViewById(R.id.profileImage)
            val usernameText: TextView = view.findViewById(R.id.usernameText)
            val messageText: TextView = view.findViewById(R.id.messageText)
            val timestampText: TextView = view.findViewById(R.id.timestampText)
            val unreadIndicator: View = view.findViewById(R.id.unreadIndicator)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_screenshot_notification, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val notification = notifications[position]

            holder.usernameText.text = notification.screenshot_taker_username
            holder.messageText.text = "took a screenshot of your chat"
            holder.timestampText.text = formatTimestamp(notification.timestamp)

            // Show/hide unread indicator
            holder.unreadIndicator.visibility = if (notification.is_read) {
                View.GONE
            } else {
                View.VISIBLE
            }

            // Default profile image
            holder.profileImage.setImageResource(R.drawable.mystory)
        }

        override fun getItemCount(): Int = notifications.size

        private fun formatTimestamp(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < 60000 -> "Just now"
                diff < 3600000 -> "${diff / 60000}m ago"
                diff < 86400000 -> "${diff / 3600000}h ago"
                diff < 604800000 -> "${diff / 86400000}d ago"
                else -> {
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    dateFormat.format(Date(timestamp))
                }
            }
        }
    }
}