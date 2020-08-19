package com.mare5x.colorcalendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment

class ProfileEditorDialogFragment : DialogFragment() {
    private lateinit var minColorPicker: ColorPickerBar
    private lateinit var maxColorPicker: ColorPickerBar

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_profile_editor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        minColorPicker = view.findViewById(R.id.minColorPicker)
        minColorPicker.showFullHue()

        maxColorPicker = view.findViewById(R.id.maxColorPicker)
        maxColorPicker.showFullHue()

        val profileColorsBar = view.findViewById<ColorSeekBar>(R.id.profileColorsBar)
        profileColorsBar.setColors(minColorPicker.getColor(), maxColorPicker.getColor())

        minColorPicker.onProgressChanged = { value, color ->
            profileColorsBar.setColors(color, maxColorPicker.getColor())
        }
        maxColorPicker.onProgressChanged = { value, color ->
            profileColorsBar.setColors(minColorPicker.getColor(), color)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.run {
            putFloat(STATE_MIN_COLOR, minColorPicker.getNormProgress())
            putFloat(STATE_MAX_COLOR, maxColorPicker.getNormProgress())
        }
        super.onSaveInstanceState(outState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (savedInstanceState != null) {
            with(savedInstanceState) {
                // TODO ONLY MAX BAR IS RESTORED, MIN BAR IS RETARDED
                // ASDFAPODIUQWEOIRUQPOIWERUQWUROPI
                minColorPicker.setNormProgress(getFloat(STATE_MIN_COLOR))
                maxColorPicker.setNormProgress(getFloat(STATE_MAX_COLOR))
            }
        }
    }

    companion object {
        private const val STATE_MIN_COLOR = "state_min_color"
        private const val STATE_MAX_COLOR = "state_max_color"
    }
}