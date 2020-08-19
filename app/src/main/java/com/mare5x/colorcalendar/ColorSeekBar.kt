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
import kotlin.math.abs

fun calcGradientColor(startColor: Int, endColor: Int, t: Float) : Int {
    val hsv1 = floatArrayOf(0f, 0f, 0f)
    Color.RGBToHSV(startColor.red, startColor.green, startColor.blue, hsv1)
    val hsv2 = floatArrayOf(0f, 0f, 0f)
    Color.RGBToHSV(endColor.red, endColor.green, endColor.blue, hsv2)

    // TODO try gamma 2.2 space?
    // Hue value is in [0, 360]. The interpolation must take the shortest route -> modulo ...
    if (abs(hsv2[0] - hsv1[0]) > 180) {
        if (hsv1[0] in 0f..180f) {
            hsv2[0] -= 360f
        } else {
            hsv1[0] -= 360f
        }
    }
    val h = ((1.0f - t) * hsv1[0] + t * hsv2[0] + 720f).rem(360f)  // Work-around for negative modulo ...
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

fun createHueGradientBitmap(width: Int) : Bitmap {
    val pixels = IntArray(width) { i ->
        Color.HSVToColor(floatArrayOf(360f * i / width, 1f, 1f))
    }
    return Bitmap.createBitmap(pixels, width, 1, Bitmap.Config.ARGB_8888)
}

class ColorSeekBar : androidx.appcompat.widget.AppCompatSeekBar {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    var startColor: Int = Color.GRAY
    var endColor: Int = Color.GRAY
    var fullHue: Boolean = false

    init {
        updateGradientBackground()
    }

    private fun updateGradientBackground() {
        if (fullHue) {
            setGradientBackground(createHueGradientBitmap(1024))
        } else {
            setGradientBackground(createGradientBitmap(startColor, endColor, 1024))
        }
    }

    private fun setGradientBackground(bitmap: Bitmap) {
        val bg = BitmapDrawable(resources, bitmap)
        // progressDrawable = grad
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            background = bg
        }
    }

    fun setColors(startColor: Int, endColor: Int) {
        fullHue = false
        this.startColor = startColor
        this.endColor = endColor
        updateGradientBackground()
    }

    fun setShowFullHue() {
        fullHue = true
        updateGradientBackground()
    }

    fun getNormProgress(): Float {
        return progress / max.toFloat()
    }

    fun setNormProgress(t: Float) {
        progress = (t * max).toInt()
    }

    fun getColor(): Int {
        if (fullHue) {
            return Color.HSVToColor(floatArrayOf(360f * getNormProgress(), 1f, 1f))
        }
        return calcGradientColor(startColor, endColor, getNormProgress())
    }
}

class ColorPickerBar : ConstraintLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private var colorRect: ColorRect
    private var colorBar: ColorSeekBar

    var onProgressChanged: (value: Float, color: Int) -> Unit = { _, _ ->  }

    init {
        LayoutInflater.from(context).inflate(R.layout.color_picker_view, this, true)

        colorRect = findViewById(R.id.colorRect)
        colorBar = findViewById(R.id.colorSeekBar)
        colorBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateColorRect()
                onProgressChanged(getNormProgress(), getColor())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        updateColorRect()
    }

    fun updateColorRect() {
        colorRect.color = colorBar.getColor()
    }

    fun getNormProgress(): Float = colorBar.getNormProgress()
    fun setNormProgress(t: Float) = colorBar.setNormProgress(t)
    fun getColor() = colorBar.getColor()

    fun setColors(startColor: Int, endColor: Int) {
        colorBar.setColors(startColor, endColor)
        updateColorRect()
    }

    fun showFullHue() {
        colorBar.setShowFullHue()
        updateColorRect()
    }
}
