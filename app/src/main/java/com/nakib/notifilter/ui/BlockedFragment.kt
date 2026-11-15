package com.nakib.notifilter.ui

import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nakib.notifilter.R
import com.nakib.notifilter.data.NotificationDbHelper
import com.nakib.notifilter.data.Prefs

/** Blocked (spam) list, with reason. Per-card menu: Restore / Always allow / Delete. */
class BlockedFragment : Fragment(), Refreshable {

    private lateinit var db: NotificationDbHelper
    private lateinit var prefs: Prefs
    private lateinit var adapter: BlockedAdapter
    private lateinit var emptyView: TextView
    private lateinit var blockedSub: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_blocked, container, false)
        db = NotificationDbHelper(requireContext())
        prefs = Prefs(requireContext())
        emptyView = view.findViewById(R.id.empty_view)
        blockedSub = view.findViewById(R.id.blocked_sub)

        adapter = BlockedAdapter(
            emptyList(),
            onRestore = { record ->
                db.restore(record.id)
                Toast.makeText(requireContext(), "Restored", Toast.LENGTH_SHORT).show()
                refresh()
            },
            onAllow = { record ->
                val allow = prefs.allowList
                allow.add(record.pkg)
                allow.add(record.appName)
                prefs.allowList = allow
                db.restore(record.id)
                Toast.makeText(
                    requireContext(),
                    "${record.appName} always allowed",
                    Toast.LENGTH_SHORT
                ).show()
                refresh()
            },
            onDelete = { record ->
                db.delete(record.id)
                refresh()
            }
        )
        val list = view.findViewById<RecyclerView>(R.id.blocked_list)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        view.findViewById<ImageButton>(R.id.blocked_menu_button).setOnClickListener { anchor ->
            val popup = PopupMenu(requireContext(), anchor)
            popup.menu.add(Menu.NONE, 1, Menu.NONE, getString(R.string.restore_all))
            val deleteTitle = SpannableString(getString(R.string.delete_all_blocked))
            deleteTitle.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.md_error)),
                0, deleteTitle.length, 0
            )
            popup.menu.add(Menu.NONE, 2, Menu.NONE, deleteTitle)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        db.listByStatus("BLOCKED").forEach { db.restore(it.id) }
                        Toast.makeText(requireContext(), "All restored", Toast.LENGTH_SHORT).show()
                        refresh()
                    }
                    2 -> {
                        db.deleteAllBlocked()
                        Toast.makeText(requireContext(), "Blocked cleared", Toast.LENGTH_SHORT).show()
                        refresh()
                    }
                }
                true
            }
            popup.show()
        }
        return view
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun refresh() {
        if (!::db.isInitialized || !::adapter.isInitialized) return
        val items = db.listByStatus("BLOCKED")
        adapter.update(items)
        blockedSub.text = if (items.isEmpty()) {
            getString(R.string.blocked_none)
        } else {
            resources.getQuantityString(R.plurals.blocked_today, items.size, items.size)
        }
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        (activity as? MainActivity)?.updateBadges()
    }
}
