package com.aazeth.calla.signaling

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.IceCandidate
import java.util.concurrent.TimeUnit

data class RoomState(
    val roomId: String = "",
    val hostName: String = "",
    val joinerName: String? = null,
    val bitrateKbps: Long = 128L,
    val status: String = "waiting", // waiting, connected, ended
    val offerSdp: String? = null,
    val answerSdp: String? = null
)

class SignalingClient(context: Context) {

    companion object {
        private const val TAG = "SignalingClient"
        private const val NTFY_BASE_URL = "https://ntfy.sh"
        private const val NTFY_WS_URL = "wss://ntfy.sh"
        private const val TOPIC_PREFIX = "calla_v1_room_"
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "SignalingClient coroutine caught error: ${throwable.message}")
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // For persistent WebSocket
        .build()

    private var activeWebSocket: WebSocket? = null
    private var currentRoomId: String? = null
    private var isHostRole: Boolean = false

    // Listeners registered by CallViewModel
    private var onRoomStateUpdate: ((RoomState) -> Unit)? = null
    private var onRemoteIceCandidate: ((IceCandidate) -> Unit)? = null

    // Cache current room state
    private var currentRoomState = RoomState()

    // ICE Candidate batching queue to avoid ntfy.sh HTTP 429 rate limit
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private var candidateFlushJob: Job? = null

    fun createRoom(
        roomId: String,
        hostName: String,
        bitrateKbps: Int,
        offerSdp: String
    ): Result<Unit> {
        return try {
            currentRoomId = roomId
            isHostRole = true
            currentRoomState = RoomState(
                roomId = roomId,
                hostName = hostName,
                bitrateKbps = bitrateKbps.toLong(),
                status = "waiting",
                offerSdp = offerSdp
            )
            onRoomStateUpdate?.invoke(currentRoomState)

            // Connect to WebSocket relay
            connectWebSocket(roomId)

            // Publish Offer via relay
            scope.launch {
                delay(300) // Brief delay to ensure WebSocket connection
                val payload = JSONObject().apply {
                    put("type", "offer")
                    put("sender", "host")
                    put("hostName", hostName)
                    put("bitrate", bitrateKbps)
                    put("sdp", offerSdp)
                }
                broadcastSignal(roomId, payload)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating room: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun joinRoom(
        roomId: String,
        joinerName: String
    ): Result<Unit> {
        return try {
            currentRoomId = roomId
            isHostRole = false
            currentRoomState = RoomState(
                roomId = roomId,
                joinerName = joinerName,
                status = "joining"
            )
            onRoomStateUpdate?.invoke(currentRoomState)

            // Connect to WebSocket relay
            connectWebSocket(roomId)

            // Announce join to request Offer from host if already waiting
            scope.launch {
                delay(300)
                val payload = JSONObject().apply {
                    put("type", "join")
                    put("sender", "guest")
                    put("guestName", joinerName)
                }
                broadcastSignal(roomId, payload)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error joining room: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Listener for guest requesting offer during reconnect
    var onRequestOfferRequested: (() -> Unit)? = null

    fun sendOffer(roomId: String, offerSdp: String) {
        scope.launch {
            currentRoomState = currentRoomState.copy(offerSdp = offerSdp)
            val payload = JSONObject().apply {
                put("type", "offer")
                put("sender", if (isHostRole) "host" else "guest")
                put("hostName", currentRoomState.hostName)
                put("bitrate", currentRoomState.bitrateKbps)
                put("sdp", offerSdp)
            }
            broadcastSignal(roomId, payload)
        }
    }

    fun requestOffer(roomId: String) {
        scope.launch {
            val payload = JSONObject().apply {
                put("type", "request_offer")
                put("sender", if (isHostRole) "host" else "guest")
            }
            broadcastSignal(roomId, payload)
        }
    }

    fun sendAnswer(
        roomId: String,
        answerSdp: String,
        joinerName: String
    ) {
        scope.launch {
            val payload = JSONObject().apply {
                put("type", "answer")
                put("sender", "guest")
                put("guestName", joinerName)
                put("sdp", answerSdp)
            }
            broadcastSignal(roomId, payload)
        }
    }

    fun listenToRoom(
        roomId: String,
        onStateChanged: (RoomState) -> Unit
    ) {
        onRoomStateUpdate = onStateChanged
        onStateChanged(currentRoomState)
    }

    fun sendIceCandidate(
        roomId: String,
        isHost: Boolean,
        candidate: IceCandidate
    ) {
        val count: Int
        synchronized(pendingCandidates) {
            pendingCandidates.add(candidate)
            count = pendingCandidates.size
        }

        if (count >= 5) {
            candidateFlushJob?.cancel()
            flushCandidates(roomId, isHost)
        } else if (candidateFlushJob?.isActive != true) {
            candidateFlushJob = scope.launch {
                delay(150)
                flushCandidates(roomId, isHost)
            }
        }
    }

    private fun flushCandidates(roomId: String, isHost: Boolean) {
        val candidatesToSend = synchronized(pendingCandidates) {
            val list = pendingCandidates.toList()
            pendingCandidates.clear()
            list
        }
        if (candidatesToSend.isEmpty()) return

        scope.launch {
            try {
                val candidateArray = JSONArray()
                for (c in candidatesToSend) {
                    val sdp = c.sdp ?: continue
                    candidateArray.put(JSONObject().apply {
                        put("sdpMid", c.sdpMid ?: "")
                        put("sdpMLineIndex", c.sdpMLineIndex)
                        put("sdp", sdp)
                    })
                }
                if (candidateArray.length() == 0) return@launch

                val payload = JSONObject().apply {
                    put("type", "ice_batch")
                    put("sender", if (isHost) "host" else "guest")
                    put("candidates", candidateArray)
                }
                broadcastSignal(roomId, payload)
            } catch (e: Exception) {
                Log.w(TAG, "Error sending ICE candidate batch: ${e.message}")
            }
        }
    }

    fun listenToIceCandidates(
        roomId: String,
        isHost: Boolean,
        onCandidate: (IceCandidate) -> Unit
    ) {
        onRemoteIceCandidate = onCandidate
    }

    fun observeRoom(
        roomId: String,
        onStateChanged: (RoomState?) -> Unit
    ) {
        onRoomStateUpdate = onStateChanged
        onStateChanged(currentRoomState)
    }

    fun observeRemoteCandidates(
        roomId: String,
        isHost: Boolean,
        onCandidate: (IceCandidate) -> Unit
    ) {
        onRemoteIceCandidate = onCandidate
    }

    fun stopObservation() {
        closeConnection()
    }

    fun cleanupRoom(roomId: String) {
        leaveRoom(roomId, isHost = true)
    }

    fun leaveRoom(roomId: String, isHost: Boolean) {
        scope.launch {
            try {
                val payload = JSONObject().apply {
                    put("type", "bye")
                    put("sender", if (isHost) "host" else "guest")
                }
                broadcastSignal(roomId, payload)
            } catch (_: Exception) {
            } finally {
                closeConnection()
            }
        }
    }

    fun reconnectWebSocket(roomId: String) {
        Log.d(TAG, "Reconnecting WebSocket for room: $roomId")
        connectWebSocket(roomId)
    }

    private fun connectWebSocket(roomId: String) {
        closeConnection()
        val topic = "$TOPIC_PREFIX$roomId"
        val wsUrl = "$NTFY_WS_URL/$topic/ws"
        val request = Request.Builder().url(wsUrl).build()

        activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Signaling WebSocket connected to topic: $topic")
                com.aazeth.calla.util.NetworkLogger.log("WebSocket", "Connected to topic: $topic")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Signaling WebSocket failure: ${t.message}")
                com.aazeth.calla.util.NetworkLogger.log("WebSocket", "Failure: ${t.message}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Signaling WebSocket closed: $reason")
                com.aazeth.calla.util.NetworkLogger.log("WebSocket", "Closed: $reason (code: $code)")
            }
        })
    }

    private fun handleIncomingMessage(rawJson: String) {
        try {
            val root = JSONObject(rawJson)
            val event = root.optString("event")
            if (event != "message") return

            val messageStr = root.optString("message")
            if (messageStr.isBlank()) return

            val signal = JSONObject(messageStr)
            val type = signal.optString("type")
            val sender = signal.optString("sender")

            // Ignore messages sent by ourselves
            val isFromMe = (isHostRole && sender == "host") || (!isHostRole && sender == "guest")
            if (isFromMe) return

            com.aazeth.calla.util.NetworkLogger.log("WebSocket", "Incoming [$type] from $sender")

            when (type) {
                "join" -> {
                    // Guest joined, if we are host, re-announce offer
                    if (isHostRole && currentRoomState.offerSdp != null) {
                        val guestName = signal.optString("guestName", "Guest")
                        currentRoomState = currentRoomState.copy(
                            joinerName = guestName,
                            status = "connected"
                        )
                        onRoomStateUpdate?.invoke(currentRoomState)

                        scope.launch {
                            val offerPayload = JSONObject().apply {
                                put("type", "offer")
                                put("sender", "host")
                                put("hostName", currentRoomState.hostName)
                                put("bitrate", currentRoomState.bitrateKbps)
                                put("sdp", currentRoomState.offerSdp)
                            }
                            currentRoomId?.let { broadcastSignal(it, offerPayload) }
                        }
                    }
                }

                "offer" -> {
                    // Host provided offer
                    if (!isHostRole) {
                        val hostName = signal.optString("hostName", "Host")
                        val bitrate = signal.optLong("bitrate", 128L)
                        val sdp = signal.optString("sdp")
                        currentRoomState = currentRoomState.copy(
                            hostName = hostName,
                            bitrateKbps = bitrate,
                            offerSdp = sdp,
                            status = "waiting"
                        )
                        onRoomStateUpdate?.invoke(currentRoomState)
                    }
                }

                "request_offer" -> {
                    if (isHostRole) {
                        Log.d(TAG, "Received request_offer signal from guest")
                        onRequestOfferRequested?.invoke()
                    }
                }

                "answer" -> {
                    // Guest provided answer
                    if (isHostRole) {
                        val guestName = signal.optString("guestName", "Guest")
                        val sdp = signal.optString("sdp")
                        currentRoomState = currentRoomState.copy(
                            joinerName = guestName,
                            answerSdp = sdp,
                            status = "connected"
                        )
                        onRoomStateUpdate?.invoke(currentRoomState)
                    }
                }

                "ice" -> {
                    val sdpMid = signal.optString("sdpMid")
                    val sdpMLineIndex = signal.optInt("sdpMLineIndex", 0)
                    val sdp = signal.optString("sdp")
                    if (sdp.isNotBlank()) {
                        val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                        onRemoteIceCandidate?.invoke(candidate)
                    }
                }

                "ice_batch" -> {
                    val candidatesArr = signal.optJSONArray("candidates")
                    if (candidatesArr != null) {
                        for (i in 0 until candidatesArr.length()) {
                            val candObj = candidatesArr.optJSONObject(i) ?: continue
                            val sdpMid = candObj.optString("sdpMid")
                            val sdpMLineIndex = candObj.optInt("sdpMLineIndex", 0)
                            val sdp = candObj.optString("sdp")
                            if (sdp.isNotBlank()) {
                                val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                                onRemoteIceCandidate?.invoke(candidate)
                            }
                        }
                    }
                }

                "bye" -> {
                    currentRoomState = currentRoomState.copy(status = "ended")
                    onRoomStateUpdate?.invoke(currentRoomState)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming signal: ${e.message}", e)
        }
    }

    private fun broadcastSignal(roomId: String, payload: JSONObject) {
        try {
            val topic = "$TOPIC_PREFIX$roomId"
            val url = "$NTFY_BASE_URL/$topic"
            val requestBody = payload.toString().toRequestBody("text/plain; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Failed to broadcast signal: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error broadcasting signal to room $roomId: ${e.message}")
        }
    }

    private fun closeConnection() {
        try {
            activeWebSocket?.close(1000, "Room ended")
        } catch (_: Exception) {
        }
        activeWebSocket = null
    }
}
