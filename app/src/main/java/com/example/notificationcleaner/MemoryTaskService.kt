package com.example.notificationcleaner

import android.app.*
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MemoryTaskService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val cancelled = AtomicBoolean(false)

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                cancelled.set(true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SCAN -> scan()
            ACTION_DELETE -> {
                val paths = intent.getStringArrayListExtra(EXTRA_PATHS) ?: arrayListOf()
                startForeground(NID, notification("Preparing deletion", 0, paths.size, true))
                executor.execute {
                    val result = StorageRepository.delete(paths) { done, total, name ->
                        update("Deleting: $name", done, total)
                    }
                    update("Deleted ${result.first} files • ${formatSize(result.second)} freed", result.first, paths.size, false)
                    sendBroadcast(Intent(ACTION_FINISHED).setPackage(packageName))
                    stopSelf()
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun scan() {
        cancelled.set(false)
        startForeground(NOTIFICATION_ID, buildNotification("Scanning storage", 0, 0, true))
        executor.execute {
            try {
                StorageRepository.scan { total, matched, _ ->
                    if (!cancelled.get()) update("Scanned $total files • Found $matched matches", total, 0)
                }
                update("Scan complete • ${StorageRepository.files.size} supported files", 1, 1, false)
            } catch (e: Exception) {
                update("Scan stopped: ${e.message ?: "Storage access error"}", 0, 1, false)
            }
            sendBroadcast(Intent(ACTION_FINISHED).setPackage(packageName))
            stopSelf()
        }
    }

    private fun update(text: String, progress: Int, max: Int, ongoing: Boolean = true) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(text, progress, max, ongoing))
        sendBroadcast(Intent(ACTION_PROGRESS).setPackage(packageName).putExtra("text", text))
    }

    private fun buildNotification(text: String, progress: Int, max: Int, ongoing: Boolean): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Storage tasks", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("Memory Cleaner")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (max > 0) builder.setProgress(max, progress.coerceAtMost(max), false)
        if (ongoing) {
            val stop = PendingIntent.getService(this, 1,
                Intent(this, MemoryTaskService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(android.R.drawable.ic_media_pause, "Stop", stop)
        }
        return builder.build()
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "memory_tasks"
        const val NOTIFICATION_ID = 2201
        const val ACTION_SCAN = "memory.scan"
        const val ACTION_DELETE = "memory.delete"
        const val ACTION_STOP = "memory.stop"
        const val ACTION_PROGRESS = "memory.progress"
        const val ACTION_FINISHED = "memory.finished"
        const val EXTRA_PATHS = "paths"
    }
}
