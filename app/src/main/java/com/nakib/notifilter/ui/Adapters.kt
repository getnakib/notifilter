package com.nakib.notifilter.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.nakib.notifilter.R
import com.nakib.notifilter.data.AppStat
import com.nakib.notifilter.data.Record
import java.util.Date

/** Shown notifications. OTP rows have a Copy button. */
class NotificationAdapter(
    private var items: List<Record>,
    private val onChanged: () -> Unit = {}
) : RecyclerView.Adapter<NotificationAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view as MaterialCardView
        val initial: TextView = view.findViewById(R.id.app_initial)
        val appName: TextView = view.findViewById(R.id.app_name)
        val time: TextView = view.findViewById(R.id.time)
        val title: TextView = view.findViewById(R.id.title)
        val body: TextView = view.findViewById(R.id.body)
        val tag: TextView = view.findViewById(R.id.tag)
        val otpRow: View = view.findViewById(R.id.otp_row)
        val otpCode: TextView = view.findViewById(R.id.otp_code)
        val copyButton: MaterialButton = view.findViewById(R.id.copy_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.initial.text = item.appName.firstOrNull()?.uppercase() ?: "?"
        holder.appName.text = item.appName
        holder.time.text = DateFormat.getTimeFormat(holder.itemView.context).format(Date(item.time))
        holder.title.text = item.title
        holder.body.text = item.body
        holder.tag.text = when {
            item.isOtp -> "OTP · Instant"
            item.hasImage -> "Image analysed"
            else -> "Shown"
        }
        if (item.otp != null) {
            holder.otpRow.visibility = View.VISIBLE
            holder.otpCode.text = item.otp.chunked(3).joinToString(" ")
            holder.copyButton.setOnClickListener {
                val cm = holder.itemView.context
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("OTP", item.otp))
                Toast.makeText(holder.itemView.context, "OTP copied", Toast.LENGTH_SHORT).show()
            }
        } else {
            holder.otpRow.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = items.size

    fun update(newItems: List<Record>) {
        items = newItems
        notifyDataSetChanged()
    }
}

/** Blocked notifications, with Restore / Always allow / Delete. */
class BlockedAdapter(
    private var items: List<Record>,
    private val onRestore: (Record) -> Unit,
    private val onAllow: (Record) -> Unit,
    private val onDelete: (Record) -> Unit
) : RecyclerView.Adapter<BlockedAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val initial: TextView = view.findViewById(R.id.app_initial)
        val appName: TextView = view.findViewById(R.id.app_name)
        val time: TextView = view.findViewById(R.id.time)
        val title: TextView = view.findViewById(R.id.title)
        val body: TextView = view.findViewById(R.id.body)
        val reason: TextView = view.findViewById(R.id.reason)
        val restoreButton: MaterialButton = view.findViewById(R.id.restore_button)
        val allowButton: MaterialButton = view.findViewById(R.id.allow_button)
        val deleteButton: MaterialButton = view.findViewById(R.id.delete_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blocked, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.initial.text = item.appName.firstOrNull()?.uppercase() ?: "?"
        holder.appName.text = item.appName
        holder.time.text = DateFormat.getTimeFormat(holder.itemView.context).format(Date(item.time))
        holder.title.text = item.title
        holder.body.text = item.body
        holder.reason.text = item.reason ?: "Blocked"
        holder.restoreButton.setOnClickListener { onRestore(item) }
        holder.allowButton.setOnClickListener { onAllow(item) }
        holder.deleteButton.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount(): Int = items.size

    fun update(newItems: List<Record>) {
        items = newItems
        notifyDataSetChanged()
    }
}

/** One row per app in Analytics. */
class AnalyticsAdapter(
    private val items: List<AppStat>
) : RecyclerView.Adapter<AnalyticsAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val initial: TextView = view.findViewById(R.id.app_initial)
        val appName: TextView = view.findViewById(R.id.app_name)
        val counts: TextView = view.findViewById(R.id.counts)
        val percents: TextView = view.findViewById(R.id.percents)
        val bar: ProgressBar = view.findViewById(R.id.spam_bar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_analytics, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.initial.text = item.appName.firstOrNull()?.uppercase() ?: "?"
        holder.appName.text = item.appName
        holder.counts.text = "${item.total} total · ${item.real} real · ${item.spam} spam"
        holder.percents.text = "${item.spamPct}% spam · ${item.realPct}% real"
        holder.bar.max = 100
        holder.bar.progress = item.spamPct
    }

    override fun getItemCount(): Int = items.size
}
