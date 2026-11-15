package com.nakib.notifilter.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.nakib.notifilter.R
import com.nakib.notifilter.ai.AiClient
import com.nakib.notifilter.data.Prefs

/**
 * AI settings: provider keys, model pickers, Test connection, strictness.
 * Keys are stored on the device only.
 */
class AiFragment : Fragment() {

    private lateinit var prefs: Prefs

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ai, container, false)
        prefs = Prefs(requireContext())

        val providerSpinner = view.findViewById<Spinner>(R.id.provider_spinner)
        providerSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf("OpenRouter", "Google (Gemini)")
        )
        providerSpinner.setSelection(if (prefs.aiProvider == "google") 1 else 0)

        val openRouterField = view.findViewById<TextInputEditText>(R.id.openrouter_key_field)
        openRouterField.setText(prefs.openRouterKey)
        val googleField = view.findViewById<TextInputEditText>(R.id.google_key_field)
        googleField.setText(prefs.googleKey)

        val openRouterModel = view.findViewById<Spinner>(R.id.openrouter_model_spinner)
        openRouterModel.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf("openai/gpt-4o-mini", "openai/gpt-4o", "google/gemini-flash-1.5", "anthropic/claude-3-haiku")
        )
        setSpinner(openRouterModel, prefs.openRouterModel)

        val geminiModel = view.findViewById<Spinner>(R.id.gemini_model_spinner)
        geminiModel.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf("gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro")
        )
        setSpinner(geminiModel, prefs.geminiModel)

        val visionModel = view.findViewById<Spinner>(R.id.vision_model_spinner)
        visionModel.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf("gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro")
        )
        setSpinner(visionModel, prefs.visionModel)

        val strictnessBar = view.findViewById<SeekBar>(R.id.strictness_bar)
        val strictnessLabel = view.findViewById<TextView>(R.id.strictness_label)
        strictnessBar.progress = prefs.strictness
        strictnessLabel.text = "Strictness: ${prefs.strictness}%"
        strictnessBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                prefs.strictness = value
                strictnessLabel.text = "Strictness: $value%"
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })

        view.findViewById<MaterialButton>(R.id.save_ai_button).setOnClickListener {
            prefs.aiProvider = if (providerSpinner.selectedItemPosition == 1) "google" else "openrouter"
            prefs.openRouterKey = openRouterField.text?.toString().orEmpty()
            prefs.googleKey = googleField.text?.toString().orEmpty()
            prefs.openRouterModel = openRouterModel.selectedItem?.toString() ?: prefs.openRouterModel
            prefs.geminiModel = geminiModel.selectedItem?.toString() ?: prefs.geminiModel
            prefs.visionModel = visionModel.selectedItem?.toString() ?: prefs.visionModel
            Toast.makeText(requireContext(), "AI settings saved", Toast.LENGTH_SHORT).show()
        }

        val testResult = view.findViewById<TextView>(R.id.test_result)
        view.findViewById<MaterialButton>(R.id.test_button).setOnClickListener {
            val provider = if (providerSpinner.selectedItemPosition == 1) "google" else "openrouter"
            val key = if (provider == "google") {
                googleField.text?.toString().orEmpty()
            } else {
                openRouterField.text?.toString().orEmpty()
            }
            val model = if (provider == "google") {
                geminiModel.selectedItem?.toString().orEmpty()
            } else {
                openRouterModel.selectedItem?.toString().orEmpty()
            }
            testResult.text = "Testing…"
            Thread {
                val ok = AiClient.testSync(provider, key, model)
                requireActivity().runOnUiThread {
                    testResult.text = if (ok) "Connected" else "Failed — check key and model"
                    Toast.makeText(
                        requireContext(),
                        if (ok) "Connected" else "Connection failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.start()
        }
        return view
    }

    private fun setSpinner(spinner: Spinner, value: String) {
        val adapter = spinner.adapter
        for (i in 0 until adapter.count) {
            if (adapter.getItem(i).toString() == value) {
                spinner.setSelection(i)
                return
            }
        }
    }
}
