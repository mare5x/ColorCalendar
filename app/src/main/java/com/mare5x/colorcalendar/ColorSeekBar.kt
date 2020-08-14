package com.mare5x.colorcalendar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import kotlin.math.roundToInt

fun calcGradientColor(startColor: Int, endColor: Int, x: Float) : Int {
    return Color.rgb((x * 255).roundToInt(), 0, 0)
}

fun createGradientBitmap(width: Int) : Bitmap {
    val pixels = IntArray(width) { i ->
        calcGradientColor(Color.RED, Color.GREEN, i / width.toFloat())
    }
    return Bitmap.createBitmap(pixels, width, 1, Bitmap.Config.ARGB_8888)
}

class ColorSeekBar : androidx.appcompat.widget.AppCompatSeekBar {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private val grad = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.RED, Color.GREEN))

    init {
        val bitmap = createGradientBitmap(1024)
        val drawable = BitmapDrawable(resources, bitmap)

        // progressDrawable = grad
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // background = grad
            background = drawable
        }
    }
}