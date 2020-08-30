package com.mare5x.colorcalendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.observe
import androidx.navigation.fragment.findNavController

class ColorGridFragment : Fragment() {
    private lateinit var grid: ColorGrid
    private lateinit var adapter: ColorRectAdapter

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.color_grid_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Structure:
        // Data is stored in a database. The view model fetches data from the database in
        // a separate coroutine. The view model's live data is observed for changes, which notifies
        // the grid adapter. The adapter is given the data and creates the views for
        // grid (RecyclerView).
        // The view model is scoped to the fragment's lifecycle.
        val gridModel: ColorGridViewModel by viewModels { ColorGridViewModelFactory(DatabaseHelper(view.context)) }

        // Fragment created using companion 'create' function with bundle arguments (profile id).
        val profileId = requireArguments().getLong(PROFILE_ID_KEY)
        gridModel.setProfile(profileId)

        adapter = ColorRectAdapter(gridModel.getProfile().value!!)
        grid = view.findViewById(R.id.colorGrid)
        grid.adapter = adapter

        // TODO
        view.findViewById<Button>(R.id.button_first).setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }

        val mainModel: MainViewModel by activityViewModels()
        mainModel.getInsertedEntry().observe(viewLifecycleOwner) { entry ->
            if (entry.profile!!.id == profileId && entry.id != -1L) {
                val profile = gridModel.getProfile().value!!
                val position = calcDayDifference(profile.creationDate, entry.date!!)
                val entriesByDayData = adapter.dayEntries
                // TODO ensure day list is big enough (midnight ...)
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

        adapter.clickListener = { day ->
            val dayEntries = adapter.dayEntries
            Toast.makeText(context, "Day: $day (${dayEntries[day].size})", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val PROFILE_ID_KEY = "PROFILE_ID"

        fun create(profile: ProfileEntry): ColorGridFragment {
            val fragment = ColorGridFragment()
            fragment.arguments = Bundle().apply { putLong(PROFILE_ID_KEY, profile.id) }
            return fragment
        }
    }
}