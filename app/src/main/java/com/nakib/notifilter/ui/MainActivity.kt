package com.nakib.notifilter.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.nakib.notifilter.R
import com.nakib.notifilter.data.NotificationDbHelper
import com.nakib.notifilter.data.Prefs

/**
 * Main screen. The header matches the approved preview: shield logo,
 * "Filter", a Filtering on/paused status line, and a 3-dot menu that holds
 * ONLY Pause filtering, Mark all as read, Analytics. The bottom bar holds
 * Inbox / Blocked / Rules / AI — nothing is duplicated between the two.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var db: NotificationDbHelper
    private lateinit var nav: BottomNavigationView
    private lateinit var statusDot: View
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        db = NotificationDbHelper(this)
        setContentView(R.layout.activity_main)

        if (!prefs.onboarded) {
            startActivity(Intent(this, PermissionActivity::class.java))
        }

        statusDot = findViewById(R.id.status_dot)
        statusText = findViewById(R.id.status_text)
        updateStatus()

        findViewById<ImageButton>(R.id.overflow_button).setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menu.add(
                Menu.NONE, 1, Menu.NONE,
                if (prefs.paused) getString(R.string.resume_filtering) else getString(R.string.pause_filtering)
            )
            popup.menu.add(Menu.NONE, 2, Menu.NONE, getString(R.string.mark_all_read))
            popup.menu.add(Menu.NONE, 3, Menu.NONE, getString(R.string.analytics))
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        prefs.paused = !prefs.paused
                        Toast.makeText(
                            this,
                            if (prefs.paused) "Filtering paused" else "Filtering on",
                            Toast.LENGTH_SHORT
                        ).show()
                        updateStatus()
                        refreshCurrent()
                        true
                    }
                    2 -> {
                        db.markAllRead()
                        Toast.makeText(this, "All marked as read", Toast.LENGTH_SHORT).show()
                        refreshCurrent()
                        true
                    }
                    3 -> {
                        startActivity(Intent(this, AnalyticsActivity::class.java))
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        nav = findViewById(R.id.bottom_nav)
        nav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_blocked -> BlockedFragment()
                R.id.nav_rules -> RulesFragment()
                R.id.nav_ai -> AiFragment()
                else -> InboxFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
            true
        }
        if (savedInstanceState == null) {
            nav.selectedItemId = R.id.nav_inbox
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        updateBadges()
    }

    /** Header status line: green dot + "Filtering on", red dot + "Filtering paused". */
    fun updateStatus() {
        if (!::statusDot.isInitialized) return
        val paused = prefs.paused
        statusText.text = if (paused) getString(R.string.filtering_paused) else getString(R.string.filtering_on)
        statusDot.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (paused) R.color.md_error else R.color.md_ok)
        )
    }

    /** Badges on Inbox (shown) and Blocked (spam) counts. */
    fun updateBadges() {
        if (!::nav.isInitialized) return
        val shown = db.listByStatus("SHOWN").size
        val blocked = db.listByStatus("BLOCKED").size
        val inboxBadge = nav.getOrCreateBadge(R.id.nav_inbox)
        inboxBadge.number = shown
        inboxBadge.isVisible = shown > 0
        val blockedBadge = nav.getOrCreateBadge(R.id.nav_blocked)
        blockedBadge.number = blocked
        blockedBadge.isVisible = blocked > 0
    }

    private fun refreshCurrent() {
        supportFragmentManager.findFragmentById(R.id.fragment_container)
            ?.let { (it as? Refreshable)?.refresh() }
        updateBadges()
    }
}

interface Refreshable {
    fun refresh()
}
