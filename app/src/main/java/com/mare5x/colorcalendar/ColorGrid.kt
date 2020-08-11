package com.mare5x.colorcalendar

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*
import java.util.concurrent.TimeUnit

class ColorRectAdapter(private val db: DatabaseHelper) :
        RecyclerView.Adapter<ColorRectAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val rect: ColorRect

        init {
            v.setOnClickListener { Log.i(TAG, "$adapterPosition clicked") }
            rect = v.findViewById(R.id.colorRect)
        }
    }

    private val profile: ProfileEntry = db.queryProfile(1)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.color_grid_item, parent, false)
        v.setOnClickListener { Log.i(TAG, "onCreateViewHolder: ") }
        return ViewHolder(v)
    }

    override fun getItemCount(): Int {
        return TimeUnit.DAYS.convert(Date().time - profile.creationDate!!.time, TimeUnit.MILLISECONDS).toInt()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.rect.color = Color.BLUE
    }

    companion object {
        private const val TAG = "ColorRectAdapter"
    }
}

class ColorGrid : RecyclerView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    init {
        layoutManager = GridLayoutManager(context, 7)

    }
}