package com.FEdev.i221279_i220809

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging

object FCMTokenManager {

    private const val TAG = "FCMTokenManager"

    /**
     * Initialize FCM and get token for current user
     */
    fun initializeFCM() {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }

                // Get new FCM registration token
                val token = task.result
                Log.d(TAG, "FCM Registration Token: $token")

                // Save token to Firebase for current user
                saveTokenToFirebase(token)
            }
    }

    /**
     * Save FCM token to Firebase for current authenticated user
     */
    private fun saveTokenToFirebase(token: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            saveTokenForUser(userId, token)
        } else {
            Log.d(TAG, "No authenticated user, token will be saved after login")
        }
    }

    /**
     * Save FCM token for specific user ID
     */
    fun saveTokenForUser(userId: String, token: String? = null) {
        if (token != null) {
            // Use provided token
            saveTokenToDatabase(userId, token)
        } else {
            // Get current token and save
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val fcmToken = task.result
                        saveTokenToDatabase(userId, fcmToken)
                    } else {
                        Log.e(TAG, "Failed to get FCM token", task.exception)
                    }
                }
        }
    }

    private fun saveTokenToDatabase(userId: String, token: String) {
        FirebaseDatabase.getInstance()
            .getReference("users")
            .child(userId)
            .child("fcmToken")
            .setValue(token)
            .addOnSuccessListener {
                Log.d(TAG, "FCM token saved successfully for user: $userId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save FCM token: ${e.message}")
            }
    }

    /**
     * Save FCM token for user using their database user ID (not Firebase Auth UID)
     */
    fun saveTokenForDatabaseUserId(databaseUserId: Int) {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    FirebaseDatabase.getInstance()
                        .getReference("users")
                        .child(databaseUserId.toString())
                        .child("fcmToken")
                        .setValue(token)
                        .addOnSuccessListener {
                            Log.d(TAG, "FCM token saved for database user ID: $databaseUserId")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to save FCM token for database user: ${e.message}")
                        }
                } else {
                    Log.e(TAG, "Failed to get FCM token", task.exception)
                }
            }
    }

    /**
     * Clear FCM token for user (on logout)
     */
    fun clearTokenForUser(userId: String) {
        FirebaseDatabase.getInstance()
            .getReference("users")
            .child(userId)
            .child("fcmToken")
            .removeValue()
            .addOnSuccessListener {
                Log.d(TAG, "FCM token cleared for user: $userId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to clear FCM token: ${e.message}")
            }
    }

    /**
     * Subscribe to topic for general notifications
     */
    fun subscribeToTopic(topic: String) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Subscribed to topic: $topic")
                } else {
                    Log.e(TAG, "Failed to subscribe to topic: $topic", task.exception)
                }
            }
    }

    /**
     * Unsubscribe from topic
     */
    fun unsubscribeFromTopic(topic: String) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Unsubscribed from topic: $topic")
                } else {
                    Log.e(TAG, "Failed to unsubscribe from topic: $topic", task.exception)
                }
            }
    }
}