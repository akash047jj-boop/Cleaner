package com.example.notificationcleaner

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.text.SimpleDateFormat
import java.util.*

class NotificationCleanerService :
    NotificationListenerService() {

    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {

        val preferences =
            getSharedPreferences(
                "cleaner_settings",
                MODE_PRIVATE
            )

        val running =
            preferences.getBoolean(
                "running",
                true
            )

        if (!running) {
            return
        }

        val packageName = sbn.packageName

        if (packageName == this.packageName) {
            return
        }

        val selected =
            preferences.getBoolean(
                "selected_$packageName",
                false
            )

        if (!selected) {
            return
        }

        if (shouldClean(
                preferences,
                packageName
            )
        ) {

            try {
                cancelNotification(sbn.key)
            } catch (_: Exception) {
            }
        }
    }

    private fun shouldClean(
        preferences:
            android.content.SharedPreferences,
        packageName: String
    ): Boolean {

        val mode =
            preferences.getString(
                "mode_$packageName",
                "always"
            )

        when (mode) {

            "always" -> {
                return true
            }

            "duration" -> {

                val end =
                    preferences.getLong(
                        "duration_end_$packageName",
                        0
                    )

                if (System.currentTimeMillis() < end) {
                    return true
                }

                preferences.edit()
                    .putString(
                        "mode_$packageName",
                        "always"
                    )
                    .apply()

                return false
            }

            "clock" -> {

                val start =
                    preferences.getString(
                        "start_$packageName",
                        "22:00"
                    ) ?: "22:00"

                val end =
                    preferences.getString(
                        "end_$packageName",
                        "07:00"
                    ) ?: "07:00"

                return isInsideTimeRange(
                    start,
                    end
                )
            }
        }

        return false
    }

    private fun isInsideTimeRange(
        start: String,
        end: String
    ): Boolean {

        val formatter =
            SimpleDateFormat(
                "HH:mm",
                Locale.getDefault()
            )

        val now =
            formatter.format(Date())

        return if (start <= end) {
            now >= start && now < end
        } else {
            now >= start || now < end
        }
    }

    override fun onListenerConnected() {

        super.onListenerConnected()

        val preferences =
            getSharedPreferences(
                "cleaner_settings",
                MODE_PRIVATE
            )

        val running =
            preferences.getBoolean(
                "running",
                true
            )

        NotificationHelper.showStatus(
            this,
            running
        )
    }
    }
