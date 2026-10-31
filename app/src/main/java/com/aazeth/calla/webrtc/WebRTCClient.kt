package com.aazeth.calla.webrtc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.aazeth.calla.util.SdpUtil
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

enum class CallConnectionStatus {
    IDLE,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    FAILED
}

enum class AudioRoute {
    SPEAKER,
    EARPIECE,
    HEADSET
}

data class AudioDspSettings(
    val noiseSuppression: Boolean = false,
    val echoCancellation: Boolean = false,
    val autoGainControl: Boolean = false,
    val highpassFilter: Boolean = false
)

class WebRTCClient(
    private val context: Context,
    private val onIceCandidateGenerated: (IceCandidate) -> Unit,
    private val onConnectionStatusChanged: (CallConnectionStatus) -> Unit,
    private val onAudioRouteChanged: ((AudioRoute) -> Unit)? = null
) {
    companion object {
        private const val TAG = "WebRTCClient"
        private const val AUDIO_TRACK_ID = "calla_audio_track"

        // Public STUN servers for reliable remote NAT traversal across cellular & WiFi
        private val STUN_SERVERS = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
            "stun:stun2.l.google.com:19302",
            "stun:stun3.l.google.com:19302",
            "stun:stun4.l.google.com:19302",
            "stun:stun.cloudflare.com:3478"
        )
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private val queuedRemoteCandidates = mutableListOf<IceCandidate>()
    @Volatile private var isRemoteDescriptionSet = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var originalAudioMode = AudioManager.MODE_NORMAL
    private var originalSpeakerphoneOn = false

    var isMicMuted: Boolean = false
        private set

    var currentAudioRoute: AudioRoute = AudioRoute.SPEAKER
        private set

    private var isHeadsetConnected: Boolean = false

    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", 0)
                    isHeadsetConnected = state == 1
                    if (isHeadsetConnected) {
                        setAudioRoute(AudioRoute.HEADSET)
                    } else {
                        // Revert to speaker when headset unplugged
                        setAudioRoute(AudioRoute.SPEAKER)
                    }
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    // Headset disconnected abruptly
                    isHeadsetConnected = false
                    setAudioRoute(AudioRoute.SPEAKER)
                }
            }
        }
    }

    init {
        initPeerConnectionFactory()
        registerHeadsetDetection()
    }

    private fun initPeerConnectionFactory() {
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val options = PeerConnectionFactory.Options()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()
    }

    private fun registerHeadsetDetection() {
        // Initial detection of wired/bluetooth headset
        checkInitialHeadsetState()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        try {
            context.registerReceiver(headsetReceiver, filter)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register headset receiver: ${e.message}")
        }
    }

    private fun checkInitialHeadsetState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            isHeadsetConnected = devices.any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
        } else {
            @Suppress("DEPRECATION")
            isHeadsetConnected = audioManager.isWiredHeadsetOn
        }
    }

    fun startLocalAudio(dspSettings: AudioDspSettings = AudioDspSettings()) {
        if (localAudioTrack != null) return

        val factory = peerConnectionFactory ?: return

        // Configure audio DSP constraints based on user preferences.
        // If all are false, WebRTC captures unfiltered raw audio preserving breathing & fine dynamics.
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", if (dspSettings.echoCancellation) "true" else "false"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", if (dspSettings.autoGainControl) "true" else "false"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", if (dspSettings.highpassFilter) "true" else "false"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", if (dspSettings.noiseSuppression) "true" else "false"))
        }

        audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource).apply {
            setEnabled(true)
        }

        // Configure audio routing for call
        originalAudioMode = audioManager.mode
        originalSpeakerphoneOn = audioManager.isSpeakerphoneOn

        // Initial audio routing
        if (isHeadsetConnected) {
            setAudioRoute(AudioRoute.HEADSET)
        } else {
            setAudioRoute(AudioRoute.SPEAKER)
        }
    }

    fun setAudioRoute(route: AudioRoute) {
        currentAudioRoute = route
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        when (route) {
            AudioRoute.SPEAKER -> {
                audioManager.isSpeakerphoneOn = true
            }
            AudioRoute.EARPIECE, AudioRoute.HEADSET -> {
                audioManager.isSpeakerphoneOn = false
            }
        }

        onAudioRouteChanged?.invoke(currentAudioRoute)
    }

    fun cycleAudioRoute(): AudioRoute {
        val nextRoute = when (currentAudioRoute) {
            AudioRoute.SPEAKER -> if (isHeadsetConnected) AudioRoute.HEADSET else AudioRoute.EARPIECE
            AudioRoute.EARPIECE -> AudioRoute.SPEAKER
            AudioRoute.HEADSET -> AudioRoute.SPEAKER
        }
        setAudioRoute(nextRoute)
        return nextRoute
    }

    fun createPeerConnection(turnConfig: TurnConfig = TurnConfig()) {
        val factory = peerConnectionFactory ?: return

        isRemoteDescriptionSet = false
        synchronized(queuedRemoteCandidates) {
            queuedRemoteCandidates.clear()
        }

        val iceServers = mutableListOf<PeerConnection.IceServer>()

        // 1. Public STUN servers for direct NAT hole punching
        STUN_SERVERS.forEach { url ->
            iceServers.add(PeerConnection.IceServer.builder(url).createIceServer())
        }

        // 2. TURN servers for cross-network relay across cellular (CGNAT) & symmetric NAT routers
        if (turnConfig.useCustomTurn && turnConfig.customServer.isNotBlank()) {
            try {
                val serverUri = turnConfig.customServer.trim()
                val formattedUri = if (!serverUri.startsWith("turn:") && !serverUri.startsWith("turns:") && !serverUri.startsWith("stun:")) {
                    "turn:$serverUri"
                } else {
                    serverUri
                }
                val builder = PeerConnection.IceServer.builder(formattedUri)
                if (turnConfig.customUsername.isNotBlank()) {
                    builder.setUsername(turnConfig.customUsername.trim())
                }
                if (turnConfig.customCredential.isNotBlank()) {
                    builder.setPassword(turnConfig.customCredential.trim())
                }
                iceServers.add(builder.createIceServer())
                Log.d(TAG, "Configured custom TURN server: $formattedUri")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to build custom TURN server: ${e.message}", e)
            }
        } else {
            // Built-in free OpenRelay TURN servers (Metered.ca)
            // UDP ports 80 & 443 + TCP port 443 fallback for carrier firewalls
            val openRelayEndpoints = listOf(
                "turn:openrelay.metered.ca:80",
                "turn:openrelay.metered.ca:443",
                "turn:openrelay.metered.ca:443?transport=tcp",
                "turns:openrelay.metered.ca:443?transport=tcp"
            )
            openRelayEndpoints.forEach { uri ->
                iceServers.add(
                    PeerConnection.IceServer.builder(uri)
                        .setUsername("openrelayproject")
                        .setPassword("openrelayproject")
                        .createIceServer()
                )
            }
            Log.d(TAG, "Configured built-in OpenRelay TURN servers (${openRelayEndpoints.size} endpoints)")
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        }

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    Log.d(TAG, "Local ICE candidate generated: ${it.sdp}")
                    com.aazeth.calla.util.NetworkLogger.log("ICE", "Local candidate generated: ${it.sdpMid} - ${it.sdp}")
                    onIceCandidateGenerated(it)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "SignalingState changed: $state")
                com.aazeth.calla.util.NetworkLogger.log("WebRTC", "SignalingState changed: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "IceConnectionState changed: $state")
                com.aazeth.calla.util.NetworkLogger.log("WebRTC", "IceConnectionState changed: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        onConnectionStatusChanged(CallConnectionStatus.CONNECTED)
                    }
                    PeerConnection.IceConnectionState.CHECKING -> {
                        onConnectionStatusChanged(CallConnectionStatus.CONNECTING)
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        onConnectionStatusChanged(CallConnectionStatus.DISCONNECTED)
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        onConnectionStatusChanged(CallConnectionStatus.FAILED)
                    }
                    PeerConnection.IceConnectionState.CLOSED -> {
                        onConnectionStatusChanged(CallConnectionStatus.IDLE)
                    }
                    else -> {}
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "IceGatheringState changed: $state")
                com.aazeth.calla.util.NetworkLogger.log("WebRTC", "IceGatheringState changed: $state")
            }

            override fun onAddStream(stream: org.webrtc.MediaStream?) {
                Log.d(TAG, "Stream added with audio tracks: ${stream?.audioTracks?.size}")
            }

            override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}

            override fun onDataChannel(dataChannel: org.webrtc.DataChannel?) {}

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "Renegotiation needed")
            }

            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out org.webrtc.MediaStream>?) {
                Log.d(TAG, "Track added: ${receiver?.track()?.kind()}")
            }
        })

        // Add local audio track
        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("calla_stream"))
        }
    }

    fun createOffer(bitrateKbps: Int, onOfferReady: (SessionDescription) -> Unit) {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                val mungedSdp = SdpUtil.setOpusBitrate(desc.description, bitrateKbps)
                val mungedDesc = SessionDescription(desc.type, mungedSdp)
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d(TAG, "SetLocalDescription (Offer) success with bitrate $bitrateKbps kbps")
                        onOfferReady(mungedDesc)
                    }
                    override fun onSetFailure(err: String?) {
                        Log.e(TAG, "SetLocalDescription failure: $err")
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, mungedDesc)
            }

            override fun onCreateFailure(err: String?) {
                Log.e(TAG, "CreateOffer failure: $err")
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(err: String?) {}
        }, constraints)
    }

    fun restartIce(bitrateKbps: Int, onOfferReady: (SessionDescription) -> Unit) {
        val pc = peerConnection ?: return
        Log.d(TAG, "Triggering ICE restart offer")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                val mungedSdp = SdpUtil.setOpusBitrate(desc.description, bitrateKbps)
                val mungedDesc = SessionDescription(desc.type, mungedSdp)
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d(TAG, "SetLocalDescription (ICE Restart Offer) success")
                        onOfferReady(mungedDesc)
                    }
                    override fun onSetFailure(err: String?) {
                        Log.e(TAG, "SetLocalDescription ICE restart failure: $err")
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, mungedDesc)
            }

            override fun onCreateFailure(err: String?) {
                Log.e(TAG, "CreateOffer ICE restart failure: $err")
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(err: String?) {}
        }, constraints)
    }

    fun handleRemoteOfferAndCreateAnswer(
        remoteOfferSdp: String,
        bitrateKbps: Int,
        onAnswerReady: (SessionDescription) -> Unit
    ) {
        val pc = peerConnection ?: return
        val remoteOffer = SessionDescription(SessionDescription.Type.OFFER, remoteOfferSdp)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d(TAG, "SetRemoteDescription (Offer) success")
                isRemoteDescriptionSet = true
                drainQueuedCandidates()
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                }
                pc.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        if (desc == null) return
                        val mungedSdp = SdpUtil.setOpusBitrate(desc.description, bitrateKbps)
                        val mungedAnswer = SessionDescription(desc.type, mungedSdp)
                        pc.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() {
                                Log.d(TAG, "SetLocalDescription (Answer) success with bitrate $bitrateKbps kbps")
                                onAnswerReady(mungedAnswer)
                            }
                            override fun onSetFailure(err: String?) {
                                Log.e(TAG, "SetLocalDescription (Answer) failure: $err")
                            }
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onCreateFailure(p0: String?) {}
                        }, mungedAnswer)
                    }

                    override fun onCreateFailure(err: String?) {
                        Log.e(TAG, "CreateAnswer failure: $err")
                    }

                    override fun onSetSuccess() {}
                    override fun onSetFailure(err: String?) {}
                }, constraints)
            }

            override fun onSetFailure(err: String?) {
                Log.e(TAG, "SetRemoteDescription (Offer) failure: $err")
            }

            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, remoteOffer)
    }

    fun handleRemoteAnswer(remoteAnswerSdp: String) {
        val pc = peerConnection ?: return
        val remoteAnswer = SessionDescription(SessionDescription.Type.ANSWER, remoteAnswerSdp)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d(TAG, "SetRemoteDescription (Answer) success")
                isRemoteDescriptionSet = true
                drainQueuedCandidates()
            }

            override fun onSetFailure(err: String?) {
                Log.e(TAG, "SetRemoteDescription (Answer) failure: $err")
            }

            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, remoteAnswer)
    }

    private fun drainQueuedCandidates() {
        val pc = peerConnection ?: return
        synchronized(queuedRemoteCandidates) {
            if (queuedRemoteCandidates.isNotEmpty()) {
                Log.d(TAG, "Draining ${queuedRemoteCandidates.size} queued remote candidates")
                for (candidate in queuedRemoteCandidates) {
                    pc.addIceCandidate(candidate)
                }
                queuedRemoteCandidates.clear()
            }
        }
    }

    fun addRemoteIceCandidate(candidate: IceCandidate) {
        val pc = peerConnection ?: return
        if (!isRemoteDescriptionSet || pc.remoteDescription == null) {
            synchronized(queuedRemoteCandidates) {
                queuedRemoteCandidates.add(candidate)
            }
            Log.d(TAG, "Queued remote candidate until remote description set: ${candidate.sdp}")
            com.aazeth.calla.util.NetworkLogger.log("ICE", "Queued remote candidate: ${candidate.sdpMid} - ${candidate.sdp}")
        } else {
            pc.addIceCandidate(candidate)
            com.aazeth.calla.util.NetworkLogger.log("ICE", "Added remote candidate: ${candidate.sdpMid} - ${candidate.sdp}")
        }
    }

    fun toggleMute(): Boolean {
        isMicMuted = !isMicMuted
        localAudioTrack?.setEnabled(!isMicMuted)
        return isMicMuted
    }

    fun toggleSpeaker(): AudioRoute {
        return cycleAudioRoute()
    }

    fun close() {
        try {
            isRemoteDescriptionSet = false
            synchronized(queuedRemoteCandidates) {
                queuedRemoteCandidates.clear()
            }
            try {
                context.unregisterReceiver(headsetReceiver)
            } catch (_: Exception) {}

            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null

            localAudioTrack?.setEnabled(false)
            localAudioTrack?.dispose()
            localAudioTrack = null

            audioSource?.dispose()
            audioSource = null

            audioManager.mode = originalAudioMode
            audioManager.isSpeakerphoneOn = originalSpeakerphoneOn
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebRTCClient", e)
        }
    }
}
