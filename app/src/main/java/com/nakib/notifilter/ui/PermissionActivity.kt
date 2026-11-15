package com.nakib.notifilter.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nakib.notifilter.R
import com.nakib.notifilter.data.Prefs

/**
 * First-run permissions. Nothing is filtered until notification access is on.
 * The app never crashes without these permissions — it shows a disabled state.
 */
class PermissionActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var listenerStatus: TextView
    private lateinit var notifyStatus: TextView
    private lateinit var continueButton: Button

    private val notifyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Toast.makeText(
            this,
            if (granted) "Notifications allowed" else "Notifications not allowed",
            Toast.LENGTH_SHORT
        ).show()
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_permission)

        listenerStatus = findViewById(R.id.listener_status)
        notifyStatus = findViewById(R.id.notify_status)
        continueButton = findViewById(R.id.continue_button)

        findViewById<Button>(R.id.listener_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.notify_button).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifyPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Toast.makeText(this, "Not needed on this Android version", Toast.LENGTH_SHORT).show()
            }
        }
        continueButton.setOnClickListener {
            if (isListenerEnabled()) {
                prefs.onboarded = true
                finish()
            } else {
                Toast.makeText(
                    this,
                    "Turn on notification access first",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun isListenerEnabled(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(this)
            .contains(packageName)
    }

    private fun refreshStatus() {
        val listenerOn = isListenerEnabled()
        listenerStatus.text = if (listenerOn) "On" else "Off — required"
        val notifyOn = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        notifyStatus.text = if (notifyOn) "On" else "Off — optional"
        continueButton.isEnabled = listenerOn
    }
}
