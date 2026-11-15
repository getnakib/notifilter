package com.nakib.notifilter.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nakib.notifilter.R
import com.nakib.notifilter.data.NotificationDbHelper

/**
 * Analytics — reachable only from the header 3-dot menu.
 * Every number is computed from the same stored rows shown in Inbox/Blocked,
 * so the totals always match.
 */
class AnalyticsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.analytics)

        val db = NotificationDbHelper(this)
        val all = db.all()
        val total = all.size
        val real = all.count { it.status == "SHOWN" }
        val spam = all.count { it.status == "BLOCKED" }
        val otp = all.count { it.isOtp }
        val images = all.count { it.hasImage }
        val spamPct = if (total == 0) 0 else spam * 100 / total

        findViewById<TextView>(R.id.total_value).text = total.toString()
        findViewById<TextView>(R.id.real_value).text = real.toString()
        findViewById<TextView>(R.id.spam_value).text = spam.toString()
        findViewById<TextView>(R.id.spam_pct_value).text = "$spamPct%"
        findViewById<TextView>(R.id.otp_value).text = otp.toString()
        findViewById<TextView>(R.id.image_value).text = images.toString()

        val recycler = findViewById<RecyclerView>(R.id.analytics_list)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = AnalyticsAdapter(db.appStats())
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
