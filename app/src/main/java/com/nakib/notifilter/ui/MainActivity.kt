package com.nakib.notifilter.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.nakib.notifilter.R
import com.nakib.notifilter.data.NotificationDbHelper
import com.nakib.notifilter.data.Prefs

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var db: NotificationDbHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        db = NotificationDbHelper(this)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        if (!prefs.onboarded) {
            startActivity(Intent(this, PermissionActivity::class.java))
        }

        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.findItem(R.id.action_pause)?.title =
            if (prefs.paused) "Resume filtering" else "Pause filtering"
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_pause -> {
                prefs.paused = !prefs.paused
                Toast.makeText(
                    this,
                    if (prefs.paused) "Filtering paused" else "Filtering on",
                    Toast.LENGTH_SHORT
                ).show()
                invalidateOptionsMenu()
                true
            }
            R.id.action_mark_read -> {
                db.markAllRead()
                Toast.makeText(this, "All marked as read", Toast.LENGTH_SHORT).show()
                // Refresh the visible list.
                supportFragmentManager.findFragmentById(R.id.fragment_container)
                    ?.let { (it as? Refreshable)?.refresh() }
                true
            }
            R.id.action_analytics -> {
                startActivity(Intent(this, AnalyticsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

interface Refreshable {
    fun refresh()
}
