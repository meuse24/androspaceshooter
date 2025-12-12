package info.meuse24.game.entities

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Path
import kotlin.math.abs
import kotlin.math.sin

class Player(
    var x: Float,
    var y: Float,
    val width: Float,
    val height: Float,
    private val paint: Paint,
    private val baseSprite: Bitmap? = null,
    private val flameSprites: List<Bitmap>? = null
) {
    private var targetX: Float = x
    private val shapePath = Path()
    private val wingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2DD4BF.toInt() }
    private val cockpitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFBDE0FE.toInt() }
    private val flamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF7A18.toInt() }
    private var flameTimer = 0f

    fun setTarget(target: Float) {
        targetX = target
    }

    fun update(deltaSeconds: Float, followSpeed: Float, screenWidth: Int) {
        val dx = targetX - x
        val maxStep = followSpeed * deltaSeconds * screenWidth
        // Smoothly follow the finger, clamping movement per frame
        if (abs(dx) > maxStep) {
            x += if (dx > 0) maxStep else -maxStep
        } else {
            x = targetX
        }
        // Keep the player inside the screen
        val halfWidth = width / 2f
        x = x.coerceIn(halfWidth, screenWidth - halfWidth)
        flameTimer += deltaSeconds * 10f
    }

    fun draw(canvas: Canvas) {
        val halfWidth = width / 2f
        val halfHeight = height / 2f
        val noseY = y - halfHeight
        val tailY = y + halfHeight

        baseSprite?.let { sprite ->
            val left = x - sprite.width / 2f
            val top = y - sprite.height / 2f
            canvas.drawBitmap(sprite, left, top, null)
        } ?: run {
            // Wings
            shapePath.reset()
            shapePath.moveTo(x - halfWidth * 1.1f, y + halfHeight * 0.3f)
            shapePath.lineTo(x, tailY)
            shapePath.lineTo(x + halfWidth * 1.1f, y + halfHeight * 0.3f)
            shapePath.close()
            canvas.drawPath(shapePath, wingPaint)

            // Fuselage
            shapePath.reset()
            shapePath.moveTo(x, noseY) // nose
            shapePath.lineTo(x + halfWidth * 0.6f, y + halfHeight * 0.8f) // right base
            shapePath.lineTo(x - halfWidth * 0.6f, y + halfHeight * 0.8f) // left base
            shapePath.close()

            canvas.drawPath(shapePath, paint)

            // Cockpit bubble
            val cockpitWidth = width * 0.35f
            val cockpitHeight = height * 0.32f
            val cockpitRect = RectF(
                x - cockpitWidth / 2f,
                y - cockpitHeight * 0.2f,
                x + cockpitWidth / 2f,
                y + cockpitHeight * 0.8f
            )
            canvas.drawRoundRect(cockpitRect, cockpitWidth * 0.4f, cockpitWidth * 0.4f, cockpitPaint)
        }

        // Thruster flame with slight flicker
        val flicker = 0.85f + 0.15f * sin(flameTimer)
        val flameWidth = width * 0.28f * flicker
        val flameHeight = height * 0.5f * flicker
        flameSprites?.takeIf { it.isNotEmpty() }?.let { frames ->
            val idx = ((flameTimer % 1f) * frames.size).toInt().coerceIn(0, frames.size - 1)
            val sprite = frames[idx]
            val left = x - sprite.width / 2f
            val top = tailY
            canvas.drawBitmap(sprite, left, top, null)
        } ?: run {
            shapePath.reset()
            shapePath.moveTo(x - flameWidth / 2f, tailY)
            shapePath.lineTo(x + flameWidth / 2f, tailY)
            shapePath.lineTo(x, tailY + flameHeight)
            shapePath.close()
            canvas.drawPath(shapePath, flamePaint)
        }
    }

    fun top(): Float = y - height / 2f
}
