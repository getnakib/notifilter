package com.nakib.notifilter.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.nakib.notifilter.FilterService
import com.nakib.notifilter.R
import com.nakib.notifilter.data.NotificationDbHelper
import com.nakib.notifilter.data.Prefs
import com.nakib.notifilter.data.Record
import java.util.Date

/**
 * Inbox: search + All / OTP / Important filters + shown notifications.
 * No stats card here — stats live in Analytics (3-dot menu).
 * Each card has a menu: Copy code (OTP), Mark as spam, Dismiss.
 */
class InboxFragment : Fragment(), Refreshable {

    private lateinit var db: NotificationDbHelper
    private lateinit var prefs: Prefs
    private lateinit var adapter: NotificationAdapter
    private lateinit var emptyView: TextView
    private lateinit var pausedBanner: LinearLayout
    private var filter = "all"
    private var query = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_inbox, container, false)
        db = NotificationDbHelper(requireContext())
        prefs = Prefs(requireContext())
        emptyView = view.findViewById(R.id.empty_view)
        pausedBanner = view.findViewById(R.id.paused_banner)

        view.findViewById<MaterialButton>(R.id.resume_button).setOnClickListener {
            prefs.paused = false
            (activity as? MainActivity)?.updateStatus()
            Toast.makeText(requireContext(), "Filtering on", Toast.LENGTH_SHORT).show()
            refresh()
        }

        view.findViewById<MaterialButton>(R.id.listener_settings_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        adapter = NotificationAdapter(
            emptyList(),
            onMarkSpam = { record ->
                // Cancel the original notification, move the row to Blocked.
                // It counts as spam for Analytics.
                if (record.key.isNotBlank()) FilterService.cancelByKey(record.key)
                db.markSpam(record.id)
                Toast.makeText(requireContext(), "Marked as spam", Toast.LENGTH_SHORT).show()
                refresh()
            },
            onDismiss = { record ->
                // Clear the original notification and drop the row.
                if (record.key.isNotBlank()) FilterService.cancelByKey(record.key)
                db.delete(record.id)
                refresh()
            }
        )
        val list = view.findViewById<RecyclerView>(R.id.inbox_list)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        view.findViewById<ChipGroup>(R.id.filter_chips)
            .setOnCheckedStateChangeListener { _, checkedIds ->
                filter = when (checkedIds.firstOrNull()) {
                    R.id.chip_otp -> "otp"
                    R.id.chip_important -> "important"
                    else -> "all"
                }
                refresh()
            }

        view.findViewById<EditText>(R.id.search_box).addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                    query = s?.toString().orEmpty()
                    refresh()
                }
                override fun afterTextChanged(s: Editable?) {}
            }
        )
        return view
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun refresh() {
        if (!::db.isInitialized || !::adapter.isInitialized) return
        updateStatusRow()
        pausedBanner.visibility = if (prefs.paused) View.VISIBLE else View.GONE
        var items: List<Record> = db.listByStatus("SHOWN")
        items = when (filter) {
            "otp" -> items.filter { it.isOtp }
            "important" -> items.filter { !it.isOtp }
            else -> items
        }
        if (query.isNotBlank()) {
            val q = query.lowercase()
            items = items.filter {
                it.title.lowercase().contains(q) ||
                    it.body.lowercase().contains(q) ||
                    it.appName.lowercase().contains(q)
            }
        }
        adapter.update(items)
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        (activity as? MainActivity)?.updateBadges()
    }

    /** Listener + AI status row, at the top of the Inbox. Tells Rani at a
     *  glance whether notifications are reaching the app ("Listener: not
     *  granted" = the toggle is off) and whether AI has a key. */
    private fun updateStatusRow() {
        val view = view ?: return
        val context = requireContext()
        val granted = NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
        val connected = FilterService.isConnected()
        val dot = view.findViewById<View>(R.id.listener_dot)
        val statusText = view.findViewById<TextView>(R.id.listener_status)
        val settingsButton = view.findViewById<MaterialButton>(R.id.listener_settings_button)
        val aiText = view.findViewById<TextView>(R.id.ai_status)

        val lastSeen = prefs.lastNotificationSeen
        val lastSeenText = if (lastSeen > 0) {
            " · last notification ${DateFormat.getTimeFormat(context).format(Date(lastSeen))}"
        } else {
            " · no notification yet"
        }
        when {
            connected -> {
                dot.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.md_ok)
                )
                statusText.text = "Listener: connected$lastSeenText"
                settingsButton.visibility = View.GONE
            }
            granted -> {
                dot.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.md_error)
                )
                statusText.text = "Listener: granted — waiting to connect$lastSeenText"
                settingsButton.visibility = View.GONE
            }
            else -> {
                dot.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.md_error)
                )
                statusText.text = "Listener: not granted — open Settings"
                settingsButton.visibility = View.VISIBLE
            }
        }

        val hasOpenRouter = prefs.openRouterKey.isNotBlank()
        val hasGoogle = prefs.googleKey.isNotBlank()
        aiText.text = when {
            !hasOpenRouter && !hasGoogle -> "AI: no key added"
            prefs.aiProvider == "google" && hasGoogle -> "AI: Gemini key added"
            prefs.aiProvider == "google" && hasOpenRouter -> "AI: OpenRouter key added — not the active provider"
            prefs.aiProvider == "openrouter" && hasOpenRouter -> "AI: OpenRouter key added"
            else -> "AI: Gemini key added — not the active provider"
        }
    }
}
