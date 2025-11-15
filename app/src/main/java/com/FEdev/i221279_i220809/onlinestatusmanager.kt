package com.FEdev.i221279_i220809

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

object OnlineStatusManager {

    private var userStatusRef: DatabaseReference? = null
    private var connectedRef: DatabaseReference? = null

    /**
     * Initialize online/offline status tracking for current user
     * Call this in your main activity (homepage) or base activity
     */
    fun initializeStatus() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w("OnlineStatus", "No user logged in")
            return
        }

        val uid = currentUser.uid
        val email = currentUser.email?.replace(".", ",") // Convert email for Firebase key

        // Reference to user's status
        userStatusRef = FirebaseDatabase.getInstance()
            .getReference()
            .child(email ?: uid)
            .child("status")

        // Reference to Firebase's special .info/connected
        connectedRef = FirebaseDatabase.getInstance()
            .getReference(".info/connected")

        // Set up connection listener
        connectedRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false

                if (connected) {
                    Log.d("OnlineStatus", "User connected")

                    // When user connects, set status to online
                    userStatusRef?.setValue("online")

                    // When user disconnects (closes app), set status to offline
                    userStatusRef?.onDisconnect()?.setValue("offline")

                    // Also update last seen timestamp
                    val lastSeenRef = FirebaseDatabase.getInstance()
                        .getReference()
                        .child(email ?: uid)
                        .child("lastSeen")

                    lastSeenRef.onDisconnect()
                        .setValue(ServerValue.TIMESTAMP)
                } else {
                    Log.d("OnlineStatus", "User disconnected")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("OnlineStatus", "Connection listener error: ${error.message}")
            }
        })
    }

    /**
     * Manually set user status to online
     */
    fun setOnline() {
        userStatusRef?.setValue("online")
    }

    /**
     * Manually set user status to offline
     */
    fun setOffline() {
        userStatusRef?.setValue("offline")

        // Update last seen
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val email = currentUser.email?.replace(".", ",")
            val uid = currentUser.uid

            FirebaseDatabase.getInstance()
                .getReference()
                .child(email ?: uid)
                .child("lastSeen")
                .setValue(ServerValue.TIMESTAMP)
        }
    }

    /**
     * Clean up listeners when app is destroyed
     */
    fun cleanup() {
        connectedRef?.removeEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    /**
     * Listen to another user's online status
     * @param userEmail The email key of the user (with comma instead of dot)
     * @param callback Function to call when status changes (true = online, false = offline)
     */
    fun listenToUserStatus(userEmail: String, callback: (Boolean, Long?) -> Unit) {
        val statusRef = FirebaseDatabase.getInstance()
            .getReference()
            .child(userEmail)
            .child("status")

        statusRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java)
                val isOnline = status == "online"

                // Get last seen if offline
                if (!isOnline) {
                    FirebaseDatabase.getInstance()
                        .getReference()
                        .child(userEmail)
                        .child("lastSeen")
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(lastSeenSnapshot: DataSnapshot) {
                                val lastSeen = lastSeenSnapshot.getValue(Long::class.java)
                                callback(false, lastSeen)
                            }

                            override fun onCancelled(error: DatabaseError) {
                                callback(false, null)
                            }
                        })
                } else {
                    callback(true, null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("OnlineStatus", "Error listening to status: ${error.message}")
                callback(false, null)
            }
        })
    }

    /**
     * Get user status once (not real-time)
     */
    fun getUserStatus(userEmail: String, callback: (Boolean, Long?) -> Unit) {
        val statusRef = FirebaseDatabase.getInstance()
            .getReference()
            .child(userEmail)
            .child("status")

        statusRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java)
                val isOnline = status == "online"

                if (!isOnline) {
                    FirebaseDatabase.getInstance()
                        .getReference()
                        .child(userEmail)
                        .child("lastSeen")
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(lastSeenSnapshot: DataSnapshot) {
                                val lastSeen = lastSeenSnapshot.getValue(Long::class.java)
                                callback(false, lastSeen)
                            }

                            override fun onCancelled(error: DatabaseError) {
                                callback(false, null)
                            }
                        })
                } else {
                    callback(true, null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                callback(false, null)
            }
        })
    }
}