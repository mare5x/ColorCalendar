package com.mare5x.colorcalendar

import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.TimePicker
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.*

class EntryAdapter(
    private val entries: List<Entry>,
    private val profile: ProfileEntry,
    private val onAddEntryClicked: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val entryDateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // N.B. this can't be the entry id (-1 problem) or index (insertion problem)
    var selectedItem: Entry? = null
    var selectionChanged = false

    inner class EntryViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val colorItem: ColorRect = v.findViewById(R.id.colorRect)
        private val entryText: TextView = v.findViewById(R.id.entryText)

        fun bind(entry: Entry) {
            colorItem.setColor(entry.color ?: calcGradientColor(profile, entry.value))
            entryText.text = entryDateFormat.format(entry.date)
            itemView.isActivated = entry == selectedItem
            itemView.setOnClickListener {
                selectItem(entry)
                selectionChanged = true
            }
        }
    }

    class AdderViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val button: ImageButton = v.findViewById(R.id.addButton)
    }

    fun selectItem(entry: Entry?) {
        selectedItem?.let { prev ->
            prev.flags = prev.flags.setFlag0(EntryFlag.IS_SELECTED)
            entries.indexOf(prev).let {
                if (it != -1) notifyItemChanged(it)
            }
        }
        if (entry != null) {
            entry.flags = entry.flags.setFlag1(EntryFlag.IS_SELECTED)
            entries.indexOf(entry).let {
                if (it != -1) {
                    notifyItemChanged(it)
                    selectedItem = entry
                } else {
                    selectedItem = null
                }
            }
        } else {
            selectedItem = null
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (position == itemCount - 1) return ADDER_TYPE
        return ENTRY_TYPE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ADDER_TYPE -> {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.entry_viewer_new_item, parent, false)
                AdderViewHolder(v)
            }
            else -> {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.entry_viewer_item, parent, false)
                EntryViewHolder(v)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is EntryViewHolder -> {
                val entry = entries[position]
                holder.bind(entry)
            }
            is AdderViewHolder -> {
                holder.button.setOnClickListener {
                    onAddEntryClicked()
                }
            }
        }
    }

    override fun getItemCount() = entries.size + 1

    companion object {
        const val ENTRY_TYPE = 0
        const val ADDER_TYPE = 1
    }
}


class EntryViewerDialog : DialogFragment(), EntryEditorDialog.EntryEditorListener {

    private val profilesViewModel: ProfilesViewModel by activityViewModels()
    // Get the parent view model created by ColorGridFragment, to access the entry data.
    private val entriesViewModel: EntriesViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )
    private lateinit var entries: MutableList<Entry>
    private var dayPosition: Int = 0
    private var entriesChanged: Boolean = false
    private lateinit var profile: ProfileEntry
    private lateinit var adapter: EntryAdapter

    private val titleDateFormat = SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_entry_viewer, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)  // Necessary for low API versions
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        val profileId = args.getLong(PROFILE_ID_KEY)
        dayPosition = args.getInt(POSITION_KEY)

        profile = profilesViewModel.getProfile(profileId)!!
        // Create a copy of this day's entries. Modify the list and finally update the view model/database
        // with the changes once editing has been finished.
        entries = entriesViewModel.getEntriesByDay().value!![dayPosition].toMutableList()
        entries.sortDescending()

        adapter = EntryAdapter(entries, profile) {
            val e = if (entries.isEmpty()) {
                profilesViewModel.getClosestEntry(profile, makeDate(0, 0))
            } else entries.last()
            // Don't compare by e.id because new entries don't have ids yet.
            val dialog = EntryEditorDialog.create(profile, if (e.profile == null) null else e)
            dialog.show(childFragmentManager, "EntryEditorDialog")
        }
        if (entries.size > 0) {
            val entry = entries.find { e ->
                e.flags hasFlag EntryFlag.IS_SELECTED
            }
            adapter.selectItem(entry ?: entries.first())
        }
        val viewer = view.findViewById<EntryViewer>(R.id.entryViewer)
        viewer.adapter = adapter

        view.findViewById<TextView>(R.id.entryViewerTitle).let {
            val t = Calendar.getInstance().apply {
                time = profile.creationDate
                add(Calendar.DAY_OF_MONTH, dayPosition)
            }
            it.text = titleDateFormat.format(t.time)
        }

        // Set up handler for managing swiping to delete items from the list.
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT.or(ItemTouchHelper.LEFT)) {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                // Disable swiping the add entry button.
                if (viewHolder is EntryAdapter.AdderViewHolder) return 0
                return super.getMovementFlags(recyclerView, viewHolder)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val entry = entries[position]
                entries.removeAt(position)
                val selectedItem = adapter.selectedItem
                adapter.notifyItemRemoved(position)
                if (entries.isEmpty()) {
                    adapter.selectItem(null)
                } else if (entry.flags hasFlag EntryFlag.IS_SELECTED) {
                    adapter.selectItem(entries.first())
                }
                entriesChanged = true

                val snackbar = Snackbar.make(view, "Item removed", Snackbar.LENGTH_LONG)
                    .setAction("UNDO") {
                        entries.add(position, entry)
                        adapter.notifyItemInserted(position)
                        adapter.selectItem(selectedItem)
                        viewer.scrollToPosition(position)
                    }
                snackbar.show()
            }
        })
        touchHelper.attachToRecyclerView(viewer)
    }

    override fun onDestroy() {
        super.onDestroy()

        if (entriesChanged || adapter.selectionChanged) {
            entriesViewModel.setDayEntries(dayPosition, entries)
            entriesChanged = false
            adapter.selectionChanged = false
        }
    }

    override fun onPause() {
        super.onPause()

        if (entriesChanged || adapter.selectionChanged) {
            entriesViewModel.setDayEntries(dayPosition, entries)
            entriesChanged = false
            adapter.selectionChanged = false
        }
    }

    private fun makeDate(hourOfDay: Int, minute: Int) : Date {
        val s = Calendar.getInstance().get(Calendar.SECOND)
        val t = Calendar.getInstance().apply {
            time = profile.creationDate
            set(Calendar.SECOND, s)
            set(Calendar.MINUTE, minute)
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            add(Calendar.DAY_OF_MONTH, dayPosition)
        }
        return t.time
    }

    override fun onEntryConfirm(value: Float, hourOfDay: Int, minute: Int) {
        val t = makeDate(hourOfDay, minute)
        val entry = Entry(
            profile = this.profile,
            value = value,
            date = t
        )
        entries.add(entry)
        entriesChanged = true
        adapter.notifyItemInserted(entries.size - 1)
        adapter.selectItem(entry)
    }

    override fun onEntryConfirm(color: Int, hourOfDay: Int, minute: Int) {
        val t = makeDate(hourOfDay, minute)
        val entry = Entry(
            profile = this.profile,
            value = 1f,
            date = t,
            color = color
        )
        entries.add(entry)
        entriesChanged = true
        adapter.notifyItemInserted(entries.size - 1)
        adapter.selectItem(entry)
    }

    companion object {
        const val PROFILE_ID_KEY = "ENTRY_PROFILE_ID"
        const val POSITION_KEY = "ENTRY_POSITION"

        fun create(profile: ProfileEntry, dayPosition: Int): EntryViewerDialog {
            val fragment = EntryViewerDialog()
            fragment.arguments = Bundle().apply {
                    putLong(PROFILE_ID_KEY, profile.id)
                    putInt(POSITION_KEY, dayPosition)
                }
            return fragment
        }
    }
}

class EntryViewer : RecyclerView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    init {
        layoutManager = LinearLayoutManager(context)
        // setHasFixedSize(true)
    }
}

class EntryEditorDialog : DialogFragment(), TimePickerDialog.OnTimeSetListener, ColorPickerDialogFragment.ColorPickerListener {
    interface EntryEditorListener {
        fun onEntryCancel() { }
        fun onEntryConfirm(value: Float, hourOfDay: Int, minute: Int) { }
        fun onEntryConfirm(color: Int, hourOfDay: Int, minute: Int) { }
    }

    private var listener: EntryEditorListener? = null
    private var hourOfDay: Int = 0
    private var minute: Int = 0

    private lateinit var timeButton: Button
    private lateinit var profileType: ProfileType

    init {
        val c = Calendar.getInstance()
        hourOfDay = c.get(Calendar.HOUR_OF_DAY)
        minute = c.get(Calendar.MINUTE)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        profileType = requireArguments().getSerializable(PROFILE_TYPE) as ProfileType
        return when (profileType) {
            ProfileType.TWO_COLOR_CIRCLE, ProfileType.ONE_COLOR_HSV -> inflater.inflate(R.layout.dialog_entry_editor, container, false)
            ProfileType.FREE_COLOR -> inflater.inflate(R.layout.dialog_entry_editor_free, container, false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)  // Necessary for low API versions
    }

    private fun onViewCreatedBar(view: View, savedInstanceState: Bundle?) {
        val args = requireArguments()
        val minColor = args.getInt(LOW_COLOR_KEY)
        val maxColor = args.getInt(HIGH_COLOR_KEY)
        val barValue = args.getFloat(BAR_VALUE_KEY)
        val typeFlags = args.getInt(PROFILE_FLAGS)

        val colorBar = view.findViewById<ColorButtonBar>(R.id.colorBar)
        colorBar.setIsLinear(profileType == ProfileType.ONE_COLOR_HSV)
        colorBar.setColors(minColor, maxColor)
        colorBar.setNormProgress(barValue)
        colorBar.setTypeFlags(typeFlags)
        if (args.containsKey(FORCED_BAR_COLOR)) {
            colorBar.setForcedColor(args.getInt(FORCED_BAR_COLOR))
        }

        // Free color on click
        colorBar.onClick = {
            ColorPickerDialogFragment
                .create(colorBar.getColor())
                .show(childFragmentManager, "colorPicker")
        }

        val confirmButton = view.findViewById<Button>(R.id.confirmButton)
        confirmButton.setOnClickListener {
            if (colorBar.forcedColor != null) {
                listener?.onEntryConfirm(colorBar.forcedColor!!, hourOfDay, minute)
            } else {
                listener?.onEntryConfirm(colorBar.getNormProgress(), hourOfDay, minute)
            }
            if (showsDialog)
                dismiss()
        }
    }

    private fun onViewCreatedCircle(view: View, savedInstanceState: Bundle?) {
        val args = requireArguments()

        val colorBar = view.findViewById<ColorPickerBar>(R.id.colorBar)
        val colorPickerCircle = view.findViewById<HSVCircleBar>(R.id.colorPickerCircle)

        val hsv = floatArrayOf(0f, 1f, 1f)
        Color.colorToHSV(args.getInt(PREFERRED_COLOR), hsv)

        colorPickerCircle.setThumbColor(0, Color.HSVToColor(hsv))
        colorPickerCircle.onValueChanged = { thumbs ->
            thumbs.first().let {
                hsv[0] = it.angleProgress * 360f
                hsv[1] = it.radiusProgress
            }
            colorBar.setColors(Color.BLACK, colorPickerCircle.getThumbColor(0))
        }

        // Modify 'value' part of HSV
        colorBar.setIsLinear(true)
        colorBar.setNormProgress(hsv[2])
        colorBar.onValueChanged = { value, _ ->
            hsv[2] = value
        }
        colorBar.setColors(Color.BLACK, colorPickerCircle.getThumbColor(0))

        val confirmButton = view.findViewById<Button>(R.id.confirmButton)
        confirmButton.setOnClickListener {
            listener?.onEntryConfirm(colorBar.getColor(), hourOfDay, minute)
            if (showsDialog)
                dismiss()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        when (profileType) {
            ProfileType.TWO_COLOR_CIRCLE, ProfileType.ONE_COLOR_HSV -> onViewCreatedBar(view, savedInstanceState)
            ProfileType.FREE_COLOR -> onViewCreatedCircle(view, savedInstanceState)
        }

        savedInstanceState?.let { state ->
            hourOfDay = state.getInt(HOUR_KEY, hourOfDay)
            minute = state.getInt(MINUTE_KEY, minute)
        }

        val cancelButton = view.findViewById<Button>(R.id.cancelButton)
        cancelButton.setOnClickListener {
            listener?.onEntryCancel()
            if (showsDialog)
                dismiss()
        }
        timeButton = view.findViewById(R.id.timePickerButton)
        timeButton.setOnClickListener {
            TimePickerFragment().show(childFragmentManager, "timePicker")
        }

        onTimeSet(null, hourOfDay, minute)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Dialog can be spawned either by a fragment/dialog or the main activity.
        listener = if (parentFragment is EntryEditorListener) {
            parentFragment as EntryEditorListener
        } else {
            context as? EntryEditorListener
        }
    }

    override fun onTimeSet(view: TimePicker?, hourOfDay: Int, minute: Int) {
        this.hourOfDay = hourOfDay
        this.minute = minute
        timeButton.text = resources.getString(R.string.entry_time, hourOfDay, minute)
    }

    override fun onColorConfirm(color: Int) {
        // Callback for freely selectable color
        val colorBar = view?.findViewById<ColorButtonBar>(R.id.colorBar)
        colorBar?.setForcedColor(color)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putInt(HOUR_KEY, hourOfDay)
        outState.putInt(MINUTE_KEY, minute)

        when (profileType) {
            ProfileType.TWO_COLOR_CIRCLE -> {
                val colorBar = view?.findViewById<ColorButtonBar>(R.id.colorBar)
                colorBar?.forcedColor?.let {
                    outState.putInt(FORCED_BAR_COLOR, it)
                }
            }
            else -> {}
        }
    }

    companion object {
        const val TAG = "EntryEditorDialog"

        const val HOUR_KEY = "HOUR_KEY"
        const val MINUTE_KEY = "MINUTE_KEY"
        const val LOW_COLOR_KEY = "LOW_COLOR"
        const val HIGH_COLOR_KEY = "HIGH_COLOR_KEY"
        const val BAR_VALUE_KEY = "BAR_VALUE"
        const val PROFILE_FLAGS = "PROFILE_FLAGS"
        const val PROFILE_TYPE = "PROFILE_TYPE"
        const val PREFERRED_COLOR = "PREFERRED_COLOR_KEY"
        const val FORCED_BAR_COLOR = "FORCED_BAR_COLOR_KEY"

        fun create(profile: ProfileEntry, closestEntry: Entry? = null): EntryEditorDialog {
            val fragment = EntryEditorDialog()
            when (profile.type) {
                ProfileType.TWO_COLOR_CIRCLE, ProfileType.ONE_COLOR_HSV ->
                    fragment.arguments = Bundle().apply {
                        putInt(LOW_COLOR_KEY, profile.minColor)
                        putInt(HIGH_COLOR_KEY, profile.maxColor)
                        putFloat(BAR_VALUE_KEY,
                            if (closestEntry != null) closestEntry.value
                            else calcGradientProgress(profile))
                        if (closestEntry != null && closestEntry.color != null)
                            putInt(FORCED_BAR_COLOR, closestEntry.color!!)
                        putInt(PROFILE_FLAGS, profile.flags)
                        putSerializable(PROFILE_TYPE, profile.type)
                    }
                ProfileType.FREE_COLOR ->
                    fragment.arguments = Bundle().apply {
                        putInt(PREFERRED_COLOR, closestEntry?.color ?: (profile.bannerColor ?: profile.prefColor))
                        putInt(PROFILE_FLAGS, profile.flags)
                        putSerializable(PROFILE_TYPE, profile.type)
                    }
            }
            return fragment
        }
    }
}

class TimePickerFragment : DialogFragment(), TimePickerDialog.OnTimeSetListener {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Use the current time as the default values for the picker
        val c = Calendar.getInstance()
        val hour = c.get(Calendar.HOUR_OF_DAY)
        val minute = c.get(Calendar.MINUTE)
        return TimePickerDialog(activity, this, hour, minute, true)
    }

    override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int) {
        val parent = requireParentFragment() as EntryEditorDialog
        parent.onTimeSet(view, hourOfDay, minute)
    }
}
