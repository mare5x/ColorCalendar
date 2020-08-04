package com.mare5x.colorcalendar

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.OnItemClickListener
import android.widget.BaseAdapter
import android.widget.GridView

class ColorRectAdapter(private val ctx : Context) : BaseAdapter() {

    override fun getItem(p0: Int): Any {
        return 0
    }

    override fun getItemId(p0: Int): Long {
        return 0
    }

    override fun getCount(): Int {
        return 100
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

    private val adapter = ColorRectAdapter(context)

    init {
        setAdapter(adapter)

        onItemClickListener = OnItemClickListener { parent, view, position, id ->
            itemClickedListener(position)
        }
    }

    var itemClickedListener: (position: Int) -> Unit = {}
}