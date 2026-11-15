package com.nakib.notifilter.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.nakib.notifilter.data.Prefs

/**
 * Rules: AI / Regex segmented mode, OTP regex (multi-line, never cut off),
 * Block / Always-allow keywords, image-analysis toggles.
 */
class RulesFragment : Fragment() {

    private lateinit var prefs: Prefs

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_rules, container, false)
        prefs = Prefs(requireContext())

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
