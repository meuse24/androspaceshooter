package info.meuse24.game.entities

import android.graphics.Canvas
import android.graphics.Paint

class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    var radius: Float,
    var paint: Paint
) {
    fun update(deltaSeconds: Float) {
        x += vx * deltaSeconds
        y += vy * deltaSeconds
        life -= deltaSeconds
    }

    fun draw(canvas: Canvas) {
        paint.alpha = (life * 255).toInt().coerceIn(0, 255) // Added fade out effect for better visual
        canvas.drawCircle(x, y, radius, paint)
    }

    fun isDead(): Boolean = life <= 0f

    fun reset(x: Float, y: Float, vx: Float, vy: Float, life: Float, radius: Float, paint: Paint) {
        this.x = x
        this.y = y
        this.vx = vx
        this.vy = vy
        this.life = life
        this.radius = radius
        this.paint = paint
    }
}
