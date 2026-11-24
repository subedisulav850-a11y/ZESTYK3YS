package com.roha.zestyaes

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ExtractorService : Service() {

    companion object {
        const val ACTION_START = "com.roha.zestyaes.START"
        const val CHANNEL_ID = "zesty_channel"
        const val NOTIF_ID = 7701
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            startForeground(NOTIF_ID, buildNotif("Starting..."))
            job?.cancel()
            job = scope.launch {
                update("Root + hook active")
                HookEngine.run(
                    onKey = { type, hex, str ->
                        KeyStore().append(type, hex, str)
                        update("Captured $type")
                    },
                    onStatus = { msg -> update(msg) }
                )
            }
        }
        return START_STICKY
    }

    private fun buildNotif(status: String): Notification {
        createChannel()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Zesty AES — ${HookEngine.targetName}")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_key)
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun update(status: String) {
        try {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIF_ID, buildNotif(status))
        } catch (_: Exception) { }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Zesty Extractor", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
