package com.nakib.notifilter.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nakib.notifilter.R
import com.nakib.notifilter.data.NotificationDbHelper

/**
 * Analytics — reachable only from the header 3-dot menu.
 * Every number is computed from the same stored rows shown in Inbox/Blocked,
 * so the totals always match. Layout matches the approved preview: stats
 * strip, spam-rate card with real-vs-spam split bar, How-it-filters, By app.
 */
class AnalyticsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }

        val db = NotificationDbHelper(this)
        val all = db.all()
        val total = all.size
        val real = all.count { it.status == "SHOWN" }
        val spam = all.count { it.status == "BLOCKED" }
        val otp = all.count { it.isOtp }
        val images = all.count { it.hasImage }
        val spamPct = if (total == 0) 0 else spam * 100 / total
        val realPct = 100 - spamPct

        findViewById<TextView>(R.id.total_value).text = total.toString()
        findViewById<TextView>(R.id.real_value).text = real.toString()
        findViewById<TextView>(R.id.spam_value).text = spam.toString()
        findViewById<TextView>(R.id.spam_rate_title).text = getString(R.string.spam_rate_title, spamPct)
        findViewById<TextView>(R.id.spam_rate_hint).text = getString(R.string.spam_rate_hint, spamPct)
        findViewById<TextView>(R.id.legend_real_pct).text = "$realPct%"
        findViewById<TextView>(R.id.legend_spam_pct).text = "$spamPct%"
        findViewById<TextView>(R.id.otp_value).text = otp.toString()
        findViewById<TextView>(R.id.image_value).text = images.toString()

        // Overall split bar: real (green) vs spam (red).
        val realSeg = findViewById<View>(R.id.overall_real_seg)
        val spamSeg = findViewById<View>(R.id.overall_spam_seg)
        realSeg.layoutParams = (realSeg.layoutParams as LinearLayout.LayoutParams).apply {
            weight = realPct.toFloat().coerceAtLeast(0f)
        }
        spamSeg.layoutParams = (spamSeg.layoutParams as LinearLayout.LayoutParams).apply {
            weight = spamPct.toFloat().coerceAtLeast(0f)
        }
        realSeg.visibility = if (realPct > 0) View.VISIBLE else View.GONE
        spamSeg.visibility = if (spamPct > 0) View.VISIBLE else View.GONE

        val recycler = findViewById<RecyclerView>(R.id.analytics_list)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = AnalyticsAdapter(db.appStats())
    }
}
