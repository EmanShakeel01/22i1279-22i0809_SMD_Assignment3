package com.FEdev.i221279_i220809

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.FEdev.i221279_i220809.models.SearchUserResult
import de.hdodenhof.circleimageview.CircleImageView

class SearchResultsAdapter(
    private val users: MutableList<SearchUserResult>,
    private val onClick: (SearchUserResult) -> Unit
) : RecyclerView.Adapter<SearchResultsAdapter.SearchResultViewHolder>() {

    // Map to store online status for each user
    private val userStatusMap = mutableMapOf<Int, Boolean>()

    inner class SearchResultViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: CircleImageView = view.findViewById(R.id.searchresult)
        val username: TextView = view.findViewById(R.id.username)
        val fullname: TextView = view.findViewById(R.id.fullname)
        val statusIndicator: View = view.findViewById(R.id.statusIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return SearchResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        val user = users[position]

        holder.username.text = user.username
        holder.fullname.text = user.fullname ?: user.email

        // Set default avatar
        holder.avatar.setImageResource(R.drawable.mystory)

        // Set online/offline status
        val isOnline = userStatusMap[user.user_id] ?: false
        holder.statusIndicator.setBackgroundResource(
            if (isOnline) R.drawable.status_dot_online else R.drawable.status_dot_offline
        )

        // Make status indicator visible
        holder.statusIndicator.visibility = View.VISIBLE

        // Click listener
        holder.itemView.setOnClickListener {
            onClick(user)
        }
    }

    override fun getItemCount(): Int = users.size

    // Public method to update multiple user statuses at once
    fun updateStatuses(statuses: Map<Int, Boolean>) {
        userStatusMap.clear()
        userStatusMap.putAll(statuses)
        notifyDataSetChanged()
    }

    // Public method to update single user status
    fun updateUserStatus(userId: Int, isOnline: Boolean) {
        userStatusMap[userId] = isOnline
        val position = users.indexOfFirst { it.user_id == userId }
        if (position != -1) {
            notifyItemChanged(position)
        }
    }
}