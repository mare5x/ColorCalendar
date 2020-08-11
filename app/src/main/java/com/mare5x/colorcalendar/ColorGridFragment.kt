package com.mare5x.colorcalendar

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
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

        view.findViewById<Button>(R.id.button_first).setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }

        grid = view.findViewById(R.id.colorGrid)
        grid!!.adapter = adapter
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        adapter = ColorRectAdapter(DatabaseHelper(context))
        grid?.adapter = adapter
    }
}