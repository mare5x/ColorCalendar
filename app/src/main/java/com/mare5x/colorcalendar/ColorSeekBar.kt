package com.mare5x.colorcalendar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.SeekBar
import androidx.constraintlayout.widget.ConstraintLayout
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

    // min = 0
    fun getRange() = max
}

class ColorPickerBar : ConstraintLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private var colorRect: ColorRect
    private var seekBar: ColorSeekBar

    init {
        LayoutInflater.from(context).inflate(R.layout.color_picker_view, this, true)

        colorRect = findViewById(R.id.colorRect)
        seekBar = findViewById(R.id.colorSeekBar)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) =
                updateColorRect(progress)
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        updateColorRect(seekBar.progress)
    }

    fun updateColorRect(progress: Int) {
        val x = progress / seekBar.max.toFloat()
        colorRect.color = calcGradientColor(Color.RED, Color.GREEN, x)
    }
}
