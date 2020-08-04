package com.mare5x.colorcalendar

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.View
import kotlin.math.max


class ColorRect(context: Context?) : View(context) {
    /*
    init {
        // ignored :)
        // layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
    */

    private var rectDrawable = ShapeDrawable(RectShape()).apply {
        paint.color = color
    }

    var color : Int = 0
        set(value) {
            field = value
            rectDrawable.paint.color = value
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // height is 0 :)
        // this is stupid :)
        // android is great :)
        val w = max(MeasureSpec.getSize(widthMeasureSpec), suggestedMinimumWidth)
        setMeasuredDimension(w, w)
    }

    override fun onDraw(canvas: Canvas) {
        rectDrawable.setBounds(0, 0, width, height)
        rectDrawable.draw(canvas)
    }
}
