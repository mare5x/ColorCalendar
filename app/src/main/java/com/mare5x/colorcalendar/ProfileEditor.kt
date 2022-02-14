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
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.widget.DatePicker
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.preference.*
import com.mare5x.colorcalendar.databinding.DialogColorPickerBinding
import com.mare5x.colorcalendar.databinding.DialogTwoColorPickerBinding
import com.mare5x.colorcalendar.databinding.ProfileEditorActivityBinding
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


class TwoColorPickerFragment : DialogFragment() {
    interface ColorPickerListener {
        fun onColorConfirm(colors: List<Int>, barColor: Int, typeFlags: Int)
    }

    private lateinit var listener: ColorPickerListener
    private var _binding: DialogTwoColorPickerBinding? = null
    private val binding get() = _binding!!

    private var typeFlags: Int = 0
        set(value) {
            field = value
            binding.colorCircleBar.setTypeFlags(value)
            binding.colorBar.setTypeFlags(value)
            binding.circleSwitch.isChecked = value hasFlag ProfileFlag.CIRCLE_LONG
            updateBarColors()
        }

    private val hsvs: List<FloatArray> = listOf(floatArrayOf(0f, 1f, 1f), floatArrayOf(0.25f, 1f, 1f))

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = DialogTwoColorPickerBinding.inflate(inflater, container, false)
        return _binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireArguments().let {
            it.getIntArray(COLORS_KEY)?.forEachIndexed { i, color ->
                Color.colorToHSV(color, hsvs[i])
            }
            typeFlags = it.getInt(PROFILE_FLAGS_KEY)
            binding.colorBar.setNormProgress(it.getFloat(BAR_VALUE_KEY))
        }

        hsvs.forEachIndexed { i, hsv ->
            binding.colorCircleBar.setThumbColor(i, Color.HSVToColor(hsv))
        }
        binding.colorCircleBar.onValueChanged = { thumbs ->
            hsvs.forEachIndexed { i, hsv ->
                hsv[0] = thumbs[i].angleProgress * 360f
                hsv[1] = thumbs[i].radiusProgress
            }
            updateBarColors()
        }
        binding.circleSwitch.setOnCheckedChangeListener { _, isChecked ->
            typeFlags = typeFlags.setFlag(ProfileFlag.CIRCLE_LONG, isChecked)
        }

        binding.cancelButton.setOnClickListener {
            if (showsDialog)
                dismiss()
        }
        binding.confirmButton.setOnClickListener {
            listener.onColorConfirm(hsvs.map { Color.HSVToColor(it) }, binding.colorBar.getColor(), typeFlags)
            if (showsDialog)
                dismiss()
        }

        updateBarColors()
    }

    private fun updateBarColors() {
        binding.colorBar.setColors(Color.HSVToColor(hsvs[0]), Color.HSVToColor(hsvs[1]))
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
        private const val COLORS_KEY = "colors_key"
        private const val PROFILE_FLAGS_KEY = "type_key"
        private const val BAR_VALUE_KEY = "bar_value"

        fun create(colors: IntArray, typeFlags: Int, barValue: Float): TwoColorPickerFragment {
            val fragment = TwoColorPickerFragment()
            fragment.arguments = Bundle().apply {
                putIntArray(COLORS_KEY, colors)
                putSerializable(PROFILE_FLAGS_KEY, typeFlags)
                putFloat(BAR_VALUE_KEY, barValue)
            }
            return fragment
        }
    }
}


class ProfileEditorViewModel(var profileId: Long, db: DatabaseHelper) : ViewModel() {
    // var profileId = -1L
    val name: MutableLiveData<String> = MutableLiveData()
    val profileDate: MutableLiveData<Date> = MutableLiveData()
    val minColor: MutableLiveData<Int> = MutableLiveData()
    val maxColor: MutableLiveData<Int> = MutableLiveData()
    val prefColor: MutableLiveData<Int> = MutableLiveData()
    val bannerColor: MutableLiveData<Int?> = MutableLiveData()
    val profileType: MutableLiveData<ProfileType> = MutableLiveData()
    val profileFlags: MutableLiveData<Int> = MutableLiveData()

    var prefBarProgress: Float = 0.8f

    init {
        if (profileId == -1L) {
            initDefaultProfile()
        } else {
            fromProfile(db.queryProfile(profileId))
        }
    }

    private fun fromProfile(profile: ProfileEntry) {
        Log.i("ProfileEditorViewModel", "fromProfile: $profile")
        profileId = profile.id
        name.value = profile.name
        profileDate.value = profile.creationDate
        minColor.value = profile.minColor
        maxColor.value = profile.maxColor
        prefColor.value = profile.prefColor
        bannerColor.value = profile.bannerColor
        profileType.value = profile.type
        profileFlags.value = profile.flags

        prefBarProgress = calcGradientProgress(profile.minColor, profile.maxColor, profile.prefColor, profile.flags)
    }

    fun makeProfile(): ProfileEntry {
        val profile = ProfileEntry()
        profile.id = profileId
        profile.name = name.value ?: profile.name
        profile.minColor = minColor.value ?: profile.minColor
        profile.maxColor = maxColor.value ?: profile.maxColor
        profile.prefColor = prefColor.value ?: profile.prefColor
        profile.bannerColor = bannerColor.value ?: profile.bannerColor
        profile.creationDate = profileDate.value ?: profile.creationDate
        profile.type = profileType.value ?: profile.type
        profile.flags = profileFlags.value ?: profile.flags
        return profile
    }

    // Defaults for when creating a new profile
    private fun initDefaultProfile() {
        val profile = ProfileEntry().apply {
            minColor = Color.RED
            maxColor = Color.GREEN
            prefColor = Color.GREEN
        }
        fromProfile(profile)
    }
}

class ProfileEditorViewModelFactory(private val profileId: Long, private val db: DatabaseHelper) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ProfileEditorViewModel(profileId, db) as T
    }
}

// Custom data store instead of shared preferences because the preferences are local for each profile.
class ProfileEditorDataStore(val model: ProfileEditorViewModel) : PreferenceDataStore() {
    override fun putString(key: String?, value: String?) {
        when (key) {
            "profile_name" -> model.name.value = value ?: ""
        }
    }

    override fun getString(key: String?, defValue: String?): String? {
        return when (key) {
            "profile_name" -> model.name.value
            else -> defValue
        }
    }

    override fun putBoolean(key: String?, value: Boolean) {
        when (key) {
            "profile_banner_color_flag" -> model.profileFlags.value =
                model.profileFlags.value?.setFlag(ProfileFlag.CUSTOM_BANNER, value)
        }
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return when (key) {
            "profile_banner_color_flag" -> model.profileFlags.value!! hasFlag ProfileFlag.CUSTOM_BANNER
            else -> defValue
        }
    }
}

class ColorBarPreference : Preference {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    var colorBar: ColorSeekBar2? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        colorBar = holder.itemView.findViewById(R.id.colorBar)
        val viewModel = (preferenceDataStore as ProfileEditorDataStore).model
        colorBar?.setColors(viewModel.minColor.value!!, viewModel.maxColor.value!!)
        colorBar?.typeFlags = viewModel.profileFlags.value!!
        colorBar?.setNormProgress(viewModel.prefBarProgress)
        // Must be set last
        colorBar?.onValueChanged = { t, color ->
            viewModel.prefBarProgress = t
            viewModel.prefColor.value = color
        }
    }

    fun update(viewModel: ProfileEditorViewModel) {
        colorBar?.setColors(viewModel.minColor.value!!, viewModel.maxColor.value!!)
        colorBar?.typeFlags = viewModel.profileFlags.value!!
        colorBar?.setNormProgress(
            calcGradientProgress(viewModel.minColor.value!!, viewModel.maxColor.value!!,
                viewModel.prefColor.value!!, viewModel.profileFlags.value!!))
    }
}

class ProfileSettingsFragment : PreferenceFragmentCompat() {
    private val viewModel: ProfileEditorViewModel by activityViewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.profile_editor_preferences, rootKey)
        preferenceManager.preferenceDataStore = ProfileEditorDataStore(viewModel)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val profileNamePref = findPreference<EditTextPreference>("profile_name")!!
        profileNamePref.setOnBindEditTextListener { editor ->
            editor.setText(viewModel.name.value)
        }
        viewModel.name.observe(viewLifecycleOwner) { profileName ->
            profileNamePref.summary = profileName
        }

        val profileDatePref = findPreference<Preference>("profile_date")!!
        profileDatePref.setOnPreferenceClickListener {
            val c = Calendar.getInstance().apply { time = viewModel.profileDate.value!! }
            DatePickerFragment
                .create(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
                .show(childFragmentManager, "datePicker")
            true
        }
        viewModel.profileDate.observe(viewLifecycleOwner) { date ->
            profileDatePref.summary = DateFormat.getDateInstance().format(date)
        }

        val profileBannerColor = findPreference<Preference>("profile_banner_color")!!
        profileBannerColor.setOnPreferenceClickListener {
            ColorPickerFragment
                .create(viewModel.bannerColor.value ?: viewModel.prefColor.value!!)
                .show(childFragmentManager, "colorPicker")
            true
        }
        viewModel.bannerColor.observe(viewLifecycleOwner) { color ->
            profileBannerColor.icon = ColorRectDrawable(color ?: viewModel.prefColor.value!!)
        }
        val profileBannerColorFlag = findPreference<CheckBoxPreference>("profile_banner_color_flag")!!
        viewModel.profileFlags.observe(viewLifecycleOwner) { flags ->
            profileBannerColorFlag.isChecked = flags hasFlag ProfileFlag.CUSTOM_BANNER
        }

        val colorBarPreference = findPreference<ColorBarPreference>("profile_color_bar")!!
        colorBarPreference.setOnPreferenceClickListener {
            val colors = intArrayOf(viewModel.minColor.value!!, viewModel.maxColor.value!!)
            TwoColorPickerFragment
                .create(colors, viewModel.profileFlags.value ?: 0, viewModel.prefBarProgress)
                .show(childFragmentManager, "colorPicker")
            true
        }
        viewModel.minColor.observe(viewLifecycleOwner) {
            colorBarPreference.update(viewModel)
        }
        viewModel.maxColor.observe(viewLifecycleOwner) {
            colorBarPreference.update(viewModel)
        }
        viewModel.profileFlags.observe(viewLifecycleOwner) {
            colorBarPreference.update(viewModel)
        }
        viewModel.prefColor.observe(viewLifecycleOwner) { color ->
            if (viewModel.bannerColor.value == null) {
                profileBannerColor.icon = ColorRectDrawable(color)
            }
        }
    }
}

class ProfileEditorActivity : AppCompatActivity(), ProfileDiscardDialog.ProfileDiscardListener, DatePickerDialog.OnDateSetListener, ColorPickerFragment.ColorPickerListener, TwoColorPickerFragment.ColorPickerListener {
    private lateinit var binding: ProfileEditorActivityBinding
    private lateinit var db: DatabaseHelper
    private val viewModel: ProfileEditorViewModel by viewModels()

    private var forceSelection = false  // For first profile

    override fun getDefaultViewModelProviderFactory(): ViewModelProvider.Factory {
        val profileId = intent.getLongExtra(PROFILE_ID_KEY, -1L)
        return ProfileEditorViewModelFactory(profileId, db)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        db = DatabaseHelper(applicationContext)

        super.onCreate(savedInstanceState)
        binding = ProfileEditorActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        forceSelection = intent.getBooleanExtra(FORCE_SELECTION_KEY, false)

        // Add back arrow to toolbar (shouldn't it be automatic???)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(!forceSelection)
            actionBar.setDisplayShowHomeEnabled(!forceSelection)
        }

        val profileId = intent.getLongExtra(PROFILE_ID_KEY, -1L)
        if (profileId == -1L) {
            actionBar?.title = "Create profile"
        } else {
            actionBar?.title = "Edit profile"
        }

        // Ugly... it would have been better to avoid LiveData...
        viewModel.minColor.observe(this) {
            updateUIColor()
        }
        viewModel.maxColor.observe(this) {
            updateUIColor()
        }
        viewModel.profileType.observe(this) {
            updateUIColor()
        }
        viewModel.prefColor.observe(this) {
            updateUIColor()
        }
        viewModel.bannerColor.observe(this) {
            updateUIColor()
        }
        viewModel.profileFlags.observe(this) {
            updateUIColor()
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

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }

    private fun attemptDismiss() {
        val msg = if (viewModel.profileId < 0) "Discard this profile?" else "Discard changes?"
        val dialog = ProfileDiscardDialog.create(msg)
        dialog.show(supportFragmentManager, "profileDiscard")
    }

    private fun dismiss() {
        super.onBackPressed()
    }

    override fun onDateSet(view: DatePicker?, year: Int, month: Int, dayOfMonth: Int) {
        // I don't know how to move this callback into ProfileSettingsFragment :(
        // month \in [0, 11]
        val date = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
        }.time
        viewModel.profileDate.value = date
    }

    private fun confirmProfile() {
        val msg = if (viewModel.profileId < 0) "created" else "updated"
        Toast.makeText(this, """Profile $msg""", Toast.LENGTH_SHORT).show()

        val profile = viewModel.makeProfile()
        if (profile.id < 0) {
            profile.id = db.insertProfile(profile)
        } else {
            db.updateProfile(profile)
        }

        val intent = Intent().apply {
            putExtra(PROFILE_ID_KEY, profile.id)
            putExtra(PROFILE_EDIT_MSG_KEY, msg)
        }
        setResult(RESULT_OK, intent)
    }

    private fun updateUIColor() {
        if (viewModel.profileFlags.value!! hasFlag ProfileFlag.CUSTOM_BANNER) {
            setUIColor(viewModel.bannerColor.value ?: viewModel.prefColor.value!!)
        } else {
            setUIColor(viewModel.prefColor.value!!)
        }
    }

    private fun setUIColor(color: Int) {
        supportActionBar?.setBackgroundDrawable(ColorDrawable(color))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = dimColor(color, 0.8f)
        }
    }

    override fun onColorConfirm(color: Int) {
        // Custom banner callback
        viewModel.profileFlags.value = viewModel.profileFlags.value?.setFlag1(ProfileFlag.CUSTOM_BANNER)
        viewModel.bannerColor.value = color
    }

    override fun onColorConfirm(colors: List<Int>, barColor: Int, typeFlags: Int) {
        // Color range picker callback
        viewModel.minColor.value = colors[0]
        viewModel.maxColor.value = colors[1]
        viewModel.prefColor.value = barColor
        viewModel.profileFlags.value = typeFlags
    }

    companion object {
        const val FORCE_SELECTION_KEY = "force_selection_key"
        const val PROFILE_ID_KEY = "profile_id_key"
        const val PROFILE_EDIT_MSG_KEY = "profile_editor_msg"
    }
}
