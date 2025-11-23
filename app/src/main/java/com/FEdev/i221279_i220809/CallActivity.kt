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
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.database.*
import io.agora.rtc2.*
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtc2.video.VideoEncoderConfiguration

class CallActivity : AppCompatActivity() {

    private var rtcEngine: RtcEngine? = null
    private var localSurfaceView: SurfaceView? = null
    private var remoteSurfaceView: SurfaceView? = null

    private lateinit var callDurationText: TextView
    private lateinit var contactNameText: TextView
    private lateinit var profileImage: ShapeableImageView
    private lateinit var endCallButton: ImageButton
    private lateinit var muteButton: ImageButton
    private lateinit var switchModeButton: ImageButton
    private lateinit var speakerButton: ImageButton

    private var channelName = ""
    private var contactName = ""
    private var isMuted = false
    private var isSpeakerOn = true
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

        Log.d("CallActivity", "Permission results: Audio=$grantedAudio, Camera=$grantedCam")

        if (grantedAudio && grantedCam) {
            listenForCallSignalling()
        } else {
            Toast.makeText(this, "Permissions required for calls", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        // Get intent extras
        channelName = intent.getStringExtra("CHANNEL_NAME") ?: "default_channel"
        isCaller = intent.getBooleanExtra("IS_CALLER", false)
        isVideo = intent.getBooleanExtra("IS_VIDEO", true)
        contactName = intent.getStringExtra("CONTACT_NAME") ?: "Contact"

        Log.d("CallActivity", "=== CallActivity onCreate ===")
        Log.d("CallActivity", "Channel: $channelName")
        Log.d("CallActivity", "Caller: $isCaller")
        Log.d("CallActivity", "Video: $isVideo")
        Log.d("CallActivity", "Contact: $contactName")

        initializeViews()
        setupClickListeners()
        requestPermissions()
    }

    private fun initializeViews() {
        callDurationText = findViewById(R.id.callDuration)
        contactNameText = findViewById(R.id.contactName)
        profileImage = findViewById(R.id.profileImage)
        endCallButton = findViewById(R.id.endCallButton)
        muteButton = findViewById(R.id.muteButton)
        switchModeButton = findViewById(R.id.switchCameraButton)
        speakerButton = findViewById(R.id.speakerBtn)

        // Set contact name
        contactNameText.text = contactName

        // Initially hide profile image and name if video call
        if (isVideo) {
            profileImage.visibility = View.GONE
            contactNameText.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        endCallButton.setOnClickListener {
            Log.d("CallActivity", "End call pressed")
            endCallManually()
        }

        muteButton.setOnClickListener {
            Log.d("CallActivity", "Mute pressed")
            toggleMute()
        }

        switchModeButton.setOnClickListener {
            Log.d("CallActivity", "Switch mode pressed")
            toggleCallMode()
        }

        speakerButton.setOnClickListener {
            Log.d("CallActivity", "Speaker pressed")
            toggleSpeaker()
        }
    }

    private fun requestPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (isVideo) perms.add(Manifest.permission.CAMERA)
        permissionLauncher.launch(perms.toTypedArray())
    }

    private var callRef: DatabaseReference? = null
    private var callStatusListener: ValueEventListener? = null
    private var callTypeListener: ValueEventListener? = null

    private fun listenForCallSignalling() {
        Log.d("CallActivity", "Setting up Firebase signaling...")

        callRef = rtdb.child(channelName)

        callStatusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("status").getValue(String::class.java)
                val type = snapshot.child("type").getValue(String::class.java) ?: "video"

                Log.d("CallActivity", "Status: $status, Type: $type")
                isVideo = (type == "video")

                when (status) {
                    "accepted" -> {
                        if (!joined) {
                            Log.d("CallActivity", "Call accepted, initializing...")
                            initializeAndJoinChannel()
                        }
                    }
                    "declined", "ended" -> {
                        Log.d("CallActivity", "Call ended: $status")
                        endCall()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CallActivity", "Firebase error: ${error.message}")
            }
        }

        callRef?.addValueEventListener(callStatusListener!!)
    }

    private fun initializeAndJoinChannel() {
        try {
            val config = RtcEngineConfig().apply {
                mContext = applicationContext
                mAppId = agoraAppId
                mEventHandler = createEventHandler()
            }

            rtcEngine = RtcEngine.create(config)
            Log.d("CallActivity", "RtcEngine created")

            configureAudio()

            if (isVideo) {
                configureVideo()
            } else {
                rtcEngine?.disableVideo()
                showVoiceCallUI()
            }

            joinChannel()
            startCallTimer()
            listenForTypeChanges()

        } catch (e: Exception) {
            Log.e("CallActivity", "Init error: ${e.message}", e)
            Toast.makeText(this, "Failed to initialize call", Toast.LENGTH_SHORT).show()
            endCall()
        }
    }

    private fun createEventHandler() = object : IRtcEngineEventHandler() {
        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.d("CallActivity", "Remote user joined: $uid")
            remoteUserJoined = true
            runOnUiThread {
                setupRemoteVideo(uid)
                // Hide profile image when remote user joins video call
                if (isVideo) {
                    profileImage.visibility = View.GONE
                    contactNameText.visibility = View.GONE
                }
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            Log.d("CallActivity", "Remote user offline: $uid")
            remoteUserJoined = false
            runOnUiThread {
                remoteSurfaceView?.let {
                    (it.parent as? ViewGroup)?.removeView(it)
                }
                remoteSurfaceView = null
                // Show profile image again if video call
                if (isVideo) {
                    profileImage.visibility = View.VISIBLE
                    contactNameText.visibility = View.VISIBLE
                }
            }
        }

        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            Log.d("CallActivity", "Joined channel: $channel, UID: $uid")
        }

        override fun onError(err: Int) {
            Log.e("CallActivity", "Agora error: $err")
            runOnUiThread {
                val message = when (err) {
                    101 -> "Invalid App ID"
                    102 -> "Invalid channel"
                    103 -> "Token error"
                    110 -> "Connection issue"
                    else -> "Call error: $err"
                }
                Toast.makeText(this@CallActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) {
            Log.d("CallActivity", "Connection state: $state, reason: $reason")
        }

        override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            Log.d("CallActivity", "Remote video state: state=$state, reason=$reason")
        }
    }

    private fun configureAudio() {
        rtcEngine?.enableAudio()
        rtcEngine?.setAudioProfile(
            Constants.AUDIO_PROFILE_DEFAULT,
            Constants.AUDIO_SCENARIO_DEFAULT
        )
        rtcEngine?.setDefaultAudioRoutetoSpeakerphone(true)
        rtcEngine?.adjustRecordingSignalVolume(100)
        rtcEngine?.adjustPlaybackSignalVolume(100)
        isMuted = false
        isSpeakerOn = true
    }

    private fun configureVideo() {
        rtcEngine?.enableVideo()

        val videoConfig = VideoEncoderConfiguration(
            VideoEncoderConfiguration.VD_640x360,
            VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
            VideoEncoderConfiguration.STANDARD_BITRATE,
            VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT
        )
        rtcEngine?.setVideoEncoderConfiguration(videoConfig)
        setupLocalVideoView()
    }

    private fun setupLocalVideoView() {
        if (localSurfaceView != null) return

        localSurfaceView = SurfaceView(baseContext).apply {
            setZOrderMediaOverlay(true)
        }

        val localContainer = findViewById<FrameLayout>(R.id.local_video_view_container)
        localContainer.removeAllViews()
        localContainer.addView(
            localSurfaceView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        rtcEngine?.setupLocalVideo(
            VideoCanvas(localSurfaceView, VideoCanvas.RENDER_MODE_FIT, 0)
        )
        rtcEngine?.startPreview()

        localContainer.visibility = View.VISIBLE
        Log.d("CallActivity", "Local video setup complete")
    }

    private fun setupRemoteVideo(uid: Int) {
        if (remoteSurfaceView != null) return

        remoteSurfaceView = SurfaceView(baseContext).apply {
            setZOrderMediaOverlay(false)
        }

        val remoteContainer = findViewById<FrameLayout>(R.id.remote_video_view_container)
        remoteContainer.removeAllViews()
        remoteContainer.addView(
            remoteSurfaceView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        rtcEngine?.setupRemoteVideo(
            VideoCanvas(remoteSurfaceView, VideoCanvas.RENDER_MODE_FIT, uid)
        )

        remoteContainer.visibility = View.VISIBLE
        Log.d("CallActivity", "Remote video setup complete")
    }

    private fun showVoiceCallUI() {
        profileImage.visibility = View.VISIBLE
        contactNameText.visibility = View.VISIBLE
        findViewById<FrameLayout>(R.id.local_video_view_container).visibility = View.GONE
        findViewById<FrameLayout>(R.id.remote_video_view_container).visibility = View.GONE
    }

    private fun showVideoCallUI() {
        findViewById<FrameLayout>(R.id.local_video_view_container).visibility = View.VISIBLE
        findViewById<FrameLayout>(R.id.remote_video_view_container).visibility = View.VISIBLE
        if (remoteUserJoined) {
            profileImage.visibility = View.GONE
            contactNameText.visibility = View.GONE
        }
    }

    private fun joinChannel() {
        val options = ChannelMediaOptions().apply {
            channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            autoSubscribeAudio = true
            autoSubscribeVideo = isVideo
            publishMicrophoneTrack = true
            publishCameraTrack = isVideo
        }

        val uid = (System.currentTimeMillis() % 1000000).toInt()
        rtcEngine?.joinChannel(null, channelName, uid, options)
        joined = true

        callRef?.child("started")?.setValue(true)
        if (isCaller) {
            callRef?.child("startTime")?.setValue(ServerValue.TIMESTAMP)
        }
    }

    private fun listenForTypeChanges() {
        callTypeListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val type = snapshot.getValue(String::class.java) ?: return
                val newIsVideo = (type == "video")

                if (newIsVideo != isVideo) {
                    isVideo = newIsVideo
                    runOnUiThread { applyCallModeToUI() }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CallActivity", "Type listener error: ${error.message}")
            }
        }
        callRef?.child("type")?.addValueEventListener(callTypeListener!!)
    }

    private fun applyCallModeToUI() {
        if (isVideo) {
            rtcEngine?.enableVideo()
            setupLocalVideoView()
            showVideoCallUI()
        } else {
            rtcEngine?.disableVideo()
            rtcEngine?.stopPreview()
            localSurfaceView?.let {
                (it.parent as? ViewGroup)?.removeView(it)
                localSurfaceView = null
            }
            showVoiceCallUI()
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
        muteButton.setImageResource(
            if (isMuted) R.drawable.mic_off else R.drawable.mic
        )
        Toast.makeText(
            this,
            if (isMuted) "Muted" else "Unmuted",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        rtcEngine?.setEnableSpeakerphone(isSpeakerOn)
        speakerButton.setImageResource(
            if (isSpeakerOn) R.drawable.soundon else R.drawable.soundon
        )
        Toast.makeText(
            this,
            if (isSpeakerOn) "Speaker On" else "Speaker Off",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun toggleCallMode() {
        isVideo = !isVideo
        callRef?.child("type")?.setValue(if (isVideo) "video" else "voice")
        applyCallModeToUI()
        Toast.makeText(
            this,
            if (isVideo) "Switched to Video" else "Switched to Voice",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun endCallManually() {
        callRef?.child("status")?.setValue("ended")
        endCall()
    }

    private fun endCall() {
        callTimer?.cancel()

        try {
            rtcEngine?.stopPreview()
            rtcEngine?.leaveChannel()
            RtcEngine.destroy()
            rtcEngine = null
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
            rtcEngine?.stopPreview()
            rtcEngine?.leaveChannel()
            RtcEngine.destroy()
        } catch (e: Exception) {
            Log.e("CallActivity", "Error in onDestroy: ${e.message}")
        }
    }
}