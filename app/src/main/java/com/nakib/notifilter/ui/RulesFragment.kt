package com.nakib.notifilter.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.nakib.notifilter.R
import com.nakib.notifilter.data.Prefs

/**
 * Rules: AI / Regex mode, OTP regex, Block / Always-allow keywords,
 * image-analysis toggles.
 */
class RulesFragment : Fragment() {

    private lateinit var prefs: Prefs

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_rules, container, false)
        prefs = Prefs(requireContext())

        val modeSwitch = view.findViewById<SwitchMaterial>(R.id.mode_switch)
        modeSwitch.isChecked = prefs.mode == "regex"
        modeSwitch.setOnCheckedChangeListener { _, isRegex ->
            prefs.mode = if (isRegex) "regex" else "ai"
            Toast.makeText(
                requireContext(),
                if (isRegex) "Regex mode on — AI skipped" else "AI mode on",
                Toast.LENGTH_SHORT
            ).show()
        }

        val otpField = view.findViewById<TextInputEditText>(R.id.otp_regex_field)
        otpField.setText(prefs.otpRegex)
        view.findViewById<MaterialButton>(R.id.save_regex_button).setOnClickListener {
            prefs.otpRegex = otpField.text?.toString().orEmpty()
            Toast.makeText(requireContext(), "OTP pattern saved", Toast.LENGTH_SHORT).show()
        }

        refreshChips(view)

        view.findViewById<MaterialButton>(R.id.add_block_button).setOnClickListener {
            val field = view.findViewById<EditText>(R.id.add_block_field)
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
            val field = view.findViewById<EditText>(R.id.add_allow_field)
            val word = field.text.toString().trim()
            if (word.isNotBlank()) {
                val set = prefs.allowList
                set.add(word)
                prefs.allowList = set
                field.setText("")
                refreshChips(view)
            }
        }

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

    private fun refreshChips(view: View) {
        val blockGroup = view.findViewById<ChipGroup>(R.id.block_chips)
        val allowGroup = view.findViewById<ChipGroup>(R.id.allow_chips)
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
        for (word in words.sorted()) {
            val chip = Chip(requireContext()).apply {
                text = word
                isCloseIconVisible = true
                setOnCloseIconClickListener { onRemove?.invoke(word) }
            }
            group.addView(chip)
        }
    }
}
