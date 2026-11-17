package com.jarvis.mark

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The animated arc-reactor at the centre of the HUD. Its speed and colour show the assistant's state. */
class ReactorView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {

    enum class State { IDLE, LISTENING, THINKING, SPEAKING }

    var state: State = State.IDLE

    private var t = 0f
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(cv: Canvas) {
        super.onDraw(cv)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f * 0.92f
        val speed = when (state) {
            State.IDLE -> 0.4f
            State.LISTENING -> 1.2f
            State.THINKING -> 2.8f
            State.SPEAKING -> 1.7f
        }
        t += speed * 0.02f
        val color = when (state) {
            State.IDLE -> 0xFF00B8D4
            State.LISTENING -> 0xFF00E676
            State.THINKING -> 0xFFFFB300
            State.SPEAKING -> 0xFF00E5FF
        }.toInt()
        val glowColor = (color and 0x00FFFFFF) or (0xAA shl 24)
        val clear = color and 0x00FFFFFF
        val amp = if (state == State.SPEAKING) 0.09f else 0.04f
        val pulse = 1f + amp * sin(t * 4f)

        fill.shader = RadialGradient(cx, cy, r * 0.5f * pulse, intArrayOf(glowColor, clear), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r * 0.5f * pulse, fill)

        ring.color = color
        ring.strokeWidth = r * 0.02f
        cv.drawCircle(cx, cy, r, ring)
        cv.drawCircle(cx, cy, r * 0.62f, ring)

        ring.strokeWidth = r * 0.055f
        rect.set(cx - r * 0.84f, cy - r * 0.84f, cx + r * 0.84f, cy + r * 0.84f)
        cv.drawArc(rect, t * 70f, 95f, false, ring)
        cv.drawArc(rect, t * 70f + 180f, 95f, false, ring)
        rect.set(cx - r * 0.72f, cy - r * 0.72f, cx + r * 0.72f, cy + r * 0.72f)
        cv.drawArc(rect, -t * 110f, 60f, false, ring)
        cv.drawArc(rect, -t * 110f + 120f, 60f, false, ring)
        cv.drawArc(rect, -t * 110f + 240f, 60f, false, ring)

        ring.strokeWidth = r * 0.015f
        for (i in 0 until 12) {
            val ang = Math.toRadians((i * 30.0) - t * 25.0)
            val x1 = cx + cos(ang).toFloat() * r * 0.3f
            val y1 = cy + sin(ang).toFloat() * r * 0.3f
            val x2 = cx + cos(ang).toFloat() * r * 0.46f
            val y2 = cy + sin(ang).toFloat() * r * 0.46f
            cv.drawLine(x1, y1, x2, y2, ring)
        }

        fill.shader = null
        fill.color = color
        cv.drawCircle(cx, cy, r * 0.12f * pulse, fill)
        postInvalidateOnAnimation()
    }
}
