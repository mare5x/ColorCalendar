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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

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
        val dayPosition = args.getInt(POSITION_KEY)

        val profilesViewModel: ProfilesViewModel by activityViewModels()
        // Get the parent view model created by ColorGridFragment, to access the entry data.
        val entryViewModel: ColorGridViewModel by viewModels(
            ownerProducer = { requireParentFragment() }
        )

        val profile = profilesViewModel.getProfile(profileId)!!
        val entries = entryViewModel.getEntriesByDay().value!![dayPosition]

        val adapter = EntryAdapter(entries, profile)
        val viewer = view.findViewById<EntryViewer>(R.id.entryViewer)
        viewer.adapter = adapter
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
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
        setHasFixedSize(true)
    }
}