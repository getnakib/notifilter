package com.nakib.notifilter.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.nakib.notifilter.R
import com.nakib.notifilter.data.NotificationDbHelper
import com.nakib.notifilter.data.Prefs
import com.nakib.notifilter.data.Rule

/**
 * Rules: "My rules" list at the top (FilterBox way — a rule picks the apps
 * it applies to), then the global AI / Regex segmented mode, OTP regex
 * (multi-line, never cut off), Block / Always-allow keywords, and
 * image-analysis toggles. The global sections are unchanged from v1.2.
 */
class RulesFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var db: NotificationDbHelper
    private var rootView: View? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_rules, container, false)
        rootView = view
        prefs = Prefs(requireContext())
        db = NotificationDbHelper(requireContext())

        view.findViewById<MaterialButton>(R.id.add_rule_button).setOnClickListener {
            startActivity(Intent(requireContext(), RuleEditorActivity::class.java))
        }
        refreshRules(view)

        val aiButton = view.findViewById<MaterialButton>(R.id.mode_ai_button)
        val regexButton = view.findViewById<MaterialButton>(R.id.mode_regex_button)
        fun updateModeButtons() {
            val isRegex = prefs.mode == "regex"
            styleSegment(aiButton, !isRegex)
            styleSegment(regexButton, isRegex)
        }
        aiButton.setOnClickListener {
            prefs.mode = "ai"
            updateModeButtons()
            Toast.makeText(requireContext(), "AI mode on", Toast.LENGTH_SHORT).show()
        }
        regexButton.setOnClickListener {
            prefs.mode = "regex"
            updateModeButtons()
            Toast.makeText(requireContext(), "Regex mode on — AI skipped", Toast.LENGTH_SHORT).show()
        }
        updateModeButtons()

        val otpToggle = view.findViewById<SwitchMaterial>(R.id.otp_toggle)
        otpToggle.isChecked = prefs.otpEnabled
        otpToggle.setOnCheckedChangeListener { _, checked -> prefs.otpEnabled = checked }

        val otpField = view.findViewById<TextInputEditText>(R.id.otp_regex_field)
        otpField.setText(prefs.otpRegex)
        view.findViewById<MaterialButton>(R.id.save_regex_button).setOnClickListener {
            prefs.otpRegex = otpField.text?.toString().orEmpty()
            Toast.makeText(requireContext(), "OTP pattern saved", Toast.LENGTH_SHORT).show()
        }

        refreshChips(view)

        view.findViewById<MaterialButton>(R.id.add_block_button).setOnClickListener {
            val field = view.findViewById<TextInputEditText>(R.id.add_block_field)
            val word = field.text.toString().trim()
            if (word.isNotBlank()) {
                val set = prefs.blockKeywords
                set.add(word)
                prefs.blockKeywords = set
                field.setText("")
                refreshChips(view)
            }
        }
        view.findViewById<MaterialButton>(R.id.add_allow_button).setOnClickListener {
            val field = view.findViewById<TextInputEditText>(R.id.add_allow_field)
            val word = field.text.toString().trim()
            if (word.isNotBlank()) {
                val set = prefs.allowList
                set.add(word)
                prefs.allowList = set
                field.setText("")
                refreshChips(view)
            }
        }

        val masterSwitch = view.findViewById<SwitchMaterial>(R.id.image_master_switch)
        masterSwitch.isChecked = prefs.imageAnalysisEnabled
        masterSwitch.setOnCheckedChangeListener { _, checked -> prefs.imageAnalysisEnabled = checked }

        val ocrSwitch = view.findViewById<SwitchMaterial>(R.id.ocr_switch)
        ocrSwitch.isChecked = prefs.imageOcr
        ocrSwitch.setOnCheckedChangeListener { _, checked -> prefs.imageOcr = checked }

        val promoSwitch = view.findViewById<SwitchMaterial>(R.id.promo_switch)
        promoSwitch.isChecked = prefs.imagePromoBlock
        promoSwitch.setOnCheckedChangeListener { _, checked -> prefs.imagePromoBlock = checked }

        val qrSwitch = view.findViewById<SwitchMaterial>(R.id.qr_switch)
        qrSwitch.isChecked = prefs.imageQrCheck
        qrSwitch.setOnCheckedChangeListener { _, checked -> prefs.imageQrCheck = checked }

        return view
    }

    override fun onResume() {
        super.onResume()
        // The editor may have added/changed/deleted a rule — reload.
        rootView?.let { refreshRules(it) }
    }

    /** Render the "My rules" list into the container. */
    private fun refreshRules(view: View) {
        if (!::db.isInitialized) return
        val rules = db.listRules()
        view.findViewById<TextView>(R.id.rules_count).text = rules.size.toString()
        view.findViewById<TextView>(R.id.no_rules_hint).visibility =
            if (rules.isEmpty()) View.VISIBLE else View.GONE
        val container = view.findViewById<LinearLayout>(R.id.rules_container)
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        for (rule in rules) {
            container.addView(bindRuleRow(inflater, container, rule))
        }
    }

    private fun bindRuleRow(inflater: LayoutInflater, parent: ViewGroup, rule: Rule): View {
        val row = inflater.inflate(R.layout.item_rule, parent, false)
        row.findViewById<TextView>(R.id.rule_name).text = rule.name

        val toggle = row.findViewById<SwitchMaterial>(R.id.rule_enabled)
        toggle.isChecked = rule.enabled
        toggle.setOnCheckedChangeListener { _, checked ->
            db.updateRule(rule.copy(enabled = checked))
        }

        // App icons (up to 4) + "+N" for the rest.
        val icons = row.findViewById<LinearLayout>(R.id.rule_app_icons)
        icons.removeAllViews()
        val pm = requireContext().packageManager
        val shown = rule.apps.take(4)
        for (pkg in shown) {
            val drawable: Drawable? = try { pm.getApplicationIcon(pkg) } catch (_: Exception) { null }
            val iconView = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(24.dp(), 24.dp()).apply {
                    marginEnd = 6.dp()
                }
                if (drawable != null) setImageDrawable(drawable)
            }
            icons.addView(iconView)
        }
        if (rule.apps.size > 4) {
            val more = TextView(requireContext()).apply {
                text = "+${rule.apps.size - 4}"
                setTextColor(ContextCompat.getColor(requireContext(), R.color.md_on_variant))
                textSize = 12f
            }
            icons.addView(more)
        }

        // Meta: action · apps · keywords · time window.
        val parts = mutableListOf(rule.actionLabel())
        parts.add(
            if (rule.apps.size == 1) "1 app"
            else "${rule.apps.size} apps"
        )
        if (rule.keywords.isNotEmpty()) {
            parts.add(
                if (rule.keywords.size == 1) "1 keyword"
                else "${rule.keywords.size} keywords"
            )
        }
        rule.timeLabel()?.let { parts.add(it) }
        row.findViewById<TextView>(R.id.rule_meta).text = parts.joinToString(" · ")

        row.setOnClickListener {
            val intent = Intent(requireContext(), RuleEditorActivity::class.java)
            intent.putExtra("rule_id", rule.id)
            startActivity(intent)
        }
        return row
    }

    private fun Int.dp(): Int = (this * requireContext().resources.displayMetrics.density).toInt()

    /** Segmented control: selected = surface fill + shadow, unselected = clear. */
    private fun styleSegment(button: MaterialButton, selected: Boolean) {
        val context = requireContext()
        if (selected) {
            button.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.md_surface)
            )
            button.setTextColor(ContextCompat.getColor(context, R.color.md_on_surface))
            button.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.md_on_surface)
            )
            button.elevation = 2f
        } else {
            button.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, android.R.color.transparent)
            )
            button.setTextColor(ContextCompat.getColor(context, R.color.md_on_variant))
            button.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.md_on_variant)
            )
            button.elevation = 0f
        }
    }

    private fun refreshChips(view: View) {
        val blockGroup = view.findViewById<ChipGroup>(R.id.block_chips)
        val allowGroup = view.findViewById<ChipGroup>(R.id.allow_chips)
        view.findViewById<TextView>(R.id.block_count).text = prefs.blockKeywords.size.toString()
        view.findViewById<TextView>(R.id.allow_count).text = prefs.allowList.size.toString()
        renderChips(blockGroup, prefs.blockKeywords) { word ->
            val set = prefs.blockKeywords
            set.remove(word)
            prefs.blockKeywords = set
            refreshChips(view)
        }
        renderChips(allowGroup, prefs.allowList) { word ->
            val set = prefs.allowList
            set.remove(word)
            prefs.allowList = set
            refreshChips(view)
        }
    }

    private fun renderChips(
        group: ChipGroup,
        words: Set<String>,
        onRemove: ((String) -> Unit)?
    ) {
        group.removeAllViews()
        val context = requireContext()
        for (word in words.sorted()) {
            val chip = Chip(context).apply {
                text = word
                isCloseIconVisible = true
                // Preview keyword chips: secondary-container fill.
                chipBackgroundColor = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.md_secondary_container)
                )
                setTextColor(ContextCompat.getColor(context, R.color.md_on_secondary_container))
                closeIconTint = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.md_on_secondary_container)
                )
                setOnCloseIconClickListener { onRemove?.invoke(word) }
            }
            group.addView(chip)
        }
    }
}
