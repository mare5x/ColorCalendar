package com.mare5x.colorcalendar

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import java.text.DateFormat
import java.util.*


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
        val msg = arguments?.getString(MSG_KEY, "Discard this profile?")
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.setMessage(msg)
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

    companion object {
        const val MSG_KEY = "msg_key"

        fun create(msg: String): ProfileDiscardDialog {
            val d = ProfileDiscardDialog()
            d.arguments = Bundle().apply {
                putString(MSG_KEY, msg)
            }
            return d
        }
    }
}


class DatePickerFragment : DialogFragment() {
    private lateinit var listener: DatePickerDialog.OnDateSetListener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Use the current time as the default values for the picker
        val c = Calendar.getInstance()
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)
        return DatePickerDialog(requireContext(), listener, year, month, day).also { dialog ->
            // Don't allow profiles in the future.
            dialog.datePicker.maxDate = c.timeInMillis
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as DatePickerDialog.OnDateSetListener
    }
}


class ProfileEditorActivity : AppCompatActivity(), ProfileDiscardDialog.ProfileDiscardListener, DatePickerDialog.OnDateSetListener {
    private lateinit var circleBar: ColorCircleBar
    private lateinit var profileText: EditText
    private lateinit var colorBar: ColorSeekBar2
    private lateinit var dateButton: Button
    private var profileId: Long = -1L  // Used when editing profile.
    private var profileCreationDate: Long = -1L
    private var forceSelection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_profile_editor)
        setSupportActionBar(findViewById(R.id.toolbar))

        forceSelection = intent.getBooleanExtra(FORCE_SELECTION_KEY, false)

        // Add back arrow to toolbar (shouldn't it be automatic???)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(!forceSelection)
            actionBar.setDisplayShowHomeEnabled(!forceSelection)
        }

        circleBar = findViewById(R.id.colorCircleBar)
        profileText = findViewById(R.id.profileNameEdit)
        colorBar = findViewById(R.id.colorSeekBar)
        circleBar.onValueChanged = { _, _ ->
            colorBar.setColors(circleBar.getColor0(), circleBar.getColor1())
            setUIColor(colorBar.getColor())
        }
        colorBar.onValueChanged = { _, prefColor ->
            setUIColor(prefColor)
        }
        dateButton = findViewById(R.id.dateButton)
        dateButton.setOnClickListener {
            DatePickerFragment().show(supportFragmentManager, "datePicker")
        }

        profileId = intent.getLongExtra(PROFILE_ID_KEY, -1L)

        // NOTE: since time is measured since 1970, dates before that are negative!
        intent.getLongExtra(PROFILE_CREATION_DATE_KEY, -1L).let { date ->
            profileCreationDate = date
            val d =
                if (date == -1L) Calendar.getInstance()
                else Calendar.getInstance().apply { timeInMillis = date }
            onDateSet(null, d.get(Calendar.YEAR), d.get(Calendar.MONTH), d.get(Calendar.DAY_OF_MONTH))
        }

        intent.getStringExtra(PROFILE_NAME_KEY).let { name ->
            if (name != null) {
                profileText.setText(name)
            }
        }
        intent.getIntExtra(PROFILE_MIN_COLOR_KEY, circleBar.getColor0()).let { color ->
            circleBar.setColor0(color)
        }
        intent.getIntExtra(PROFILE_MAX_COLOR_KEY, circleBar.getColor1()).let { color ->
            circleBar.setColor1(color)
        }

        colorBar.setNormProgress(0.8f)
        colorBar.setColors(circleBar.getColor0(), circleBar.getColor1())

        intent.getIntExtra(PROFILE_PREF_COLOR_KEY, colorBar.getColor()).let { color ->
            colorBar.setNormProgress(
                calcGradientProgress(circleBar.getColor0(), circleBar.getColor1(), color))
        }

        setUIColor(colorBar.getColor())
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
        if (forceSelection) {
            confirmProfile()
            dismiss()
        } else {
            attemptDismiss()
        }
    }

    override fun onProfileDiscard() {
        dismiss()
    }

    private fun attemptDismiss() {
        val msg = if (profileId < 0) "Discard this profile?" else "Discard changes?"
        val dialog = ProfileDiscardDialog.create(msg)
        dialog.show(supportFragmentManager, "profileDiscard")
    }

    private fun dismiss() {
        super.onBackPressed()
    }

    private fun confirmProfile() {
        Toast.makeText(this, """Profile ${if (profileId < 0) "created" else "updated"}""", Toast.LENGTH_SHORT).show()

        val intent = Intent().apply {
            putExtra(PROFILE_ID_KEY, profileId)
            putExtra(PROFILE_NAME_KEY, profileText.text.toString())
            putExtra(PROFILE_MIN_COLOR_KEY, circleBar.getColor0())
            putExtra(PROFILE_MAX_COLOR_KEY, circleBar.getColor1())
            putExtra(PROFILE_PREF_COLOR_KEY, colorBar.getColor())
            putExtra(PROFILE_CREATION_DATE_KEY, profileCreationDate)
        }
        setResult(RESULT_OK, intent)
    }

    private fun setUIColor(color: Int) {
        supportActionBar?.setBackgroundDrawable(ColorDrawable(color))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val hsv = floatArrayOf(0f, 0f, 0f)
            Color.colorToHSV(color, hsv)
            hsv[2] *= 0.8f
            window.statusBarColor = Color.HSVToColor(hsv)
        }
    }

    override fun onDateSet(view: DatePicker?, year: Int, month: Int, dayOfMonth: Int) {
        // month \in [0, 11]
        profileCreationDate = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
        }.timeInMillis
        dateButton.text = DateFormat.getDateInstance().format(Date(profileCreationDate))
    }

    companion object {
        const val FORCE_SELECTION_KEY = "force_selection_key"
        const val PROFILE_ID_KEY = "profile_id_key"
        const val PROFILE_NAME_KEY = "profile_name_key"
        const val PROFILE_MIN_COLOR_KEY = "profile_min_color_key"
        const val PROFILE_MAX_COLOR_KEY = "profile_max_color_key"
        const val PROFILE_PREF_COLOR_KEY = "profile_pref_color_key"
        const val PROFILE_CREATION_DATE_KEY = "profile_creation_date_key"
    }
}