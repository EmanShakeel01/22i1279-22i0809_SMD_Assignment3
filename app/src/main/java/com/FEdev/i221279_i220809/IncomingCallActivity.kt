package com.FEdev.i221279_i220809

import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*

class IncomingCallActivity : AppCompatActivity() {

    private lateinit var db: DatabaseReference
    private var channelName: String = ""
    private var ringtone: Ringtone? = null
    private var callListener: ValueEventListener? = null
    private var isCallActive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)

        channelName = intent.getStringExtra("CHANNEL_NAME") ?: run {
            Log.e("IncomingCall", "No channel name provided")
            finish()
            return
        }

        val isVideo = intent.getBooleanExtra("IS_VIDEO", true)
        val callerName = intent.getStringExtra("CALLER_NAME") ?: "Unknown Caller"

        Log.d("IncomingCall", "=== Incoming Call Activity ===")
        Log.d("IncomingCall", "Channel: $channelName")
        Log.d("IncomingCall", "Is Video: $isVideo")
        Log.d("IncomingCall", "Caller: $callerName")

        db = FirebaseDatabase.getInstance(
            "https://i1279-22i0809-assignment2-default-rtdb.firebaseio.com/"
        ).reference.child("calls").child(channelName)

        setupUI(callerName, isVideo)
        playRingtone()
        watchCallStatus()
    }

    private fun setupUI(callerName: String, isVideo: Boolean) {
        val callerText = findViewById<TextView>(R.id.callerName)
        val acceptBtn = findViewById<Button>(R.id.acceptBtn)
        val declineBtn = findViewById<Button>(R.id.declineBtn)

        // Display caller name and call type
        val displayText = if (isVideo) {
            "$callerName\n📹 Video Call"
        } else {
            "$callerName\n📞 Voice Call"
        }
        callerText.text = displayText

        // Accept button
        acceptBtn.setOnClickListener {
            if (isCallActive) {
                acceptCall(callerName, isVideo)
            }
        }

        // Decline button
        declineBtn.setOnClickListener {
            if (isCallActive) {
                declineCall()
            }
        }
    }

    private fun playRingtone() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            ringtone?.play()
            Log.d("IncomingCall", "Ringtone playing")
        } catch (e: Exception) {
            Log.e("IncomingCall", "Ringtone error: ${e.message}")
        }
    }

    private fun acceptCall(callerName: String, isVideo: Boolean) {
        if (!isCallActive) return

        Log.d("IncomingCall", "Call accepted")
        isCallActive = false

        // Update Firebase status
        db.child("status").setValue("accepted")
        db.child("started").setValue(true)
        db.child("startTime").setValue(ServerValue.TIMESTAMP)

        // Start CallActivity
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("CHANNEL_NAME", channelName)
            putExtra("IS_CALLER", false)
            putExtra("IS_VIDEO", isVideo)
            putExtra("CONTACT_NAME", callerName)
        }

        stopRingtone()
        startActivity(intent)
        finish()
    }

    private fun declineCall() {
        if (!isCallActive) return

        Log.d("IncomingCall", "Call declined")
        isCallActive = false

        db.child("status").setValue("declined")
        stopRingtone()
        finish()
    }

    private fun watchCallStatus() {
        callListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("status").getValue(String::class.java)
                Log.d("IncomingCall", "Status changed: $status")

                // If caller cancels or ends the call
                if (status == "ended" || status == "cancelled") {
                    if (isCallActive) {
                        Log.d("IncomingCall", "Call was cancelled by caller")
                        isCallActive = false
                        stopRingtone()
                        finish()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("IncomingCall", "Listener error: ${error.message}")
            }
        }
        db.addValueEventListener(callListener!!)
    }

    private fun stopRingtone() {
        try {
            ringtone?.stop()
            ringtone = null
            Log.d("IncomingCall", "Ringtone stopped")
        } catch (e: Exception) {
            Log.e("IncomingCall", "Stop ringtone error: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtone()
        callListener?.let { db.removeEventListener(it) }
        Log.d("IncomingCall", "Activity destroyed")
    }


}