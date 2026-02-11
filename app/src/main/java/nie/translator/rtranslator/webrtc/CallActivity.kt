package nie.translator.rtranslator.webrtc

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import nie.translator.rtranslator.R

class CallActivity : AppCompatActivity() {

    private val viewModel: CallViewModel by viewModels { CallViewModelFactory(this) }
    
    private lateinit var textStatus: TextView
    private lateinit var btnStartCall: Button
    private lateinit var btnMute: Button
    private lateinit var btnSpeaker: Button
    private lateinit var btnEndCall: Button

    private var isMuted = false
    private var isSpeakerOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        textStatus = findViewById(R.id.text_status)
        btnStartCall = findViewById(R.id.button_start_call)
        btnMute = findViewById(R.id.button_mute)
        btnSpeaker = findViewById(R.id.button_speaker)
        btnEndCall = findViewById(R.id.button_end_call)

        setupListeners()
        observeViewModel()
        checkPermissions()
    }

    private fun setupListeners() {
        btnStartCall.setOnClickListener {
            viewModel.startCall()
        }

        btnMute.setOnClickListener {
            isMuted = !isMuted
            viewModel.toggleMute(isMuted)
            btnMute.text = if (isMuted) "Unmute" else "Mute"
        }

        btnSpeaker.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            viewModel.toggleSpeaker(isSpeakerOn)
            btnSpeaker.text = if (isSpeakerOn) "Earpiece" else "Speaker"
        }

        btnEndCall.setOnClickListener {
            viewModel.endCall()
            finish()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.callState.collect { state ->
                when (state) {
                    is CallState.Idle -> textStatus.text = "Idle"
                    is CallState.Connecting -> textStatus.text = "Connecting..."
                    is CallState.Connected -> textStatus.text = "Connected"
                    is CallState.Ended -> textStatus.text = "Call Ended"
                }
            }
        }
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
        
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
             if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                 Toast.makeText(this, "Permissions required for call", Toast.LENGTH_SHORT).show()
                 finish()
             }
        }
    }
}
