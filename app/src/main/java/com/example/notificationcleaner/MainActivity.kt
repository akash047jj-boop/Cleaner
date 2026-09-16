package com.example.notificationcleaner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat

class MainActivity : Activity() {

    private lateinit var preferences: SharedPreferences
    private lateinit var statusText: TextView
    private lateinit var startStopButton: Button

    private val tiffanyBlue = Color.rgb(10, 186, 181)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = getSharedPreferences(
            "cleaner_settings",
            MODE_PRIVATE
        )

        if (!preferences.contains("running")) {
            preferences.edit()
                .putBoolean("running", true)
                .apply()
        }

        createScreen()

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }

        updateScreen()
    }

    private fun createScreen() {

        val background = Color.BLACK

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(40, 60, 40, 30)
            setBackgroundColor(background)
        }

        val title = TextView(this).apply {
            text = "Notification Cleaner"
            textSize = 28f
            setTextColor(tiffanyBlue)
            gravity = Gravity.CENTER
        }

        val description = TextView(this).apply {
            text = "Automatically clears notifications from the notification bar except WhatsApp."
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 30)
        }

        statusText = TextView(this).apply {
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        startStopButton = Button(this).apply {
            textSize = 18f
            setTextColor(Color.BLACK)
            setBackgroundColor(tiffanyBlue)

            setOnClickListener {

                val currentlyRunning =
                    preferences.getBoolean("running", true)

                preferences.edit()
                    .putBoolean("running", !currentlyRunning)
                    .apply()

                updateScreen()
            }
        }

        val accessButton = Button(this).apply {
            text = "Notification Access"
            textSize = 16f
            setTextColor(Color.BLACK)
            setBackgroundColor(tiffanyBlue)

            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                )
            }
        }

        val spacer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                1,
                0,
                1f
            )
        }

        val footer = TextView(this).apply {
            text = "App by Potato's man"
            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        }

        layout.addView(
            title,
            LinearLayout.LayoutParams(
                -1,
                -2
            )
        )

        layout.addView(
            description,
            LinearLayout.LayoutParams(
                -1,
                -2
            )
        )

        layout.addView(
            statusText,
            LinearLayout.LayoutParams(
                -1,
                -2
            )
        )

        layout.addView(
            startStopButton,
            LinearLayout.LayoutParams(
                -1,
                -2
            )
        )

        layout.addView(
            accessButton,
            LinearLayout.LayoutParams(
                -1,
                -2
            )
        )

        layout.addView(spacer)

        layout.addView(
            footer,
            LinearLayout.LayoutParams(
                -1,
                -2
            )
        )

        setContentView(layout)
    }

    private fun updateScreen() {

        val running =
            preferences.getBoolean("running", true)

        if (running) {

            statusText.text = "● RUNNING"
            statusText.setTextColor(tiffanyBlue)

            startStopButton.text = "STOP"

        } else {

            statusText.text = "● STOPPED"
            statusText.setTextColor(Color.GRAY)

            startStopButton.text = "START"
        }

        NotificationHelper.showStatus(
            this,
            running
        )
    }
}
