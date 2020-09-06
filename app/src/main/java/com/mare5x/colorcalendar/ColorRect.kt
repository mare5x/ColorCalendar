package com.mare5x.colorcalendar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min


// TODO simplify ...
class ColorRect : View {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private var rectDrawable = ShapeDrawable(RectShape()).apply {
        paint.color = color
    }

    var color : Int = 0
        set(value) {
            field = value
            rectDrawable.paint.color = value
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = max(MeasureSpec.getSize(widthMeasureSpec), suggestedMinimumWidth)
        setMeasuredDimension(w, w)
    }

    override fun onDraw(canvas: Canvas) {
        rectDrawable.setBounds(0, 0, width, height)
        rectDrawable.draw(canvas)
    }
}

class ColorRectButton : androidx.appcompat.widget.AppCompatButton {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    var color : Int = Color.GRAY
        set(value) {
            field = value
            setBackgroundColor(value)
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = min(MeasureSpec.getSize(widthMeasureSpec), suggestedMinimumWidth)
        setMeasuredDimension(w, w)
    }
}