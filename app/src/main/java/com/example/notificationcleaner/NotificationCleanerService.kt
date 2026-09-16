package com.example.notificationcleaner

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationCleanerService : NotificationListenerService() {

    private val allowedPackages = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b"
    )

    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {
        if (sbn.packageName !in allowedPackages) {
            try {
                cancelNotification(sbn.key)
            } catch (_: Exception) {
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()

        try {
            activeNotifications
                ?.filter {
                    it.packageName !in allowedPackages
                }
                ?.forEach {
                    try {
                        cancelNotification(it.key)
                    } catch (_: Exception) {
                    }
                }
        } catch (_: Exception) {
        }
    }
}
