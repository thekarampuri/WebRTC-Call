package nie.translator.rtranslator.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONObject
import org.webrtc.*

class WebRTCManager(private val context: Context) {
    private val TAG = "WebRTCManager"

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var localMediaStream: MediaStream? = null

    private val rootEglBase: EglBase = EglBase.create()
    
    private val _webRTCEvent = MutableSharedFlow<WebRTCEvent>()
    val webRTCEvent: SharedFlow<WebRTCEvent> = _webRTCEvent

    private val iceServers = listOf(
        // Google STUN server
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    init {
        initializePeerConnectionFactory()
        createPeerConnectionFactory()
    }

    private fun initializePeerConnectionFactory() {
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)
    }

    private fun createPeerConnectionFactory() {
        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()
    }
    
    fun startLocalAudio() {
        val audioConstraints = MediaConstraints()
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        audioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource)
        audioTrack?.setEnabled(true)
        
        localMediaStream = peerConnectionFactory.createLocalMediaStream("ARDAMS")
        localMediaStream?.addTrack(audioTrack)
    }

    fun createPeerConnection(observer: PeerConnection.Observer) {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA
        
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, observer)
        
        // Add local stream to PeerConnection
        localMediaStream?.let { stream ->
            peerConnection?.addStream(stream)
        }
    }

    fun createOffer(callback: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    peerConnection?.setLocalDescription(SimpleSdpObserver(), it)
                    callback(it)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) { Log.e(TAG, "createOffer failed: $p0") }
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    fun createAnswer(callback: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    peerConnection?.setLocalDescription(SimpleSdpObserver(), it)
                    callback(it)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) { Log.e(TAG, "createAnswer failed: $p0") }
            override fun onSetFailure(p0: String?) {}

        }, constraints)
    }

    fun setRemoteDescription(desc: SessionDescription) {
        peerConnection?.setRemoteDescription(SimpleSdpObserver(), desc)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun toggleMute(mute: Boolean) {
        audioTrack?.setEnabled(!mute)
    }
    
    fun setSpeakerMode(enableSpeaker: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isSpeakerphoneOn = enableSpeaker
    }

    fun close() {
        peerConnection?.close()
        peerConnection = null
        audioSource?.dispose()
        audioTrack?.dispose()
        peerConnectionFactory.dispose()
        
        // Reset audio output
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isSpeakerphoneOn = false
    }

    sealed class WebRTCEvent {
        data class OnIceCandidate(val candidate: IceCandidate) : WebRTCEvent()
        data class OnConnectionChange(val newState: PeerConnection.PeerConnectionState) : WebRTCEvent()
    }
    
    // Helper adapter to avoid implementing all methods everywhere
    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }
}
