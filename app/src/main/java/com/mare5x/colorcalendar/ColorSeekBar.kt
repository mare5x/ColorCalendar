package com.mare5x.colorcalendar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.PaintDrawable
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
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

// TODO pick a shade of a single color

fun square(x: Float) = x * x
fun dist2(a: PointF, b: PointF): Float = square(b.x - a.x) + square(b.y - a.y)

fun circleDistance(alpha: Float, beta: Float, type: ProfileType) : Float {
    val d = abs(beta - alpha).mod(360f)
    return when (type) {
        ProfileType.CIRCLE_SHORT -> min(d, 360f - d)
        ProfileType.CIRCLE_LONG -> max(d, 360f - d)
        else -> TODO()
    }
}

fun correctDistanceAngles(alpha: Float, beta: Float, type: ProfileType) : Pair<Float, Float> {
    var a = alpha
    var b = beta
    // TODO try gamma 2.2 space?
    // NOTE: Hue value is in [0, 360].
    when (type) {
        ProfileType.CIRCLE_SHORT ->
            if (abs(b - a) > 180) {
                if (alpha in 0f..180f) {
                    b -= 360f
                } else {
                    a -= 360f
                }
            }
        ProfileType.CIRCLE_LONG ->
            if (abs(b - a) < 180) {
                if (a < b) {
                    b -= 360f
                } else {
                    a -= 360f
                }
            }
        else -> TODO()
    }
    return Pair(a, b)
}

fun complementaryColor(color: Int) : Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(color, hsv)
    hsv[0] = (hsv[0] + 180f) % 360f
    return Color.HSVToColor(hsv)
}

// HSV Extension properties for Integers
val Int.hue: Float
    get() {
        val hsv = floatArrayOf(0f, 0f, 0f)
        Color.colorToHSV(this, hsv)
        return hsv[0]
    }


fun dimColor(color: Int, dim: Float) : Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(color, hsv)
    hsv[2] *= dim
    return Color.HSVToColor(hsv)
}

fun mixColors(c0: Int, c1: Int, t: Float): Int {
    val r = (1 - t) * c0.red + t * c1.red
    val g = (1 - t) * c0.green + t * c1.green
    val b = (1 - t) * c0.blue + t * c1.blue
    return Color.rgb(r.roundToInt(), g.roundToInt(), b.roundToInt())
}

fun calcGradientColor(startColor: Int, endColor: Int, t: Float, type: ProfileType = ProfileType.CIRCLE_SHORT) : Int {
    val hsv1 = floatArrayOf(0f, 0f, 0f)
    val hsv2 = floatArrayOf(0f, 0f, 0f)
    Color.RGBToHSV(startColor.red, startColor.green, startColor.blue, hsv1)
    Color.RGBToHSV(endColor.red, endColor.green, endColor.blue, hsv2)
    val (h0, h1) = correctDistanceAngles(hsv1[0], hsv2[0], type)
    val h = ((1.0f - t) * h0 + t * h1).mod(360f)
    val s = (1.0f - t) * hsv1[1] + t * hsv2[1]
    val v = (1.0f - t) * hsv1[2] + t * hsv2[2]
    val hsv = floatArrayOf(h, s, v)
    return Color.HSVToColor(hsv)
}

fun calcGradientProgress(startColor: Int, endColor: Int, midColor: Int, type: ProfileType) : Float {
    // Assume startColor and endColor have the same Saturation and Value.
    val hsvStart = FloatArray(3)
    val hsvEnd = FloatArray(3)
    val hsvMid = FloatArray(3)
    Color.colorToHSV(startColor, hsvStart)
    Color.colorToHSV(endColor, hsvEnd)
    Color.colorToHSV(midColor, hsvMid)

    // TODO check correctness? Consider profile type!
    val (a, b) = correctDistanceAngles(hsvStart[0], hsvEnd[0], type)
    if (hsvMid[0] in a..b || hsvMid[0] in b..a) {
        return abs(hsvMid[0] - a) / abs(b - a)
    } else {
        return abs(hsvMid[0] - 360f - a) / abs(b - a)
    }
    // return circleDistance(hsvStart[0], hsvMid[0], type) / circleDistance(hsvStart[0], hsvEnd[0], type)
}

fun hueColor(h: Float, s: Float = 1f, v: Float = 1f) = Color.HSVToColor(floatArrayOf(h * 360f, s, v))

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

// A ColorSeekBar without a progress bar.
class ColorBar : View {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private var startColor: Int = Color.BLACK
    private var endColor: Int = Color.WHITE
    var profileType: ProfileType = ProfileType.CIRCLE_SHORT
        set(value) {
            field = value
            setColors(startColor, endColor)
        }

    fun setColors(startColor: Int, endColor: Int) {
        this.startColor = startColor
        this.endColor = endColor
        background = HueGradientDrawable(startColor, endColor, false, profileType)
    }
}

class ColorSeekBar2 : ColorSeekBar {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    var isLinear = false
        set(value) {
            field = value
            updateGradientBackground()
        }

    init {
        updateGradientBackground()
    }

    override fun updateGradientBackground() {
        val bg = when {
            isLinear -> LinearColorDrawable(startColor, endColor)
            else -> HueGradientDrawable(startColor, endColor, fullHue, profileType)
        }

        // TODO this
        // progressDrawable = grad
        if (Build.VERSION.SDK_INT >= 16) {
            background = bg
        } else {
            setBackgroundDrawable(bg)
        }
    }

    override fun getColor(): Int {
        return when {
            isLinear -> mixColors(startColor, endColor, getNormProgress())
            else -> super.getColor()
        }
    }
}

open class ColorSeekBar : AppCompatSeekBar {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    protected var startColor: Int = Color.GRAY
    protected var endColor: Int = Color.GRAY
    protected var fullHue: Boolean = false

    var profileType: ProfileType = ProfileType.CIRCLE_SHORT
        set(value) {
            if (field != value) {
                field = value
                updateGradientBackground()
            }
        }

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
        } else {
            setBackgroundDrawable(bg)
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

    open fun getColor(): Int {
        if (fullHue) {
            return hueColor(getNormProgress())
        }
        return when (profileType) {
            ProfileType.CIRCLE_SHORT, ProfileType.CIRCLE_LONG -> calcGradientColor(startColor, endColor, getNormProgress(), profileType)
            else -> TODO()
        }
    }
}

class ColorPickerBar : ConstraintLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private var colorRect: ColorRect
    private var colorBar: ColorSeekBar2

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
        colorRect.setColor(colorBar.getColor())
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

    fun setProfileType(type: ProfileType) {
        colorBar.profileType = type
        updateColorRect()
    }

    fun setIsLinear(value: Boolean) {
        colorBar.isLinear = value
        updateColorRect()
    }
}

val HUE_COLORS = intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA)
val HUES = HUE_COLORS.map { it.hue / 360f }  // [0,1] position of each basic color

class HueGradientDrawable(private var startColor: Int = Color.GRAY,
                          private var endColor: Int = Color.GRAY,
                          private var fullHue: Boolean = false,
                          private var profileType: ProfileType = ProfileType.CIRCLE_SHORT
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
        // Assume startColor and endColor have the same Saturation and Value.
        val hsv0 = FloatArray(3)
        val hsv1 = FloatArray(3)
        Color.colorToHSV(startColor, hsv0)
        Color.colorToHSV(endColor, hsv1)
        var alpha = hsv0[0] / 360f
        var beta: Float
        val h = 1.0f / 6.0f
        // alpha == k0 / 6 + r0
        val r0 = alpha % h
        val k0: Int = (6 * (alpha - r0)).toInt()

        correctDistanceAngles(hsv0[0], hsv1[0], profileType).let {
            alpha = it.first / 360f
            beta = it.second / 360f
        }

        val delta = abs(beta - alpha)
        val dir: Int = sign(beta - alpha).toInt()

        var colors = IntArray(12)
        var offsets = FloatArray(12)
        val st = if (dir > 0) 0 else 1  // Necessary correction depending on direction
        var n = 0
        colors[n] = startColor
        while (offsets[n] < 1f) {
            n += 1
            colors[n] = hueColor(HUES[(k0 + n * dir + st).mod(6)], hsv0[1], hsv0[2])
            if (n == 1) {
                offsets[n] = min(1f, (if (dir > 0) (h - r0) else r0) / delta)
            } else {
                offsets[n] = min(1f, offsets[n - 1] + h / delta)
            }
        }
        colors[n] = endColor
        colors = colors.sliceArray(0..n)
        offsets = offsets.sliceArray(0..n)

        // Color and position arrays must be of equal length.
        return LinearGradient(0f, 0f, width, 0f, colors, offsets, Shader.TileMode.CLAMP)
    }
}

class LinearColorDrawable(private val startColor: Int,
                          private val endColor: Int) : PaintDrawable() {
    init {
        paint.isAntiAlias = true
    }

    override fun draw(canvas: Canvas) {
        paint.shader = LinearGradient(0f, 0f, bounds.width().toFloat(), 0f, startColor, endColor, Shader.TileMode.CLAMP)
        super.draw(canvas)
    }
}


class ThumbDrawable(accentColor: Int) : Drawable() {
    private val outerPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            color = Color.WHITE
        }

    private val accentPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = accentColor
    }

    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val r = (cx - bounds.left)

        outerPaint.strokeWidth = r * 0.5f
        accentPaint.strokeWidth = r * 0.25f

        canvas.drawCircle(cx, cy, r * 0.75f, outerPaint)
        canvas.drawCircle(cx, cy, r * 0.75f, accentPaint)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

class BarThumb(color: Int) {
    var pointerID: Int = INVALID_POINTER_ID
    var touchPoint = PointF()

    var centerPoint = PointF()
    var angleProgress: Float = 0f  // in range [0, 1]
    var radiusProgress: Float = 1f  // in range [0, 1]

    var isDragging: Boolean = false
    var radius: Float = 0f  // px units

    private var drawable: Drawable = ThumbDrawable(color)

    fun updatePosition(circleCenter: PointF, circleRadius: Float) {
        val phi: Float = (angleProgress * 2f * PI).toFloat()
        val r: Float = radiusProgress * circleRadius
        centerPoint.apply {
            set(cos(phi) * r, -sin(phi) * r)
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

// TODO change to HSVCircleBar
class ColorCircleBar : View {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    var onValueChanged: (value0: Float, value1: Float) -> Unit = { _, _ ->  }

    private var profileType: ProfileType = ProfileType.CIRCLE_SHORT

    private var circleRadius: Float = 0f  // px units
    private val centerPoint: PointF = PointF()  // local px units
    private val thumb0 = BarThumb(Color.RED).apply {
        angleProgress = 0f
    }
    private val thumb1 = BarThumb(Color.GREEN).apply {
        val hsv = FloatArray(3)
        angleProgress = Color.colorToHSV(Color.GREEN, hsv).let { hsv[0] / 360f }
    }
    private val thumbs = listOf(thumb0, thumb1)

    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val trackPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = Color.GRAY
        strokeWidth = 2f
    }

    private var barRadius: Float = 12f  // dp units
    private var padding: Float = 8f  // dp units
    private var thumbDetectionRadius: Float = 4f  // dp units

    private var arcRect = RectF()

    override fun onDraw(canvas: Canvas?) {
        arcRect.apply {
            left = centerPoint.x - circleRadius
            top = centerPoint.y - circleRadius
            right = centerPoint.x + circleRadius
            bottom = centerPoint.y + circleRadius
        }
        var thumb0Angle = (1 - thumb0.angleProgress) * 360f
        var thumb1Angle = (1 - thumb1.angleProgress) * 360f
        if (profileType == ProfileType.CIRCLE_SHORT) {
            if (abs(thumb1Angle - thumb0Angle) > 180f) {
                if (thumb0Angle <= 180f) {
                    thumb1Angle -= 360f
                } else {
                    thumb0Angle -= 360f
                }
            }
        } else {
            if (abs(thumb1Angle - thumb0Angle) < 180f) {
                if (thumb0Angle < thumb1Angle) {
                    thumb1Angle -= 360f
                } else {
                    thumb0Angle -= 360f
                }
            }
        }

        canvas?.run {
            drawCircle(centerPoint.x, centerPoint.y, circleRadius, huePaint)
            drawArc(arcRect, thumb0Angle, thumb1Angle - thumb0Angle, false, trackPaint)
            thumb0.draw(canvas)
            thumb1.draw(canvas)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        centerPoint.set(w * 0.5f, h * 0.5f)

        val scale = resources.displayMetrics.density
        val barRadiusPx = barRadius * scale
        val a = min(w, h) - 2f * scale * padding
        circleRadius = 0.5f * a - barRadiusPx

        val hueShader = SweepGradient(centerPoint.x, centerPoint.y, intArrayOf(
            Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN,
            Color.GREEN, Color.YELLOW, Color.RED
        ), null)
        huePaint.shader = hueShader
        huePaint.strokeWidth = 2f * barRadiusPx

        thumb0.radius = barRadiusPx * 0.8f
        thumb1.radius = barRadiusPx * 0.8f
        thumb0.updatePosition(centerPoint, circleRadius)
        thumb1.updatePosition(centerPoint, circleRadius)
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
        thumb.angleProgress = progress.toFloat()
        thumb.updatePosition(centerPoint, circleRadius)

        onValueChanged(thumb0.angleProgress, thumb1.angleProgress)

        // TODO invalidate drawable
        invalidate()
        return true
    }

    fun getColor0(): Int = hueColor(thumb0.angleProgress)
    fun getColor1(): Int = hueColor(thumb1.angleProgress)

    fun setColor0(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        thumb0.angleProgress = hsv[0] / 360f
        invalidate()
    }

    fun setColor1(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        thumb1.angleProgress = hsv[0] / 360f
        invalidate()
    }

    fun setProfileType(type: ProfileType) {
        if (type != profileType) {
            this.profileType = type
            invalidate()
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        val state = SavedState(super.onSaveInstanceState())
        state.thumb0Progress = thumb0.angleProgress
        state.thumb1Progress = thumb1.angleProgress
        return state
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }

        super.onRestoreInstanceState(state.superState)

        thumb0.angleProgress = state.thumb0Progress
        thumb1.angleProgress = state.thumb1Progress
        onValueChanged(thumb0.angleProgress, thumb1.angleProgress)
        invalidate()
    }

    class SavedState : BaseSavedState {
        var thumb0Progress: Float = 0f
        var thumb1Progress: Float = 0f

        constructor(superState: Parcelable?) : super(superState)

        constructor(source: Parcel) : super(source) {
            thumb1Progress = source.readFloat()
            thumb0Progress = source.readFloat()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeFloat(thumb0Progress)
            out.writeFloat(thumb1Progress)
        }

        companion object {
            @JvmField
            val CREATOR = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(parcel: Parcel): SavedState {
                    return SavedState(parcel)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return Array(size) { null }
                }
            }
        }
    }
}


open class HSVCircleBar : View {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    var onValueChanged: (thumbs: List<BarThumb>) -> Unit = { _ ->  }

    protected var circleRadius: Float = 0f  // px units
    protected val centerPoint: PointF = PointF()  // local px units

    protected val thumbs: MutableList<BarThumb> = mutableListOf()

    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val saturationPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val trackPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = Color.GRAY
        strokeWidth = 2f
    }

    private var padding: Float = 8f  // dp units
    private var thumbDetectionRadius: Float = 4f  // dp units
    private var thumbRadius: Float = 12f  // dp units

    init {
        addThumb(Color.RED, Color.RED)
    }

    fun drawCircle(canvas: Canvas) {
        canvas.drawCircle(centerPoint.x, centerPoint.y, circleRadius, huePaint)
        canvas.drawCircle(centerPoint.x, centerPoint.y, circleRadius, saturationPaint)
    }

    fun drawThumbs(canvas: Canvas) {
        thumbs.forEach { thumb -> thumb.draw(canvas) }
    }

    override fun onDraw(canvas: Canvas?) {
        canvas?.let{
            drawCircle(it)
            drawThumbs(it)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        centerPoint.set(w * 0.5f, h * 0.5f)

        val scale = resources.displayMetrics.density
        thumbRadius = 12f * scale
        val a = min(w, h) - 2f * scale * padding
        circleRadius = 0.5f * a - thumbRadius

        val hueShader = SweepGradient(centerPoint.x, centerPoint.y, intArrayOf(
            Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN,
            Color.GREEN, Color.YELLOW, Color.RED
        ), null)
        huePaint.shader = hueShader

        // Idea from: https://github.com/skydoves/ColorPickerView/blob/master/colorpickerview/src/main/java/com/skydoves/colorpickerview/ColorHsvPalette.java
        val saturationShader = RadialGradient(centerPoint.x, centerPoint.y, circleRadius,
            Color.WHITE, 0x00FFFFFF, Shader.TileMode.CLAMP)
        saturationPaint.shader = saturationShader

        thumbs.forEach {
            it.radius = thumbRadius
            it.updatePosition(centerPoint, circleRadius)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                var touchHandled = false
                val detectionRadius = thumbDetectionRadius * resources.displayMetrics.density + thumbRadius

                val pointerIndex = event.actionIndex
                val pointerID = event.getPointerId(pointerIndex)

                val touchPoint = PointF(event.getX(pointerIndex), event.getY(pointerIndex))
                val r = hypot(touchPoint.x - centerPoint.x, touchPoint.y - centerPoint.y)
                if (r <= circleRadius + detectionRadius) {
                    performClick()
                    val thumb = closestThumbTo(touchPoint)
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

    // Closest Thumb to point that isn't being used.
    private fun closestThumbTo(point: PointF): BarThumb? {
        return thumbs
            .filter { it.pointerID == INVALID_POINTER_ID }
            .minByOrNull { dist2(it.centerPoint, point) }
    }

    fun addThumb(thumbColor: Int, colorPosition: Int) {
        val thumb = BarThumb(thumbColor)
        thumbs.add(thumb)
        setThumbColor(thumbs.lastIndex, colorPosition)
        invalidate()
    }

    fun removeThumb() {
        if (thumbs.isNotEmpty()) {
            thumbs.removeLast()
            invalidate()
        }
    }

    open fun handleTouch(thumb: BarThumb): Boolean {
        val (x, y) = (thumb.touchPoint.x - centerPoint.x) to (thumb.touchPoint.y - centerPoint.y)
        val phi = atan2(-y, x)

        var progress = phi / (2 * PI)
        progress = if (progress < 0) (1 + progress) else (progress)
        thumb.angleProgress = progress.toFloat()
        thumb.radiusProgress = min(1f, hypot(x, y) / circleRadius)  // Set to 1.0 to lock onto edge
        thumb.updatePosition(centerPoint, circleRadius)

        onValueChanged(thumbs)

        // TODO invalidate drawable
        invalidate()
        return true
    }

    fun getThumbColor(i: Int): Int = thumbs[i].let { hueColor(it.angleProgress, it.radiusProgress) }
    fun setThumbColor(i: Int, color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        thumbs[i].angleProgress = hsv[0] / 360f
        thumbs[i].radiusProgress = hsv[1]
        invalidate()
    }

    override fun onSaveInstanceState(): Parcelable {
        val state = SavedState(super.onSaveInstanceState())
        state.thumbAngles = FloatArray(thumbs.size, { i -> thumbs[i].angleProgress })
        state.thumbRadii = FloatArray(thumbs.size, { i -> thumbs[i].radiusProgress })
        return state
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }

        super.onRestoreInstanceState(state.superState)

        state.thumbAngles.forEachIndexed { i, v -> thumbs[i].angleProgress = v }
        state.thumbRadii.forEachIndexed { i, v -> thumbs[i].radiusProgress = v }
        onValueChanged(thumbs)
        invalidate()
    }

    class SavedState : BaseSavedState {
        var thumbAngles: FloatArray = FloatArray(0)
        var thumbRadii: FloatArray = FloatArray(0)

        constructor(superState: Parcelable?) : super(superState)

        constructor(source: Parcel) : super(source) {
            source.readFloatArray(thumbRadii)
            source.readFloatArray(thumbAngles)
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeFloatArray(thumbAngles)
            out.writeFloatArray(thumbRadii)
        }

        companion object {
            @JvmField
            val CREATOR = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(parcel: Parcel): SavedState {
                    return SavedState(parcel)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return Array(size) { null }
                }
            }
        }
    }
}

class HSVTwoColorBar : HSVCircleBar {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private var profileType: ProfileType = ProfileType.CIRCLE_SHORT
    private var arcRect = RectF()

    init {
        addThumb(Color.GREEN, Color.GREEN)
    }

    fun setProfileType(type: ProfileType) {
        if (type != profileType) {
            this.profileType = type
            invalidate()
        }
    }

    override fun handleTouch(thumb: BarThumb): Boolean {
        val (x, y) = (thumb.touchPoint.x - centerPoint.x) to (thumb.touchPoint.y - centerPoint.y)
        val phi = atan2(-y, x)

        var progress = phi / (2 * PI)
        progress = if (progress < 0) (1 + progress) else (progress)
        thumb.angleProgress = progress.toFloat()
        thumb.radiusProgress = min(1f, hypot(x, y) / circleRadius)  // Set to 1.0 to lock onto edge
        thumb.updatePosition(centerPoint, circleRadius)

        thumbs.forEach {
            it.radiusProgress = thumb.radiusProgress
            it.updatePosition(centerPoint, circleRadius)
        }

        onValueChanged(thumbs)

        // TODO invalidate drawable
        invalidate()
        return true
    }

    fun drawThumbArc(canvas: Canvas) {
        // Both thumbs must always have the same radius!
        val r = thumbs.first().radiusProgress * circleRadius
        arcRect.apply {
            left = centerPoint.x - r
            top = centerPoint.y - r
            right = centerPoint.x + r
            bottom = centerPoint.y + r
        }
        val a0 = (1 - thumbs[0].angleProgress) * 360f
        val a1 = (1 - thumbs[1].angleProgress) * 360f
        val (thumb0Angle, thumb1Angle) = correctDistanceAngles(a0, a1, profileType)
        canvas.drawArc(arcRect, thumb0Angle, thumb1Angle - thumb0Angle, false, trackPaint)
    }

    override fun onDraw(canvas: Canvas?) {
        canvas?.let {
            drawCircle(it)
            drawThumbArc(it)
            drawThumbs(it)
        }
    }
}
