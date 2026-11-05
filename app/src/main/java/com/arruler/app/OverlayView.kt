package com.arruler.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    @Volatile var snapshot: Snapshot = Snapshot()
    @Volatile var unit: UnitSystem = UnitSystem.METRIC

    private val d = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; strokeWidth = 3f * d; strokeCap = Paint.Cap.ROUND
    }
    private val liveLine = Paint(line).apply {
        color = Color.argb(170, 255, 255, 255)
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f * d, 10f * d), 0f)
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 193, 7) }
    private val dotRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f * d
    }
    private val reticlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f * d
    }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(210, 21, 101, 192) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 15f * d; textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val s = snapshot
        val u = unit

        // Placed segments
        for (i in 0 until s.points.size - 1) {
            val a = s.points[i]; val b = s.points[i + 1]
            if (a != null && b != null) {
                canvas.drawLine(a.x, a.y, b.x, b.y, line)
                label(canvas, u.format(s.segments[i]), (a.x + b.x) / 2f, (a.y + b.y) / 2f)
            }
        }

        // Live segment to reticle
        val last = s.points.lastOrNull()
        val r = s.reticle
        if (last != null && r != null && s.reticleHit && s.liveMeters != null) {
            canvas.drawLine(last.x, last.y, r.x, r.y, liveLine)
            label(canvas, u.format(s.liveMeters), (last.x + r.x) / 2f, (last.y + r.y) / 2f)
        }

        // Points
        for (p in s.points) {
            if (p == null) continue
            canvas.drawCircle(p.x, p.y, 7f * d, dot)
            canvas.drawCircle(p.x, p.y, 7f * d, dotRing)
        }

        // Reticle
        if (r != null && s.tracking) {
            reticlePaint.color = if (s.reticleHit) Color.rgb(76, 217, 100) else Color.argb(160, 255, 255, 255)
            canvas.drawCircle(r.x, r.y, 18f * d, reticlePaint)
            canvas.drawCircle(r.x, r.y, 2.5f * d, dot)
        }
    }

    private fun label(canvas: Canvas, str: String, cx: Float, cy: Float) {
        val w = text.measureText(str) + 20f * d
        val h = 28f * d
        rect.set(cx - w / 2, cy - h / 2 - 18f * d, cx + w / 2, cy + h / 2 - 18f * d)
        canvas.drawRoundRect(rect, h / 2, h / 2, pill)
        val base = rect.centerY() - (text.descent() + text.ascent()) / 2
        canvas.drawText(str, rect.centerX(), base, text)
    }
}
