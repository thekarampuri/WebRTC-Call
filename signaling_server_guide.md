# WebRTC Signaling Server Guide

This guide explains the Node.js signaling server implementation, how to run it, and how to integrate it with the Android client.

## 1. Concepts

### What is Signaling?
WebRTC allows peer-to-peer communication, but peers don't know each other's IP addresses or capabilities initially. **Signaling** is the mechanism to exchange this information *before* the direct connection is established.
-   **SDP (Session Description Protocol)**: Describes media capabilities (codecs, resolution, etc.).
    -   **Offer**: Sent by the caller.
    -   **Answer**: Sent by the callee.
-   **ICE Candidates**: Network paths (IP:Port combinations) to reach the peer.

### Signaling vs. Media Server
*   **Signaling Server** (This Node.js app): Handles the setup. Low bandwidth. Messages are small JSON texts.
*   **Media Server (SFU/MCU)**: (Optional, not used here) Relays audio/video streams. High bandwidth. Used for group calls or recording.
*   **STUN Server**: Used by peers to discover their own public IP. We use Google's free STUN server.
*   **TURN Server**: (Optional) Relays traffic if P2P fails (e.g., symmetric NATs). Required for production reliability but omitted for this minimal setup.

## 2. Server Implementation (`server.js`)
The server uses `ws` for WebSockets. It acts as a router:
1.  **Login**: Maps `userId` to `WebSocket` connection.
2.  **Routing**: When User A sends a message to User B, the server looks up User B's WebSocket and forwards the message.

## 3. Running the Server

### Local
1.  Navigate to `signaling-server`:
    ```bash
    cd signaling-server
    ```
2.  Install dependencies:
    ```bash
    npm install
    ```
3.  Start server:
    ```bash
    node server.js
    ```
    Output: `Signaling Server running on ws://localhost:8080`

### Deployment (e.g., Render/Heroku)
1.  Push the `signaling-server` folder to a git repo.
2.  Create a Web Service on Render/Heroku.
3.  Set build command: `npm install`
4.  Set start command: `node server.js`
5.  Get the public URL (e.g., `wss://my-webrtc-app.onrender.com`).

## 4. Android Client Integration

Update your `SignalingClient.kt` to handle the login flow and target user.

### Current `SignalingClient.kt` Updates needed:

1.  **Change `connect()` to accept `userId`**:
    ```kotlin
    fun connect(userId: String) {
        // ... (connection logic)
        // ON OPEN: Send login message
        val loginJson = JSONObject().apply {
            put("type", "login")
            put("userId", userId)
        }
        send(loginJson)
    }
    ```

2.  **Update `createOffer` to include `targetId`**:
    In `CallViewModel.kt`, when sending the offer:
    ```kotlin
    val offerJson = JSONObject().apply {
        put("type", "offer")
        put("sdp", sessionDescription.description)
        put("targetId", targetUserId) // YOU NEED THE TARGET ID
        put("userId", myUserId)
    }
    signalingClient.send(offerJson)
    ```

3.  **Handle `senderId` in `CallViewModel`**:
    When receiving an offer:
    ```kotlin
    // Store senderId to know who to answer
    val senderId = data.getString("senderId")
    ```
    When sending answer:
    ```kotlin
    val answerJson = JSONObject().apply {
        put("type", "answer")
        put("sdp", answerSdp.description)
        put("targetId", senderId) // Reply to sender
    }
    signalingClient.send(answerJson)
    ```

## 5. Message Flow

1.  **User A** connects -> Sends `{type: 'login', userId: 'A'}`.
2.  **User B** connects -> Sends `{type: 'login', userId: 'B'}`.
3.  **User A** wants to call **User B**:
    -   A creates Offer.
    -   A sends `{type: 'offer', targetId: 'B', sdp: '...'}` to Server.
    -   Server forwards to B.
4.  **User B** receives Offer:
    -   B sets Remote Description.
    -   B creates Answer.
    -   B sends `{type: 'answer', targetId: 'A', sdp: '...'}` to Server.
    -   Server forwards to A.
5.  **User A** receives Answer -> Sets Remote Description.
6.  **Both** exchange ICE candidates via Server (`{type: 'candidate', targetId: '...', candidate: ...}`).
7.  **P2P Connection Established** -> Audio flows directly between A and B.
