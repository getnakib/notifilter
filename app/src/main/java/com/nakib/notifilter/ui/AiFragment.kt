package com.nakib.notifilter.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.nakib.notifilter.R
import com.nakib.notifilter.ai.AiClient
import com.nakib.notifilter.data.Prefs
import com.nakib.notifilter.summary.SummaryScheduler

/**
 * AI settings: provider keys, model pickers, Test connection, strictness,
 * vision model, behaviour. Keys are stored on the device only, saved as
 * they are typed (no Save button, like the approved preview).
 */
class AiFragment : Fragment() {

    private lateinit var prefs: Prefs

    // Vision model options: display text -> stored value.
    private val visionOptions = listOf(
        "Auto — use selected provider" to "auto",
        "google/gemini-2.0-flash" to "google/gemini-2.0-flash",
        "openai/gpt-4o-mini" to "openai/gpt-4o-mini"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ai, container, false)
        prefs = Prefs(requireContext())

        val openRouterRadio = view.findViewById<RadioButton>(R.id.openrouter_radio)
        val googleRadio = view.findViewById<RadioButton>(R.id.google_radio)
        val openRouterFields = view.findViewById<LinearLayout>(R.id.openrouter_fields)
        val googleFields = view.findViewById<LinearLayout>(R.id.google_fields)
        val openRouterConn = view.findViewById<TextView>(R.id.openrouter_conn)
        val googleConn = view.findViewById<TextView>(R.id.google_conn)

        fun updateProviderUi() {
            val isGoogle = prefs.aiProvider == "google"
            openRouterRadio.isChecked = !isGoogle
            googleRadio.isChecked = isGoogle
            openRouterFields.visibility = if (isGoogle) View.GONE else View.VISIBLE
            googleFields.visibility = if (isGoogle) View.VISIBLE else View.GONE
            openRouterConn.visibility = if (prefs.openRouterKey.isNotBlank()) View.VISIBLE else View.GONE
            googleConn.visibility = if (prefs.googleKey.isNotBlank()) View.VISIBLE else View.GONE
        }
        view.findViewById<View>(R.id.openrouter_header).setOnClickListener {
            prefs.aiProvider = "openrouter"
            updateProviderUi()
        }
        view.findViewById<View>(R.id.google_header).setOnClickListener {
            prefs.aiProvider = "google"
            updateProviderUi()
        }
        openRouterRadio.setOnClickListener { prefs.aiProvider = "openrouter"; updateProviderUi() }
        googleRadio.setOnClickListener { prefs.aiProvider = "google"; updateProviderUi() }

        val openRouterField = view.findViewById<TextInputEditText>(R.id.openrouter_key_field)
        openRouterField.setText(prefs.openRouterKey)
        openRouterField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.openRouterKey = s?.toString().orEmpty()
                updateProviderUi()
            }
        })

        val googleField = view.findViewById<TextInputEditText>(R.id.google_key_field)
        googleField.setText(prefs.googleKey)
        googleField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.googleKey = s?.toString().orEmpty()
                updateProviderUi()
            }
        })

        setupModelDropdown(
            view.findViewById(R.id.openrouter_model_field),
            listOf("openai/gpt-4o-mini", "openai/gpt-4o", "google/gemini-2.0-flash", "meta-llama/llama-3.3-70b", "deepseek/deepseek-chat"),
            prefs.openRouterModel
        ) { prefs.openRouterModel = it }

        setupModelDropdown(
            view.findViewById(R.id.gemini_model_field),
            listOf("gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-1.5-flash"),
            prefs.geminiModel
        ) { prefs.geminiModel = it }

        // Vision model dropdown.
        val visionField = view.findViewById<AutoCompleteTextView>(R.id.vision_model_field)
        val visionDisplay = visionOptions.firstOrNull { it.second == prefs.visionModel }?.first
            ?: visionOptions[0].first
        visionField.setText(visionDisplay, false)
        visionField.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, visionOptions.map { it.first })
        )
        visionField.setOnItemClickListener { _, _, position, _ ->
            prefs.visionModel = visionOptions[position].second
        }

        // Strictness: 3 steps — Lenient (40) / Balanced (60) / Strict (80).
        val strictnessSlider = view.findViewById<Slider>(R.id.strictness_slider)
        val strictnessValue = view.findViewById<TextView>(R.id.strictness_value)
        fun strictnessStep(value: Int): Int = when {
            value < 50 -> 0
            value <= 70 -> 1
            else -> 2
        }
        fun strictnessLabel(step: Int): String = when (step) {
            0 -> getString(R.string.strict_lenient)
            2 -> getString(R.string.strict_strict)
            else -> getString(R.string.strict_balanced)
        }
        fun strictnessScore(step: Int): Int = when (step) {
            0 -> 40
            2 -> 80
            else -> 60
        }
        val initialStep = strictnessStep(prefs.strictness)
        strictnessSlider.value = initialStep.toFloat()
        strictnessValue.text = strictnessLabel(initialStep)
        strictnessSlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val step = value.toInt()
            prefs.strictness = strictnessScore(step)
            strictnessValue.text = strictnessLabel(step)
        }

        // Per-provider Test connection.
        view.findViewById<MaterialButton>(R.id.test_openrouter_button).setOnClickListener {
            val result = view.findViewById<TextView>(R.id.openrouter_test_result)
            val key = openRouterField.text?.toString().orEmpty()
            val model = view.findViewById<AutoCompleteTextView>(R.id.openrouter_model_field).text.toString()
            result.text = "Testing…"
            Thread {
                val ok = AiClient.testSync("openrouter", key, model)
                activity?.runOnUiThread {
                    result.text = if (ok) "Connected" else "Failed — check key and model"
                    if (ok) updateProviderUi()
                    context?.let {
                        Toast.makeText(it, if (ok) "Connected" else "Connection failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
        view.findViewById<MaterialButton>(R.id.test_google_button).setOnClickListener {
            val result = view.findViewById<TextView>(R.id.google_test_result)
            val key = googleField.text?.toString().orEmpty()
            val model = view.findViewById<AutoCompleteTextView>(R.id.gemini_model_field).text.toString()
            result.text = "Testing…"
            Thread {
                val ok = AiClient.testSync("google", key, model)
                activity?.runOnUiThread {
                    result.text = if (ok) "Connected" else "Failed — check key and model"
                    if (ok) updateProviderUi()
                    context?.let {
                        Toast.makeText(it, if (ok) "Connected" else "Connection failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }

        // Behaviour toggles.
        val instantOtpSwitch = view.findViewById<SwitchMaterial>(R.id.instant_otp_switch)
        instantOtpSwitch.isChecked = prefs.otpEnabled
        instantOtpSwitch.setOnCheckedChangeListener { _, checked -> prefs.otpEnabled = checked }

        val silentSwitch = view.findViewById<SwitchMaterial>(R.id.silent_blocked_switch)
        silentSwitch.isChecked = prefs.silentBlocked
        silentSwitch.setOnCheckedChangeListener { _, checked -> prefs.silentBlocked = checked }

        val dailySwitch = view.findViewById<SwitchMaterial>(R.id.daily_summary_switch)
        dailySwitch.isChecked = prefs.dailySummary
        dailySwitch.setOnCheckedChangeListener { _, checked ->
            prefs.dailySummary = checked
            context?.let {
                if (checked) SummaryScheduler.schedule(it) else SummaryScheduler.cancel(it)
            }
        }

        updateProviderUi()
        return view
    }

    private fun setupModelDropdown(
        field: AutoCompleteTextView,
        options: List<String>,
        current: String,
        onSelect: (String) -> Unit
    ) {
        field.setText(if (options.contains(current)) current else options[0], false)
        field.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, options)
        )
        field.setOnItemClickListener { _, _, position, _ -> onSelect(options[position]) }
    }
}
