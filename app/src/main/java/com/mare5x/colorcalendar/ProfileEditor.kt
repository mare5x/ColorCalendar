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
import android.view.*
import android.widget.DatePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.mare5x.colorcalendar.databinding.ActivityProfileEditorBinding
import com.mare5x.colorcalendar.databinding.DialogColorPickerBinding
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
        val args = requireArguments()
        val year = args.getInt(YEAR_KEY)
        val month = args.getInt(MONTH_KEY)
        val day = args.getInt(DAY_KEY)
        return DatePickerDialog(requireContext(), listener, year, month, day).also { dialog ->
            // Don't allow profiles in the future.
            dialog.datePicker.maxDate = Calendar.getInstance().timeInMillis
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as DatePickerDialog.OnDateSetListener
    }

    companion object {
        const val YEAR_KEY = "YEAR_KEY"
        const val MONTH_KEY = "MONTH_KEY"
        const val DAY_KEY = "DAY_KEY"

        fun create(year: Int, month: Int, dayOfMonth: Int): DatePickerFragment {
            val fragment = DatePickerFragment()
            fragment.arguments = Bundle().apply {
                putInt(YEAR_KEY, year)
                putInt(MONTH_KEY, month)
                putInt(DAY_KEY, dayOfMonth)
            }
            return fragment
        }
    }
}


class ColorPickerFragment : DialogFragment() {
    interface ColorPickerListener {
        fun onColorConfirm(color: Int)
        fun onColorCancel() { }
    }

    private lateinit var listener: ColorPickerListener
    private var _binding: DialogColorPickerBinding? = null
    private val binding get() = _binding!!

    private val hsv = floatArrayOf(0f, 1f, 1f)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = DialogColorPickerBinding.inflate(inflater, container, false)
        return _binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireArguments().let {
            Color.colorToHSV(it.getInt(COLOR_KEY), hsv)
        }

        binding.colorPickerCircle.setThumbColor(0, Color.HSVToColor(hsv))
        binding.colorPickerCircle.onValueChanged = { thumbs ->
            thumbs.first().let {
                hsv[0] = it.angleProgress * 360f
                hsv[1] = it.radiusProgress
            }
            updateBarColors()
        }

        // Modify 'value' part of HSV
        binding.colorPickerBar.setIsLinear(true)
        binding.colorPickerBar.setNormProgress(hsv[2])
        binding.colorPickerBar.onValueChanged = { value, _ ->
            hsv[2] = value
        }

        binding.cancelButton.setOnClickListener {
            listener.onColorCancel()
            if (showsDialog)
                dismiss()
        }
        binding.confirmButton.setOnClickListener {
            listener.onColorConfirm(Color.HSVToColor(hsv))
            if (showsDialog)
                dismiss()
        }

        updateBarColors()
    }

    private fun updateBarColors() {
        // NOTE: This doesn't work! It just becomes Color.BLACK!
        // val blackHSV = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], 0f))
        // HSV mixing would then interpolate both Hue and Sat instead of just value.
        // However, changing just the Value is equivalent to RGB mixing between Black and the full HSV color.
        // This is done using mixColors()
        binding.colorPickerBar.setColors(Color.BLACK, binding.colorPickerCircle.getThumbColor(0))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as ColorPickerListener
    }

    companion object {
        private const val COLOR_KEY = "color_key"

        fun create(color: Int): ColorPickerFragment {
            val fragment = ColorPickerFragment()
            fragment.arguments = Bundle().apply {
                putInt(COLOR_KEY, color)
            }
            return fragment
        }
    }
}


class ProfileEditorActivity : AppCompatActivity(), ProfileDiscardDialog.ProfileDiscardListener, DatePickerDialog.OnDateSetListener, ColorPickerFragment.ColorPickerListener {
    private lateinit var binding: ActivityProfileEditorBinding
    private lateinit var circleBar: ColorCircleBar
    private lateinit var colorBar: ColorSeekBar2

    private var profileId: Long = -1L  // Used when editing profile.
    private var profileCreationDate: Long = -1L
    private var prefColor: Int = Color.GRAY
    private var profileType: ProfileType = ProfileType.CIRCLE_SHORT
        set(value) {
            field = value
            circleBar.setProfileType(value)
            colorBar.profileType = value
            when (value) {
                ProfileType.CIRCLE_SHORT -> binding.circleSwitch.isChecked = false
                ProfileType.CIRCLE_LONG -> binding.circleSwitch.isChecked = true
                else -> TODO()
            }
            updateUIColor()
        }
    private var profileFlags: Int = 0
        set(value) {
            field = value
            when {
                value hasFlag ProfileFlag.FREE_PREF_COLOR -> {
                    binding.barRadioButton.isChecked = false
                    binding.colorSeekBar.isEnabled = false
                    binding.freeRadioButton.isChecked = true
                }
                value hasFlagNot ProfileFlag.FREE_PREF_COLOR -> {
                    binding.barRadioButton.isChecked = true
                    binding.colorSeekBar.isEnabled = true
                    binding.freeRadioButton.isChecked = false
                }
            }
            updateUIColor()
        }
    private var forceSelection = false  // For first profile

    private var year: Int = 0
    private var month: Int = 0
    private var dayOfMonth: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        forceSelection = intent.getBooleanExtra(FORCE_SELECTION_KEY, false)

        // Add back arrow to toolbar (shouldn't it be automatic???)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(!forceSelection)
            actionBar.setDisplayShowHomeEnabled(!forceSelection)
        }

        circleBar = findViewById(R.id.colorCircleBar)
        colorBar = findViewById(R.id.colorSeekBar)
        circleBar.onValueChanged = { _, _ ->
            colorBar.setColors(circleBar.getColor0(), circleBar.getColor1())
            updateUIColor()
        }
        colorBar.onValueChanged = { _, _ ->
            updateUIColor()
        }
        binding.dateButton.setOnClickListener {
            DatePickerFragment.create(year, month, dayOfMonth).show(supportFragmentManager, "datePicker")
        }
        binding.circleSwitch.setOnCheckedChangeListener { _, isChecked ->
            profileType = when (isChecked) {
                false -> ProfileType.CIRCLE_SHORT
                true -> ProfileType.CIRCLE_LONG
            }
        }
        binding.barRadioButton.setOnClickListener {
            profileFlags = profileFlags.setFlag0(ProfileFlag.FREE_PREF_COLOR)
        }
        binding.freeRadioButton.setOnClickListener {
            profileFlags = profileFlags.setFlag1(ProfileFlag.FREE_PREF_COLOR)
        }

        binding.profileColorButton.setOnClickListener {
            ColorPickerFragment.create(prefColor).show(supportFragmentManager, "colorPicker")
        }

        profileId = intent.getLongExtra(PROFILE_ID_KEY, -1L)
        intent.getSerializableExtra(PROFILE_TYPE_KEY).let { type ->
            val t = savedInstanceState?.getSerializable(PROFILE_TYPE_KEY) ?: type
            if (t != null)
                profileType = t as ProfileType
        }

        // NOTE: since time is measured since 1970, dates before that are negative!
        intent.getLongExtra(PROFILE_CREATION_DATE_KEY, -1L).let { date ->
            profileCreationDate = date
            val d =
                if (date == -1L) Calendar.getInstance()
                else Calendar.getInstance().apply { timeInMillis = date }
            val year = savedInstanceState?.getInt(YEAR_KEY, d.get(Calendar.YEAR)) ?: d.get(Calendar.YEAR)
            val month = savedInstanceState?.getInt(MONTH_KEY, d.get(Calendar.MONTH)) ?: d.get(Calendar.MONTH)
            val day = savedInstanceState?.getInt(DAY_KEY, d.get(Calendar.DAY_OF_MONTH)) ?: d.get(Calendar.DAY_OF_MONTH)
            onDateSet(null, year, month, day)
        }

        intent.getStringExtra(PROFILE_NAME_KEY).let { name ->
            if (name != null) {
                binding.profileNameEdit.setText(name)
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

        intent.getIntExtra(PROFILE_FLAGS_KEY, 0).let { flags ->
            profileFlags = savedInstanceState?.getInt(PROFILE_FLAGS_KEY, flags) ?: flags
        }

        intent.getIntExtra(PROFILE_PREF_COLOR_KEY, colorBar.getColor()).let { color ->
            val c = savedInstanceState?.getInt(PROFILE_PREF_COLOR_KEY, color) ?: color
            prefColor = c
            binding.profileColorButton.color = c
            if (profileFlags hasFlagNot ProfileFlag.FREE_PREF_COLOR) {
                colorBar.setNormProgress(calcGradientProgress(circleBar.getColor0(), circleBar.getColor1(), c, profileType))
            }
        }

        updateUIColor()
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
            putExtra(PROFILE_NAME_KEY, binding.profileNameEdit.text.toString())
            putExtra(PROFILE_MIN_COLOR_KEY, circleBar.getColor0())
            putExtra(PROFILE_MAX_COLOR_KEY, circleBar.getColor1())
            putExtra(PROFILE_PREF_COLOR_KEY,
                if (profileFlags hasFlag ProfileFlag.FREE_PREF_COLOR) prefColor
                else colorBar.getColor())
            putExtra(PROFILE_CREATION_DATE_KEY, profileCreationDate)
            putExtra(PROFILE_TYPE_KEY, profileType)
            putExtra(PROFILE_FLAGS_KEY, profileFlags)
        }
        setResult(RESULT_OK, intent)
    }

    private fun updateUIColor() {
        if (profileFlags hasFlag ProfileFlag.FREE_PREF_COLOR) {
            setUIColor(prefColor)
        } else {
            setUIColor(colorBar.getColor())
        }
    }

    private fun setUIColor(color: Int) {
        supportActionBar?.setBackgroundDrawable(ColorDrawable(color))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = dimColor(color, 0.8f)
        }
    }

    override fun onDateSet(view: DatePicker?, year: Int, month: Int, dayOfMonth: Int) {
        // Store selected date, so that we can reopen the date picker dialog at the saved date.
        this.year = year
        this.month = month
        this.dayOfMonth = dayOfMonth

        // month \in [0, 11]
        profileCreationDate = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
        }.timeInMillis
        binding.dateButton.text = DateFormat.getDateInstance().format(Date(profileCreationDate))
    }

    override fun onColorConfirm(color: Int) {
        prefColor = color
        binding.profileColorButton.color = color
        profileFlags = profileFlags.setFlag1(ProfileFlag.FREE_PREF_COLOR)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // So that the set date is not lost on configuration change.
        outState.apply {
            putInt(YEAR_KEY, year)
            putInt(MONTH_KEY, month)
            putInt(DAY_KEY, dayOfMonth)
            putSerializable(PROFILE_TYPE_KEY, profileType)
            putInt(PROFILE_FLAGS_KEY, profileFlags)
            putInt(PROFILE_PREF_COLOR_KEY, prefColor)
        }
    }

    companion object {
        const val YEAR_KEY = "YEAR_KEY"
        const val MONTH_KEY = "MONTH_KEY"
        const val DAY_KEY = "DAY_KEY"

        const val FORCE_SELECTION_KEY = "force_selection_key"
        const val PROFILE_ID_KEY = "profile_id_key"
        const val PROFILE_NAME_KEY = "profile_name_key"
        const val PROFILE_MIN_COLOR_KEY = "profile_min_color_key"
        const val PROFILE_MAX_COLOR_KEY = "profile_max_color_key"
        const val PROFILE_PREF_COLOR_KEY = "profile_pref_color_key"
        const val PROFILE_CREATION_DATE_KEY = "profile_creation_date_key"
        const val PROFILE_TYPE_KEY = "profile_type_key"
        const val PROFILE_FLAGS_KEY = "profile_flags_key"
    }
}