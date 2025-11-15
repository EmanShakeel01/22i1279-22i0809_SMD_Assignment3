// file: app/src/main/java/com/FEdev/i221279_i220809/adapters/ChatUserAdapter.kt
package com.FEdev.i221279_i220809

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.FEdev.i221279_i220809.R
import com.FEdev.i221279_i220809.User

class ChatUserAdapter(
    private val users: MutableList<User>,
    private val onClick: (User) -> Unit
) : RecyclerView.Adapter<ChatUserAdapter.VH>() {

    fun update(newUsers: List<User>) {
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
        val u = users[position]
        holder.name.text = u.name ?: (u.email ?: "User")
        holder.email.text = u.email ?: ""
        holder.itemView.setOnClickListener { onClick(u) }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: ImageView = v.findViewById(R.id.avatar)
        val name: TextView = v.findViewById(R.id.name)
        val email: TextView = v.findViewById(R.id.email)
    }
}