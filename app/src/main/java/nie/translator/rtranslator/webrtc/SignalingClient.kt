package nie.translator.rtranslator.webrtc

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class SignalingClient {
    private val TAG = "SignalingClient"
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    
    // Default to a placeholder, ideally this comes from config
    private val signalingUrl = "ws://echo.websocket.org" // Placeholder

    private val _signalingEvents = MutableSharedFlow<SignalingEvent>()
    val signalingEvents: SharedFlow<SignalingEvent> = _signalingEvents

    fun connect() {
        val request = Request.Builder().url(signalingUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket Connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received message: $text")
                handleMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket Closed: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket Error", t)
            }
        })
    }

    private fun handleMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.optString("type")
            CoroutineScope(Dispatchers.Default).launch {
                when (type) {
                    "offer" -> _signalingEvents.emit(SignalingEvent.OfferReceived(json))
                    "answer" -> _signalingEvents.emit(SignalingEvent.AnswerReceived(json))
                    "candidate" -> _signalingEvents.emit(SignalingEvent.IceCandidateReceived(json))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: $message", e)
        }
    }

    fun send(data: JSONObject) {
        webSocket?.send(data.toString())
    }

    fun close() {
        webSocket?.close(1000, "User disconnected")
    }

    sealed class SignalingEvent {
        data class OfferReceived(val data: JSONObject) : SignalingEvent()
        data class AnswerReceived(val data: JSONObject) : SignalingEvent()
        data class IceCandidateReceived(val data: JSONObject) : SignalingEvent()
    }
}
