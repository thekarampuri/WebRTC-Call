package nie.translator.rtranslator.webrtc

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class CallViewModel(private val webRTCManager: WebRTCManager, private val signalingClient: SignalingClient) : ViewModel() {

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState

    init {
        // Observe Signaling Events
        viewModelScope.launch {
            signalingClient.signalingEvents.collect { event ->
                when (event) {
                    is SignalingClient.SignalingEvent.OfferReceived -> handleOffer(event.data)
                    is SignalingClient.SignalingEvent.AnswerReceived -> handleAnswer(event.data)
                    is SignalingClient.SignalingEvent.IceCandidateReceived -> handleIceCandidate(event.data)
                }
            }
        }
    }

    fun startCall() {
        _callState.value = CallState.Connecting
        signalingClient.connect()
        webRTCManager.startLocalAudio()
        webRTCManager.createOffer { sessionDescription ->
            val offerJson = JSONObject().apply {
                put("type", "offer")
                put("sdp", sessionDescription.description)
            }
            signalingClient.send(offerJson)
        }
    }

    private fun handleOffer(data: JSONObject) {
        val sdp = data.getString("sdp")
        val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdp)
        webRTCManager.setRemoteDescription(sessionDescription)
        webRTCManager.startLocalAudio()
        webRTCManager.createAnswer { answerSdp ->
             val answerJson = JSONObject().apply {
                put("type", "answer")
                put("sdp", answerSdp.description)
            }
            signalingClient.send(answerJson)
        }
        _callState.value = CallState.Connected
    }

    private fun handleAnswer(data: JSONObject) {
        val sdp = data.getString("sdp")
        val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        webRTCManager.setRemoteDescription(sessionDescription)
        _callState.value = CallState.Connected
    }

    private fun handleIceCandidate(data: JSONObject) {
        val sdpMid = data.getString("sdpMid")
        val sdpMLineIndex = data.getInt("sdpMLineIndex")
        val sdp = data.getString("candidate")
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
        webRTCManager.addIceCandidate(candidate)
    }

    fun toggleMute(isMuted: Boolean) {
        webRTCManager.toggleMute(isMuted)
    }
    
    fun toggleSpeaker(isSpeaker: Boolean) {
        webRTCManager.setSpeakerMode(isSpeaker)
    }

    fun endCall() {
        webRTCManager.close()
        signalingClient.close()
        _callState.value = CallState.Ended
    }

    override fun onCleared() {
        super.onCleared()
        webRTCManager.close()
        signalingClient.close()
    }
}

sealed class CallState {
    object Idle : CallState()
    object Connecting : CallState()
    object Connected : CallState()
    object Ended : CallState()
}

class CallViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CallViewModel::class.java)) {
            val webRTCManager = WebRTCManager(context)
            val signalingClient = SignalingClient()
            return CallViewModel(webRTCManager, signalingClient) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
