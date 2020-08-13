package com.mare5x.colorcalendar

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        fetchGridData(profile)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        db = DatabaseHelper(context)
        profile = db.queryProfile(1)
        adapter = ColorRectAdapter(profile)
        grid?.adapter = adapter
    }

    private fun fetchGridData(profile: ProfileEntry) {
        lifecycleScope.launch(Dispatchers.IO) {
            val entries = db.queryAllEntries(profile)
            val dayEntries = Array<MutableList<Entry>>(adapter.itemCount) { mutableListOf() }
            entries.forEach {
                if (it.date != null) {
                    val day = calcDayDifference(profile.creationDate!!, it.date!!)
                    dayEntries[day].add(it)
                }
            }
            withContext(Dispatchers.Main) {
                adapter.dayEntries = dayEntries
            }
        }

        /*
        val entries = db.queryAllEntries(profile)
        val dayEntries = Array<MutableList<Entry>>(adapter.itemCount) { mutableListOf() }
        entries.forEach {
            if (it.date != null) {
                val day = calcDayDifference(profile.creationDate!!, it.date!!)
                dayEntries[day].add(it)
            }
        }
        adapter.dayEntries = dayEntries
        */
    }
}