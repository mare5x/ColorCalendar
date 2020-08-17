package com.mare5x.colorcalendar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.SeekBar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red

fun calcGradientColor(startColor: Int, endColor: Int, t: Float) : Int {
    val hsv1 = floatArrayOf(0f, 0f, 0f)
    Color.RGBToHSV(startColor.red, startColor.green, startColor.blue, hsv1)
    val hsv2 = floatArrayOf(0f, 0f, 0f)
    Color.RGBToHSV(endColor.red, endColor.green, endColor.blue, hsv2)

    // return Color.rgb((x * 255).roundToInt(), 0, 0)
    val h = (1.0f - t) * hsv1[0] + t * hsv2[0]
    val s = (1.0f - t) * hsv1[1] + t * hsv2[1]
    val v = (1.0f - t) * hsv1[2] + t * hsv2[2]
    val hsv = floatArrayOf(h, s, v)
    return Color.HSVToColor(hsv)
}

fun createGradientBitmap(startColor: Int, endColor: Int, width: Int) : Bitmap {
    val pixels = IntArray(width) { i ->
        calcGradientColor(startColor, endColor, i / width.toFloat())
    }
    return Bitmap.createBitmap(pixels, width, 1, Bitmap.Config.ARGB_8888)
}

class ColorSeekBar : androidx.appcompat.widget.AppCompatSeekBar {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    var startColor: Int = Color.GRAY
    var endColor: Int = Color.GRAY

    init {
        updateGradientBackground()
    }

    private fun updateGradientBackground() {
        val bg = createGradientDrawable()

        // progressDrawable = grad
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            background = bg
        }
    }

    private fun createGradientDrawable(): BitmapDrawable {
        val bitmap = createGradientBitmap(startColor, endColor, 1024)
        return BitmapDrawable(resources, bitmap)
    }

    fun setColors(startColor: Int, endColor: Int) {
        this.startColor = startColor
        this.endColor = endColor
        updateGradientBackground()
    }
}

class ColorPickerBar : ConstraintLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private var colorRect: ColorRect
    private var colorBar: ColorSeekBar

    init {
        LayoutInflater.from(context).inflate(R.layout.color_picker_view, this, true)

        colorRect = findViewById(R.id.colorRect)
        colorBar = findViewById(R.id.colorSeekBar)
        colorBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) =
                updateColorRect(progress)
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        updateColorRect(colorBar.progress)
    }

    private fun updateColorRect() {
        colorRect.color = calcGradientColor(colorBar.startColor, colorBar.endColor, getNormProgress())
    }

    fun updateColorRect(progress: Int) {
        val x = progress / colorBar.max.toFloat()
        colorRect.color = calcGradientColor(colorBar.startColor, colorBar.endColor, x)
    }

    fun getNormProgress(): Float {
        return colorBar.progress / colorBar.max.toFloat()
    }

    fun setColors(startColor: Int, endColor: Int) {
        colorBar.setColors(startColor, endColor)
        updateColorRect()
    }
}
