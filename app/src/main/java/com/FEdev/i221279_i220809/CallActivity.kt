package com.FEdev.i221279_i220809

import android.Manifest
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import io.agora.rtc2.*
import io.agora.rtc2.video.VideoCanvas

class CallActivity : AppCompatActivity() {

    private var rtcEngine: RtcEngine? = null
    private var localSurfaceView: SurfaceView? = null
    private var remoteSurfaceView: SurfaceView? = null
    private lateinit var callDurationText: TextView
    private lateinit var endCallButton: ImageButton
    private lateinit var muteButton: ImageButton
    private lateinit var switchModeButton: ImageButton

    private var channelName = ""
    private var isMuted = false
    private var callTimer: CountDownTimer? = null
    private var isCaller = false
    private var isVideo = true
    private var joined = false
    private var remoteUserJoined = false

    private val agoraAppId by lazy { getString(R.string.agora_app_id) }

    private val rtdb = FirebaseDatabase.getInstance(
        "https://i1279-22i0809-assignment2-default-rtdb.firebaseio.com/"
    ).reference.child("calls")

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val grantedAudio = permissions[Manifest.permission.RECORD_AUDIO] == true
        val grantedCam = permissions[Manifest.permission.CAMERA] == true || !isVideo
        
        Log.d("CallActivity", "🔒 Permission results:")
        Log.d("CallActivity", "   Audio: $grantedAudio")
        Log.d("CallActivity", "   Camera: $grantedCam (required: $isVideo)")
        
        if (grantedAudio && grantedCam) {
            Log.d("CallActivity", "✅ All required permissions granted")
            listenForCallSignalling()
        } else {
            Log.e("CallActivity", "❌ Required permissions not granted")
            Toast.makeText(this, "Microphone permission required for calls", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        channelName = intent.getStringExtra("CHANNEL_NAME") ?: "default_channel"
        isCaller = intent.getBooleanExtra("IS_CALLER", false)
        isVideo = intent.getBooleanExtra("IS_VIDEO", true)

        Log.d("CallActivity", "=== CallActivity onCreate ===")
        Log.d("CallActivity", "Channel Name: $channelName")
        Log.d("CallActivity", "Is Caller: $isCaller")
        Log.d("CallActivity", "Is Video: $isVideo")
        Log.d("CallActivity", "Agora App ID: ${agoraAppId.take(10)}...")

        callDurationText = findViewById(R.id.callDuration)
        endCallButton = findViewById(R.id.endCallButton)
        muteButton = findViewById(R.id.muteButton)
        switchModeButton = findViewById(R.id.switchCameraButton)

        endCallButton.setOnClickListener { 
            Log.d("CallActivity", "📞 End call button pressed")
            endCallManually() 
        }
        muteButton.setOnClickListener { 
            Log.d("CallActivity", "🎤 Mute button pressed")
            toggleMute() 
        }
        switchModeButton.setOnClickListener { 
            Log.d("CallActivity", "📹 Switch mode button pressed")
            toggleCallMode() 
        }

        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (isVideo) perms.add(Manifest.permission.CAMERA)
        
        Log.d("CallActivity", "🔒 Requesting permissions: $perms")
        permissionLauncher.launch(perms.toTypedArray())
    }

    private var callRef: DatabaseReference? = null
    private var callStatusListener: ValueEventListener? = null
    private var callTypeListener: ValueEventListener? = null

    private fun listenForCallSignalling() {
        Log.d("CallActivity", "🔄 Setting up Firebase call signaling...")
        
        callRef = rtdb.child(channelName)
        
        callStatusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("status").getValue(String::class.java)
                val type = snapshot.child("type").getValue(String::class.java) ?: "video"

                Log.d("CallActivity", "📞 Call status changed: $status, type: $type")

                isVideo = (type == "video")

                when (status) {
                    "accepted" -> {
                        if (!joined) {
                            Log.d("CallActivity", "✅ Call accepted, initializing Agora...")
                            initializeAndJoinChannel()
                        }
                    }
                    "declined" -> {
                        Log.d("CallActivity", "❌ Call declined")
                        endCall()
                    }
                    "ended" -> {
                        Log.d("CallActivity", "📞 Call ended")
                        endCall()
                    }
                    else -> {
                        Log.d("CallActivity", "📞 Call status: $status")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CallActivity", "❌ Firebase error: ${error.message}")
            }
        }

        callRef?.addValueEventListener(callStatusListener!!)
        Log.d("CallActivity", "✅ Firebase call signaling set up")
    }

    private fun initializeAndJoinChannel() {
        Log.d("CallActivity", "🚀 Initializing Agora RTC Engine...")
        
        try {
            val config = RtcEngineConfig().apply {
                mContext = applicationContext
                mAppId = agoraAppId
                mEventHandler = object : IRtcEngineEventHandler() {
                    override fun onUserJoined(uid: Int, elapsed: Int) {
                        Log.d("CallActivity", "👤 Remote user joined: $uid (elapsed: ${elapsed}ms)")
                        remoteUserJoined = true
                        runOnUiThread { 
                            Log.d("CallActivity", "🖥️ Setting up remote video for user: $uid")
                            setupRemoteVideo(uid) 
                        }
                    }

                    override fun onUserOffline(uid: Int, reason: Int) {
                        Log.d("CallActivity", "👤 Remote user offline: $uid, reason: $reason")
                        remoteUserJoined = false
                        runOnUiThread {
                            remoteSurfaceView?.let {
                                val parent = it.parent as? ViewGroup
                                parent?.removeView(it)
                            }
                            remoteSurfaceView = null
                            Log.d("CallActivity", "🖥️ Removed remote video view")
                        }
                    }

                    override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                        Log.d("CallActivity", "✅ Successfully joined channel: $channel with UID: $uid (elapsed: ${elapsed}ms)")
                    }

                    override fun onError(err: Int) {
                        Log.e("CallActivity", "❌ Agora error code: $err")
                        when (err) {
                            101 -> Log.e("CallActivity", "❌ Invalid App ID")
                            102 -> Log.e("CallActivity", "❌ Invalid channel name")
                            103 -> Log.e("CallActivity", "❌ Invalid token")
                            else -> Log.e("CallActivity", "❌ Unknown Agora error: $err")
                        }
                        
                        runOnUiThread {
                            Toast.makeText(this@CallActivity, "Call failed: Error $err", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onConnectionStateChanged(state: Int, reason: Int) {
                        Log.d("CallActivity", "🌐 Connection state changed: state=$state, reason=$reason")
                    }
                }
            }
            
            rtcEngine = RtcEngine.create(config)
            Log.d("CallActivity", "✅ RtcEngine created successfully")
            
        } catch (e: Exception) {
            Log.e("CallActivity", "❌ RtcEngine init error: ${e.message}", e)
            Toast.makeText(this, "Failed to initialize call engine", Toast.LENGTH_SHORT).show()
            endCall()
            return
        }

        // ✅ Enable audio first
        Log.d("CallActivity", "🎤 Enabling audio...")
        rtcEngine?.enableAudio()
        rtcEngine?.setDefaultAudioRoutetoSpeakerphone(true)
        rtcEngine?.adjustRecordingSignalVolume(100)
        rtcEngine?.adjustPlaybackSignalVolume(100)
        Log.d("CallActivity", "✅ Audio enabled")

        // ✅ Enable video if needed
        if (isVideo) {
            Log.d("CallActivity", "📹 Enabling video...")
            rtcEngine?.enableVideo()
            setupLocalVideoView()
            Log.d("CallActivity", "✅ Video enabled")
        } else {
            Log.d("CallActivity", "🔇 Disabling video for voice call")
            rtcEngine?.disableVideo()
            removeLocalVideoViewIfAny()
        }

        isMuted = false
        rtcEngine?.muteLocalAudioStream(false)

        val options = ChannelMediaOptions().apply {
            channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            autoSubscribeAudio = true
            autoSubscribeVideo = isVideo
            publishMicrophoneTrack = true
            publishCameraTrack = isVideo
        }

        Log.d("CallActivity", "🔗 Joining channel: $channelName")
        rtcEngine?.joinChannel(null, channelName, 0, options)
        joined = true
        
        Log.d("CallActivity", "⏱️ Starting call timer")
        startCallTimer()

        // Update Firebase
        callRef?.child("started")?.setValue(true)
        if (isCaller) {
            callRef?.child("startTime")?.setValue(ServerValue.TIMESTAMP)
        }

        // Listen for call type changes
        callTypeListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val type = snapshot.getValue(String::class.java) ?: return
                val newIsVideo = (type == "video")
                
                Log.d("CallActivity", "📞 Call type changed to: $type")
                
                if (newIsVideo != isVideo) {
                    isVideo = newIsVideo
                    runOnUiThread { 
                        Log.d("CallActivity", "🔄 Applying call mode change to UI")
                        applyCallModeToUI() 
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CallActivity", "❌ Call type listener error: ${error.message}")
            }
        }
        callRef?.child("type")?.addValueEventListener(callTypeListener!!)
        
        Log.d("CallActivity", "✅ Agora initialization complete")
    }

    private fun setupLocalVideoView() {
        if (localSurfaceView != null) return
        localSurfaceView = SurfaceView(baseContext)
        localSurfaceView?.setZOrderMediaOverlay(true)
        findViewById<FrameLayout>(R.id.local_video_view_container).apply {
            removeAllViews()
            addView(localSurfaceView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        rtcEngine?.setupLocalVideo(VideoCanvas(localSurfaceView, VideoCanvas.RENDER_MODE_FIT, 0))
        Log.d("CallActivity", "Local video view setup")
    }

    private fun removeLocalVideoViewIfAny() {
        localSurfaceView?.let {
            val parent = it.parent as? ViewGroup
            parent?.removeView(it)
            localSurfaceView = null
        }
    }

    private fun setupRemoteVideo(uid: Int) {
        if (remoteSurfaceView != null) return
        remoteSurfaceView = SurfaceView(baseContext)
        remoteSurfaceView?.setZOrderMediaOverlay(false)
        findViewById<FrameLayout>(R.id.remote_video_view_container).apply {
            removeAllViews()
            addView(remoteSurfaceView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        rtcEngine?.setupRemoteVideo(VideoCanvas(remoteSurfaceView, VideoCanvas.RENDER_MODE_FIT, uid))
        Log.d("CallActivity", "Remote video view setup for uid: $uid")
    }

    private fun applyCallModeToUI() {
        val localContainer = findViewById<FrameLayout>(R.id.local_video_view_container)
        val remoteContainer = findViewById<FrameLayout>(R.id.remote_video_view_container)

        if (isVideo) {
            rtcEngine?.enableVideo()
            setupLocalVideoView()
            remoteContainer.visibility = View.VISIBLE
            localContainer.visibility = View.VISIBLE
        } else {
            rtcEngine?.disableVideo()
            removeLocalVideoViewIfAny()
            remoteContainer.visibility = View.GONE
            localContainer.visibility = View.GONE
        }
    }

    private fun startCallTimer() {
        callTimer?.cancel()
        callTimer = object : CountDownTimer(3600000, 1000) {
            var secondsPassed = 0
            override fun onTick(millisUntilFinished: Long) {
                secondsPassed++
                val minutes = secondsPassed / 60
                val seconds = secondsPassed % 60
                callDurationText.text = String.format("%02d:%02d", minutes, seconds)
            }
            override fun onFinish() {}
        }.start()
    }

    private fun toggleMute() {
        isMuted = !isMuted
        rtcEngine?.muteLocalAudioStream(isMuted)
        muteButton.setImageResource(if (isMuted) R.drawable.mic_off else R.drawable.mic)
    }

    private fun toggleCallMode() {
        isVideo = !isVideo
        callRef?.child("type")?.setValue(if (isVideo) "video" else "voice")
        applyCallModeToUI()
    }

    private fun endCallManually() {
        callRef?.child("status")?.setValue("ended")
        endCall()
    }

    private fun endCall() {
        callTimer?.cancel()
        try {
            rtcEngine?.leaveChannel()
            RtcEngine.destroy()
        } catch (e: Exception) {
            Log.e("CallActivity", "Error destroying engine: ${e.message}")
        }

        callStatusListener?.let { callRef?.removeEventListener(it) }
        callTypeListener?.let { callRef?.child("type")?.removeEventListener(it) }

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        callTimer?.cancel()
        try {
            rtcEngine?.leaveChannel()
            RtcEngine.destroy()
        } catch (e: Exception) {
            Log.e("CallActivity", "Error in onDestroy: ${e.message}")
        }
    }
}