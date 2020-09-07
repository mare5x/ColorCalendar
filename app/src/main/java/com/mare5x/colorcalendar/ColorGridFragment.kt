package com.mare5x.colorcalendar

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.observe

class ColorGridFragment : Fragment() {
    private lateinit var grid: ColorGrid
    private lateinit var adapter: ColorRectAdapter

    // Structure:
    // Data is stored in a database. The view model fetches data from the database in
    // a separate coroutine. The view model's live data is observed for changes, which notifies
    // the grid adapter. The adapter is given the data and creates the views for
    // grid (RecyclerView).
    // The view model is scoped to the fragment's lifecycle.
    private lateinit var db: DatabaseHelper
    private val gridModel: EntriesViewModel by viewModels { ColorGridViewModelFactory(db) }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.color_grid_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainModel: MainViewModel by activityViewModels()
        db = mainModel.db

        // Fragment created using companion 'create' function with bundle arguments (profile id).
        val profileId = requireArguments().getLong(PROFILE_ID_KEY)
        gridModel.setProfile(profileId)

        adapter = ColorRectAdapter(gridModel.getProfile().value!!)
        grid = view.findViewById(R.id.colorGrid)
        grid.adapter = adapter

        mainModel.getInsertedEntry().observe(viewLifecycleOwner) { entry ->
            if (entry.profile!!.id == profileId && entry.id != -1L) {
                val profile = gridModel.getProfile().value!!
                val position = calcDayDifference(profile.creationDate, entry.date!!)
                val entriesByDayData = adapter.dayEntries
                ensureEntriesSize()
                if (entriesByDayData.isNotEmpty() && position < entriesByDayData.size) {
                    entriesByDayData[position].add(entry)
                }

                adapter.notifyItemChanged(position)
            }
        }

        gridModel.getEntriesByDay().observe(viewLifecycleOwner) { entries ->
            adapter.dayEntries = entries
        }
        gridModel.getProfile().observe(viewLifecycleOwner) { profile ->
            adapter.profile = profile
        }
        gridModel.getDayChanged().observe(viewLifecycleOwner) { dayPosition ->
            adapter.notifyItemChanged(dayPosition)
        }

        adapter.clickListener = { day ->
            val dayEntries = adapter.dayEntries
            Toast.makeText(context, "Day: $day (${dayEntries[day].size})", Toast.LENGTH_SHORT).show()

            val dialog = EntryViewerDialog.create(adapter.profile, day)
            dialog.show(childFragmentManager, "entryViewer")
        }
    }

    override fun onResume() {
        super.onResume()

        // If the app lives past midnight, the day list must be enlarged.
        ensureEntriesSize()
    }

    fun ensureEntriesSize() {
        val profile = gridModel.getProfile().value
        if (profile != null) {
            val oldSize = adapter.dayEntries.size
            val newSize = getProfileDayAge(profile)
            if (newSize > oldSize) {
                gridModel.ensureEntriesSize(newSize)
                adapter.notifyItemRangeInserted(oldSize - 1, newSize - oldSize)
            }
        }
    }

    companion object {
        const val TAG = "ColorGridFragment"

        const val PROFILE_ID_KEY = "PROFILE_ID"

        fun create(profile: ProfileEntry): ColorGridFragment {
            val fragment = ColorGridFragment()
            fragment.arguments = Bundle().apply { putLong(PROFILE_ID_KEY, profile.id) }
            return fragment
        }
    }
}