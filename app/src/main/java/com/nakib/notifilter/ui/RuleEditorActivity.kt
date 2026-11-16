package com.nakib.notifilter.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.nakib.notifilter.R
import com.nakib.notifilter.data.NotificationDbHelper
import com.nakib.notifilter.data.Rule

/**
 * Rule editor — FilterBox way. A rule picks the apps it applies to
 * ("From apps"), optional keywords, an action (Dismiss / Snooze only —
 * there is no honest Mute, and NotiFilter never re-posts), an optional
 * time window, and an enable toggle. With no keywords the rule matches
 * the whole app. OTP is always checked before rules in the service, so
 * a rule can never block an OTP.
 */
class RuleEditorActivity : AppCompatActivity() {

    private lateinit var db: NotificationDbHelper
    private var ruleId: Long = -1
    private var editing: Rule? = null

    private val selectedApps = mutableSetOf<String>()
    private val keywords = mutableSetOf<String>()
    private var allApps: List<AppEntry> = emptyList()
    private lateinit var appAdapter: AppPickAdapter

    data class AppEntry(val pkg: String, val label: String, val icon: Drawable?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rule_editor)
        db = NotificationDbHelper(this)
        ruleId = intent.getLongExtra("rule_id", -1)
        if (ruleId > 0) editing = db.getRule(ruleId)

        val title = findViewById<TextView>(R.id.editor_title)
        title.text = if (editing != null) getString(R.string.edit_rule) else getString(R.string.new_rule)

        val nameField = findViewById<TextInputEditText>(R.id.rule_name_field)
        val actionDismiss = findViewById<MaterialRadioButton>(R.id.action_dismiss)
        val actionSnooze = findViewById<MaterialRadioButton>(R.id.action_snooze)
        val snoozeGroup = findViewById<View>(R.id.snooze_group)
        val snooze15 = findViewById<MaterialRadioButton>(R.id.snooze_15)
        val snooze60 = findViewById<MaterialRadioButton>(R.id.snooze_60)
        val snoozeUntil = findViewById<MaterialRadioButton>(R.id.snooze_until)
        val timeSwitch = findViewById<SwitchMaterial>(R.id.time_window_switch)
        val timeRow = findViewById<View>(R.id.time_window_row)
        val startField = findViewById<AutoCompleteTextView>(R.id.start_hour_field)
        val endField = findViewById<AutoCompleteTextView>(R.id.end_hour_field)
        val enabledSwitch = findViewById<SwitchMaterial>(R.id.rule_enabled_switch)
        val deleteButton = findViewById<MaterialButton>(R.id.delete_rule_button)

        // Hour dropdowns: 00–23.
        val hours = (0..23).map { "%02d".format(it) }
        val hourAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, hours)
        startField.setAdapter(hourAdapter)
        endField.setAdapter(hourAdapter)
        startField.setText("22", false)
        endField.setText("07", false)

        // Pre-fill when editing.
        editing?.let { rule ->
            nameField.setText(rule.name)
            selectedApps.addAll(rule.apps)
            keywords.addAll(rule.keywords)
            if (rule.isSnooze) {
                actionSnooze.isChecked = true
                snoozeGroup.visibility = View.VISIBLE
                when (rule.snoozeMinutes) {
                    60 -> snooze60.isChecked = true
                    -1 -> snoozeUntil.isChecked = true
                    else -> snooze15.isChecked = true
                }
            }
            if (rule.hasTimeWindow) {
                timeSwitch.isChecked = true
                timeRow.visibility = View.VISIBLE
                startField.setText("%02d".format(rule.startHour), false)
                endField.setText("%02d".format(rule.endHour), false)
            }
            enabledSwitch.isChecked = rule.enabled
            deleteButton.visibility = View.VISIBLE
        }

        // Action radio: show snooze durations only for Snooze.
        val updateSnoozeVisibility = {
            snoozeGroup.visibility = if (actionSnooze.isChecked) View.VISIBLE else View.GONE
        }
        actionDismiss.setOnCheckedChangeListener { _, _ -> updateSnoozeVisibility() }
        actionSnooze.setOnCheckedChangeListener { _, _ -> updateSnoozeVisibility() }

        timeSwitch.setOnCheckedChangeListener { _, checked ->
            timeRow.visibility = if (checked) View.VISIBLE else View.GONE
        }

        // App picker: launcher apps, real names + icons, searchable.
        allApps = loadLauncherApps()
        appAdapter = AppPickAdapter(allApps, selectedApps) { updateAppCount() }
        val appList = findViewById<RecyclerView>(R.id.app_list)
        appList.layoutManager = LinearLayoutManager(this)
        appList.adapter = appAdapter
        updateAppCount()

        findViewById<TextInputEditText>(R.id.app_search_field).addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                    appAdapter.filter(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) {}
            }
        )

        refreshKeywordChips()

        findViewById<MaterialButton>(R.id.add_rule_keyword_button).setOnClickListener {
            val field = findViewById<TextInputEditText>(R.id.add_rule_keyword_field)
            val word = field.text.toString().trim()
            if (word.isNotBlank()) {
                keywords.add(word)
                field.setText("")
                refreshKeywordChips()
            }
        }

        findViewById<MaterialButton>(R.id.save_rule_button).setOnClickListener {
            if (selectedApps.isEmpty()) {
                Toast.makeText(this, getString(R.string.pick_at_least_one_app), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            var name = nameField.text.toString().trim()
            if (name.isBlank()) {
                // Auto-name from the first selected app.
                val first = allApps.firstOrNull { selectedApps.contains(it.pkg) }
                name = if (first != null) "${first.label} rule" else getString(R.string.new_rule)
            }
            val action = if (actionSnooze.isChecked) "snooze" else "dismiss"
            val snoozeMinutes = when {
                !actionSnooze.isChecked -> 15
                snooze60.isChecked -> 60
                snoozeUntil.isChecked -> -1
                else -> 15
            }
            val (startHour, endHour) = if (timeSwitch.isChecked) {
                val start = startField.text.toString().toIntOrNull()?.coerceIn(0, 23) ?: 22
                val end = endField.text.toString().toIntOrNull()?.coerceIn(0, 23) ?: 7
                start to end
            } else {
                -1 to -1
            }
            val rule = Rule(
                id = if (ruleId > 0) ruleId else 0,
                name = name,
                enabled = enabledSwitch.isChecked,
                apps = selectedApps.sorted(),
                keywords = keywords.sorted(),
                action = action,
                snoozeMinutes = snoozeMinutes,
                startHour = startHour,
                endHour = endHour
            )
            if (ruleId > 0) db.updateRule(rule) else db.insertRule(rule)
            setResult(Activity.RESULT_OK)
            finish()
        }

        deleteButton.setOnClickListener {
            if (ruleId > 0) db.deleteRule(ruleId)
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun updateAppCount() {
        val count = findViewById<TextView>(R.id.apps_selected_count)
        count.text = if (selectedApps.isEmpty()) {
            getString(R.string.no_apps_selected)
        } else {
            resources.getQuantityString(
                R.plurals.apps_selected, selectedApps.size, selectedApps.size
            )
        }
    }

    private fun refreshKeywordChips() {
        val group = findViewById<ChipGroup>(R.id.rule_keyword_chips)
        group.removeAllViews()
        for (word in keywords.sorted()) {
            val chip = Chip(this).apply {
                text = word
                isCloseIconVisible = true
                chipBackgroundColor = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.md_secondary_container)
                )
                setTextColor(ContextCompat.getColor(context, R.color.md_on_secondary_container))
                closeIconTint = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.md_on_secondary_container)
                )
                setOnCloseIconClickListener {
                    keywords.remove(word)
                    refreshKeywordChips()
                }
            }
            group.addView(chip)
        }
    }

    /** Installed launcher apps, sorted by label, excluding NotiFilter itself.
     *  Uses the launcher-intent <queries> declaration, so it works on
     *  Android 11+ without depending on QUERY_ALL_PACKAGES. */
    private fun loadLauncherApps(): List<AppEntry> {
        return try {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            val infos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
            infos.mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == packageName) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isBlank()) return@mapNotNull null
                AppEntry(pkg, label, try { info.loadIcon(pm) } catch (_: Exception) { null })
            }.sortedBy { it.label.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** App picker rows: icon + name + package, checkbox, tap to toggle. */
    private class AppPickAdapter(
        private val all: List<AppEntry>,
        private val selected: MutableSet<String>,
        private val onChanged: () -> Unit
    ) : RecyclerView.Adapter<AppPickAdapter.Holder>() {

        private var shown: List<AppEntry> = all

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.pick_app_icon)
            val name: TextView = view.findViewById(R.id.pick_app_name)
            val pkg: TextView = view.findViewById(R.id.pick_app_pkg)
            val check: CheckBox = view.findViewById(R.id.pick_app_check)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_pick, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = shown[position]
            holder.name.text = item.label
            holder.pkg.text = item.pkg
            if (item.icon != null) holder.icon.setImageDrawable(item.icon)
            holder.check.isChecked = selected.contains(item.pkg)
            holder.itemView.setOnClickListener {
                if (selected.contains(item.pkg)) selected.remove(item.pkg)
                else selected.add(item.pkg)
                holder.check.isChecked = selected.contains(item.pkg)
                onChanged()
            }
        }

        override fun getItemCount(): Int = shown.size

        fun filter(query: String) {
            val q = query.trim().lowercase()
            shown = if (q.isBlank()) all else all.filter {
                it.label.lowercase().contains(q) || it.pkg.lowercase().contains(q)
            }
            notifyDataSetChanged()
        }
    }
}
