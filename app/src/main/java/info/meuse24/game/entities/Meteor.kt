package info.meuse24.game.entities

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Meteor(
    var x: Float,
    var y: Float,
    private val radius: Float,
    private val speed: Float,
    private val paint: Paint,
    private val sprite: Bitmap? = null
) {
    private val shapePath = Path()
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF4B2E1B.toInt()
        strokeWidth = radius * 0.12f
    }
    private val craterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5F4230.toInt()
    }
    private val cracksPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFFB169.toInt()
        strokeWidth = radius * 0.05f
    }
    private val craters = mutableListOf<Crater>()
    private val cracks = mutableListOf<Crack>()

    init {
        if (sprite == null) {
            buildShape()
            buildCraters()
            buildCracks()
        }
    }

    fun update(deltaSeconds: Float) {
        y += speed * deltaSeconds
    }

    fun isOffScreen(screenHeight: Int): Boolean = y - radius > screenHeight

    fun draw(canvas: Canvas) {
        canvas.save()
        canvas.translate(x, y)
        sprite?.let {
            val left = -it.width / 2f
            val top = -it.height / 2f
            canvas.drawBitmap(it, left, top, null)
        } ?: run {
            canvas.drawPath(shapePath, paint)
            canvas.drawPath(shapePath, rimPaint)
            craters.forEach { crater ->
                canvas.drawCircle(crater.x, crater.y, crater.r, craterPaint)
            }
            cracks.forEach { crack ->
                canvas.drawLine(crack.x1, crack.y1, crack.x2, crack.y2, cracksPaint)
            }
        }
        canvas.restore()
    }

    fun radius(): Float = radius

    private fun buildShape() {
        val random = Random(System.nanoTime())
        val sides = random.nextInt(7, 13) // varied polygon count
        val angleStep = (Math.PI * 2 / sides).toFloat()
        val jitter = 0.35f

        shapePath.reset()
        for (i in 0 until sides) {
            val angle = angleStep * i + random.nextFloat() * angleStep * 0.2f
            val r = radius * (1f - jitter + random.nextFloat() * jitter * 2f)
            val px = (cos(angle.toDouble()) * r).toFloat()
            val py = (sin(angle.toDouble()) * r).toFloat()
            if (i == 0) {
                shapePath.moveTo(px, py)
            } else {
                shapePath.lineTo(px, py)
            }
        }
        shapePath.close()
    }

    private fun buildCraters() {
        val random = Random(System.nanoTime() + 17)
        val count = random.nextInt(3, 6)
        repeat(count) {
            val r = radius * random.nextFloat() * 0.2f + radius * 0.08f
            val angle = random.nextFloat() * Math.PI * 2f
            val dist = radius * (0.2f + random.nextFloat() * 0.5f)
            val cx = (cos(angle) * dist).toFloat()
            val cy = (sin(angle) * dist).toFloat()
            craters.add(Crater(cx, cy, r))
        }
    }

    private fun buildCracks() {
        val random = Random(System.nanoTime() + 33)
        val count = random.nextInt(3, 5)
        repeat(count) {
            val startAngle = random.nextFloat() * Math.PI * 2f
            val endAngle = startAngle + (random.nextFloat() * 0.8f - 0.4f)
            val startRadius = radius * (0.1f + random.nextFloat() * 0.5f)
            val endRadius = radius * (0.3f + random.nextFloat() * 0.4f)
            val x1 = (cos(startAngle) * startRadius).toFloat()
            val y1 = (sin(startAngle) * startRadius).toFloat()
            val x2 = (cos(endAngle) * endRadius).toFloat()
            val y2 = (sin(endAngle) * endRadius).toFloat()
            cracks.add(Crack(x1, y1, x2, y2))
        }
    }

    private data class Crater(val x: Float, val y: Float, val r: Float)
    private data class Crack(val x1: Float, val y1: Float, val x2: Float, val y2: Float)
}
