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

    private val agoraAppId by lazy { getString(R.string.agora_app_id) }

    private val rtdb = FirebaseDatabase.getInstance(
        "https://i1279-22i0809-assignment2-default-rtdb.firebaseio.com/"
    ).reference.child("calls")

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val grantedAudio = permissions[Manifest.permission.RECORD_AUDIO] == true
        val grantedCam = permissions[Manifest.permission.CAMERA] == true || !isVideo
        if (grantedAudio && grantedCam) {
            listenForCallSignalling()
        } else {
            Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        channelName = intent.getStringExtra("CHANNEL_NAME") ?: "default_channel"
        isCaller = intent.getBooleanExtra("IS_CALLER", false)
        isVideo = intent.getBooleanExtra("IS_VIDEO", true)

        callDurationText = findViewById(R.id.callDuration)
        endCallButton = findViewById(R.id.endCallButton)
        muteButton = findViewById(R.id.muteButton)
        switchModeButton = findViewById(R.id.switchCameraButton)

        endCallButton.setOnClickListener { endCallManually() }
        muteButton.setOnClickListener { toggleMute() }
        switchModeButton.setOnClickListener { toggleCallMode() }

        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (isVideo) perms.add(Manifest.permission.CAMERA)
        permissionLauncher.launch(perms.toTypedArray())
    }

    private var callRef: DatabaseReference? = null
    private var callStatusListener: ValueEventListener? = null
    private var callTypeListener: ValueEventListener? = null

    private fun listenForCallSignalling() {
        callRef = rtdb.child(channelName)
        callStatusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("status").getValue(String::class.java)
                val type = snapshot.child("type").getValue(String::class.java) ?: "video"

                isVideo = (type == "video")
                applyCallModeToUI()

                if (status == "accepted" && !joined) {
                    initializeAndJoinChannel()
                } else if (status == "declined" || status == "ended") {
                    endCall()
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        callRef?.addValueEventListener(callStatusListener!!)
    }

    private fun initializeAndJoinChannel() {
        try {
            val config = RtcEngineConfig().apply {
                mContext = applicationContext
                mAppId = agoraAppId
                mEventHandler = object : IRtcEngineEventHandler() {
                    override fun onUserJoined(uid: Int, elapsed: Int) {
                        runOnUiThread { setupRemoteVideo(uid) }
                    }

                    override fun onUserOffline(uid: Int, reason: Int) {
                        runOnUiThread { endCall() }
                    }

                    override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                        Log.d("CallActivity", "Joined channel: $channel successfully")
                    }
                }
            }
            rtcEngine = RtcEngine.create(config)
        } catch (e: Exception) {
            Log.e("CallActivity", "RtcEngine init error: ${e.message}")
            Toast.makeText(this, "Unable to init Agora", Toast.LENGTH_SHORT).show()
            return
        }

        rtcEngine?.enableAudio()
        rtcEngine?.setDefaultAudioRoutetoSpeakerphone(true)

        if (isVideo) {
            rtcEngine?.enableVideo()
            setupLocalVideoView()
        } else {
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
        }

        rtcEngine?.joinChannel(null, channelName, 0, options)
        joined = true
        startCallTimer()

        callRef?.child("started")?.setValue(true)
        if (isCaller) callRef?.child("startTime")?.setValue(ServerValue.TIMESTAMP)

        callTypeListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val type = snapshot.getValue(String::class.java) ?: return
                val newIsVideo = (type == "video")
                if (newIsVideo != isVideo) {
                    isVideo = newIsVideo
                    runOnUiThread { applyCallModeToUI() }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        callRef?.child("type")?.addValueEventListener(callTypeListener!!)
    }

    // ✅ UPDATED: Ensure correct z-ordering for local and remote views
    private fun setupLocalVideoView() {
        if (localSurfaceView != null) return
        localSurfaceView = SurfaceView(baseContext)
        localSurfaceView?.setZOrderMediaOverlay(true) // local view above remote
        findViewById<FrameLayout>(R.id.local_video_view_container).apply {
            removeAllViews()
            addView(localSurfaceView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        rtcEngine?.setupLocalVideo(VideoCanvas(localSurfaceView, VideoCanvas.RENDER_MODE_FIT, 0))
    }

    private fun removeLocalVideoViewIfAny() {
        localSurfaceView?.let {
            val parent = it.parent as? ViewGroup
            parent?.removeView(it)
            localSurfaceView = null
        }
    }

    // ✅ UPDATED: Remote video stays behind all UI
    private fun setupRemoteVideo(uid: Int) {
        if (remoteSurfaceView != null) return
        remoteSurfaceView = SurfaceView(baseContext)
        remoteSurfaceView?.setZOrderMediaOverlay(false) // behind everything
        findViewById<FrameLayout>(R.id.remote_video_view_container).apply {
            removeAllViews()
            addView(remoteSurfaceView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        rtcEngine?.setupRemoteVideo(VideoCanvas(remoteSurfaceView, VideoCanvas.RENDER_MODE_FIT, uid))
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
        } catch (e: Exception) { }

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
        } catch (e: Exception) {}
    }
}
