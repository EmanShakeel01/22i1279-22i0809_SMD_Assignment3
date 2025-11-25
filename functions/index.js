/**
 * Import function triggers from their respective submodules:
 *
 * const {onCall} = require("firebase-functions/v2/https");
 * const {onDocumentWritten} = require("firebase-functions/v2/firestore");
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

const {setGlobalOptions} = require("firebase-functions");
const {onRequest} = require("firebase-functions/https");
const logger = require("firebase-functions/logger");

// For cost control, you can set the maximum number of containers that can be
// running at the same time. This helps mitigate the impact of unexpected
// traffic spikes by instead downgrading performance. This limit is a
// per-function limit. You can override the limit for each function using the
// `maxInstances` option in the function's options, e.g.
// `onRequest({ maxInstances: 5 }, (req, res) => { ... })`.
// NOTE: setGlobalOptions does not apply to functions using the v1 API. V1
// functions should each use functions.runWith({ maxInstances: 10 }) instead.
// In the v1 API, each function can only serve one request per container, so
// this will be the maximum concurrent request count.
setGlobalOptions({ maxInstances: 10 });

const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

// Send FCM notification when a new message is created
exports.sendMessageNotification = functions.database
    .ref('/chats/{chatId}/messages/{messageId}')
    .onCreate(async (snapshot, context) => {
        const message = snapshot.val();
        const chatId = context.params.chatId;

        // Don't send notification for system messages
        if (message.messageType === 'system') {
            return null;
        }

        // Get the receiver ID from chat ID
        const [user1, user2] = chatId.split('_');
        const receiverId = message.senderId === user1 ? user2 : user1;

        // Get receiver's FCM token
        const userSnapshot = await admin.database()
            .ref(`users/${receiverId}`)
            .once('value');

        const fcmToken = userSnapshot.val()?.fcmToken;

        if (!fcmToken) {
            console.log('No FCM token found for user:', receiverId);
            return null;
        }

        // Get sender info
        const senderSnapshot = await admin.database()
            .ref(`users/${message.senderId}`)
            .once('value');

        const senderName = senderSnapshot.val()?.username ||
                          senderSnapshot.val()?.email?.split('@')[0] ||
                          'Someone';

        // Prepare notification payload
        const payload = {
            notification: {
                title: senderName,
                body: message.messageText || '📷 Sent an image',
                sound: 'default',
                badge: '1'
            },
            data: {
                type: 'message',
                senderId: message.senderId,
                senderName: senderName,
                chatId: chatId,
                timestamp: message.timestamp.toString()
            }
        };

        // Send notification
        try {
            await admin.messaging().sendToDevice(fcmToken, payload);
            console.log('Notification sent successfully to:', receiverId);
        } catch (error) {
            console.error('Error sending notification:', error);
        }

        return null;
    });

// Send FCM notification for new follow request
exports.sendFollowRequestNotification = functions.database
    .ref('/followRequests/{userId}/{requestId}')
    .onCreate(async (snapshot, context) => {
        const request = snapshot.val();
        const userId = context.params.userId;

        // Get receiver's FCM token
        const userSnapshot = await admin.database()
            .ref(`users/${userId}`)
            .once('value');

        const fcmToken = userSnapshot.val()?.fcmToken;

        if (!fcmToken) {
            console.log('No FCM token found for user:', userId);
            return null;
        }

        const payload = {
            notification: {
                title: 'New Follow Request',
                body: `${request.fromUsername} wants to follow you`,
                sound: 'default'
            },
            data: {
                type: 'follow_request',
                fromUserId: request.fromUserId,
                fromUsername: request.fromUsername,
                timestamp: request.timestamp.toString()
            }
        };

        try {
            await admin.messaging().sendToDevice(fcmToken, payload);
            console.log('Follow request notification sent to:', userId);
        } catch (error) {
            console.error('Error sending notification:', error);
        }

        return null;
    });

// Send FCM notification for new comment
exports.sendCommentNotification = functions.database
    .ref('/posts/{postId}/comments/{commentId}')
    .onCreate(async (snapshot, context) => {
        const comment = snapshot.val();
        const postId = context.params.postId;

        // Get post owner
        const postSnapshot = await admin.database()
            .ref(`posts/${postId}`)
            .once('value');

        const postOwnerId = postSnapshot.val()?.userId;

        if (!postOwnerId || postOwnerId === comment.userId) {
            return null; // Don't notify if commenting on own post
        }

        // Get post owner's FCM token
        const userSnapshot = await admin.database()
            .ref(`users/${postOwnerId}`)
            .once('value');

        const fcmToken = userSnapshot.val()?.fcmToken;

        if (!fcmToken) {
            console.log('No FCM token found for user:', postOwnerId);
            return null;
        }

        const payload = {
            notification: {
                title: 'New Comment',
                body: `${comment.username} commented: ${comment.text}`,
                sound: 'default'
            },
            data: {
                type: 'comment',
                commenterId: comment.userId,
                commenterName: comment.username,
                postId: postId,
                timestamp: comment.timestamp
            }
        };

        try {
            await admin.messaging().sendToDevice(fcmToken, payload);
            console.log('Comment notification sent to:', postOwnerId);
        } catch (error) {
            console.error('Error sending notification:', error);
        }

        return null;
    });

// Send FCM notification for new like
exports.sendLikeNotification = functions.database
    .ref('/posts/{postId}/likes/{likerId}')
    .onCreate(async (snapshot, context) => {
        const postId = context.params.postId;
        const likerId = context.params.likerId;

        // Get post owner
        const postSnapshot = await admin.database()
            .ref(`posts/${postId}`)
            .once('value');

        const postOwnerId = postSnapshot.val()?.userId;

        if (!postOwnerId || postOwnerId === likerId) {
            return null; // Don't notify if liking own post
        }

        // Get liker info
        const likerSnapshot = await admin.database()
            .ref(`users/${likerId}`)
            .once('value');

        const likerName = likerSnapshot.val()?.username ||
                         likerSnapshot.val()?.email?.split('@')[0] ||
                         'Someone';

        // Get post owner's FCM token
        const ownerSnapshot = await admin.database()
            .ref(`users/${postOwnerId}`)
            .once('value');

        const fcmToken = ownerSnapshot.val()?.fcmToken;

        if (!fcmToken) {
            console.log('No FCM token found for user:', postOwnerId);
            return null;
        }

        const payload = {
            notification: {
                title: 'New Like',
                body: `${likerName} liked your post`,
                sound: 'default'
            },
            data: {
                type: 'like',
                likerId: likerId,
                likerName: likerName,
                postId: postId,
                timestamp: Date.now().toString()
            }
        };

        try {
            await admin.messaging().sendToDevice(fcmToken, payload);
            console.log('Like notification sent to:', postOwnerId);
        } catch (error) {
            console.error('Error sending notification:', error);
        }

        return null;
    });

// Send FCM notification for follow accept
exports.sendFollowAcceptNotification = functions.database
    .ref('/following/{followerId}/{followedId}')
    .onCreate(async (snapshot, context) => {
        const followerId = context.params.followerId;
        const followedId = context.params.followedId;

        // Get followed user's info
        const followedSnapshot = await admin.database()
            .ref(`users/${followedId}`)
            .once('value');

        const followedName = followedSnapshot.val()?.username ||
                            followedSnapshot.val()?.email?.split('@')[0] ||
                            'Someone';

        // Get follower's FCM token
        const followerSnapshot = await admin.database()
            .ref(`users/${followerId}`)
            .once('value');

        const fcmToken = followerSnapshot.val()?.fcmToken;

        if (!fcmToken) {
            console.log('No FCM token found for user:', followerId);
            return null;
        }

        const payload = {
            notification: {
                title: 'Follow Request Accepted',
                body: `${followedName} accepted your follow request`,
                sound: 'default'
            },
            data: {
                type: 'follow_accepted',
                userId: followedId,
                timestamp: Date.now().toString()
            }
        };

        try {
            await admin.messaging().sendToDevice(fcmToken, payload);
            console.log('Follow accept notification sent to:', followerId);
        } catch (error) {
            console.error('Error sending notification:', error);
        }

        return null;
    });

// Create and deploy your first functions
// https://firebase.google.com/docs/functions/get-started

// exports.helloWorld = onRequest((request, response) => {
//   logger.info("Hello logs!", {structuredData: true});
//   response.send("Hello from Firebase!");
// });
