package com.mare5x.colorcalendar

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.PaintDrawable
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toRectF
import kotlin.math.max
import kotlin.math.min


// A circle where each color takes an equal amount of the circle.
class ColorCircleDrawable(colors: IntArray) : Drawable() {
    private val paints = Array(colors.size) { i ->
        Paint().apply {
            isAntiAlias = true
            color = colors[i]
        }
    }

    override fun draw(canvas: Canvas) {
        val a = 360f / paints.size
        paints.forEachIndexed { i, paint ->
            canvas.drawArc(bounds.toRectF(), i*a, a, true, paint)
        }
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}


class ColorRectDrawable(color: Int) : PaintDrawable(color) {
    init {
        intrinsicWidth = 9999
        intrinsicHeight = 9999
    }
}

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

    private val borderPaint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.STROKE
        color = Color.RED
        strokeWidth = 4f * resources.displayMetrics.density  // Used as radius, not as width!
    }

    private var badgeDrawable: ColorCircleDrawable? = null

    fun setColor(color: Int) {
        setBackgroundColor(color)
        invalidate()
    }

    fun setBorderColor(color: Int) {
        borderPaint.color = color
        invalidate()
    }

    fun showBadge(colors: IntArray) {
        badgeDrawable = ColorCircleDrawable(colors)
    }

    fun hideBadge() {
        badgeDrawable = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = max(MeasureSpec.getSize(widthMeasureSpec), suggestedMinimumWidth)
        setMeasuredDimension(w, w)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        canvas ?: return

        if (drawBorder) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)
        }

        badgeDrawable?.setBounds(
            (width * 0.75f).toInt(), // left
            0, // top
            (width), // right
            (height * 0.25f).toInt() // bottom
        )
        badgeDrawable?.draw(canvas)
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