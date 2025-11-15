package com.FEdev.i221279_i220809

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import de.hdodenhof.circleimageview.CircleImageView

class FollowRequestsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var auth: FirebaseAuth
    private val requestsList = mutableListOf<FollowRequest>()
    private lateinit var adapter: FollowRequestsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_follow_requests)

        auth = FirebaseAuth.getInstance()
        val currentUserId = auth.currentUser?.uid ?: return

        recyclerView = findViewById(R.id.requestsRecyclerView)
        emptyText = findViewById(R.id.emptyText)

        adapter = FollowRequestsAdapter(requestsList, currentUserId)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadPendingRequests(currentUserId)

        findViewById<View>(R.id.backButton).setOnClickListener {
            finish()
        }
    }

    private fun loadPendingRequests(userId: String) {
        FollowRequestManager.getPendingRequests(userId) { requests ->
            requestsList.clear()
            requestsList.addAll(requests)
            adapter.notifyDataSetChanged()

            if (requests.isEmpty()) {
                emptyText.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyText.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

    inner class FollowRequestsAdapter(
        private val requests: List<FollowRequest>,
        private val currentUserId: String
    ) : RecyclerView.Adapter<FollowRequestsAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val profileImage: CircleImageView = view.findViewById(R.id.profileImage)
            val username: TextView = view.findViewById(R.id.username)
            val acceptBtn: Button = view.findViewById(R.id.acceptBtn)
            val rejectBtn: Button = view.findViewById(R.id.rejectBtn)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_follow_request, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val request = requests[position]

            holder.username.text = request.fromUsername
            holder.profileImage.setImageResource(R.drawable.profile10)

            holder.acceptBtn.setOnClickListener {
                FollowRequestManager.acceptFollowRequest(
                    request.requestId,
                    request.fromUserId,
                    currentUserId,
                    onSuccess = {
                        Toast.makeText(holder.itemView.context, "Request accepted", Toast.LENGTH_SHORT).show()
                        loadPendingRequests(currentUserId)
                    },
                    onFailure = { error ->
                        Toast.makeText(holder.itemView.context, "Error: $error", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            holder.rejectBtn.setOnClickListener {
                FollowRequestManager.rejectFollowRequest(
                    request.requestId,
                    currentUserId,
                    onSuccess = {
                        Toast.makeText(holder.itemView.context, "Request rejected", Toast.LENGTH_SHORT).show()
                        loadPendingRequests(currentUserId)
                    },
                    onFailure = { error ->
                        Toast.makeText(holder.itemView.context, "Error: $error", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        override fun getItemCount() = requests.size
    }
}