package com.mare5x.colorcalendar

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
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


class ProfileDeleteDialog : DialogFragment() {
    private var listener: ProfileDeleteListener? = null

    interface ProfileDeleteListener {
        fun onProfileDelete()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val profileName = requireArguments().getString(PROFILE_NAME_KEY)
            val builder = AlertDialog.Builder(it)
            builder.setMessage("Are you sure you wish to delete '${profileName}'?")
                .setPositiveButton("Delete") { _, _ ->
                    listener?.onProfileDelete()
                }
                .setNegativeButton("Cancel") { _, _ ->
                    if (showsDialog)
                        dismiss()
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? ProfileDeleteListener
    }

    companion object {
        const val PROFILE_NAME_KEY = "PROFILE_NAME"

        fun create(profile: ProfileEntry): ProfileDeleteDialog {
            val fragment = ProfileDeleteDialog()
            fragment.arguments = Bundle().apply { putString(PROFILE_NAME_KEY, profile.name) }
            return fragment
        }
    }
}


class ProfileDiscardDialog : DialogFragment() {
    interface ProfileDiscardListener {
        fun onProfileDiscard()
    }

    private var listener: ProfileDiscardListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.setMessage("Discard this profile?")
                .setPositiveButton("Discard") { _, _ ->
                    listener?.onProfileDiscard()
                }
                .setNegativeButton("Cancel") { _, _ ->
                    if (showsDialog)
                        dismiss()
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? ProfileDiscardListener
    }
}


class ProfileEditorActivity : AppCompatActivity(), ProfileDiscardDialog.ProfileDiscardListener {
    private lateinit var circleBar: ColorCircleBar
    private lateinit var profileText: EditText
    private lateinit var colorBar: ColorSeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_profile_editor)
        setSupportActionBar(findViewById(R.id.toolbar))

        // Add back arrow to toolbar (shouldn't it be automatic???)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setDisplayShowHomeEnabled(true)
        }

        circleBar = findViewById(R.id.colorCircleBar)
        profileText = findViewById(R.id.profileNameEdit)
        val testText = findViewById<TextView>(R.id.testText)
        colorBar = findViewById(R.id.colorSeekBar)
        circleBar.onValueChanged = { a, b ->
            testText.text = "$a $b"
            colorBar.setColors(hueColor(a), hueColor(b))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_profile_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                attemptDismiss()
                true
            }
            R.id.action_confirm_profile -> {
                confirmProfile()
                dismiss()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        attemptDismiss()
    }

    override fun onProfileDiscard() {
        dismiss()
    }

    private fun attemptDismiss() {
        val dialog = ProfileDiscardDialog()
        dialog.show(supportFragmentManager, "profileDiscard")
    }

    private fun dismiss() {
        super.onBackPressed()
    }

    private fun confirmProfile() {
        Toast.makeText(this, "Profile created", Toast.LENGTH_SHORT).show()

        val intent = Intent().apply {
            putExtra(PROFILE_NAME_KEY, profileText.text.toString())
            putExtra(PROFILE_MIN_COLOR_KEY, circleBar.getColor0())
            putExtra(PROFILE_MAX_COLOR_KEY, circleBar.getColor1())
            putExtra(PROFILE_PREF_COLOR_KEY, colorBar.getColor())
        }
        setResult(RESULT_OK, intent)
    }

    companion object {
        const val PROFILE_NAME_KEY = "profile_name_key"
        const val PROFILE_MIN_COLOR_KEY = "profile_min_color_key"
        const val PROFILE_MAX_COLOR_KEY = "profile_max_color_key"
        const val PROFILE_PREF_COLOR_KEY = "profile_pref_color_key"
    }
}