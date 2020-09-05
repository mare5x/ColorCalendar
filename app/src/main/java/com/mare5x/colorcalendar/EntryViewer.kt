package com.mare5x.colorcalendar

import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.TimePicker
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import java.util.*

class EntryAdapter(
    private val entries: List<Entry>,
    private val profile: ProfileEntry,
    private val onAddEntryClicked: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    class EntryViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val colorItem: ColorRect = v.findViewById(R.id.colorRect)
        val entryText: TextView = v.findViewById(R.id.entryText)
    }

    // TODO style this button
    class AdderViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val button: Button = v.findViewById(R.id.addButton)
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
                holder.colorItem.color = calcGradientColor(profile.minColor, profile.maxColor, entry.value)
                holder.entryText.text = entry.date.toString()
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_entry_viewer, container, false)
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

        adapter = EntryAdapter(entries, profile) {
            val dialog = EntryEditorDialog.create(profile)
            dialog.show(childFragmentManager, "EntryEditorDialog")
        }
        val viewer = view.findViewById<EntryViewer>(R.id.entryViewer)
        viewer.adapter = adapter

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
                adapter.notifyItemRemoved(position)
                entriesChanged = true

                val snackbar = Snackbar.make(view, "Item removed", Snackbar.LENGTH_LONG)
                    .setAction("UNDO") {
                        entries.add(position, entry)
                        adapter.notifyItemInserted(position)
                    }
                snackbar.show()
            }
        })
        touchHelper.attachToRecyclerView(viewer)
    }

    override fun onDestroy() {
        super.onDestroy()

        if (entriesChanged) {
            entriesViewModel.setDayEntries(dayPosition, entries)
        }
    }

    override fun onEntryCancel() {}

    override fun onEntryConfirm(value: Float, hourOfDay: Int, minute: Int) {
        val t = Calendar.getInstance().apply {
            time = profile.creationDate
            set(Calendar.SECOND, 0)
            set(Calendar.MINUTE, minute)
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, dayPosition)
        }
        val entry = Entry(
            profile = this.profile,
            value = value,
            date = t.time
        )
        entries.add(entry)
        entriesChanged = true
        adapter.notifyItemInserted(entries.size - 1)
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

class EntryEditorDialog : DialogFragment(), TimePickerDialog.OnTimeSetListener {
    interface EntryEditorListener {
        fun onEntryCancel()
        fun onEntryConfirm(value: Float, hourOfDay: Int, minute: Int)
    }

    private var listener: EntryEditorListener? = null
    private var hourOfDay: Int = 0
    private var minute: Int = 0

    private lateinit var timeText: TextView

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
        return inflater.inflate(R.layout.dialog_entry_editor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        val minColor = args.getInt(LOW_COLOR_KEY)
        val maxColor = args.getInt(HIGH_COLOR_KEY)

        val colorPickerBar = view.findViewById<ColorPickerBar>(R.id.colorPickerBar)
        colorPickerBar.setColors(minColor, maxColor)

        timeText = view.findViewById(R.id.timeText)

        val cancelButton = view.findViewById<Button>(R.id.cancelButton)
        cancelButton.setOnClickListener {
            listener?.onEntryCancel()
            if (showsDialog)
                dismiss()
        }
        val confirmButton = view.findViewById<Button>(R.id.confirmButton)
        confirmButton.setOnClickListener {
            listener?.onEntryConfirm(colorPickerBar.getNormProgress(), hourOfDay, minute)
            if (showsDialog)
                dismiss()
        }
        val timePickerButton = view.findViewById<Button>(R.id.timePickerButton)
        timePickerButton.setOnClickListener {
            TimePickerFragment().show(childFragmentManager, "timePicker")
        }

        onTimeSet(null, hourOfDay, minute)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Dialog can be spawned either by a fragment/dialog or the main activity.
        if (parentFragment is EntryEditorListener) {
            listener = parentFragment as EntryEditorListener
        } else {
            listener = context as? EntryEditorListener
        }
    }

    override fun onTimeSet(view: TimePicker?, hourOfDay: Int, minute: Int) {
        this.hourOfDay = hourOfDay
        this.minute = minute
        timeText.text = resources.getString(R.string.entry_time, hourOfDay, minute)
    }

    companion object {
        const val TAG = "EntryEditorDialog"

        const val LOW_COLOR_KEY = "LOW_COLOR"
        const val HIGH_COLOR_KEY = "HIGH_COLOR_KEY"

        fun create(profile: ProfileEntry): EntryEditorDialog {
            val fragment = EntryEditorDialog()
            fragment.arguments = Bundle().apply {
                putInt(LOW_COLOR_KEY, profile.minColor)
                putInt(HIGH_COLOR_KEY, profile.maxColor)
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
