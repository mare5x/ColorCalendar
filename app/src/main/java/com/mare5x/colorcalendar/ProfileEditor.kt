package com.mare5x.colorcalendar

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.PersistableBundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment

class ProfileEditorDialogFragment : DialogFragment() {
    interface ProfileEditorListener {
        fun onProfileConfirm(name: String, minColor: Int, maxColor: Int, prefColor: Int)
        fun onProfileColorChanged(color: Int)
        fun onProfileDismiss()
    }

    private lateinit var minColorPicker: ColorPickerBar
    private lateinit var maxColorPicker: ColorPickerBar

    private var listener: ProfileEditorListener? = null

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

        minColorPicker.onValueChanged = { _, color ->
            profileColorsBar.setColors(color, maxColorPicker.getColor())
            listener?.onProfileColorChanged(profileColorsBar.getColor())
        }
        maxColorPicker.onValueChanged = { _, color ->
            profileColorsBar.setColors(minColorPicker.getColor(), color)
            listener?.onProfileColorChanged(profileColorsBar.getColor())
        }

        profileColorsBar.onValueChanged = { _, color ->
            listener?.onProfileColorChanged(color)
        }

        val nameEditor = view.findViewById<EditText>(R.id.profileNameEdit)

        val cancelButton = view.findViewById<Button>(R.id.cancelButton)
        cancelButton.setOnClickListener {
            if (showsDialog)
                dismiss()
        }

        val confirmButton = view.findViewById<Button>(R.id.confirmButton)
        confirmButton.setOnClickListener {
            listener?.onProfileConfirm(nameEditor.text.toString(),
                minColorPicker.getColor(),
                maxColorPicker.getColor(),
                profileColorsBar.getColor())
            if (showsDialog)
                dismiss()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        listener?.onProfileDismiss()
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

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? ProfileEditorListener
    }

    companion object {
        private const val STATE_MIN_COLOR = "state_min_color"
        private const val STATE_MAX_COLOR = "state_max_color"
    }
}


class ProfileEditorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_profile_editor)
        setSupportActionBar(findViewById(R.id.toolbar))

        val circleBar = findViewById<ColorCircleBar>(R.id.colorCircleBar)
        val testText = findViewById<TextView>(R.id.testText)
        val colorBar = findViewById<ColorSeekBar>(R.id.colorSeekBar)
        circleBar.onValueChanged = { a, b ->
            testText.text = "$a $b"
            colorBar.setColors(hueColor(a), hueColor(b))
        }
    }
}