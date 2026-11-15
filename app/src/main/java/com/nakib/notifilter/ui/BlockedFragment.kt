package com.nakib.notifilter.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.nakib.notifilter.R
import com.nakib.notifilter.data.NotificationDbHelper
import com.nakib.notifilter.data.Prefs

/** Blocked (spam) list, with reason, Restore / Always allow / Delete. */
class BlockedFragment : Fragment(), Refreshable {

    private lateinit var db: NotificationDbHelper
    private lateinit var prefs: Prefs
    private lateinit var adapter: BlockedAdapter
    private lateinit var emptyView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_blocked, container, false)
        db = NotificationDbHelper(requireContext())
        prefs = Prefs(requireContext())
        emptyView = view.findViewById(R.id.empty_view)

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

        view.findViewById<MaterialButton>(R.id.restore_all_button).setOnClickListener {
            db.listByStatus("BLOCKED").forEach { db.restore(it.id) }
            Toast.makeText(requireContext(), "All restored", Toast.LENGTH_SHORT).show()
            refresh()
        }
        view.findViewById<MaterialButton>(R.id.delete_all_button).setOnClickListener {
            db.deleteAllBlocked()
            Toast.makeText(requireContext(), "Blocked cleared", Toast.LENGTH_SHORT).show()
            refresh()
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
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }
}
