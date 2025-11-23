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
import io.agora.rtc2.video.VideoEncoderConfiguration

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

                    override fun onLeaveChannel(stats: RtcStats?) {
                        Log.d("CallActivity", "📤 Left channel. Duration: ${stats?.totalDuration}s")
                    }

                    override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
                        Log.d("CallActivity", "📹 Remote video state changed: uid=$uid, state=$state, reason=$reason")
                    }

                    override fun onLocalVideoStateChanged(source: Constants.VideoSourceType?, state: Int, error: Int) {
                        Log.d("CallActivity", "📹 Local video state changed: state=$state, error=$error")
                    }

                    override fun onAudioVolumeIndication(speakers: Array<out AudioVolumeInfo>?, totalVolume: Int) {
                        // Log.d("CallActivity", "🔊 Audio volume: $totalVolume")
                        // Don't log this too frequently
                    }

                    override fun onError(err: Int) {
                        Log.e("CallActivity", "❌ Agora error code: $err")
                        when (err) {
                            101 -> {
                                Log.e("CallActivity", "❌ Invalid App ID")
                                runOnUiThread { Toast.makeText(this@CallActivity, "Invalid App ID", Toast.LENGTH_SHORT).show() }
                            }
                            102 -> {
                                Log.e("CallActivity", "❌ Invalid channel name")
                                runOnUiThread { Toast.makeText(this@CallActivity, "Invalid channel", Toast.LENGTH_SHORT).show() }
                            }
                            103 -> {
                                Log.e("CallActivity", "❌ Invalid token")
                                runOnUiThread { Toast.makeText(this@CallActivity, "Token error", Toast.LENGTH_SHORT).show() }
                            }
                            110 -> {
                                Log.e("CallActivity", "❌ Token expired or network issue")
                                Log.d("CallActivity", "💡 Retrying without token...")
                                // Don't end call immediately, let it retry
                                runOnUiThread { Toast.makeText(this@CallActivity, "Connection issue, retrying...", Toast.LENGTH_SHORT).show() }
                            }
                            else -> {
                                Log.e("CallActivity", "❌ Unknown Agora error: $err")
                                runOnUiThread { Toast.makeText(this@CallActivity, "Call error: $err", Toast.LENGTH_SHORT).show() }
                            }
                        }
                    }

                    override fun onConnectionStateChanged(state: Int, reason: Int) {
                        Log.d("CallActivity", "🌐 Connection state changed: state=$state, reason=$reason")
                        when (state) {
                            1 -> Log.d("CallActivity", "🔄 Disconnected")
                            2 -> Log.d("CallActivity", "🔄 Connecting")
                            3 -> Log.d("CallActivity", "✅ Connected")
                            4 -> Log.d("CallActivity", "🔄 Reconnecting")
                            5 -> Log.d("CallActivity", "❌ Failed")
                        }
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

        // ✅ Enable audio first with better configuration
        Log.d("CallActivity", "🎤 Configuring audio...")
        rtcEngine?.enableAudio()
        
        // Set audio profile for better quality
        rtcEngine?.setAudioProfile(Constants.AUDIO_PROFILE_DEFAULT, Constants.AUDIO_SCENARIO_DEFAULT)
        
        // Route audio to speaker for calls
        rtcEngine?.setDefaultAudioRoutetoSpeakerphone(true)
        
        // Set volume levels
        rtcEngine?.adjustRecordingSignalVolume(100)
        rtcEngine?.adjustPlaybackSignalVolume(100)
        
        // Enable audio volume indication
        rtcEngine?.enableAudioVolumeIndication(1000, 3, true)
        
        Log.d("CallActivity", "✅ Audio configured")

        // ✅ Enable video if needed
        if (isVideo) {
            Log.d("CallActivity", "📹 Configuring video...")
            rtcEngine?.enableVideo()
            
            // Set video configuration for better quality
            val videoConfig = VideoEncoderConfiguration(
                VideoEncoderConfiguration.VD_640x360,
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                VideoEncoderConfiguration.STANDARD_BITRATE,
                VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT
            )
            rtcEngine?.setVideoEncoderConfiguration(videoConfig)
            
            setupLocalVideoView()
            Log.d("CallActivity", "✅ Video configured")
        } else {
            Log.d("CallActivity", "🔇 Disabling video for voice call")
            rtcEngine?.disableVideo()
            removeLocalVideoViewIfAny()
        }

        // Set unmuted initially
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

        // Generate a random UID for this user
        val uid = (System.currentTimeMillis() % 1000000).toInt()
        
        Log.d("CallActivity", "🔗 Joining channel: $channelName with UID: $uid")
        Log.d("CallActivity", "📊 Channel options: audio=${options.autoSubscribeAudio}, video=${options.autoSubscribeVideo}")
        
        rtcEngine?.joinChannel(null, channelName, uid, options)
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
        if (localSurfaceView != null) {
            Log.d("CallActivity", "🖥️ Local video view already exists")
            return
        }
        
        Log.d("CallActivity", "🖥️ Creating local video view")
        
        localSurfaceView = SurfaceView(baseContext)
        localSurfaceView?.setZOrderMediaOverlay(true)
        
        val localContainer = findViewById<FrameLayout>(R.id.local_video_view_container)
        localContainer.removeAllViews()
        localContainer.addView(localSurfaceView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        
        val canvas = VideoCanvas(localSurfaceView, VideoCanvas.RENDER_MODE_FIT, 0)
        rtcEngine?.setupLocalVideo(canvas)
        
        // Start local preview
        rtcEngine?.startPreview()
        
        Log.d("CallActivity", "✅ Local video view setup complete")
        
        // Make sure local container is visible
        runOnUiThread {
            localContainer.visibility = View.VISIBLE
            Log.d("CallActivity", "🖥️ Local video container made visible")
        }
    }

    private fun removeLocalVideoViewIfAny() {
        localSurfaceView?.let {
            val parent = it.parent as? ViewGroup
            parent?.removeView(it)
            localSurfaceView = null
        }
    }

    private fun setupRemoteVideo(uid: Int) {
        if (remoteSurfaceView != null) {
            Log.d("CallActivity", "🖥️ Remote video view already exists")
            return
        }
        
        Log.d("CallActivity", "🖥️ Creating remote video view for UID: $uid")
        
        remoteSurfaceView = SurfaceView(baseContext)
        remoteSurfaceView?.setZOrderMediaOverlay(false)
        
        val remoteContainer = findViewById<FrameLayout>(R.id.remote_video_view_container)
        remoteContainer.removeAllViews()
        remoteContainer.addView(remoteSurfaceView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        
        val canvas = VideoCanvas(remoteSurfaceView, VideoCanvas.RENDER_MODE_FIT, uid)
        rtcEngine?.setupRemoteVideo(canvas)
        
        Log.d("CallActivity", "✅ Remote video view setup complete for UID: $uid")
        
        // Make sure remote container is visible
        runOnUiThread {
            remoteContainer.visibility = View.VISIBLE
            Log.d("CallActivity", "🖥️ Remote video container made visible")
        }
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