package info.meuse24.game.entities

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.sin

class Kraken(
    var x: Float,
    var y: Float,
    val radius: Float,
    private val speedX: Float,
    private val sprites: List<Bitmap>
) {
    private val startY = y
    private var timeAlive = 0f
    private val amplitude = radius * 0.5f // Height of the sine wave
    private val frequency = 5f // Speed of the bobbing
    private val animationSpeed = 8f // Frames per second

    fun update(deltaSeconds: Float) {
        x += speedX * deltaSeconds
        timeAlive += deltaSeconds
        // "Swimming" motion: simple sine wave on Y axis
        y = startY + sin(timeAlive * frequency) * amplitude
    }

    fun draw(canvas: Canvas, paint: Paint) {
        if (sprites.isEmpty()) return
        val frameIndex = ((timeAlive * animationSpeed).toInt() % sprites.size)
        val bitmap = sprites[frameIndex]
        
        // Draw the bitmap centered
        canvas.drawBitmap(bitmap, x - radius, y - radius, paint)
    }

    fun isOffScreen(screenWidth: Int): Boolean {
        // Check if it moved past the left or right edge completely
        return if (speedX > 0) {
            x - radius > screenWidth
        } else {
            x + radius < 0
        }
    }
}
