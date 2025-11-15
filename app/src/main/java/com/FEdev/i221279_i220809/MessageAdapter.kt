package com.FEdev.i221279_i220809

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val messages: MutableList<Message2>,
    private val onMessageLongClick: (Message2, Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_SENT = 1
        const val VIEW_TYPE_RECEIVED = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isSentByMe) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_sent2, parent, false)
            SentMessageViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_received2, parent, false)
            ReceivedMessageViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]

        if (holder is SentMessageViewHolder) {
            holder.bind(message, position)
        } else if (holder is ReceivedMessageViewHolder) {
            holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    inner class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val messageImage: ImageView = itemView.findViewById(R.id.messageImage)
        private val messageTime: TextView = itemView.findViewById(R.id.messageTime)
        private val messageContainer: LinearLayout = itemView.findViewById(R.id.messageContainer)

        fun bind(message: Message2, position: Int) {
            // Handle text message
            if (message.text != null) {
                messageText.visibility = View.VISIBLE
                messageText.text = message.text
                messageImage.visibility = View.GONE
            }
            // Handle image message
            else if (message.imageUri != null) {
                messageImage.visibility = View.VISIBLE
                messageText.visibility = View.GONE
                Glide.with(itemView.context)
                    .load(message.imageUri)
                    .into(messageImage)
            }

            // Format timestamp
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val timeText = timeFormat.format(Date(message.timestamp))
            messageTime.text = if (message.isEdited) "$timeText (edited)" else timeText

            // Long press for edit/delete
            messageContainer.setOnLongClickListener {
                onMessageLongClick(message, position)
                true
            }
        }
    }

    inner class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val messageImage: ImageView = itemView.findViewById(R.id.messageImage)
        private val messageTime: TextView = itemView.findViewById(R.id.messageTime)
        private val senderAvatar: CircleImageView = itemView.findViewById(R.id.senderAvatar)

        fun bind(message: Message2) {
            // Handle text message
            if (message.text != null) {
                messageText.visibility = View.VISIBLE
                messageText.text = message.text
                messageImage.visibility = View.GONE
            }
            // Handle image message
            else if (message.imageUri != null) {
                messageImage.visibility = View.VISIBLE
                messageText.visibility = View.GONE
                Glide.with(itemView.context)
                    .load(message.imageUri)
                    .into(messageImage)
            }

            // Format timestamp
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            messageTime.text = timeFormat.format(Date(message.timestamp))

            // Set sender avatar if available
            message.senderAvatar?.let {
                senderAvatar.setImageResource(it)
            }
        }
    }

    fun updateMessage(position: Int, newText: String) {
        messages[position].text = newText
        messages[position].isEdited = true
        notifyItemChanged(position)
    }

    fun deleteMessage(position: Int) {
        messages.removeAt(position)
        notifyItemRemoved(position)
    }

    fun addMessage(message: Message2) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
}