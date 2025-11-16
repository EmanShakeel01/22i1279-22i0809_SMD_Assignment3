package com.FEdev.i221279_i220809

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatUserAdapter(
    private val users: MutableList<ChatUser>,
    private val onClick: (ChatUser) -> Unit
) : RecyclerView.Adapter<ChatUserAdapter.VH>() {

    fun update(newUsers: List<ChatUser>) {
        users.clear()
        users.addAll(newUsers)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_user_row, parent, false)
        return VH(v)
    }

    override fun getItemCount() = users.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val user = users[position]

        // Set name (prefer fullname over username)
        holder.name.text = user.fullname?.takeIf { it.isNotEmpty() } ?: user.username

        // Set email
        holder.email.text = user.email

        // Set online/offline status
        holder.statusIndicator.setBackgroundResource(
            if (user.isOnline) R.drawable.status_dot_online else R.drawable.status_dot_offline
        )

        // Set default avatar
        holder.avatar.setImageResource(R.drawable.mystory)

        // Click listener
        holder.itemView.setOnClickListener { onClick(user) }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: ImageView = v.findViewById(R.id.avatar)
        val name: TextView = v.findViewById(R.id.name)
        val email: TextView = v.findViewById(R.id.email)
        val statusIndicator: View = v.findViewById(R.id.statusIndicator)
    }
}

data class ChatUser(
    val userId: Int,
    val username: String,
    val email: String,
    val fullname: String?,
    var isOnline: Boolean = false,
    val lastMessage: String? = null,
    val lastMessageTime: Long? = null,
    val unreadCount: Int = 0
)