const WebSocket = require('ws');

const wss = new WebSocket.Server({ port: 8080 });

// Store connected users: userId -> WebSocket
const users = new Map();

console.log('Signaling Server running on ws://localhost:8080');

wss.on('connection', (ws) => {
    console.log('New client connected');

    ws.on('message', (message) => {
        try {
            const data = JSON.parse(message);
            handleMessage(ws, data);
        } catch (error) {
            console.error('Error parsing message:', error);
        }
    });

    ws.on('close', () => {
        handleDisconnect(ws);
    });

    ws.on('error', (error) => {
        console.error('WebSocket error:', error);
    });
});

function handleMessage(ws, data) {
    const { type, userId, targetId } = data;

    switch (type) {
        case 'login':
            handleLogin(ws, userId);
            break;
        case 'offer':
        case 'answer':
        case 'candidate':
            handleSignaling(type, ws, targetId, data);
            break;
        default:
            console.warn('Unknown message type:', type);
            break;
    }
}

function handleLogin(ws, userId) {
    if (!userId) {
        sendError(ws, 'Missing userId for login');
        return;
    }

    // Check if user is already connected (optional: disconnect old session)
    if (users.has(userId)) {
        console.log(`User ${userId} reconnected, replacing session`);
    }

    // Store user connection
    users.set(userId, ws);
    ws.userId = userId; // Attach userId to WebSocket object for easy access

    console.log(`User logged in: ${userId}`);
    send(ws, { type: 'login', success: true });
    
    // Notify others (optional, for simple peer discovery)
    broadcastUserList();
}

function handleSignaling(type, ws, targetId, data) {
    if (!targetId) {
        sendError(ws, `Missing targetId for ${type}`);
        return;
    }

    const targetWs = users.get(targetId);
    if (targetWs && targetWs.readyState === WebSocket.OPEN) {
        console.log(`Forwarding ${type} from ${ws.userId} to ${targetId}`);
        // Add senderId to the message so the receiver knows who sent it
        data.senderId = ws.userId;
        send(targetWs, data);
    } else {
        console.warn(`Target user ${targetId} not found or offline`);
        sendError(ws, `User ${targetId} is offline`);
    }
}

function handleDisconnect(ws) {
    if (ws.userId) {
        console.log(`User disconnected: ${ws.userId}`);
        users.delete(ws.userId);
        broadcastUserList();
    }
}

function broadcastUserList() {
    const userList = Array.from(users.keys());
    const message = { type: 'users', users: userList };
    
    users.forEach((client) => {
        if (client.readyState === WebSocket.OPEN) {
            send(client, message);
        }
    });
}

function send(ws, message) {
    if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify(message));
    }
}

function sendError(ws, errorMessage) {
    send(ws, { type: 'error', message: errorMessage });
}

/*
Example Message Formats:

1. Login:
   { "type": "login", "userId": "user1" }

2. Offer:
   { "type": "offer", "targetId": "user2", "api": "...", "sdp": "..." }

3. Answer:
   { "type": "answer", "targetId": "user1", "api": "...", "sdp": "..." }

4. ICE Candidate:
   { "type": "candidate", "targetId": "user2", "candidate": { ... } }
*/
