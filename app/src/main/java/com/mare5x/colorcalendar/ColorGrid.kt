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
import kotlin.math.roundToInt

fun calcDateDifference(d: Date): Int {
    return TimeUnit.DAYS.convert(Date().time - d.time, TimeUnit.MILLISECONDS).toInt()
}

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
        return ViewHolder(v)
    }

    override fun getItemCount(): Int = 10000 //calcDateDifference(profile.creationDate!!)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Use 'position' as the number of days since profile creation date.

        val date = Calendar.getInstance().apply {
            time = profile.creationDate!!
            add(Calendar.DAY_OF_MONTH, position)
        }.time

        // TODO database operations shouldn't run on the main thread
        val entry = db.queryEntry(profile, date)
        holder.rect.color = if (entry != null) Color.rgb((255 * entry.value).roundToInt(), 0, 0) else Color.GRAY
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