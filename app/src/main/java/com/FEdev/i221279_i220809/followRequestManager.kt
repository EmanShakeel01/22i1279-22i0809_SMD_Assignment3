package com.FEdev.i221279_i220809

import android.util.Log
import com.google.firebase.database.*

object FollowRequestManager {
    private val database = FirebaseDatabase.getInstance()
    private val followRequestsRef = database.getReference("followRequests")
    private val followersRef = database.getReference("followers")
    private val followingRef = database.getReference("following")
    private val usersRef = database.getReference("users")

    // Send follow request
    fun sendFollowRequest(
        fromUserId: String,
        fromUsername: String,
        toUserId: String,
        toUsername: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        checkIfFollowing(fromUserId, toUserId) { isFollowing ->
            if (isFollowing) {
                onFailure("Already following this user")
                return@checkIfFollowing
            }

            checkIfRequestExists(fromUserId, toUserId) { exists ->
                if (exists) {
                    onFailure("Request already sent")
                    return@checkIfRequestExists
                }

                val requestId = followRequestsRef.push().key ?: return@checkIfRequestExists
                val request = FollowRequest(
                    requestId = requestId,
                    fromUserId = fromUserId,
                    fromUsername = fromUsername,
                    toUserId = toUserId,
                    toUsername = toUsername,
                    timestamp = System.currentTimeMillis(),
                    status = "pending"
                )

                followRequestsRef.child(toUserId).child(requestId).setValue(request)
                    .addOnSuccessListener {
                        Log.d("FollowRequest", "Request sent successfully")

                        // ✅ Send push notification
                        Notificationhelperfcm.sendFollowRequestNotification(
                            fromUserId = fromUserId,
                            fromUsername = fromUsername,
                            toUserId = toUserId
                        )

                        onSuccess()
                    }
                    .addOnFailureListener { e ->
                        Log.e("FollowRequest", "Failed to send request: ${e.message}")
                        onFailure(e.message ?: "Unknown error")
                    }
            }
        }
    }

    // Accept follow request
    fun acceptFollowRequest(
        requestId: String,
        fromUserId: String,
        toUserId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val updates = mutableMapOf<String, Any>()
        updates["followers/$toUserId/$fromUserId"] = true
        updates["following/$fromUserId/$toUserId"] = true

        database.reference.updateChildren(updates)
            .addOnSuccessListener {
                followRequestsRef.child(toUserId).child(requestId).removeValue()
                    .addOnSuccessListener {
                        Log.d("FollowRequest", "Request accepted")

                        // ✅ Get accepter's username and send notification
                        usersRef.child(toUserId)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    val accepterName = snapshot.child("username")
                                        .getValue(String::class.java) ?: "Someone"

                                    Notificationhelperfcm.sendFollowAcceptedNotification(
                                        accepterId = toUserId,
                                        accepterName = accepterName,
                                        requesterId = fromUserId
                                    )
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Log.e("FollowRequest", "Error: ${error.message}")
                                }
                            })

                        onSuccess()
                    }
                    .addOnFailureListener { e ->
                        onFailure(e.message ?: "Failed to remove request")
                    }
            }
            .addOnFailureListener { e ->
                Log.e("FollowRequest", "Failed to accept: ${e.message}")
                onFailure(e.message ?: "Unknown error")
            }
    }

    // Reject follow request
    fun rejectFollowRequest(
        requestId: String,
        toUserId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        followRequestsRef.child(toUserId).child(requestId).removeValue()
            .addOnSuccessListener {
                Log.d("FollowRequest", "Request rejected")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("FollowRequest", "Failed to reject: ${e.message}")
                onFailure(e.message ?: "Unknown error")
            }
    }

    // Cancel follow request
    fun cancelFollowRequest(
        fromUserId: String,
        toUserId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        followRequestsRef.child(toUserId)
            .orderByChild("fromUserId")
            .equalTo(fromUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (child in snapshot.children) {
                            child.ref.removeValue()
                        }
                        onSuccess()
                    } else {
                        onFailure("Request not found")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    onFailure(error.message)
                }
            })
    }

    // Unfollow user
    fun unfollowUser(
        currentUserId: String,
        targetUserId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val updates = mutableMapOf<String, Any?>()
        updates["followers/$targetUserId/$currentUserId"] = null
        updates["following/$currentUserId/$targetUserId"] = null

        database.reference.updateChildren(updates)
            .addOnSuccessListener {
                Log.d("FollowRequest", "Unfollowed successfully")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("FollowRequest", "Failed to unfollow: ${e.message}")
                onFailure(e.message ?: "Unknown error")
            }
    }

    // Check if following
    fun checkIfFollowing(
        currentUserId: String,
        targetUserId: String,
        callback: (Boolean) -> Unit
    ) {
        followingRef.child(currentUserId).child(targetUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    callback(snapshot.exists())
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FollowRequest", "Error checking follow status: ${error.message}")
                    callback(false)
                }
            })
    }

    // Check if request exists
    private fun checkIfRequestExists(
        fromUserId: String,
        toUserId: String,
        callback: (Boolean) -> Unit
    ) {
        followRequestsRef.child(toUserId)
            .orderByChild("fromUserId")
            .equalTo(fromUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    callback(snapshot.exists())
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FollowRequest", "Error: ${error.message}")
                    callback(false)
                }
            })
    }

    // Get pending requests count
    fun getPendingRequestsCount(userId: String, callback: (Int) -> Unit) {
        followRequestsRef.child(userId)
            .orderByChild("status")
            .equalTo("pending")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    callback(snapshot.childrenCount.toInt())
                }

                override fun onCancelled(error: DatabaseError) {
                    callback(0)
                }
            })
    }

    // Get all pending requests for user
    fun getPendingRequests(userId: String, callback: (List<FollowRequest>) -> Unit) {
        followRequestsRef.child(userId)
            .orderByChild("status")
            .equalTo("pending")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val requests = mutableListOf<FollowRequest>()
                    for (child in snapshot.children) {
                        val request = child.getValue(FollowRequest::class.java)
                        if (request != null) {
                            requests.add(request)
                        }
                    }
                    callback(requests)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FollowRequest", "Error: ${error.message}")
                    callback(emptyList())
                }
            })
    }

    // Check follow request status
    fun getFollowRequestStatus(
        fromUserId: String,
        toUserId: String,
        callback: (String?) -> Unit // null, "pending", "following"
    ) {
        // First check if already following
        checkIfFollowing(fromUserId, toUserId) { isFollowing ->
            if (isFollowing) {
                callback("following")
                return@checkIfFollowing
            }

            // Check if request is pending
            followRequestsRef.child(toUserId)
                .orderByChild("fromUserId")
                .equalTo(fromUserId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            callback("pending")
                        } else {
                            callback(null)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        callback(null)
                    }
                })
        }
    }
}