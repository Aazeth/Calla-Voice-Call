package com.aazeth.calla.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aazeth.calla.MainActivity
import com.aazeth.calla.R

class CallService : Service() {

    companion object {
        const val CHANNEL_ID = "calla_voice_call_channel"
        const val NOTIFICATION_ID = 101

        const val ACTION_START_CALL = "com.aazeth.calla.START_CALL"
        const val ACTION_UPDATE_CALL = "com.aazeth.calla.UPDATE_CALL"
        const val ACTION_STOP_CALL = "com.aazeth.calla.STOP_CALL"
        const val ACTION_STOP_FROM_NOTIFICATION = "com.aazeth.calla.STOP_FROM_NOTIFICATION"
        const val ACTION_CALL_ENDED_FROM_NOTIFICATION = "com.aazeth.calla.CALL_ENDED_FROM_NOTIFICATION"

        const val EXTRA_ROOM_ID = "extra_room_id"
        const val EXTRA_PEER_NAME = "extra_peer_name"
        const val EXTRA_BITRATE_KBPS = "extra_bitrate_kbps"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_EXPAND_CALL = "extra_expand_call"

        fun start(
            context: Context,
            roomId: String,
            peerName: String,
            bitrateKbps: Int,
            status: String
        ) {
            try {
                val intent = Intent(context, CallService::class.java).apply {
                    action = ACTION_START_CALL
                    putExtra(EXTRA_ROOM_ID, roomId)
                    putExtra(EXTRA_PEER_NAME, peerName)
                    putExtra(EXTRA_BITRATE_KBPS, bitrateKbps)
                    putExtra(EXTRA_STATUS, status)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("CallService", "Failed to start CallService: ${e.message}")
            }
        }

        fun update(context: Context, status: String, peerName: String? = null) {
            try {
                val intent = Intent(context, CallService::class.java).apply {
                    action = ACTION_UPDATE_CALL
                    putExtra(EXTRA_STATUS, status)
                    peerName?.let { putExtra(EXTRA_PEER_NAME, it) }
                }
                context.startService(intent)
            } catch (e: Exception) {
                android.util.Log.w("CallService", "Failed to update CallService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, CallService::class.java).apply {
                    action = ACTION_STOP_CALL
                }
                context.startService(intent)
            } catch (e: Exception) {
                android.util.Log.w("CallService", "Failed to stop CallService: ${e.message}")
            }
        }
    }

    private var currentRoomId: String = ""
    private var currentPeerName: String = "Partner"
    private var currentBitrateKbps: Int = 128
    private var currentStatus: String = "Connecting"
    private var callStartTimeMillis: Long = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                ACTION_START_CALL -> {
                    currentRoomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: ""
                    currentPeerName = intent.getStringExtra(EXTRA_PEER_NAME) ?: "Partner"
                    currentBitrateKbps = intent.getIntExtra(EXTRA_BITRATE_KBPS, 128)
                    currentStatus = intent.getStringExtra(EXTRA_STATUS) ?: "Connecting"
                    callStartTimeMillis = System.currentTimeMillis()
                    startForegroundWithNotification()
                }
                ACTION_UPDATE_CALL -> {
                    val newStatus = intent.getStringExtra(EXTRA_STATUS)
                    if (newStatus == "Idle" || newStatus == "Ended" || newStatus == "Stopped") {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        val notificationManager = getSystemService(NotificationManager::class.java)
                        notificationManager?.cancel(NOTIFICATION_ID)
                        stopSelf()
                    } else {
                        newStatus?.let { currentStatus = it }
                        intent.getStringExtra(EXTRA_PEER_NAME)?.let { currentPeerName = it }
                        val notification = buildNotification()
                        val notificationManager = getSystemService(NotificationManager::class.java)
                        notificationManager?.notify(NOTIFICATION_ID, notification)
                    }
                }
                ACTION_STOP_FROM_NOTIFICATION -> {
                    sendBroadcast(Intent(ACTION_CALL_ENDED_FROM_NOTIFICATION).apply {
                        setPackage(packageName)
                    })
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    notificationManager?.cancel(NOTIFICATION_ID)
                    stopSelf()
                }
                ACTION_STOP_CALL -> {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    notificationManager?.cancel(NOTIFICATION_ID)
                    stopSelf()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CallService", "Error in onStartCommand: ${e.message}", e)
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e("CallService", "Failed to start foreground notification: ${e.message}", e)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_EXPAND_CALL, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val endCallIntent = Intent(this, CallService::class.java).apply {
            action = ACTION_STOP_FROM_NOTIFICATION
        }
        val endCallPendingIntent = PendingIntent.getService(
            this,
            1,
            endCallIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = if (currentPeerName.isNotBlank() && currentPeerName != "Partner") {
            "Room #$currentRoomId • $currentPeerName • ${currentBitrateKbps}kbps Opus • $currentStatus"
        } else {
            "Room #$currentRoomId • ${currentBitrateKbps}kbps Opus • $currentStatus"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Calla Voice Call")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setUsesChronometer(true)
            .setWhen(callStartTimeMillis)
            .setShowWhen(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "End Call",
                endCallPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Calla Ongoing Call",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live call status and duration for ongoing Calla WebRTC calls"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
