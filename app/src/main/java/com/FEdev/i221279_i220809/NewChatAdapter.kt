package com.FEdev.i221279_i220809

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.FEdev.i221279_i220809.models.MessageItem
import java.text.SimpleDateFormat
import java.util.*

class NewChatAdapter(
    private val context: Context,
    private val messages: MutableList<MessageItem>,  // Fixed: Use MessageItem
    private val currentUserId: Int,
    private val onEditClick: (MessageItem) -> Unit,  // Fixed: Use MessageItem
    private val onDeleteClick: (MessageItem) -> Unit  // Fixed: Use MessageItem
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_SENT = 1
    private val TYPE_RECEIVED = 2
    private val TYPE_SYSTEM = 3

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return when {
            message.message_type == "system" -> TYPE_SYSTEM
            message.sender_id == currentUserId -> TYPE_SENT
            else -> TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SENT -> {
                val view = LayoutInflater.from(context).inflate(R.layout.item_message_sent, parent, false)
                SentViewHolder(view)
            }
            TYPE_SYSTEM -> {
                val view = LayoutInflater.from(context).inflate(R.layout.item_message_received, parent, false)
                SystemViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(context).inflate(R.layout.item_message_received, parent, false)
                ReceivedViewHolder(view)
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]

        when (holder) {
            is SystemViewHolder -> {
                holder.bind(message)
            }
            is SentViewHolder -> {
                val timeSince = System.currentTimeMillis() - message.timestamp
                val canEditOrDelete = timeSince <= 5 * 60 * 1000 // 5 minutes
                holder.bind(message, canEditOrDelete)
            }
            is ReceivedViewHolder -> {
                holder.bind(message)
            }
        }
    }

    inner class SentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textMessage: TextView = view.findViewById(R.id.textMessage)
        val imageMessage: ImageView = view.findViewById(R.id.imageMessage)
        private val videoThumbnail: ImageView? = view.findViewById(R.id.videoThumbnail)  // Add to layout if missing
        private val fileInfo: TextView? = view.findViewById(R.id.fileInfo)  // Add to layout if missing
        private val timeText: TextView = view.findViewById(R.id.messageTime)  // Add to layout if missing
        private val statusIcon: ImageView? = view.findViewById(R.id.statusIcon)  // Add to layout if missing

        fun bind(message: MessageItem, canEdit: Boolean) {
            // Hide all views initially
            textMessage.visibility = View.GONE
            imageMessage.visibility = View.GONE
            videoThumbnail?.visibility = View.GONE
            fileInfo?.visibility = View.GONE

            when (message.message_type) {
                "text" -> {
                    textMessage.visibility = View.VISIBLE
                    var displayText = message.message_text ?: ""
                    if (message.edited) {
                        displayText += " (edited)"
                    }
                    textMessage.text = displayText
                }
                "image" -> {
                    imageMessage.visibility = View.VISIBLE
                    try {
                        val imageBytes = Base64.decode(message.media_base64, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        if (bitmap != null) {
                            imageMessage.setImageBitmap(bitmap)
                        } else {
                            imageMessage.setImageResource(android.R.drawable.ic_menu_gallery)
                        }
                    } catch (e: Exception) {
                        Log.e("ChatAdapter", "Error decoding image", e)
                        imageMessage.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                }
                "video" -> {
                    videoThumbnail?.visibility = View.VISIBLE
                    // Show video thumbnail or placeholder
                    videoThumbnail?.setImageResource(R.drawable.ic_video_placeholder)
                }
                "file" -> {
                    fileInfo?.visibility = View.VISIBLE
                    val sizeStr = formatFileSize(message.file_size ?: 0)
                    fileInfo?.text = "${message.file_name}\n$sizeStr"
                }
            }

            // Time
            timeText.text = formatTime(message.timestamp)

            // Status icon
            statusIcon?.setImageResource(
                when {
                    message.seen -> R.drawable.ic_check_double_blue
                    else -> R.drawable.ic_check_double_gray
                }
            )

            // Long press actions for text messages
            if (canEdit && message.sender_id == currentUserId && message.message_type == "text") {
                itemView.setOnLongClickListener {
                    onEditClick(message)
                    true
                }
                itemView.setOnClickListener {
                    onDeleteClick(message)
                }
            } else {
                itemView.setOnLongClickListener(null)
                itemView.setOnClickListener(null)
            }
        }
    }

    inner class ReceivedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textMessage: TextView = view.findViewById(R.id.textMessage)
        val imageMessage: ImageView = view.findViewById(R.id.imageMessage)
        private val videoThumbnail: ImageView? = view.findViewById(R.id.videoThumbnail)  // Add to layout if missing
        private val fileInfo: TextView? = view.findViewById(R.id.fileInfo)  // Add to layout if missing
        private val timeText: TextView = view.findViewById(R.id.messageTime)  // Add to layout if missing

        fun bind(message: MessageItem) {
            // Hide all views initially
            textMessage.visibility = View.GONE
            imageMessage.visibility = View.GONE
            videoThumbnail?.visibility = View.GONE
            fileInfo?.visibility = View.GONE

            when (message.message_type) {
                "text" -> {
                    textMessage.visibility = View.VISIBLE
                    var displayText = message.message_text ?: ""
                    if (message.edited) {
                        displayText += " (edited)"
                    }
                    textMessage.text = displayText
                }
                "image" -> {
                    imageMessage.visibility = View.VISIBLE
                    try {
                        val imageBytes = Base64.decode(message.media_base64, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        if (bitmap != null) {
                            imageMessage.setImageBitmap(bitmap)
                        } else {
                            imageMessage.setImageResource(android.R.drawable.ic_menu_gallery)
                        }
                    } catch (e: Exception) {
                        Log.e("ChatAdapter", "Error decoding image", e)
                        imageMessage.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                }
                "video" -> {
                    videoThumbnail?.visibility = View.VISIBLE
                    videoThumbnail?.setImageResource(R.drawable.ic_video_placeholder)
                }
                "file" -> {
                    fileInfo?.visibility = View.VISIBLE
                    val sizeStr = formatFileSize(message.file_size ?: 0)
                    fileInfo?.text = "${message.file_name}\n$sizeStr"
                }
            }

            // Time
            timeText.text = formatTime(message.timestamp)
        }
    }

    private fun formatTime(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    inner class SystemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textMessage: TextView = view.findViewById(R.id.textMessage)
        private val timeText: TextView = view.findViewById(R.id.messageTime)

        fun bind(message: MessageItem) {
            textMessage.text = message.message_text
            textMessage.visibility = View.VISIBLE
            textMessage.textAlignment = View.TEXT_ALIGNMENT_CENTER
            textMessage.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            textMessage.setTypeface(null, android.graphics.Typeface.ITALIC)
            textMessage.textSize = 12f
            
            timeText.text = formatTime(message.timestamp)
            timeText.visibility = View.VISIBLE
            
            // Make the entire container centered and transparent
            itemView.background = null
            
            // Hide any other views that might exist
            itemView.findViewById<ImageView>(R.id.imageMessage)?.visibility = View.GONE
        }
    }

    private fun formatFileSize(bytes: Int): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}