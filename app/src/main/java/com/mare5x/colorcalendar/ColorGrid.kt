package com.mare5x.colorcalendar

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.OnItemClickListener
import android.widget.BaseAdapter
import android.widget.GridView
import java.util.*
import java.util.concurrent.TimeUnit

class ColorRectAdapter(private val ctx: Context, private val db: DatabaseHelper) : BaseAdapter() {

    private val profile: ProfileEntry = db.queryProfile(1)

    override fun getItem(position: Int): Any {
        return 0
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getCount(): Int {
        return TimeUnit.DAYS.convert(Date().time - profile.creationDate!!.time, TimeUnit.MILLISECONDS).toInt()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        if (convertView != null) {
            return convertView
        }

        return ColorRect(ctx).apply { color = Color.parseColor("#ff0000") }
    }
}

class ColorGrid : GridView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    init {
        onItemClickListener = OnItemClickListener { parent, view, position, id ->
            itemClickedListener(position)
        }
    }

    var itemClickedListener: (position: Int) -> Unit = {}
}