package com.mare5x.colorcalendar

import ProfilesViewModel
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar

class EntryAdapter(
    private val entries: List<Entry>,
    private val profile: ProfileEntry
) : RecyclerView.Adapter<EntryAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val colorItem: ColorRect
        val entryText: TextView

        init {
            colorItem = v.findViewById(R.id.colorRect)
            entryText = v.findViewById(R.id.entryText)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.entry_viewer_item, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        holder.colorItem.color = calcGradientColor(profile.minColor, profile.maxColor, entry.value)
        holder.entryText.text = entry.date.toString()
    }

    override fun getItemCount() = entries.size
}

class EntryViewerDialog : DialogFragment() {

    private val profilesViewModel: ProfilesViewModel by activityViewModels()
    // Get the parent view model created by ColorGridFragment, to access the entry data.
    private val entriesViewModel: EntriesViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )
    private lateinit var entries: MutableList<Entry>
    private var dayPosition: Int = 0
    private var entriesChanged: Boolean = false

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

        val profile = profilesViewModel.getProfile(profileId)!!
        // Create a copy of this day's entries. Modify the list and finally update the view model/database
        // with the changes once editing has been finished.
        entries = entriesViewModel.getEntriesByDay().value!![dayPosition].toMutableList()

        val adapter = EntryAdapter(entries, profile)
        val viewer = view.findViewById<EntryViewer>(R.id.entryViewer)
        viewer.adapter = adapter

        // Set up handler for managing swiping to delete items from the list.
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT.or(ItemTouchHelper.LEFT)) {
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

    override fun onStart() {
        super.onStart()

        // TODO correctly size this piece of shit
        // dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onDestroy() {
        super.onDestroy()

        if (entriesChanged) {
            entriesViewModel.setDayEntries(dayPosition, entries)
        }
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