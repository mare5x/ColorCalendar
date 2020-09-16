package com.mare5x.colorcalendar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.PaintDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.MotionEvent.INVALID_POINTER_ID
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import kotlin.math.*

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

fun hueColor(t: Float) = Color.HSVToColor(floatArrayOf(t * 360f, 1.0f, 1.0f))

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

class ColorSeekBar2 : ColorSeekBar {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    init {
        updateGradientBackground()
    }

    override fun updateGradientBackground() {
        val bg = ColorGradientDrawable(startColor, endColor, fullHue)

        // progressDrawable = grad
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            background = bg
        }
    }
}

open class ColorSeekBar : AppCompatSeekBar {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    protected var startColor: Int = Color.GRAY
    protected var endColor: Int = Color.GRAY
    protected var fullHue: Boolean = false

    var onValueChanged: (value: Float, color: Int) -> Unit = { _, _ -> }

    init {
        // updateGradientBackground()

        this.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                onValueChanged(getNormProgress(), getColor())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    protected open fun updateGradientBackground() {
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
            return hueColor(getNormProgress())
        }
        return calcGradientColor(startColor, endColor, getNormProgress())
    }
}

class ColorPickerBar : ConstraintLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private var colorRect: ColorRect
    private var colorBar: ColorSeekBar

    var onValueChanged: (value: Float, color: Int) -> Unit = { _, _ ->  }

    init {
        LayoutInflater.from(context).inflate(R.layout.color_picker_view, this, true)

        colorRect = findViewById(R.id.colorRect)
        colorBar = findViewById(R.id.colorSeekBar)
        colorBar.onValueChanged = { value, color ->
            updateColorRect()
            onValueChanged(value, color)
        }

        updateColorRect()
    }

    private fun updateColorRect() {
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

val HUE_COLORS = intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA)

class ColorGradientDrawable(private var startColor: Int = Color.GRAY,
                            private var endColor: Int = Color.GRAY,
                            private var fullHue: Boolean = false
) : PaintDrawable() {

    init {
        val p = paint
        p.isAntiAlias = true
    }

    override fun draw(canvas: Canvas) {
        paint.shader =
            if (fullHue)
                createHueShader(bounds.width().toFloat())
            else
                createShader(startColor, endColor, bounds.width().toFloat())

        super.draw(canvas)
    }

    private fun createHueShader(width: Float): Shader {
        // The first and final colors must be the same.
        return LinearGradient(0f, 0f, width, 0f, intArrayOf(*HUE_COLORS, HUE_COLORS[0]), null, Shader.TileMode.CLAMP)
    }

    private fun createShader(startColor: Int, endColor: Int, width: Float): Shader {
        val hsv0 = FloatArray(3)
        val hsv1 = FloatArray(3)
        Color.colorToHSV(startColor, hsv0)
        Color.colorToHSV(endColor, hsv1)
        var alpha = hsv0[0] / 360f
        var beta = hsv1[0] / 360f
        val h = 1.0f / 6.0f
        val eps = 0.001f
        // alpha == k0 / 6 + r0
        val r0 = alpha % h
        val k0: Int = (6 * (alpha - r0)).toInt()
        val r1 = beta % h
        val k1 = (6 * (beta - r1)).toInt()

        if (abs(beta - alpha) > 0.5f) {
            if (alpha <= 0.5f) {
                beta -= 1.0f
            } else {
                alpha -= 1.0f
            }
        }
        val delta = abs(beta - alpha)
        val dir: Int = sign(beta - alpha).toInt()


        // If both colors are in the same color strip, just linearly interpolate.
        if (beta >= k0 / 6.0 - eps && beta <= (k0 + 1) / 6.0 + eps) {
            return LinearGradient(0f, 0f, width, 0f, startColor, endColor, Shader.TileMode.CLAMP)
        }

        val n = 2 + abs(if (dir > 0) (k1 - k0) else (k0 - k1))

        val st = if (dir > 0) 0 else 1
        val colors = IntArray(n) { i ->
            when (i) {
                0 -> startColor
                n - 1 -> endColor
                else -> HUE_COLORS[(k0 + i * dir + 12 + st) % 6]
            }
        }
        var prev = 0f
        val offsets = FloatArray(n) { i ->
            val x = when (i) {
                0 -> 0f
                1 -> (if (dir > 0) (h - r0) else r0) / delta
                n - 1 -> 1f
                else -> h / delta + prev
            }
            prev = x
            x
        }

        // Color and position arrays must be of equal length.
        return LinearGradient(0f, 0f, width, 0f, colors, offsets, Shader.TileMode.CLAMP)
    }
}

class ThumbDrawable : Drawable() {
    private val thumbPaint: Paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL_AND_STROKE
        color = Color.WHITE
    }

    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val r = cx - bounds.left
        canvas.drawCircle(cx, cy, r, thumbPaint)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

class BarThumb {
    var pointerID: Int = INVALID_POINTER_ID
    var touchPoint = PointF()

    var centerPoint = PointF()
    var progress: Float = 0f

    var isDragging: Boolean = false
    var radius: Float = 0f  // px units

    private var drawable: Drawable = ThumbDrawable()

    fun updatePosition(circleCenter: PointF, circleRadius: Float) {
        val phi: Float = (progress * 2f * PI).toFloat()
        centerPoint.apply {
            set(cos(phi) * circleRadius, -sin(phi) * circleRadius)
            offset(circleCenter.x, circleCenter.y)
        }
    }

    fun draw(canvas: Canvas) {
        drawable.setBounds(
            (centerPoint.x - radius).toInt(),
            (centerPoint.y - radius).toInt(),
            (centerPoint.x + radius).toInt(),
            (centerPoint.y + radius).toInt()
        )
        drawable.draw(canvas)
    }
}

class ColorCircleBar : View {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    var onValueChanged: (value0: Float, value1: Float) -> Unit = { _, _ ->  }

    private var circleRadius: Float = 0f  // px units
    private val centerPoint: PointF = PointF()  // local px units
    private val thumb0 = BarThumb()
    private val thumb1 = BarThumb()
    private val thumbs = listOf(thumb0, thumb1)

    private lateinit var hueShader: SweepGradient
    private val huePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private var barRadius: Float = 12f  // dp units
    private var padding: Float = 8f  // dp units
    private var thumbDetectionRadius: Float = 4f  // dp units

    override fun onDraw(canvas: Canvas?) {
        canvas?.run {
            drawCircle(centerPoint.x, centerPoint.y, circleRadius, huePaint)
            thumb0.draw(canvas)
            thumb1.draw(canvas)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val scale = resources.displayMetrics.density
        val barRadiusPx = barRadius * scale
        centerPoint.set(w * 0.5f, h * 0.5f)
        val a = min(w, h) - 2f * scale * padding
        circleRadius = 0.5f * a - barRadiusPx

        thumb0.radius = barRadiusPx * 0.8f
        thumb1.radius = barRadiusPx * 0.8f
        thumb0.updatePosition(centerPoint, circleRadius)
        thumb1.updatePosition(centerPoint, circleRadius)

        hueShader = SweepGradient(centerPoint.x, centerPoint.y, intArrayOf(
            Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN,
            Color.GREEN, Color.YELLOW, Color.RED
        ), null)
        huePaint.shader = hueShader
        huePaint.strokeWidth = 2f * barRadiusPx
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                var touchHandled = false
                val detectionRadius = thumbDetectionRadius * resources.displayMetrics.density + barRadius

                val pointerIndex = event.actionIndex
                val pointerID = event.getPointerId(pointerIndex)

                val touchPoint = PointF(event.getX(pointerIndex), event.getY(pointerIndex))
                val r = hypot(touchPoint.x - centerPoint.x, touchPoint.y - centerPoint.y)
                if (r >= circleRadius - detectionRadius && r <= circleRadius + detectionRadius) {
                    performClick()

                    val thumb =
                        if (thumb0.pointerID == INVALID_POINTER_ID && thumb1.pointerID == INVALID_POINTER_ID) {
                            if (dist2(thumb0.centerPoint, touchPoint) <= dist2(thumb1.centerPoint, touchPoint))
                                thumb0
                            else
                                thumb1
                        } else if (thumb0.pointerID == INVALID_POINTER_ID) {
                            thumb0
                        } else if (thumb1.pointerID == INVALID_POINTER_ID) {
                            thumb1
                        } else {
                            null
                        }

                    if (thumb != null) {
                        thumb.pointerID = pointerID
                        thumb.touchPoint = touchPoint
                        thumb.isDragging = true
                        touchHandled = handleTouch(thumb) || touchHandled
                    }
                }

                touchHandled
            }
            MotionEvent.ACTION_MOVE -> {
                var touchHandled = false
                for (thumb in thumbs) {
                    if (thumb.pointerID != INVALID_POINTER_ID) {
                        event.findPointerIndex(thumb.pointerID).let { index ->
                            if (index != INVALID_POINTER_ID) {
                                thumb.touchPoint.set(event.getX(index), event.getY(index))
                                touchHandled = handleTouch(thumb) || touchHandled
                            }
                        }
                    }
                }
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                for (thumb in thumbs) {
                    thumb.pointerID = INVALID_POINTER_ID
                    thumb.isDragging = false
                }
                true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerID = event.getPointerId(pointerIndex)
                for (thumb in thumbs) {
                    if (thumb.pointerID != INVALID_POINTER_ID && thumb.pointerID == pointerID) {
                        thumb.pointerID = INVALID_POINTER_ID
                        thumb.isDragging = false
                    }
                }
                true
            }
            else -> super.onTouchEvent(event)
        }
    }

    private fun handleTouch(thumb: BarThumb): Boolean {
        val (x, y) = (thumb.touchPoint.x - centerPoint.x) to (thumb.touchPoint.y - centerPoint.y)
        val phi = atan2(-y, x)

        var progress = phi / (2 * PI)
        progress = if (progress < 0) (1 + progress) else (progress)
        thumb.progress = progress.toFloat()
        thumb.updatePosition(centerPoint, circleRadius)

        onValueChanged(thumb0.progress, thumb1.progress)

        // TODO invalidate drawable
        invalidate()
        return true
    }

    fun getColor0(): Int = hueColor(thumb0.progress)
    fun getColor1(): Int = hueColor(thumb1.progress)
}

fun square(x: Float) = x * x
fun dist2(a: PointF, b: PointF): Float = square(b.x - a.x) + square(b.y - a.y)
