package com.mare5x.colorcalendar

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.observe
import androidx.navigation.fragment.findNavController

class ColorGridFragment : Fragment() {
    private var grid: ColorGrid? = null
    private lateinit var adapter: ColorRectAdapter
    private lateinit var db: DatabaseHelper
    private lateinit var profile: ProfileEntry

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.color_grid_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.button_first).setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }

        grid = view.findViewById(R.id.colorGrid)
        grid!!.adapter = adapter

        // Structure:
        // Data is stored in the view model. The view model fetches data from the database in
        // a separate coroutine. The view model's live data is observed for changes, which notify
        // the grid adapter with new data. The adapter is given the data and creates the views for
        // grid (RecyclerView).
        val model: ColorGridViewModel by viewModels { ColorGridViewModelFactory(profile, db) }
        model.getEntriesByDay().observe(viewLifecycleOwner) { entries ->
            adapter.dayEntries = entries
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        db = DatabaseHelper(context)
        profile = db.queryProfile(1)
        adapter = ColorRectAdapter(profile)
        grid?.adapter = adapter
    }
}