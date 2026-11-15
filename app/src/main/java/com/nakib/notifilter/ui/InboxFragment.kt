package com.nakib.notifilter.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.nakib.notifilter.R
import com.nakib.notifilter.data.NotificationDbHelper
import com.nakib.notifilter.data.Record

/**
 * Inbox: search + All / OTP / Important filters + shown notifications.
 * No stats card here — stats live in Analytics (3-dot menu).
 */
class InboxFragment : Fragment(), Refreshable {

    private lateinit var db: NotificationDbHelper
    private lateinit var adapter: NotificationAdapter
    private lateinit var emptyView: TextView
    private var filter = "all"
    private var query = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_inbox, container, false)
        db = NotificationDbHelper(requireContext())
        emptyView = view.findViewById(R.id.empty_view)

        adapter = NotificationAdapter(emptyList())
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
        if (!::db.isInitialized) return
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
    }
}
