package com.example.notificationcleaner

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object NotificationHelper {

    private const val CHANNEL_ID = "cleaner_status"
    private const val NOTIFICATION_ID = 1001

    fun showStatus(context: Context, running: Boolean) {

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Cleaner Status",
            NotificationManager.IMPORTANCE_LOW
        )

        manager.createNotificationChannel(channel)

        val intent = Intent(context, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (running) {
            "Notification Cleaner — RUNNING"
        } else {
            "Notification Cleaner — STOPPED"
        }

        val text = if (running) {
            "Automatically clearing notifications except WhatsApp"
        } else {
            "Notification cleaning is currently stopped"
        }

        val notification = NotificationCompat.Builder(
            context,
            CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}
