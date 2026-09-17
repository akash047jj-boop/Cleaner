package com.example.notificationcleaner

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    private val tiffanyBlue = Color.rgb(10, 186, 181)
    private val black = Color.BLACK
    private val white = Color.WHITE

    private lateinit var preferences: android.content.SharedPreferences
    private lateinit var appContainer: LinearLayout

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
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }
    }

    private fun createScreen() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(black)
            setPadding(30, 40, 30, 20)
        }

        val title = TextView(this).apply {
            text = "Notification Cleaner"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(tiffanyBlue)
        }

        root.addView(title)

        val startStop = Button(this).apply {
            text = if (isRunning()) "STOP" else "START"
            setTextColor(black)
            setBackgroundColor(tiffanyBlue)

            setOnClickListener {
                val newState = !isRunning()

                preferences.edit()
                    .putBoolean("running", newState)
                    .apply()

                text = if (newState) "STOP" else "START"

                NotificationHelper.showStatus(
                    this@MainActivity,
                    newState
                )
            }
        }

        root.addView(
            startStop,
            LinearLayout.LayoutParams(
                -1,
                -2
            )
        )

        val accessButton = Button(this).apply {
            text = "Notification Access"
            setTextColor(black)
            setBackgroundColor(tiffanyBlue)

            setOnClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                    )
                )
            }
        }

        root.addView(accessButton)

        val appsTitle = TextView(this).apply {
            text = "Select apps to clean"
            textSize = 20f
            setTextColor(white)
            setPadding(0, 25, 0, 15)
        }

        root.addView(appsTitle)

        appContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this)
        scroll.addView(appContainer)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
        )

        val footer = TextView(this).apply {
            text = "App by Potato's man"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
            setPadding(0, 15, 0, 0)
        }

        root.addView(footer)

        setContentView(root)

        loadApps()
    }

   private fun loadApps() {

    appContainer.removeAllViews()

    val pm = packageManager

    val launcherIntent = Intent(
        Intent.ACTION_MAIN,
        null
    ).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    val apps = pm.queryIntentActivities(
        launcherIntent,
        PackageManager.MATCH_ALL
    )
        .map {
            it.activityInfo.applicationInfo
        }
        .filter {
            it.packageName != packageName
        }
        .distinctBy {
            it.packageName
        }
        .sortedBy {
            pm.getApplicationLabel(it).toString()
        }

        for (app in apps) {

            val packageName = app.packageName
            val appName =
                pm.getApplicationLabel(app).toString()

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 5, 0, 5)
            }

            val checkBox = CheckBox(this).apply {

                text = appName
                textSize = 17f
                setTextColor(white)

                isChecked =
                    preferences.getBoolean(
                        "selected_$packageName",
                        false
                    )

                setOnCheckedChangeListener { _, checked ->

                    preferences.edit()
                        .putBoolean(
                            "selected_$packageName",
                            checked
                        )
                        .apply()

                    if (checked) {
                        showScheduleDialog(
                            appName,
                            packageName
                        )
                    }
                }
            }

            row.addView(
                checkBox,
                LinearLayout.LayoutParams(
                    0,
                    -2,
                    1f
                )
            )

            val settingsButton = Button(this).apply {
                text = "⚙"
                setTextColor(black)
                setBackgroundColor(tiffanyBlue)

                setOnClickListener {
                    showScheduleDialog(
                        appName,
                        packageName
                    )
                }
            }

            row.addView(settingsButton)

            appContainer.addView(row)
        }
    }

    private fun showScheduleDialog(
        appName: String,
        packageName: String
    ) {

        val options = arrayOf(
            "Always",
            "Clock schedule",
            "Duration"
        )

        AlertDialog.Builder(this)
            .setTitle("$appName cleaning")
            .setItems(options) { _, which ->

                when (which) {

                    0 -> {
                        saveMode(
                            packageName,
                            "always"
                        )
                        Toast.makeText(
                            this,
                            "Always cleaning $appName",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    1 -> showClockSchedule(
                        appName,
                        packageName
                    )

                    2 -> showDuration(
                        appName,
                        packageName
                    )
                }
            }
            .show()
    }

    private fun showClockSchedule(
        appName: String,
        packageName: String
    ) {

        TimePickerDialog(
            this,
            { _, hour, minute ->

                val start =
                    String.format(
                        "%02d:%02d",
                        hour,
                        minute
                    )

                TimePickerDialog(
                    this,
                    { _, endHour, endMinute ->

                        val end =
                            String.format(
                                "%02d:%02d",
                                endHour,
                                endMinute
                            )

                        preferences.edit()
                            .putString(
                                "mode_$packageName",
                                "clock"
                            )
                            .putString(
                                "start_$packageName",
                                start
                            )
                            .putString(
                                "end_$packageName",
                                end
                            )
                            .apply()

                        Toast.makeText(
                            this,
                            "$appName: $start → $end",
                            Toast.LENGTH_LONG
                        ).show()

                    },
                    7,
                    0,
                    true
                ).show()

            },
            22,
            0,
            true
        ).show()
    }

    private fun showDuration(
        appName: String,
        packageName: String
    ) {

        val input = EditText(this)

        input.hint = "Example: 120"
        input.inputType =
            android.text.InputType.TYPE_CLASS_NUMBER

        AlertDialog.Builder(this)
            .setTitle("Duration for $appName")
            .setMessage(
                "Enter duration in minutes"
            )
            .setView(input)
            .setPositiveButton("START") { _, _ ->

                val minutes =
                    input.text.toString().toLongOrNull()

                if (minutes != null && minutes > 0) {

                    val end =
                        System.currentTimeMillis() +
                                minutes * 60_000L

                    preferences.edit()
                        .putString(
                            "mode_$packageName",
                            "duration"
                        )
                        .putLong(
                            "duration_end_$packageName",
                            end
                        )
                        .apply()

                    Toast.makeText(
                        this,
                        "$appName cleaning for $minutes minutes",
                        Toast.LENGTH_LONG
                    ).show()

                } else {

                    Toast.makeText(
                        this,
                        "Enter a valid duration",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(
                "CANCEL",
                null
            )
            .show()
    }

    private fun saveMode(
        packageName: String,
        mode: String
    ) {

        preferences.edit()
            .putString(
                "mode_$packageName",
                mode
            )
            .apply()
    }

    private fun isRunning(): Boolean {

        return preferences.getBoolean(
            "running",
            true
        )
    }
}
