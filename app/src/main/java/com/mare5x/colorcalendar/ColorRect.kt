package com.mare5x.colorcalendar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min


class ColorRect : View {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    var drawBorder = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    val borderPaint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.STROKE
        color = Color.RED
        strokeWidth = 4f * resources.displayMetrics.density  // Used as radius, not as width!
    }

    fun setColor(color: Int) {
        setBackgroundColor(color)
        invalidate()
    }

    fun setBorderColor(color: Int) {
        borderPaint.color = color
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = max(MeasureSpec.getSize(widthMeasureSpec), suggestedMinimumWidth)
        setMeasuredDimension(w, w)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        if (drawBorder) {
            canvas?.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)
        }
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