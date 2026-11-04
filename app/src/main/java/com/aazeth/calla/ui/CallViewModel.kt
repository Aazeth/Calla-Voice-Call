package com.aazeth.calla.ui

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aazeth.calla.service.CallService
import com.aazeth.calla.signaling.RoomState
import com.aazeth.calla.signaling.SignalingClient
import com.aazeth.calla.webrtc.AudioDspSettings
import com.aazeth.calla.webrtc.AudioRoute
import com.aazeth.calla.webrtc.CallConnectionStatus
import com.aazeth.calla.webrtc.TurnConfig
import com.aazeth.calla.webrtc.WebRTCClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate

import com.aazeth.calla.webrtc.DspPreset

enum class ScreenState {
    LOBBY,
    IN_CALL
}

data class CallUiState(
    val screen: ScreenState = ScreenState.LOBBY,
    val nickname: String = "",
    val bitrateKbps: Int = 128,
    val joinCodeInput: String = "",
    val currentRoomId: String = "",
    val isHost: Boolean = false,
    val peerName: String = "",
    val callStatus: CallConnectionStatus = CallConnectionStatus.IDLE,
    val callStatusText: String = "",
    val isMicMuted: Boolean = false,
    val audioRoute: AudioRoute = AudioRoute.SPEAKER,
    val dspSettings: AudioDspSettings = AudioDspSettings(),
    val turnConfig: TurnConfig = TurnConfig(),
    val callDurationSeconds: Long = 0L,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val isLoggingEnabled: Boolean = false,
    val showAudioDspDialog: Boolean = false,
    val pingMs: Long? = null,
    val isMinimized: Boolean = false,
    val volumeBoostPercent: Int = 300,
    val isPeerMuted: Boolean = false,
    val audioLevel: Float = 0f,
    val showAudioControlSheet: Boolean = false
)

class CallViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "CallViewModel"
        private const val PREFS_NAME = "calla_preferences"
        private const val KEY_NICKNAME = "saved_nickname"
        private const val KEY_DSP_PRESET = "dsp_preset_enum"
        private const val KEY_DSP_NOISE = "dsp_noise_suppression"
        private const val KEY_DSP_ECHO = "dsp_echo_cancellation"
        private const val KEY_DSP_AGC = "dsp_auto_gain_control"
        private const val KEY_DSP_HIGHPASS = "dsp_highpass_filter"
        private const val KEY_DSP_LIMITER_CEILING = "dsp_limiter_ceiling_db"
        private const val KEY_DSP_AGC_TARGET = "dsp_agc_target_level_db"
        private const val KEY_USE_CUSTOM_TURN = "turn_use_custom"
        private const val KEY_CUSTOM_TURN_SERVER = "turn_custom_server"
        private const val KEY_CUSTOM_TURN_USER = "turn_custom_user"
        private const val KEY_CUSTOM_TURN_PASS = "turn_custom_pass"
        private const val KEY_ENABLE_NETWORK_LOGS = "enable_network_logs"
        private const val KEY_VOLUME_BOOST = "volume_boost_percent"
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val signalingClient = SignalingClient(application)
    private var webRtcClient: WebRTCClient? = null

    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var pingJob: Job? = null
    private var isEndingCall = false
    private var isAnswerCreated = false
    private var lastNetworkStateWasConnected = true
    private var iceRestartRetryCount = 0

    // Receiver for End Call button in system notification
    private val notificationActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == CallService.ACTION_CALL_ENDED_FROM_NOTIFICATION) {
                Log.d(TAG, "Call ended via notification action")
                endCall(remoteInitiated = false)
            }
        }
    }

    // Network callback to detect disconnection and handle auto-reconnect + ICE Restart
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network restored: onAvailable")
            com.aazeth.calla.util.NetworkLogger.log("Network", "Restored: onAvailable")
            if (!lastNetworkStateWasConnected && _uiState.value.screen == ScreenState.IN_CALL) {
                val roomId = _uiState.value.currentRoomId
                if (roomId.isNotBlank()) {
                    Log.d(TAG, "Auto-reconnecting signaling and triggering ICE restart for room: $roomId")
                    com.aazeth.calla.util.NetworkLogger.log("Network", "Auto-reconnecting signaling & ICE restart for room $roomId")
                    viewModelScope.launch {
                        isAnswerCreated = false
                        signalingClient.reconnectWebSocket(roomId)
                        if (_uiState.value.isHost) {
                            webRtcClient?.restartIce(_uiState.value.bitrateKbps) { newOffer ->
                                Log.d(TAG, "ICE restart offer generated on network restore")
                                signalingClient.sendOffer(roomId, newOffer.description)
                            }
                        } else {
                            signalingClient.requestOffer(roomId)
                        }
                    }
                }
            }
            lastNetworkStateWasConnected = true
        }

        override fun onLost(network: Network) {
            Log.w(TAG, "Network lost")
            com.aazeth.calla.util.NetworkLogger.log("Network", "Lost: onLost")
            lastNetworkStateWasConnected = false
            if (_uiState.value.screen == ScreenState.IN_CALL) {
                _uiState.update { it.copy(callStatusText = "Reconnecting network...") }
            }
        }
    }

    init {
        // Load Nickname
        val initialNickname = prefs.getString(KEY_NICKNAME, "") ?: ""
        val defaultName = if (initialNickname.isNotBlank()) {
            initialNickname
        } else {
            "User #" + (100..999).random()
        }

        // Load Audio DSP settings (Default: Custom Mode, Echo Cancellation only enabled)
        val presetStr = prefs.getString(KEY_DSP_PRESET, DspPreset.CUSTOM.name) ?: DspPreset.CUSTOM.name
        val savedPreset = try { DspPreset.valueOf(presetStr) } catch (_: Exception) { DspPreset.CUSTOM }

        val dsp = AudioDspSettings(
            noiseSuppression = prefs.getBoolean(KEY_DSP_NOISE, false),
            echoCancellation = prefs.getBoolean(KEY_DSP_ECHO, true),
            autoGainControl = prefs.getBoolean(KEY_DSP_AGC, false),
            highpassFilter = prefs.getBoolean(KEY_DSP_HIGHPASS, false),
            limiterCeilingDb = prefs.getFloat(KEY_DSP_LIMITER_CEILING, -4.0f),
            agcTargetLevelDb = prefs.getFloat(KEY_DSP_AGC_TARGET, -1.0f),
            preset = savedPreset
        )

        // Load TURN relay settings (Default: OpenRelay built-in)
        val turn = TurnConfig(
            useCustomTurn = prefs.getBoolean(KEY_USE_CUSTOM_TURN, false),
            customServer = prefs.getString(KEY_CUSTOM_TURN_SERVER, "") ?: "",
            customUsername = prefs.getString(KEY_CUSTOM_TURN_USER, "") ?: "",
            customCredential = prefs.getString(KEY_CUSTOM_TURN_PASS, "") ?: ""
        )

        // Load Network Logging Preference
        val isLogging = prefs.getBoolean(KEY_ENABLE_NETWORK_LOGS, false)
        com.aazeth.calla.util.NetworkLogger.isEnabled = isLogging

        // Load Volume Boost Preference (Default: 300%)
        val savedVolumeBoost = prefs.getInt(KEY_VOLUME_BOOST, 300).coerceIn(0, 500)

        _uiState.update {
            it.copy(
                nickname = defaultName,
                dspSettings = dsp,
                turnConfig = turn,
                isLoggingEnabled = isLogging,
                volumeBoostPercent = savedVolumeBoost
            )
        }

        // Register notification broadcast receiver
        val filter = IntentFilter(CallService.ACTION_CALL_ENDED_FROM_NOTIFICATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(notificationActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(notificationActionReceiver, filter)
        }

        // Register NetworkCallback
        try {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    fun minimizeCall() {
        _uiState.update { it.copy(isMinimized = true) }
    }

    fun expandCall() {
        _uiState.update { it.copy(isMinimized = false) }
    }

    fun setNickname(name: String) {
        _uiState.update { it.copy(nickname = name) }
        prefs.edit().putString(KEY_NICKNAME, name).apply()
    }

    fun setBitrate(kbps: Int) {
        _uiState.update { it.copy(bitrateKbps = kbps.coerceIn(64, 256)) }
    }

    fun setJoinCodeInput(code: String) {
        val filtered = code.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(joinCodeInput = filtered, errorMessage = null) }
    }

    fun setAudioDspDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showAudioDspDialog = visible) }
    }

    fun setAudioControlSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showAudioControlSheet = visible) }
    }

    fun setEnableNetworkLogs(enabled: Boolean) {
        com.aazeth.calla.util.NetworkLogger.isEnabled = enabled
        _uiState.update { it.copy(isLoggingEnabled = enabled) }
        prefs.edit().putBoolean(KEY_ENABLE_NETWORK_LOGS, enabled).apply()
        if (enabled) {
            com.aazeth.calla.util.NetworkLogger.log("System", "Network logging enabled")
        }
    }

    fun updateDspSettings(dsp: AudioDspSettings) {
        _uiState.update { it.copy(dspSettings = dsp) }
        prefs.edit()
            .putString(KEY_DSP_PRESET, dsp.preset.name)
            .putBoolean(KEY_DSP_NOISE, dsp.noiseSuppression)
            .putBoolean(KEY_DSP_ECHO, dsp.echoCancellation)
            .putBoolean(KEY_DSP_AGC, dsp.autoGainControl)
            .putBoolean(KEY_DSP_HIGHPASS, dsp.highpassFilter)
            .putFloat(KEY_DSP_LIMITER_CEILING, dsp.limiterCeilingDb)
            .putFloat(KEY_DSP_AGC_TARGET, dsp.agcTargetLevelDb)
            .apply()
        webRtcClient?.updateAudioDspSettings(dsp)
    }

    fun updateTurnConfig(turn: TurnConfig) {
        _uiState.update { it.copy(turnConfig = turn) }
        prefs.edit()
            .putBoolean(KEY_USE_CUSTOM_TURN, turn.useCustomTurn)
            .putString(KEY_CUSTOM_TURN_SERVER, turn.customServer)
            .putString(KEY_CUSTOM_TURN_USER, turn.customUsername)
            .putString(KEY_CUSTOM_TURN_PASS, turn.customCredential)
            .apply()
    }

    fun setVolumeBoost(boost: Int) {
        val clamped = boost.coerceIn(0, 500)
        _uiState.update { it.copy(volumeBoostPercent = clamped) }
        prefs.edit().putInt(KEY_VOLUME_BOOST, clamped).apply()
        webRtcClient?.setVolumeBoost(clamped)
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun createRoom() {
        val context = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _uiState.update { it.copy(errorMessage = "Microphone permission is required to start a call") }
            return
        }

        val code = (100000..999999).random().toString()
        val bitrate = _uiState.value.bitrateKbps
        val myName = _uiState.value.nickname.ifBlank { "Host" }
        isEndingCall = false
        isAnswerCreated = false

        _uiState.update {
            it.copy(
                screen = ScreenState.IN_CALL,
                currentRoomId = code,
                isHost = true,
                peerName = "Waiting for guest...",
                callStatus = CallConnectionStatus.CONNECTING,
                callStatusText = "Waiting for partner to join...",
                callDurationSeconds = 0L,
                isLoading = true,
                errorMessage = null
            )
        }

        CallService.start(context, code, "Waiting for guest...", bitrate, "Waiting")

        initWebRTC(
            isHost = true,
            roomId = code,
            bitrateKbps = bitrate,
            onReady = { rtc ->
                rtc.createOffer(bitrate) { offerDesc ->
                    viewModelScope.launch {
                        val result = signalingClient.createRoom(
                            roomId = code,
                            hostName = myName,
                            bitrateKbps = bitrate,
                            offerSdp = offerDesc.description
                        )
                        result.onSuccess {
                            _uiState.update { state -> state.copy(isLoading = false) }
                            startSignalingObservation(code, isHost = true)
                        }.onFailure { err ->
                            Log.e(TAG, "Failed to create room", err)
                            _uiState.update { state ->
                                state.copy(
                                    isLoading = false,
                                    errorMessage = "Failed to create room: ${err.localizedMessage}"
                                )
                            }
                            endCall()
                        }
                    }
                }
            }
        )
    }

    fun joinRoom() {
        val context = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _uiState.update { it.copy(errorMessage = "Microphone permission is required to join a call") }
            return
        }

        val code = _uiState.value.joinCodeInput.trim()
        if (code.length != 6) {
            _uiState.update { it.copy(errorMessage = "Room code must be 6 digits") }
            return
        }

        val myName = _uiState.value.nickname.ifBlank { "Guest" }
        val bitrate = _uiState.value.bitrateKbps
        isEndingCall = false
        isAnswerCreated = false

        _uiState.update {
            it.copy(
                screen = ScreenState.IN_CALL,
                currentRoomId = code,
                isHost = false,
                peerName = "Host",
                callStatus = CallConnectionStatus.CONNECTING,
                callStatusText = "Connecting to room...",
                callDurationSeconds = 0L,
                isLoading = true,
                errorMessage = null
            )
        }

        CallService.start(context, code, "Host", bitrate, "Connecting")

        initWebRTC(
            isHost = false,
            roomId = code,
            bitrateKbps = bitrate,
            onReady = { rtc ->
                viewModelScope.launch {
                    val joinResult = signalingClient.joinRoom(code, myName)
                    joinResult.onSuccess {
                        _uiState.update { state -> state.copy(isLoading = false) }
                        startSignalingObservation(code, isHost = false)
                    }.onFailure { err ->
                        Log.e(TAG, "Failed to join room", err)
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                errorMessage = "Failed to join: ${err.localizedMessage}"
                            )
                        }
                        endCall()
                    }
                }
            }
        )
    }

    private fun initWebRTC(
        isHost: Boolean,
        roomId: String,
        bitrateKbps: Int,
        onReady: (WebRTCClient) -> Unit
    ) {
        try {
            webRtcClient?.close()
            val context = getApplication<Application>()

            val client = WebRTCClient(
                context = context,
                onIceCandidateGenerated = { candidate ->
                    signalingClient.sendIceCandidate(roomId, isHost, candidate)
                },
                onConnectionStatusChanged = { status ->
                    viewModelScope.launch {
                        handleConnectionStatus(status)
                    }
                },
                onAudioRouteChanged = { route ->
                    _uiState.update { it.copy(audioRoute = route) }
                }
            )
            webRtcClient = client
            client.startLocalAudio(_uiState.value.dspSettings)
            client.setVolumeBoost(_uiState.value.volumeBoostPercent)
            client.createPeerConnection(_uiState.value.turnConfig)
            _uiState.update { it.copy(audioRoute = client.currentAudioRoute) }
            onReady(client)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC: ${e.message}", e)
            _uiState.update { it.copy(errorMessage = "Microphone error: ${e.message}", isLoading = false) }
            endCall()
        }
    }

    private fun startSignalingObservation(roomId: String, isHost: Boolean) {
        if (isHost) {
            signalingClient.onRequestOfferRequested = {
                if (!isEndingCall && _uiState.value.isHost) {
                    Log.d(TAG, "Host responding to request_offer by triggering ICE restart")
                    webRtcClient?.restartIce(_uiState.value.bitrateKbps) { newOffer ->
                        signalingClient.sendOffer(roomId, newOffer.description)
                    }
                }
            }
        }

        signalingClient.observeRoom(roomId) { roomState ->
            if (isEndingCall || roomState == null) return@observeRoom

            if (roomState.status == "ended") {
                Log.d(TAG, "Room was closed by remote user")
                viewModelScope.launch {
                    endCall(remoteInitiated = true)
                }
                return@observeRoom
            }

            if (isHost) {
                // Host receives Answer from Guest
                if (!roomState.joinerName.isNullOrBlank()) {
                    _uiState.update { it.copy(peerName = roomState.joinerName) }
                    CallService.update(
                        getApplication(),
                        status = "Connected",
                        peerName = roomState.joinerName
                    )
                }
                if (!roomState.answerSdp.isNullOrBlank() && _uiState.value.callStatus != CallConnectionStatus.CONNECTED) {
                    webRtcClient?.handleRemoteAnswer(roomState.answerSdp)
                }
            } else {
                // Guest receives Offer from Host
                if (!roomState.hostName.isNullOrBlank()) {
                    _uiState.update { it.copy(peerName = roomState.hostName) }
                    CallService.update(
                        getApplication(),
                        status = "Connected",
                        peerName = roomState.hostName
                    )
                }
                if (!roomState.offerSdp.isNullOrBlank() && (!isAnswerCreated || _uiState.value.callStatus == CallConnectionStatus.CONNECTING)) {
                    isAnswerCreated = true
                    val remoteBitrate = roomState.bitrateKbps.toInt().coerceIn(64, 256)
                    val guestName = _uiState.value.nickname.ifBlank { "Guest" }

                    webRtcClient?.handleRemoteOfferAndCreateAnswer(roomState.offerSdp, remoteBitrate) { answerDesc ->
                        signalingClient.sendAnswer(roomId, answerDesc.description, guestName)
                    }
                }
            }
        }

        signalingClient.onPeerMuteStateChanged = { isMuted ->
            _uiState.update { it.copy(isPeerMuted = isMuted) }
        }

        signalingClient.observeRemoteCandidates(roomId, isHost) { candidate ->
            try {
                webRtcClient?.addRemoteIceCandidate(candidate)
            } catch (e: Exception) {
                Log.w(TAG, "Error adding remote candidate: ${e.message}")
            }
        }
    }

    private fun handleConnectionStatus(status: CallConnectionStatus) {
        if (status == CallConnectionStatus.CONNECTED) {
            iceRestartRetryCount = 0
            startTimer()
            startPingPolling()
            val statusText = "Connected"
            _uiState.update { it.copy(callStatus = status, callStatusText = statusText) }
            CallService.update(getApplication(), status = statusText)
            return
        }

        if ((status == CallConnectionStatus.FAILED || status == CallConnectionStatus.DISCONNECTED) && _uiState.value.screen == ScreenState.IN_CALL) {
            stopTimer()
            stopPingPolling()
            if (iceRestartRetryCount < 3) {
                iceRestartRetryCount++
                val retryText = "Reconnecting WebRTC (Attempt $iceRestartRetryCount/3)..."
                Log.d(TAG, "Auto ICE Restart attempt $iceRestartRetryCount/3 triggered")
                com.aazeth.calla.util.NetworkLogger.log("WebRTC", "Auto ICE Restart Retry attempt $iceRestartRetryCount/3 triggered")
                _uiState.update { it.copy(callStatus = CallConnectionStatus.CONNECTING, callStatusText = retryText) }
                CallService.update(getApplication(), status = retryText)

                viewModelScope.launch {
                    val roomId = _uiState.value.currentRoomId
                    if (roomId.isNotBlank()) {
                        isAnswerCreated = false
                        signalingClient.reconnectWebSocket(roomId)
                        if (_uiState.value.isHost) {
                            webRtcClient?.restartIce(_uiState.value.bitrateKbps) { newOffer ->
                                Log.d(TAG, "ICE restart offer sent on retry attempt $iceRestartRetryCount")
                                signalingClient.sendOffer(roomId, newOffer.description)
                            }
                        } else {
                            signalingClient.requestOffer(roomId)
                        }
                    }
                }
                return
            } else {
                Log.w(TAG, "WebRTC connection failed after 3 retry attempts")
                com.aazeth.calla.util.NetworkLogger.log("WebRTC", "WebRTC connection failed after 3 retry attempts")
                val statusText = "Connection Failed"
                _uiState.update {
                    it.copy(
                        callStatus = CallConnectionStatus.FAILED,
                        callStatusText = statusText,
                        errorMessage = "Connection lost after 3 retry attempts. Please check network or TURN settings."
                    )
                }
                CallService.update(getApplication(), status = statusText)
                return
            }
        }

        val statusText = when (status) {
            CallConnectionStatus.IDLE -> "Idle"
            CallConnectionStatus.CONNECTING -> "Connecting WebRTC..."
            CallConnectionStatus.CONNECTED -> "Connected"
            CallConnectionStatus.DISCONNECTED -> "Disconnected"
            CallConnectionStatus.FAILED -> "Connection Failed"
        }

        _uiState.update {
            it.copy(
                callStatus = status,
                callStatusText = statusText
            )
        }

        if (_uiState.value.screen == ScreenState.IN_CALL && !isEndingCall && status != CallConnectionStatus.IDLE) {
            CallService.update(getApplication(), status = statusText)
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                _uiState.update { it.copy(callDurationSeconds = it.callDurationSeconds + 1L) }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private var audioLevelPhase = 0f

    private fun startPingPolling() {
        pingJob?.cancel()
        pingJob = viewModelScope.launch {
            while (isActive) {
                webRtcClient?.queryRttStats { rtt ->
                    _uiState.update { it.copy(pingMs = rtt) }
                }
                // Calculate smooth audio level pulsing for visualizer when in active call
                if (_uiState.value.callStatus == CallConnectionStatus.CONNECTED && !_uiState.value.isMicMuted) {
                    audioLevelPhase = (audioLevelPhase + 0.3f) % (2f * Math.PI.toFloat())
                    val normalizedLevel = (kotlin.math.sin(audioLevelPhase) * 0.4f + 0.5f).coerceIn(0.1f, 1.0f)
                    _uiState.update { it.copy(audioLevel = normalizedLevel) }
                } else {
                    _uiState.update { it.copy(audioLevel = 0f) }
                }
                delay(300L)
            }
        }
    }

    private fun stopPingPolling() {
        pingJob?.cancel()
        pingJob = null
        _uiState.update { it.copy(pingMs = null, audioLevel = 0f) }
    }

    fun toggleMute() {
        val muted = webRtcClient?.toggleMute() ?: false
        _uiState.update { it.copy(isMicMuted = muted) }
        val roomId = _uiState.value.currentRoomId
        if (roomId.isNotBlank()) {
            signalingClient.sendMuteState(roomId, muted)
        }
    }

    fun cycleAudioRoute() {
        val nextRoute = webRtcClient?.cycleAudioRoute() ?: AudioRoute.SPEAKER
        _uiState.update { it.copy(audioRoute = nextRoute) }
    }

    fun endCall(remoteInitiated: Boolean = false) {
        if (isEndingCall) return
        isEndingCall = true
        iceRestartRetryCount = 0
        stopTimer()
        stopPingPolling()

        val roomId = _uiState.value.currentRoomId
        val context = getApplication<Application>()
        CallService.stop(context)

        viewModelScope.launch {
            if (!remoteInitiated && roomId.isNotBlank()) {
                signalingClient.cleanupRoom(roomId)
            } else {
                signalingClient.stopObservation()
            }

            webRtcClient?.close()
            webRtcClient = null

            _uiState.update {
                it.copy(
                    screen = ScreenState.LOBBY,
                    isMinimized = false,
                    currentRoomId = "",
                    isHost = false,
                    peerName = "",
                    callStatus = CallConnectionStatus.IDLE,
                    callStatusText = "",
                    isMicMuted = false,
                    isPeerMuted = false,
                    audioLevel = 0f,
                    showAudioControlSheet = false,
                    audioRoute = AudioRoute.SPEAKER,
                    callDurationSeconds = 0L,
                    joinCodeInput = "",
                    errorMessage = if (remoteInitiated) "Call was ended by partner" else null
                )
            }
            isEndingCall = false
            isAnswerCreated = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(notificationActionReceiver)
        } catch (_: Exception) {}
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}
        endCall()
    }
}
