package info.meuse24.game.entities

import android.graphics.Canvas
import android.graphics.Paint

class Bullet(
    var x: Float,
    var y: Float,
    private val radius: Float,
    private val speed: Float,
    private val paint: Paint
) {
    fun update(deltaSeconds: Float) {
        y -= speed * deltaSeconds
    }

    fun isOffScreen(): Boolean = y + radius < 0f

    fun draw(canvas: Canvas) {
        canvas.drawCircle(x, y, radius, paint)
    }

    fun radius(): Float = radius
}
