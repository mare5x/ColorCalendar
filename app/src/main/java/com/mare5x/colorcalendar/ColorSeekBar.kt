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
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.MotionEvent.INVALID_POINTER_ID
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.blue
import androidx.core.graphics.contains
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.math.MathUtils
import kotlin.math.*

// TODO pick a shade of a single color

fun square(x: Float) = x * x
fun dist2(a: PointF, b: PointF): Float = square(b.x - a.x) + square(b.y - a.y)

fun circleDistance(alpha: Float, beta: Float, typeFlags: Int) : Float {
    val d = abs(beta - alpha).mod(360f)
    return when {
        typeFlags hasFlag ProfileFlag.CIRCLE_LONG -> max(d, 360f - d)
        else -> min(d, 360f - d)
    }
}

fun correctDistanceAngles(alpha: Float, beta: Float, typeFlags: Int) : Pair<Float, Float> {
    var a = alpha
    var b = beta
    // TODO try gamma 2.2 space?
    // NOTE: Hue value is in [0, 360].
    when {
        typeFlags hasFlag ProfileFlag.CIRCLE_LONG ->
            if (abs(b - a) < 180) {
                if (a < b) {
                    b -= 360f
                } else {
                    a -= 360f
                }
            }
        else ->  // short
            if (abs(b - a) > 180) {
                if (alpha in 0f..180f) {
                    b -= 360f
                } else {
                    a -= 360f
                }
            }
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

fun Int.toHSV(): FloatArray {
    val hsv = FloatArray(3)
    Color.colorToHSV(this, hsv)
    return hsv
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

fun calcGradientColor(startColor: Int, endColor: Int, t: Float, typeFlags: Int = 0) : Int {
    val hsv1 = startColor.toHSV()
    val hsv2 = endColor.toHSV()
    val (h0, h1) = correctDistanceAngles(hsv1[0], hsv2[0], typeFlags)
    val h = ((1.0f - t) * h0 + t * h1).mod(360f)
    val s = (1.0f - t) * hsv1[1] + t * hsv2[1]
    val v = (1.0f - t) * hsv1[2] + t * hsv2[2]
    val hsv = floatArrayOf(h, s, v)
    return Color.HSVToColor(hsv)
}

fun calcGradientColor(profile: ProfileEntry, t: Float): Int {
    return when (profile.type) {
        ProfileType.TWO_COLOR_CIRCLE -> calcGradientColor(profile.minColor, profile.maxColor, t, profile.flags)
        ProfileType.FREE_COLOR -> profile.bannerColor ?: profile.prefColor
        ProfileType.ONE_COLOR_HSV -> mixColors(profile.minColor, profile.maxColor, t)
    }
}

fun calcGradientProgress(profile: ProfileEntry) : Float {
    val hsvStart = FloatArray(3)
    val hsvEnd = FloatArray(3)
    val hsvMid = FloatArray(3)
    Color.colorToHSV(profile.minColor, hsvStart)
    Color.colorToHSV(profile.maxColor, hsvEnd)
    Color.colorToHSV(profile.prefColor, hsvMid)
    when (profile.type) {
        ProfileType.TWO_COLOR_CIRCLE -> {
            // Assume startColor and endColor have the same Saturation and Value.
            // TODO check correctness? Consider profile type!
            val (a, b) = correctDistanceAngles(hsvStart[0], hsvEnd[0], profile.flags)
            if (hsvMid[0] in a..b || hsvMid[0] in b..a) {
                return abs(hsvMid[0] - a) / abs(b - a)
            } else {
                return abs(hsvMid[0] - 360f - a) / abs(b - a)
            }
            // return circleDistance(hsvStart[0], hsvMid[0], type) / circleDistance(hsvStart[0], hsvEnd[0], type)
        }
        ProfileType.ONE_COLOR_HSV -> {
            // Rhis doesn't work properly because multiple HSVs map to the same colors (sat, val information is lost)
            // return (hsvMid[1] - hsvStart[1]) / (hsvEnd[1] - hsvStart[1])

            // Assume linear rgb interpolation.
            if (profile.minColor.red != profile.maxColor.red) {
                return (profile.prefColor.red - profile.minColor.red) / (profile.maxColor.red - profile.minColor.red).toFloat()
            } else if (profile.minColor.green != profile.maxColor.green) {
                return (profile.prefColor.green - profile.minColor.green) / (profile.maxColor.green - profile.maxColor.green).toFloat()
            } else {
                return (profile.prefColor.blue - profile.minColor.blue) / (profile.maxColor.blue - profile.minColor.blue).toFloat()
            }
        }
        ProfileType.FREE_COLOR -> return 1f
    }
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
    var typeFlags: Int = 0
        set(value) {
            field = value
            setColors(startColor, endColor)
        }

    fun setColors(startColor: Int, endColor: Int) {
        this.startColor = startColor
        this.endColor = endColor
        background = HueGradientDrawable(startColor, endColor, false, typeFlags)
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
            else -> HueGradientDrawable(startColor, endColor, fullHue, typeFlags)
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

    var typeFlags: Int = 0
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
        return calcGradientColor(startColor, endColor, getNormProgress(), typeFlags)
    }
}

class ColorPickerBar : ConstraintLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private var colorRect: ColorRect
    private var colorBar: ColorSeekBar2

    var onValueChanged: (value: Float, color: Int) -> Unit = { _, _ ->  }

    init {
        LayoutInflater.from(context).inflate(R.layout.color_picker_bar, this, true)

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

    fun setTypeFlags(typeFlags: Int) {
        colorBar.typeFlags = typeFlags
        updateColorRect()
    }

    fun setIsLinear(value: Boolean) {
        colorBar.isLinear = value
        updateColorRect()
    }
}

// ColorPickerBar with a button for the color rect
class ColorButtonBar : ConstraintLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private var colorRect: ColorRectButton
    private var colorBar: ColorSeekBar2

    var onValueChanged: (value: Float, color: Int) -> Unit = { _, _ ->  }
    var onClick: () -> Unit = { }

    var forcedColor: Int? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.color_button_bar, this, true)

        colorRect = findViewById(R.id.colorRect)
        colorBar = findViewById(R.id.colorSeekBar)
        colorBar.onValueChanged = { value, color ->
            updateColorRect()
            onValueChanged(value, color)
        }
        colorRect.setOnClickListener {
            onClick()
        }

        updateColorRect()
    }

    private fun updateColorRect() {
        forcedColor = null
        colorRect.color = getColor()
    }

    fun getNormProgress(): Float = colorBar.getNormProgress()
    fun setNormProgress(t: Float) = colorBar.setNormProgress(t)

    fun setForcedColor(color: Int) {
        forcedColor = color
        colorRect.color = color
    }
    fun getColor() = forcedColor ?: colorBar.getColor()

    fun setColors(startColor: Int, endColor: Int) {
        colorBar.setColors(startColor, endColor)
        updateColorRect()
    }

    fun setTypeFlags(typeFlags: Int) {
        colorBar.typeFlags = typeFlags
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
                          private var typeFlags: Int = 0
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

        correctDistanceAngles(hsv0[0], hsv1[0], typeFlags).let {
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

class CircleThumb(color: Int) {
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

class RectThumb(color: Int) {
    var pointerID: Int = INVALID_POINTER_ID
    var touchPoint = PointF()

    var centerPoint = PointF()
    var xProgress: Float = 0f  // in range [0, 1]
    var yProgress: Float = 1f  // in range [0, 1]

    var isDragging: Boolean = false
    var radius: Float = 0f  // px units

    private var drawable: Drawable = ThumbDrawable(color)

    fun updatePosition(rect: RectF) {
        centerPoint.set(
            rect.left + xProgress * rect.width(),
            rect.bottom - yProgress * rect.height()
        )
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

open class HSVCircleBar : View {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    var onValueChanged: (thumbs: List<CircleThumb>) -> Unit = { _ ->  }

    protected var circleRadius: Float = 0f  // px units
    protected val centerPoint: PointF = PointF()  // local px units

    protected val thumbs: MutableList<CircleThumb> = mutableListOf()

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
    private fun closestThumbTo(point: PointF): CircleThumb? {
        return thumbs
            .filter { it.pointerID == INVALID_POINTER_ID }
            .minByOrNull { dist2(it.centerPoint, point) }
    }

    fun addThumb(thumbColor: Int, colorPosition: Int) {
        val thumb = CircleThumb(thumbColor)
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

    open fun handleTouch(thumb: CircleThumb): Boolean {
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

    private var typeFlags: Int = 0
    private var arcRect = RectF()

    init {
        addThumb(Color.GREEN, Color.GREEN)
    }

    fun setTypeFlags(typeFlags: Int) {
        if (typeFlags != this.typeFlags) {
            this.typeFlags = typeFlags
            invalidate()
        }
    }

    override fun handleTouch(thumb: CircleThumb): Boolean {
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
        val (thumb0Angle, thumb1Angle) = correctDistanceAngles(a0, a1, typeFlags)
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

open class HSVRectPicker : View {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    var onValueChanged: (thumbs: List<RectThumb>) -> Unit = { _ ->  }

    protected val thumbs: MutableList<RectThumb> = mutableListOf()

    private val satValPaint = Paint()
    protected val trackPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = Color.GRAY
        strokeWidth = 2f
    }

    private var baseColor: Int = 0
    private var baseHsv: FloatArray = floatArrayOf(0f, 0f, 0f)

    // private var padding: Float = 8f  // dp units
    private var thumbDetectionRadius: Float = 4f  // dp units
    private var thumbRadius: Float = 0f  // set in onSizeChanged, pixel units

    init {
        // https://developer.android.com/guide/topics/graphics/hardware-accel
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        addThumb(Color.RED, Color.HSVToColor(floatArrayOf(0f, 0.5f, 0.5f)))
        addThumb(Color.GREEN, Color.HSVToColor(floatArrayOf(0f, 1f, 1f)))
        setMainColor(Color.GREEN)
    }

    private fun drawingRect(): RectF {
        val r = thumbRadius
        return RectF(
            max(paddingLeft.toFloat(), r),
            max(paddingTop.toFloat(), r),
            width - max(paddingRight.toFloat(), r),
            height - max(paddingBottom.toFloat(), r)
        )
    }

    private fun drawConnector(canvas: Canvas) {
        val t0 = thumbs[0]
        val t1 = thumbs[1]
        canvas.drawLine(t0.centerPoint.x, t0.centerPoint.y, t1.centerPoint.x, t1.centerPoint.y, trackPaint)
    }

    private fun drawRect(canvas: Canvas) {
        val rect = drawingRect()
        canvas.drawRect(rect, satValPaint)
    }

    private fun drawThumbs(canvas: Canvas) {
        thumbs.forEach { thumb -> thumb.draw(canvas) }
    }

    override fun onDraw(canvas: Canvas?) {
        canvas?.let{
            drawRect(it)
            if (thumbs.size > 1) {
                drawConnector(it)
            }
            drawThumbs(it)
        }
    }

    private fun updateShader() {
        val rect = drawingRect()
        val saturationShader = LinearGradient(
            rect.left, rect.top, rect.right, rect.top,
            Color.WHITE, baseColor,
            Shader.TileMode.CLAMP
        )
        val valueShader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            Color.WHITE, -16777216,
            Shader.TileMode.CLAMP
        )
        // NOTE: https://developer.android.com/guide/topics/graphics/hardware-accel
        // Same type shaders inside ComposeShader are not supported until API 28!!!
        satValPaint.shader = ComposeShader(valueShader, saturationShader, PorterDuff.Mode.MULTIPLY)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        thumbRadius = 12f * resources.displayMetrics.density
        val rect = drawingRect()
        updateShader()
        thumbs.forEach {
            it.radius = thumbRadius
            it.updatePosition(rect)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                var touchHandled = false
                // val detectionRadius = thumbDetectionRadius * resources.displayMetrics.density + thumbRadius

                val pointerIndex = event.actionIndex
                val pointerID = event.getPointerId(pointerIndex)

                val touchPoint = PointF(event.getX(pointerIndex), event.getY(pointerIndex))
                val detectionRect = RectF(0f, 0f, width.toFloat(), height.toFloat()) // Whole view
                if (detectionRect.contains(touchPoint)) {
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
    private fun closestThumbTo(point: PointF): RectThumb? {
        return thumbs
            .filter { it.pointerID == INVALID_POINTER_ID }
            .minByOrNull { dist2(it.centerPoint, point) }
    }

    fun addThumb(thumbColor: Int, colorPosition: Int) {
        val thumb = RectThumb(thumbColor)
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

    open fun handleTouch(thumb: RectThumb): Boolean {
        val rect = drawingRect()
        thumb.xProgress = MathUtils.clamp((thumb.touchPoint.x - rect.left) / rect.width(), 0f, 1f)
        thumb.yProgress = 1f - MathUtils.clamp((thumb.touchPoint.y - rect.top) / rect.height(), 0f, 1f)
        thumb.updatePosition(rect)

        onValueChanged(thumbs)

        invalidate()
        return true
    }

    fun setMainColor(color: Int) {
        baseColor = color
        Color.colorToHSV(color, baseHsv)
        updateShader()
        invalidate()
    }

    fun getThumbColor(i: Int): Int = thumbs[i].let {
        val hsv = floatArrayOf(baseHsv[0], it.xProgress, it.yProgress)
        Color.HSVToColor(hsv)
    }
    fun setThumbColor(i: Int, color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        thumbs[i].xProgress = hsv[1]
        thumbs[i].yProgress = hsv[2]
        thumbs[i].updatePosition(drawingRect())
        invalidate()
    }

    override fun onSaveInstanceState(): Parcelable {
        val state = SavedState(super.onSaveInstanceState())
        state.thumbXs = FloatArray(thumbs.size, { i -> thumbs[i].xProgress })
        state.thumbYs = FloatArray(thumbs.size, { i -> thumbs[i].yProgress })
        return state
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }

        super.onRestoreInstanceState(state.superState)

        state.thumbXs.forEachIndexed { i, v -> thumbs[i].xProgress = v }
        state.thumbYs.forEachIndexed { i, v -> thumbs[i].yProgress = v }
        onValueChanged(thumbs)
        invalidate()
    }

    class SavedState : BaseSavedState {
        var thumbXs: FloatArray = FloatArray(0)
        var thumbYs: FloatArray = FloatArray(0)

        constructor(superState: Parcelable?) : super(superState)

        constructor(source: Parcel) : super(source) {
            source.readFloatArray(thumbYs)
            source.readFloatArray(thumbXs)
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeFloatArray(thumbXs)
            out.writeFloatArray(thumbYs)
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
