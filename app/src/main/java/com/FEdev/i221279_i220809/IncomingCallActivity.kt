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

        val callerText = findViewById<TextView>(R.id.callerName)
        val acceptBtn = findViewById<Button>(R.id.acceptBtn)
        val declineBtn = findViewById<Button>(R.id.declineBtn)

        // Set caller name immediately
        callerText.text = callerName

        // 🔔 Play ringtone
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(applicationContext, uri)
        ringtone?.play()

        // ✅ Accept Call
        acceptBtn.setOnClickListener {
            Log.d("IncomingCall", "📞 Call accepted")
            
            db.child("status").setValue("accepted")
            db.child("started").setValue(true)
            db.child("startTime").setValue(ServerValue.TIMESTAMP)

            val intent = Intent(this, CallActivity::class.java).apply {
                putExtra("CHANNEL_NAME", channelName)
                putExtra("IS_CALLER", false)
                putExtra("IS_VIDEO", isVideo)
            }
            
            ringtone?.stop()
            startActivity(intent)
            finish()
        }

        // ❌ Decline Call
        declineBtn.setOnClickListener {
            Log.d("IncomingCall", "📞 Call declined")
            
            db.child("status").setValue("declined")
            ringtone?.stop()
            finish()
        }

        // 🔁 Watch if caller cancels
        callListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("status").getValue(String::class.java)
                Log.d("IncomingCall", "📞 Call status changed: $status")
                
                if (status == "ended" || status == "declined") {
                    ringtone?.stop()
                    finish()
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("IncomingCall", "❌ Call listener error: ${error.message}")
            }
        }
        db.addValueEventListener(callListener!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        ringtone?.stop()
        callListener?.let { db.removeEventListener(it) }
    }
}
