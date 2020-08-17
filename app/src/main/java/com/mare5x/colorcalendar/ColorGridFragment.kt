package com.mare5x.colorcalendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.observe
import androidx.navigation.fragment.findNavController

class ColorGridFragment : Fragment() {
    private var grid: ColorGrid? = null
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
        // The view model is owned by the parent activity.
        val model: ColorGridViewModel by activityViewModels()
        model.getEntriesByDay().observe(viewLifecycleOwner) { entries ->
            adapter.dayEntries = entries
        }
        model.getLastEntry().observe(viewLifecycleOwner) { entry ->
            if (entry.id != -1L) {
                val profile = model.getProfile().value!!
                val position = calcDayDifference(profile.creationDate, entry.date!!)
                adapter.notifyItemChanged(position)
            }
        }
        model.getProfile().observe(viewLifecycleOwner) { profile ->
            adapter.profile = profile
        }

        adapter = ColorRectAdapter(model.getProfile().value!!)
        grid = view.findViewById(R.id.colorGrid)
        grid!!.adapter = adapter

        view.findViewById<Button>(R.id.button_first).setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }

        adapter.clickListener = { day ->
            val dayEntries = model.getEntriesByDay().value
            Toast.makeText(context, "Day: $day (${dayEntries?.get(day)?.size})", Toast.LENGTH_SHORT).show()
        }
    }
}