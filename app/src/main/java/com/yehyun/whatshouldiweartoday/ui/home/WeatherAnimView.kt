package com.yehyun.whatshouldiweartoday.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.*

class WeatherAnimView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class WeatherType { CLEAR, PARTLY_CLOUDY, CLOUDY, OVERCAST, DRIZZLE, RAIN, THUNDERSTORM, SNOW, MIST }

    private val dp = context.resources.displayMetrics.density

    private var currentType = WeatherType.CLEAR
    private var sunRot = 0f
    private var sunScale = 1f
    private var cloudSin = 0f
    private var decorAngle = 0f
    private var lightningPhase = 0f
    private var mistPhase = 0f

    // Precipitation uses elapsed real time to avoid discontinuous jumps on animator restart
    private var precipStartMs = 0L
    private var precipPeriodMs = 750f

    private val sunRotAnim    = anim(18000L, 0f, 360f, LinearInterpolator()) { sunRot = it }
    private val sunPulseAnim  = anim(2200L, 1f, 1.08f, AccelerateDecelerateInterpolator(), reverse = true) { sunScale = it }
    private val cloudAnim     = anim(4500L, 0f, (2.0 * PI).toFloat(), LinearInterpolator()) { cloudSin = sin(it.toDouble()).toFloat() }
    private val decorAnim     = anim(7000L, 0f, (2.0 * PI).toFloat(), LinearInterpolator()) { decorAngle = it }
    private val rainAnim      = anim(16L, 0f, 1f, LinearInterpolator()) { }   // invalidate trigger only
    private val snowAnim      = anim(16L, 0f, 1f, LinearInterpolator()) { }   // invalidate trigger only
    private val lightningAnim = anim(4500L, 0f, 1f, LinearInterpolator()) { lightningPhase = it }
    private val mistAnim      = anim(6000L, 0f, 1f, LinearInterpolator()) { mistPhase = it }

    private fun anim(dur: Long, from: Float, to: Float,
                     interp: android.view.animation.Interpolator,
                     reverse: Boolean = false,
                     onUpdate: (Float) -> Unit) =
        ValueAnimator.ofFloat(from, to).apply {
            duration = dur; repeatCount = ValueAnimator.INFINITE
            repeatMode = if (reverse) ValueAnimator.REVERSE else ValueAnimator.RESTART
            interpolator = interp
            addUpdateListener { onUpdate(it.animatedValue as Float); invalidate() }
        }

    // Returns a continuously increasing fractional phase (no restart jump)
    private fun precipFrac(spd: Float, phase: Float): Float {
        val now = SystemClock.elapsedRealtime()
        if (precipStartMs == 0L) precipStartMs = now
        return ((now - precipStartMs).toFloat() / precipPeriodMs * spd + phase) % 1f
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private data class Drop(val xFrac: Float, val phase: Float, val len: Float, val spd: Float, val alpha: Float)
    private data class Flake(val xFrac: Float, val phase: Float, val sz: Float, val spd: Float, val swayAmp: Float, val rot0: Float)

    private var drops = emptyList<Drop>()
    private var flakes = emptyList<Flake>()
    private var lightningPath = Path()
    private var w = 0f; private var h = 0f
    private var iconR = 0f

    fun setWeatherFromIcon(icon: String) {
        val t = when (icon.take(2)) {
            "01" -> WeatherType.CLEAR
            "02" -> WeatherType.PARTLY_CLOUDY
            "03" -> WeatherType.CLOUDY
            "04" -> WeatherType.OVERCAST
            "09" -> WeatherType.DRIZZLE
            "10" -> WeatherType.RAIN
            "11" -> WeatherType.THUNDERSTORM
            "13" -> WeatherType.SNOW
            "50" -> WeatherType.MIST
            else -> WeatherType.CLEAR
        }
        if (t == currentType) return
        currentType = t; stopWeatherAnims(); startForType(); invalidate()
    }

    private fun stopWeatherAnims() =
        listOf(sunRotAnim, sunPulseAnim, cloudAnim, rainAnim, snowAnim, lightningAnim, mistAnim).forEach { it.cancel() }

    private fun startForType() {
        when (currentType) {
            WeatherType.CLEAR         -> { sunRotAnim.start(); sunPulseAnim.start() }
            WeatherType.PARTLY_CLOUDY -> { sunRotAnim.start(); cloudAnim.start() }
            WeatherType.CLOUDY, WeatherType.OVERCAST -> cloudAnim.start()
            WeatherType.DRIZZLE, WeatherType.RAIN -> {
                cloudAnim.start(); rainAnim.start()
                precipStartMs = 0L; precipPeriodMs = 750f
            }
            WeatherType.THUNDERSTORM  -> {
                cloudAnim.start(); rainAnim.start(); lightningAnim.start()
                precipStartMs = 0L; precipPeriodMs = 750f
            }
            WeatherType.SNOW          -> {
                cloudAnim.start(); snowAnim.start()
                precipStartMs = 0L; precipPeriodMs = 3400f
            }
            WeatherType.MIST          -> mistAnim.start()
        }
    }

    override fun onSizeChanged(sw: Int, sh: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(sw, sh, oldw, oldh)
        w = sw.toFloat(); h = sh.toFloat()
        if (sw <= 0 || sh <= 0) return
        iconR = min(26f * dp, w * 0.43f * 0.38f)
        val rng = java.util.Random(42)
        drops  = List(24) { Drop(rng.nextFloat(), rng.nextFloat(), 9f + rng.nextFloat() * 13f, 0.55f + rng.nextFloat() * 0.5f, 0.72f + rng.nextFloat() * 0.28f) }
        flakes = List(16) { Flake(rng.nextFloat(), rng.nextFloat(), 6f + rng.nextFloat() * 6f,  0.22f + rng.nextFloat() * 0.28f, 9f + rng.nextFloat() * 15f, rng.nextFloat() * 360f) }
        val lx = w * 0.785f; val ly = h * 0.28f
        lightningPath.reset()
        lightningPath.moveTo(lx, ly);             lightningPath.lineTo(lx - 9f,  ly + 18f)
        lightningPath.lineTo(lx + 1f, ly + 18f);  lightningPath.lineTo(lx - 13f, ly + 42f)
        lightningPath.lineTo(lx + 16f, ly + 25f); lightningPath.lineTo(lx + 4f,  ly + 25f)
        lightningPath.close()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        decorAnim.start()
        startForType()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopWeatherAnims()
        decorAnim.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        if (w <= 0f || h <= 0f) return
        drawDecorativeClouds(canvas)
        val cx = w * 0.785f
        val cy = h * 0.58f
        when (currentType) {
            WeatherType.CLEAR         -> drawClear(canvas, cx, cy)
            WeatherType.PARTLY_CLOUDY -> drawPartlyCloudy(canvas, cx, cy)
            WeatherType.CLOUDY        -> drawCloudy(canvas, cx, cy)
            WeatherType.OVERCAST      -> drawOvercast(canvas, cx, cy)
            WeatherType.DRIZZLE       -> drawDrizzle(canvas, cx, cy)
            WeatherType.RAIN          -> drawRain(canvas, cx, cy)
            WeatherType.THUNDERSTORM  -> drawThunderstorm(canvas, cx, cy)
            WeatherType.SNOW          -> drawSnow(canvas, cx, cy)
            WeatherType.MIST          -> drawMist(canvas)
        }
    }

    // ── Decorative clouds (always-on, card edges) ─────────────────────────────

    private fun drawDecorativeClouds(canvas: Canvas) {
        val r = 20f * dp
        // Top and bottom clouds use different phase offsets → out-of-sync movement
        val swayTop = sin(decorAngle.toDouble()).toFloat() * dp * 22f
        val swayBot = sin(decorAngle.toDouble() + PI * 0.6).toFloat() * dp * 22f
        cloud(canvas, w * 0.73f + swayTop, r * 1.05f, r, Color.WHITE, 150)
        val rb = r * 0.76f
        cloud(canvas, w * 0.27f - swayBot, h - rb * 1.10f, rb, Color.WHITE, 80)
    }

    // ── Sun ───────────────────────────────────────────────────────────────────

    private fun sun(canvas: Canvas, cx: Float, cy: Float, r: Float, nRays: Int = 10, dimAlpha: Float = 1f) {
        val ia = (dimAlpha * 255).toInt().coerceIn(0, 255)
        paint.shader = RadialGradient(cx, cy, r * 2.7f,
            intArrayOf(Color.argb((ia * 0.38f).toInt(), 255, 210, 50), Color.argb(0, 255, 195, 0)),
            null, Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL; paint.alpha = ia
        canvas.drawCircle(cx, cy, r * 2.7f, paint)
        canvas.save(); canvas.rotate(sunRot, cx, cy)
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeWidth = r * 0.17f
        val iR = r * 1.30f; val oR = iR + r * 0.60f
        repeat(nRays) { i ->
            val ang = (i * 360.0 / nRays * PI / 180).toFloat()
            val x1 = cx + iR * cos(ang); val y1 = cy + iR * sin(ang)
            val x2 = cx + oR * cos(ang); val y2 = cy + oR * sin(ang)
            paint.shader = LinearGradient(x1, y1, x2, y2,
                Color.argb(ia, 255, 200, 0), Color.argb(0, 255, 145, 0), Shader.TileMode.CLAMP)
            canvas.drawLine(x1, y1, x2, y2, paint)
        }
        canvas.restore()
        canvas.save(); canvas.scale(sunScale, sunScale, cx, cy)
        paint.shader = RadialGradient(cx - r * 0.20f, cy - r * 0.20f, r,
            intArrayOf(Color.argb(ia, 255, 245, 100), Color.argb(ia, 255, 155, 0)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL; paint.strokeWidth = 0f
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = RadialGradient(cx - r * 0.25f, cy - r * 0.25f, r * 0.46f,
            intArrayOf(Color.argb((ia * 0.52f).toInt(), 255, 255, 220), Color.argb(0, 255, 255, 200)),
            null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx - r * 0.12f, cy - r * 0.12f, r * 0.46f, paint)
        canvas.restore()
    }

    // ── Cloud: flat bottom with rounded tips ─────────────────────────────────
    // Shoulders are positioned so their bottommost point = flatBot = cy+r*0.80.
    // layerRect clips at flatBot → center circle clipped flat.
    // Beyond shoulder centers (left/right tips): circle arcs curve up naturally.
    // cloudBotY (for precipitation start) = cy + r * 0.80f

    private fun cloud(canvas: Canvas, cx: Float, cy: Float, r: Float, col: Int = Color.WHITE, alpha: Int = 255) {
        // Left:  center (cx-r*1.25, cy+r*0.08), radius r*0.72 → bottom = cy+r*0.80
        // Right: center (cx+r*0.95, cy+r*0.02), radius r*0.78 → bottom = cy+r*0.80
        val flatBot = cy + r * 0.80f
        val leftCx = cx - r * 1.25f; val leftCy = cy + r * 0.08f; val leftR = r * 0.72f
        val rightCx = cx + r * 0.95f; val rightCy = cy + r * 0.02f; val rightR = r * 0.78f

        // layerRect bottom = flatBot clips the center circle flat
        val layerRect = RectF(leftCx - leftR, cy - r * 1.05f, rightCx + rightR, flatBot)
        layerPaint.alpha = alpha
        canvas.saveLayer(layerRect, layerPaint)

        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = col; paint.alpha = 255

        canvas.drawCircle(leftCx, leftCy, leftR, paint)    // left shoulder
        canvas.drawCircle(cx, cy, r, paint)                 // center peak (clipped at flatBot)
        canvas.drawCircle(rightCx, rightCy, rightR, paint) // right shoulder
        // Fill any gaps in the middle between circles
        rect.set(leftCx, cy - r * 0.10f, rightCx, flatBot)
        canvas.drawRect(rect, paint)

        // Soft highlight
        paint.shader = RadialGradient(cx - r * 0.18f, cy - r * 0.28f, r * 0.62f,
            intArrayOf(Color.argb(40, 255, 255, 255), Color.argb(0, 255, 255, 255)),
            null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null

        canvas.restore()
    }

    // ── Scene draws ───────────────────────────────────────────────────────────

    private fun drawClear(canvas: Canvas, cx: Float, cy: Float) = sun(canvas, cx, cy, iconR)

    private fun drawPartlyCloudy(canvas: Canvas, cx: Float, cy: Float) {
        val sw = cloudSin * iconR * 0.13f
        sun(canvas, cx + iconR * 0.22f, cy - iconR * 0.22f, iconR * 0.82f, 9)
        cloud(canvas, cx - iconR * 0.12f + sw, cy + iconR * 0.20f, iconR * 0.82f)
    }

    private fun drawCloudy(canvas: Canvas, cx: Float, cy: Float) {
        val sw = cloudSin * iconR * 0.13f
        cloud(canvas, cx + iconR * 0.10f + sw, cy - iconR * 0.18f, iconR * 0.86f, Color.parseColor("#BACED8"), 210)
        cloud(canvas, cx - iconR * 0.08f - sw, cy + iconR * 0.20f, iconR * 1.00f, Color.parseColor("#A0BED0"))
    }

    private fun drawOvercast(canvas: Canvas, cx: Float, cy: Float) {
        val sw = cloudSin * iconR * 0.12f
        cloud(canvas, cx - iconR * 0.10f + sw, cy - iconR * 0.28f, iconR * 0.80f, Color.parseColor("#B0C8D8"), 200)
        cloud(canvas, cx + iconR * 0.10f - sw, cy - iconR * 0.02f, iconR * 0.90f, Color.parseColor("#96B4C6"), 230)
        cloud(canvas, cx - iconR * 0.06f + sw * 0.5f, cy + iconR * 0.28f, iconR * 1.00f, Color.parseColor("#7CA0B8"))
    }

    // ── Precipitation ─────────────────────────────────────────────────────────

    // cloudBotY = cloudCy + cloudR * 0.80f  (flat bottom level)
    // cloudL    = cloudCx - cloudR * 1.97f
    // cloudW    = cloudR * 3.70f

    private fun rainDrops(canvas: Canvas, cloudCx: Float, cloudBotY: Float, cloudR: Float,
                          thick: Float, ri: Int, gi: Int, bi: Int) {
        val startY = cloudBotY
        val range  = h - startY
        if (range <= 0f) return
        val cloudL = cloudCx - cloudR * 1.97f
        val cloudW = cloudR * 3.70f
        val slant  = 0.22f
        paint.shader = null; paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeWidth = thick
        for (d in drops) {
            val prog = precipFrac(d.spd, d.phase)
            val y  = startY + prog * range
            val x  = cloudL + d.xFrac * cloudW + prog * range * slant
            val fi = if (prog < 0.08f) prog / 0.08f else 1f
            val fo = if (prog > 0.88f) (1f - prog) / 0.12f else 1f
            paint.color = Color.argb((d.alpha * fi * fo * 255f).toInt().coerceIn(0, 255), ri, gi, bi)
            val hl = d.len * 0.5f
            canvas.drawLine(x - hl * slant, y - hl, x + hl * slant, y + hl, paint)
        }
    }

    private fun drawDrizzle(canvas: Canvas, cx: Float, cy: Float) {
        val sway = cloudSin * iconR * 0.13f
        val ccx  = cx + sway; val ccy = cy - iconR * 0.24f; val cr = iconR * 0.96f
        cloud(canvas, ccx, ccy, cr, Color.parseColor("#AACBDC"))
        rainDrops(canvas, ccx, ccy + cr * 0.80f, cr, 1.8f, 215, 238, 255)
    }

    private fun drawRain(canvas: Canvas, cx: Float, cy: Float) {
        val sway = cloudSin * iconR * 0.13f
        val ccx  = cx + sway; val ccy = cy - iconR * 0.28f; val cr = iconR * 1.00f
        cloud(canvas, ccx, ccy, cr, Color.parseColor("#9ABCCC"))
        rainDrops(canvas, ccx, ccy + cr * 0.80f, cr, 3.0f, 230, 245, 255)
    }

    private fun drawThunderstorm(canvas: Canvas, cx: Float, cy: Float) {
        val sway = cloudSin * iconR * 0.10f
        val ccx  = cx + sway; val ccy = cy - iconR * 0.32f; val cr = iconR * 1.02f
        cloud(canvas, ccx, ccy, cr, Color.parseColor("#7A8A98"))
        rainDrops(canvas, ccx, ccy + cr * 0.80f, cr, 2.4f, 210, 225, 248)
        val la = lightningAlpha()
        if (la > 0f) {
            paint.shader = null; paint.style = Paint.Style.STROKE
            paint.strokeWidth = 15f; paint.strokeJoin = Paint.Join.ROUND; paint.strokeCap = Paint.Cap.ROUND
            paint.color = Color.argb((la * 90).toInt(), 255, 238, 50)
            canvas.drawPath(lightningPath, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb((la * 255).toInt().coerceIn(0, 255), 255, 235, 50)
            canvas.drawPath(lightningPath, paint)
            paint.color = Color.argb((la * 22).toInt(), 255, 255, 255)
            canvas.drawRect(0f, 0f, w, h, paint)
        }
    }

    private fun lightningAlpha(): Float = when {
        lightningPhase < 0.05f -> lightningPhase / 0.05f
        lightningPhase < 0.13f -> 1f - (lightningPhase - 0.05f) / 0.08f
        lightningPhase < 0.20f -> 0f
        lightningPhase < 0.25f -> (lightningPhase - 0.20f) / 0.05f
        lightningPhase < 0.33f -> 1f - (lightningPhase - 0.25f) / 0.08f
        else -> 0f
    }

    private fun drawSnow(canvas: Canvas, cx: Float, cy: Float) {
        val sway = cloudSin * iconR * 0.13f
        val ccx  = cx + sway; val ccy = cy - iconR * 0.28f; val cr = iconR * 0.96f
        cloud(canvas, ccx, ccy, cr, Color.parseColor("#C4D8E8"))

        val startY = ccy + cr * 0.80f
        val range  = h - startY
        if (range <= 0f) return
        val cloudL = ccx - cr * 1.97f
        val cloudW = cr * 3.70f

        paint.shader = null; paint.color = Color.WHITE
        val elapsed = if (precipStartMs == 0L) 0f else (SystemClock.elapsedRealtime() - precipStartMs).toFloat()
        for (fl in flakes) {
            val prog  = precipFrac(fl.spd, fl.phase)
            val y     = startY + prog * range
            val xBase = cloudL + fl.xFrac * cloudW
            val x     = xBase + sin(prog * fl.swayAmp.toDouble() * 0.7 * PI * 2).toFloat() * fl.swayAmp * 0.7f
            val fi    = if (prog < 0.10f) prog / 0.10f else 1f
            val fo    = if (prog > 0.85f) (1f - prog) / 0.15f else 1f
            paint.alpha = (fi * fo * 230).toInt()
            snowflake(canvas, x, y, fl.sz, fl.rot0 + elapsed / precipPeriodMs * 200f)
        }
    }

    private fun snowflake(canvas: Canvas, cx: Float, cy: Float, r: Float, rot: Float) {
        canvas.save(); canvas.rotate(rot, cx, cy)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = r * 0.24f; paint.strokeCap = Paint.Cap.ROUND
        repeat(6) { i ->
            canvas.save(); canvas.rotate(i * 60f, cx, cy)
            canvas.drawLine(cx, cy, cx, cy - r, paint)
            val bY = cy - r * 0.57f; val bL = r * 0.38f
            canvas.drawLine(cx, bY, cx - bL, bY - bL * 0.5f, paint)
            canvas.drawLine(cx, bY, cx + bL, bY - bL * 0.5f, paint)
            canvas.restore()
        }
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, r * 0.20f, paint)
        canvas.restore()
    }

    // ── Mist: feathered horizontal wisps ─────────────────────────────────────

    private fun drawMist(canvas: Canvas) {
        val cx = w * 0.785f
        paint.style = Paint.Style.FILL
        val wisps = arrayOf(
            floatArrayOf(0.28f, 1.00f, 72f),
            floatArrayOf(0.42f, 0.86f, 60f),
            floatArrayOf(0.56f, 1.06f, 70f),
            floatArrayOf(0.70f, 0.80f, 54f),
            floatArrayOf(0.84f, 0.92f, 46f)
        )
        for ((i, wisp) in wisps.withIndex()) {
            val baseY  = h * wisp[0]
            val halfW  = w * 0.23f * wisp[1]
            val bandH  = halfW * 0.11f
            val off    = ((mistPhase + i * 0.22f) % 1f - 0.5f) * w * 0.09f
            val left   = cx - halfW + off
            val right  = cx + halfW + off
            val maxA   = wisp[2].toInt()
            paint.shader = LinearGradient(left, baseY, right, baseY,
                intArrayOf(Color.TRANSPARENT,
                    Color.argb(maxA, 255, 255, 255),
                    Color.argb(maxA, 255, 255, 255),
                    Color.TRANSPARENT),
                floatArrayOf(0f, 0.18f, 0.82f, 1f),
                Shader.TileMode.CLAMP)
            rect.set(left, baseY - bandH, right, baseY + bandH)
            canvas.drawRoundRect(rect, bandH, bandH, paint)
        }
        paint.shader = null
    }
}
