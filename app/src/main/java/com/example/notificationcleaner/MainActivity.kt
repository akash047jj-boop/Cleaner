package com.example.notificationcleaner

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(48, 64, 48, 48)

        val title = TextView(this)
        title.text = "Notification Cleaner"
        title.textSize = 26f

        val info = TextView(this)
        info.text = """
            
Automatically clears notifications from the notification bar.

WhatsApp notifications are kept.
WhatsApp Business notifications are kept.
        """.trimIndent()

        info.textSize = 17f

        val button = Button(this)
        button.text = "Enable Notification Access"

        button.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            )
        }

        layout.addView(title)
        layout.addView(info)
        layout.addView(button)

        setContentView(layout)
    }
}
