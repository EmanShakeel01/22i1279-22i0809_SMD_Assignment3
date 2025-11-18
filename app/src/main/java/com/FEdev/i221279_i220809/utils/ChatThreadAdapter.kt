package com.FEdev.i221279_i220809

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.FEdev.i221279_i220809.models.ChatThread
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.*

class ChatThreadAdapter(
    private val threads: MutableList<ChatThread>,
    private val onClick: (ChatThread) -> Unit
) : RecyclerView.Adapter<ChatThreadAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: CircleImageView = view.findViewById(R.id.threadAvatar)
        val username: TextView = view.findViewById(R.id.threadUsername)
        val lastMessage: TextView = view.findViewById(R.id.threadLastMessage)
        val timestamp: TextView = view.findViewById(R.id.threadTimestamp)
        val unreadBadge: TextView = view.findViewById(R.id.threadUnreadBadge)
        val vanishIndicator: View? = view.findViewById(R.id.threadVanishIndicator)
        val statusIndicator: View = view.findViewById(R.id.threadStatusIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_thread, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val thread = threads[position]

        // Username
        holder.username.text = thread.other_username

        // Last message preview
        holder.lastMessage.text = thread.last_message_preview

        // Timestamp
        holder.timestamp.text = formatTimestamp(thread.last_message_timestamp)

        // Unread badge
        if (thread.unread_count > 0) {
            holder.unreadBadge.visibility = View.VISIBLE
            holder.unreadBadge.text = if (thread.unread_count > 99) {
                "99+"
            } else {
                thread.unread_count.toString()
            }

            // Bold text for unread messages
            holder.username.setTypeface(null, android.graphics.Typeface.BOLD)
            holder.lastMessage.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
            holder.unreadBadge.visibility = View.GONE
            holder.username.setTypeface(null, android.graphics.Typeface.NORMAL)
            holder.lastMessage.setTypeface(null, android.graphics.Typeface.NORMAL)
        }

        // Vanish mode indicator
        if (thread.vanish_mode) {
            holder.vanishIndicator?.visibility = View.VISIBLE
        } else {
            holder.vanishIndicator?.visibility = View.GONE
        }

        // Default avatar
        holder.avatar.setImageResource(R.drawable.mystory)

        // Online/offline status (you can integrate with OnlineStatusManager)
        holder.statusIndicator.visibility = View.GONE

        // Click listener
        holder.itemView.setOnClickListener {
            onClick(thread)
        }
    }

    override fun getItemCount(): Int = threads.size

    fun updateThreads(newThreads: List<ChatThread>) {
        threads.clear()
        threads.addAll(newThreads)
        notifyDataSetChanged()
    }

    fun addThread(thread: ChatThread) {
        threads.add(0, thread)
        notifyItemInserted(0)
    }

    fun updateThread(threadId: String, lastMessage: String, timestamp: Long, unreadCount: Int) {
        val index = threads.indexOfFirst { it.thread_id == threadId }
        if (index != -1) {
            threads[index] = threads[index].copy(
                last_message_preview = lastMessage,
                last_message_timestamp = timestamp,
                unread_count = unreadCount
            )

            // Move to top
            val thread = threads.removeAt(index)
            threads.add(0, thread)

            notifyItemMoved(index, 0)
            notifyItemChanged(0)
        }
    }

    private fun formatTimestamp(timestamp: Long?): String {
        if (timestamp == null) return ""

        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000}m"
            diff < 86400000 -> "${diff / 3600000}h"
            diff < 604800000 -> "${diff / 86400000}d"
            diff < 2592000000 -> "${diff / 604800000}w"
            else -> {
                val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                dateFormat.format(Date(timestamp))
            }
        }
    }
}