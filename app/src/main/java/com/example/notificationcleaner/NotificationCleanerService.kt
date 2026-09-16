package com.example.notificationcleaner

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationCleanerService : NotificationListenerService() {

    private val allowedPackages = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.example.notificationcleaner"
    )

    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {

        val preferences = getSharedPreferences(
            "cleaner_settings",
            MODE_PRIVATE
        )

        val running =
            preferences.getBoolean("running", true)

        if (!running) {
            return
        }

        if (sbn.packageName !in allowedPackages) {

            try {
                cancelNotification(sbn.key)
            } catch (_: Exception) {
            }
        }
    }

    override fun onListenerConnected() {

        super.onListenerConnected()

        val preferences = getSharedPreferences(
            "cleaner_settings",
            MODE_PRIVATE
        )

        val running =
            preferences.getBoolean("running", true)

        if (!running) {
            return
        }

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

        NotificationHelper.showStatus(
            this,
            running
        )
    }
}
