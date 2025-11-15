package com.FEdev.i221279_i220809

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class chatadapter(
    private val context: Context,
    private val messages: MutableList<Message>,
    private val currentUserId: String,
    private val onEditClick: (Message) -> Unit,
    private val onDeleteClick: (Message) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_SENT = 1
    private val TYPE_RECEIVED = 2

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].senderId == currentUserId) TYPE_SENT else TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_SENT) {
            val view = LayoutInflater.from(context).inflate(R.layout.item_message_sent, parent, false)
            SentViewHolder(view)
        } else {
            val view = LayoutInflater.from(context).inflate(R.layout.item_message_received, parent, false)
            ReceivedViewHolder(view)
        }
    }

    override fun getItemCount(): Int = messages.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]

        // ✅ Handle system message
        if (message.messageType == "system") {
            if (holder is SentViewHolder) {
                holder.textMessage.visibility = View.VISIBLE
                holder.imageMessage.visibility = View.GONE
                holder.textMessage.text = message.messageText
                holder.textMessage.setTextColor(Color.BLACK)
                holder.textMessage.setBackgroundColor(Color.LTGRAY)
                holder.textMessage.textAlignment = View.TEXT_ALIGNMENT_CENTER
            } else if (holder is ReceivedViewHolder) {
                holder.textMessage.visibility = View.VISIBLE
                holder.imageMessage.visibility = View.GONE
                holder.textMessage.text = message.messageText
                holder.textMessage.setTextColor(Color.BLACK)
                holder.textMessage.setBackgroundColor(Color.LTGRAY)
                holder.textMessage.textAlignment = View.TEXT_ALIGNMENT_CENTER
            }
            return
        }

        val timeSince = System.currentTimeMillis() - message.timestamp
        val canEditOrDelete = timeSince <= 5 * 60 * 1000 // 5 minutes

        if (holder is SentViewHolder) {
            holder.textMessage.text = message.messageText ?: ""
            holder.textMessage.visibility =
                if (message.messageType == "text" && !message.messageText.isNullOrEmpty()) View.VISIBLE else View.GONE

            if (message.messageType == "image" && !message.imageUrl.isNullOrEmpty()) {
                holder.imageMessage.visibility = View.VISIBLE
                try {
                    val imageBytes = android.util.Base64.decode(message.imageUrl, android.util.Base64.DEFAULT)
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    holder.imageMessage.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    Log.e("chatadapter", "Error decoding Base64 image", e)
                    holder.imageMessage.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            } else {
                holder.imageMessage.visibility = View.GONE
            }

            if (canEditOrDelete && message.senderId == currentUserId) {
                holder.itemView.setOnLongClickListener {
                    if (message.messageType == "text") onEditClick(message)
                    true
                }
                holder.itemView.setOnClickListener { onDeleteClick(message) }
            } else {
                holder.itemView.setOnLongClickListener(null)
                holder.itemView.setOnClickListener(null)
            }
        } else if (holder is ReceivedViewHolder) {
            holder.textMessage.text = message.messageText ?: ""
            holder.textMessage.visibility =
                if (message.messageType == "text" && !message.messageText.isNullOrEmpty()) View.VISIBLE else View.GONE

            if (message.messageType == "image" && !message.imageUrl.isNullOrEmpty()) {
                holder.imageMessage.visibility = View.VISIBLE
                try {
                    val imageBytes = android.util.Base64.decode(message.imageUrl, android.util.Base64.DEFAULT)
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    holder.imageMessage.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    Log.e("chatadapter", "Error decoding Base64 image", e)
                    holder.imageMessage.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            } else {
                holder.imageMessage.visibility = View.GONE
            }
        }
    }

    class SentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textMessage: TextView = view.findViewById(R.id.textMessage)
        val imageMessage: ImageView = view.findViewById(R.id.imageMessage)
    }

    class ReceivedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textMessage: TextView = view.findViewById(R.id.textMessage)
        val imageMessage: ImageView = view.findViewById(R.id.imageMessage)
    }
}
