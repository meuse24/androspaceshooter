package info.meuse24.game.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import info.meuse24.game.entities.Bullet
import info.meuse24.game.entities.Kraken
import info.meuse24.game.entities.Meteor
import info.meuse24.game.entities.Particle
import info.meuse24.game.entities.Player
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

class GameEngine(
    private val listener: GameEventListener,
    private val config: GameConfig = GameConfig(),
    private val soundEffects: SoundEffects? = null
) {
    private val random = Random(System.currentTimeMillis())
    private val bullets = mutableListOf<Bullet>()
    private val meteors = mutableListOf<Meteor>()
    private val particles = mutableListOf<Particle>()
    private lateinit var player: Player
    private val stars = mutableListOf<Star>()
    private val planets = mutableListOf<Planet>()
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var playerSprite: Bitmap? = null
    private var playerFlameSprites: List<Bitmap>? = null
    private val meteorSprites = mutableListOf<MeteorSprite>()
    private val particlePaints = mutableListOf<Paint>()
    private var particlePaintIndex = 0
    // Object pool to reduce GC pressure
    private val particlePool = ArrayDeque<Particle>()

    private var kraken: Kraken? = null
    private var krakenTimer = 0f
    private var krakenSprites: List<Bitmap>? = null
    private val krakenPaint = Paint(Paint.ANTI_ALIAS_FLAG) // Helper paint for drawing bitmap

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    private var elapsedSeconds = 0f
    private var fireTimer = 0f
    private var spawnTimer = 0f
    private var score = 0
    private var currentHighscore = 0
    private var state: GameState = GameState.WAITING

    private var shakeDuration = 0f
    private var shakeTimeRemaining = 0f
    private var shakeIntensity = 0f
    private var shakeOffsetX = 0f
    private var shakeOffsetY = 0f
    private var shockwaveActive = false
    private var shockwaveTimer = 0f
    private var shockwaveCooldownRemaining = 0f

    private val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.CYAN }
    private val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val meteorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF7F50") }
    private val backgroundPaint = Paint().apply { color = Color.parseColor("#060910") }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 48f
    }
    private val shockwavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7CE7FF")
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    fun resize(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        initBackground()
        initSprites()
        initPlayer()

        if (particlePaints.isEmpty()) {
            val particleColorVariants = 20
            repeat(particleColorVariants) {
                particlePaints.add(Paint(Paint.ANTI_ALIAS_FLAG))
            }
        }
    }

    fun reset() {
        if (screenWidth == 0 || screenHeight == 0) return
        bullets.clear()
        meteors.clear()
        particles.clear()
        initBackground()
        initSprites()
        elapsedSeconds = 0f
        fireTimer = 0f
        spawnTimer = 0f
        score = 0
        state = GameState.RUNNING
        shockwaveCooldownRemaining = 0f
        shockwaveActive = false
        shockwaveTimer = 0f
        kraken = null
        krakenTimer = 5f + random.nextFloat() * 8f // 5-13s initial delay
        soundEffects?.stopKrakenLoop()
        initPlayer()
        listener.onScoreChanged(score)
        listener.onShockwaveState(ready = true, cooldownFraction = 1f)
    }

    fun setState(newState: GameState) {
        state = newState
    }

    fun updateHighscore(value: Int) {
        currentHighscore = value
    }

    fun update(deltaSeconds: Float) {
        if (state != GameState.RUNNING || !::player.isInitialized) return
        elapsedSeconds += deltaSeconds
        fireTimer += deltaSeconds
        spawnTimer += deltaSeconds
        updateStars(deltaSeconds)
        updateShockwave(deltaSeconds)

        maybeFire()
        maybeSpawnMeteor()
        maybeSpawnKraken(deltaSeconds)
        updateBullets(deltaSeconds)
        updateMeteors(deltaSeconds)
        updateKraken(deltaSeconds)
        updateParticles(deltaSeconds)
        player.update(deltaSeconds, config.playerFollowSpeed, screenWidth)
        updateShake(deltaSeconds)
        detectCollisions()
    }

    fun activateShockwave() {
        if (!::player.isInitialized || state != GameState.RUNNING) return
        if (shockwaveActive || shockwaveCooldownRemaining > 0f) return
        shockwaveActive = true
        shockwaveTimer = 0f
        shockwaveCooldownRemaining = config.shockwaveCooldownSeconds
        soundEffects?.playExplosion()
        listener.onShockwaveState(ready = false, cooldownFraction = 0f)
    }

    fun draw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), backgroundPaint)
        drawPlanets(canvas)
        drawStars(canvas)
        kraken?.draw(canvas, krakenPaint)
        if (!::player.isInitialized) return
        canvas.save()
        canvas.translate(shakeOffsetX, shakeOffsetY)

        if (shockwaveActive) {
            val progress = (shockwaveTimer / config.shockwaveDurationSeconds).coerceIn(0f, 1f)
            val maxRadius = config.shockwaveMaxRadiusFraction * screenWidth
            val currentRadius = maxRadius * easeOut(progress)
            shockwavePaint.alpha = ((1f - progress) * 200).toInt().coerceIn(0, 255)
            shockwavePaint.strokeWidth = screenWidth * 0.01f * (1f - progress).coerceAtLeast(0.4f)
            canvas.drawCircle(player.x, player.y, currentRadius, shockwavePaint)
        }

        for (i in meteors.indices) {
            meteors[i].draw(canvas)
        }
        for (i in bullets.indices) {
            bullets[i].draw(canvas)
        }
        for (i in particles.indices) {
            particles[i].draw(canvas)
        }
        player.draw(canvas)

        canvas.restore()

        if (state == GameState.GAME_OVER) {
            val msg = "Game Over"
            val msgWidth = hudPaint.measureText(msg)
            val x = (screenWidth - msgWidth) / 2f
            val y = screenHeight / 2f
            canvas.drawText(msg, x, y, hudPaint)
        }
    }

    fun setPlayerTarget(x: Float) {
        if (!::player.isInitialized) return
        player.setTarget(x)
    }

    private fun maybeFire() {
        val fireInterval = config.fireIntervalSeconds
        while (fireTimer >= fireInterval) {
            fireTimer -= fireInterval
            spawnBullet()
        }
    }

    private fun maybeSpawnKraken(deltaSeconds: Float) {
        if (kraken != null) return
        krakenTimer -= deltaSeconds
        if (krakenTimer <= 0f) {
            spawnKraken()
            krakenTimer = 8f + random.nextFloat() * 12f // 8-20s interval (Much more frequent)
        }
    }

    private fun spawnKraken() {
        val sprites = krakenSprites ?: return
        val radius = sprites[0].width / 2f
        val startLeft = random.nextBoolean()
        val x = if (startLeft) -radius * 2 else screenWidth + radius * 2
        val y = screenHeight * (0.1f + random.nextFloat() * 0.3f)
        
        // Variable speed: 0.7x (slow/deep) to 1.6x (fast/high)
        val speedFactor = 0.7f + random.nextFloat() * 0.9f
        val baseSpeed = screenWidth * 0.25f
        val speed = baseSpeed * speedFactor * (if (startLeft) 1f else -1f)
        
        kraken = Kraken(x, y, radius, speed, sprites)
        soundEffects?.startKrakenLoop(speedFactor)
    }

    private fun updateKraken(deltaSeconds: Float) {
        kraken?.let { k ->
            k.update(deltaSeconds)
            if (k.isOffScreen(screenWidth)) {
                kraken = null
                soundEffects?.stopKrakenLoop()
            }
        }
    }

    private fun maybeSpawnMeteor() {
        val interval = currentSpawnInterval()
        if (spawnTimer >= interval) {
            spawnTimer -= interval
            spawnMeteor()
        }
    }

    private fun updateBullets(deltaSeconds: Float) {
        var i = bullets.lastIndex
        while (i >= 0) {
            val bullet = bullets[i]
            bullet.update(deltaSeconds)
            if (bullet.isOffScreen()) {
                bullets.removeAt(i)
            }
            i--
        }
    }

    private fun updateMeteors(deltaSeconds: Float) {
        var i = meteors.lastIndex
        while (i >= 0) {
            val meteor = meteors[i]
            meteor.update(deltaSeconds)
            if (meteor.isOffScreen(screenHeight)) {
                meteors.removeAt(i)
            }
            i--
        }
    }

    private fun detectCollisions() {
        var bulletIndex = bullets.lastIndex
        while (bulletIndex >= 0) {
            val bullet = bullets[bulletIndex]
            
            // Kraken Collision
            val k = kraken
            if (k != null) {
                val dx = k.x - bullet.x
                val dy = k.y - bullet.y
                val combined = k.radius * 0.8f + bullet.radius() // Hitbox slightly smaller than sprite
                if (dx * dx + dy * dy <= combined * combined) {
                    kraken = null
                    score += 50
                    listener.onScoreChanged(score)
                    bullets.removeAt(bulletIndex)
                    
                    // Custom explosion for Kraken (more particles, different color)
                    spawnExplosion(k.x, k.y, k.radius, isKraken = true)
                    triggerShake(intensity = 0.8f, duration = 0.25f)
                    soundEffects?.stopKrakenLoop()
                    soundEffects?.playKrakenExplosion()
                    bulletIndex--
                    continue
                }
            }

            var hitMeteor = false
            var meteorIndex = meteors.lastIndex
            while (meteorIndex >= 0) {
                val meteor = meteors[meteorIndex]
                val dx = meteor.x - bullet.x
                val dy = meteor.y - bullet.y
                val combined = meteor.radius() + bullet.radius()
                if (dx * dx + dy * dy <= combined * combined) {
                    meteors.removeAt(meteorIndex)
                    hitMeteor = true
                    score += 1
                    listener.onScoreChanged(score)
                    spawnExplosion(meteor.x, meteor.y, meteor.radius())
                    triggerShake(intensity = 0.5f, duration = 0.18f)
                    soundEffects?.playExplosion()
                    break
                }
                meteorIndex--
            }
            if (hitMeteor) {
                bullets.removeAt(bulletIndex)
            }
            bulletIndex--
        }

        if (state != GameState.RUNNING) return
        val playerHalfWidth = player.width / 2f
        val playerHalfHeight = player.height / 2f
        for (i in meteors.indices) {
            val meteor = meteors[i]
            val xDistance = abs(meteor.x - player.x)
            val playerTop = player.y - playerHalfHeight
            val overlapX = xDistance <= meteor.radius() + playerHalfWidth
            val overlapY = meteor.y + meteor.radius() >= playerTop
            if (overlapX && overlapY) {
                state = GameState.GAME_OVER
                triggerShake(intensity = 1.2f, duration = 0.3f)
                
                soundEffects?.stopKrakenLoop()
                if (score > currentHighscore) {
                    soundEffects?.playHighscore()
                } else {
                    soundEffects?.playGameOver()
                }
                
                listener.onGameOver(score)
                break
            }
        }
    }

    private fun spawnBullet() {
        val radius = screenWidth * config.bulletRadiusFraction
        val speed = config.bulletSpeedPerSecond * screenHeight
        val bullet = Bullet(player.x, player.top() - radius, radius, speed, bulletPaint)
        bullets.add(bullet)
        soundEffects?.playLaser()
    }

    private fun spawnMeteor() {
        val sprite = meteorSprites.random(random)
        val radius = sprite.radius
        val meteorSpeed = (config.meteorBaseSpeedPerSecond + elapsedSeconds * config.meteorSpeedRampPerSecond)
            .coerceAtMost(1.5f) * screenHeight
        val x = random.nextFloat() * (screenWidth - 2f * radius) + radius
        val meteor = Meteor(x, -radius, radius, meteorSpeed, meteorPaint, sprite.bitmap)
        meteors.add(meteor)
    }

    private fun initPlayer() {
        val width = config.playerWidthFraction * screenWidth
        val height = config.playerHeightFraction * screenHeight
        val x = screenWidth / 2f
        val y = screenHeight - height * 1.5f
        player = Player(x, y, width, height, playerPaint, playerSprite, playerFlameSprites)
    }

    private fun currentSpawnInterval(): Float {
        val intervalReduction = elapsedSeconds * config.spawnAccelerationPerSecond
        val target = config.spawnBaseIntervalSeconds - intervalReduction
        return target.coerceAtLeast(config.spawnMinIntervalSeconds)
    }

    fun currentScore(): Int = score

    fun onSurfaceDestroyed() {
        state = GameState.WAITING
        soundEffects?.stopKrakenLoop()
    }

    private fun updateParticles(deltaSeconds: Float) {
        var i = particles.lastIndex
        while (i >= 0) {
            val p = particles[i]
            p.update(deltaSeconds)
            if (p.isDead()) {
                particles.removeAt(i)
                if (particlePool.size < 200) { // Limit pool size to prevent memory waste
                    particlePool.add(p)
                }
            }
            i--
        }
    }

    private fun spawnExplosion(x: Float, y: Float, objectRadius: Float, isKraken: Boolean = false) {
        val count = if (isKraken) random.nextInt(30, 45) else random.nextInt(10, 18)
        for (i in 0 until count) {
            val angle = random.nextFloat() * (Math.PI * 2).toFloat()
            val speed = (screenWidth * 0.3f) * (0.5f + random.nextFloat()) * (if (isKraken) 1.5f else 1f)
            val vx = (kotlin.math.cos(angle.toDouble()) * speed).toFloat()
            val vy = (kotlin.math.sin(angle.toDouble()) * speed).toFloat()
            val life = 0.3f + random.nextFloat() * 0.35f
            val radius = objectRadius * 0.15f
            
            val paint: Paint
            if (isKraken) {
                // Use existing paints but maybe override color temporarily or just pick a "Kraken-like" color
                // Since we reuse paints, let's just pick one and set color
                paint = particlePaints[particlePaintIndex]
                paint.color = Color.rgb(random.nextInt(100, 200), 255, random.nextInt(100, 200)) // Greenish
            } else {
                paint = particlePaints[particlePaintIndex]
                paint.color = Color.rgb(255, random.nextInt(80, 180), random.nextInt(40, 120)) // Orange/Red
            }
            particlePaintIndex = (particlePaintIndex + 1) % particlePaints.size

            val p = particlePool.removeLastOrNull()
            if (p != null) {
                p.reset(x, y, vx, vy, life, radius, paint)
                particles.add(p)
            } else {
                particles.add(
                    Particle(
                        x = x,
                        y = y,
                        vx = vx,
                        vy = vy,
                        life = life,
                        radius = radius,
                        paint = paint
                    )
                )
            }
        }
    }

    private fun updateShake(deltaSeconds: Float) {
        if (shakeTimeRemaining <= 0f) {
            shakeOffsetX = 0f
            shakeOffsetY = 0f
            return
        }
        shakeTimeRemaining = (shakeTimeRemaining - deltaSeconds).coerceAtLeast(0f)
        val progress = (shakeTimeRemaining / shakeDuration).coerceIn(0f, 1f)
        val magnitude = shakeIntensity * progress
        val maxOffset = screenWidth * 0.012f * magnitude
        shakeOffsetX = (random.nextFloat() * 2f - 1f) * maxOffset
        shakeOffsetY = (random.nextFloat() * 2f - 1f) * maxOffset
    }

    private fun updateShockwave(deltaSeconds: Float) {
        val cooldown = config.shockwaveCooldownSeconds
        if (cooldown > 0f) {
            shockwaveCooldownRemaining = (shockwaveCooldownRemaining - deltaSeconds).coerceAtLeast(0f)
            val fraction = if (cooldown <= 0f) 1f else 1f - (shockwaveCooldownRemaining / cooldown).coerceIn(0f, 1f)
            listener.onShockwaveState(ready = shockwaveCooldownRemaining <= 0f, cooldownFraction = fraction)
        } else {
            listener.onShockwaveState(ready = true, cooldownFraction = 1f)
        }

        if (!shockwaveActive) return
        shockwaveTimer += deltaSeconds
        val progress = (shockwaveTimer / config.shockwaveDurationSeconds).coerceIn(0f, 1f)
        val maxRadius = config.shockwaveMaxRadiusFraction * screenWidth
        val currentRadius = maxRadius * easeOut(progress)
        applyShockwaveToMeteors(currentRadius, deltaSeconds, progress)
        if (shockwaveTimer >= config.shockwaveDurationSeconds) {
            shockwaveActive = false
            shockwaveTimer = 0f
        }
    }

    private fun applyShockwaveToMeteors(currentRadius: Float, deltaSeconds: Float, progress: Float) {
        val destroyThreshold = screenWidth * config.shockwaveDestroyRadiusFraction
        val push = screenHeight * config.shockwavePushPerSecond * (1f - progress)
        var i = meteors.lastIndex
        while (i >= 0) {
            val meteor = meteors[i]
            val dx = meteor.x - player.x
            val dy = meteor.y - player.y
            val distSq = dx * dx + dy * dy
            if (distSq <= currentRadius * currentRadius) {
                if (meteor.radius() <= destroyThreshold) {
                    meteors.removeAt(i)
                    score += 1
                    listener.onScoreChanged(score)
                    spawnExplosion(meteor.x, meteor.y, meteor.radius())
                } else {
                    val dist = sqrt(distSq.toDouble()).toFloat().coerceAtLeast(1f)
                    val nx = dx / dist
                    val ny = dy / dist
                    meteor.x += nx * push * deltaSeconds
                    meteor.y += ny * push * deltaSeconds
                }
            }
            i--
        }
    }

    private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

    private fun triggerShake(intensity: Float, duration: Float) {
        shakeIntensity = intensity
        shakeDuration = duration
        shakeTimeRemaining = duration
    }

    private fun initBackground() {
        stars.clear()
        planets.clear()
        if (screenWidth == 0 || screenHeight == 0) return
        val starCount = 120
        repeat(starCount) {
            stars.add(
                Star(
                    x = random.nextFloat() * screenWidth,
                    y = random.nextFloat() * screenHeight,
                    radius = screenWidth * (0.0015f + random.nextFloat() * 0.0025f),
                    speedMultiplier = 0.25f + random.nextFloat() * 0.8f,
                    alpha = (160 + random.nextInt(95)).coerceAtMost(255)
                )
            )
        }

        val planetCount = 2
        repeat(planetCount) { index ->
            val r = screenWidth * (0.08f + random.nextFloat() * 0.12f)
            val x = screenWidth * (0.2f + random.nextFloat() * 0.6f)
            val y = screenHeight * (0.2f + random.nextFloat() * 0.4f) + index * screenHeight * 0.1f
            val innerColor = Color.parseColor(if (index == 0) "#2E8DD8" else "#A855F7")
            val outerColor = Color.parseColor("#0B1120")
            val gradient = RadialGradient(
                x,
                y,
                r,
                intArrayOf(innerColor, innerColor, outerColor),
                floatArrayOf(0f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = gradient
            }
            planets.add(Planet(x, y, r, paint))
        }
    }

    private fun updateStars(deltaSeconds: Float) {
        if (stars.isEmpty() || screenHeight == 0) return
        val baseSpeed = screenHeight * 0.08f
        for (i in stars.indices) {
            val star = stars[i]
            star.y += baseSpeed * star.speedMultiplier * deltaSeconds
            if (star.y > screenHeight) {
                star.y = -star.radius
                star.x = random.nextFloat() * screenWidth
            }
        }
    }

    private fun drawStars(canvas: Canvas) {
        for (i in stars.indices) {
            val star = stars[i]
            starPaint.alpha = star.alpha
            canvas.drawCircle(star.x, star.y, star.radius, starPaint)
        }
    }

    private fun drawPlanets(canvas: Canvas) {
        for (i in planets.indices) {
            val planet = planets[i]
            canvas.drawCircle(planet.x, planet.y, planet.radius, planet.paint)
        }
    }

    private fun initSprites() {
        if (screenWidth == 0 || screenHeight == 0) return
        val playerWidth = (config.playerWidthFraction * screenWidth).toInt().coerceAtLeast(8)
        val playerHeight = (config.playerHeightFraction * screenHeight).toInt().coerceAtLeast(8)
        playerSprite = createPlayerSprite(playerWidth, playerHeight)
        playerFlameSprites = createFlameSprites(playerWidth, playerHeight)

        // Kraken Sprite
        val krakenSize = (screenWidth * 0.12f).toInt().coerceAtLeast(16)
        krakenSprites = createKrakenSprites(krakenSize)

        meteorSprites.clear()
        val min = config.meteorMinRadiusFraction * screenWidth
        val max = config.meteorMaxRadiusFraction * screenWidth
        val variants = 12
        val palette = listOf(
            Color.parseColor("#FF7F50"),
            Color.parseColor("#6EE7B7"),
            Color.parseColor("#93C5FD"),
            Color.parseColor("#A855F7"),
            Color.parseColor("#FCD34D")
        )
        repeat(variants) { index ->
            val radius = min + (max - min) * (index.toFloat() / (variants - 1).coerceAtLeast(1))
            val color = palette[index % palette.size]
            val bitmap = createMeteorSprite(radius, color)
            meteorSprites.add(MeteorSprite(bitmap, radius))
        }
    }

    private fun createPlayerSprite(width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val x = width / 2f
        val y = height / 2f
        val halfWidth = width / 2f
        val halfHeight = height / 2f
        val noseY = y - halfHeight
        val tailY = y + halfHeight
        val wingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2DD4BF.toInt() }
        val cockpitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFBDE0FE.toInt() }

        val path = android.graphics.Path()
        // Wings
        path.moveTo(x - halfWidth * 1.1f, y + halfHeight * 0.3f)
        path.lineTo(x, tailY)
        path.lineTo(x + halfWidth * 1.1f, y + halfHeight * 0.3f)
        path.close()
        canvas.drawPath(path, wingPaint)

        // Fuselage
        path.reset()
        path.moveTo(x, noseY)
        path.lineTo(x + halfWidth * 0.6f, y + halfHeight * 0.8f)
        path.lineTo(x - halfWidth * 0.6f, y + halfHeight * 0.8f)
        path.close()
        canvas.drawPath(path, playerPaint)

        // Cockpit bubble
        val cockpitWidth = width * 0.35f
        val cockpitHeight = height * 0.32f
        val cockpitRect = android.graphics.RectF(
            x - cockpitWidth / 2f,
            y - cockpitHeight * 0.2f,
            x + cockpitWidth / 2f,
            y + cockpitHeight * 0.8f
        )
        canvas.drawRoundRect(cockpitRect, cockpitWidth * 0.4f, cockpitWidth * 0.4f, cockpitPaint)

        return bmp
    }

    private fun createFlameSprites(shipWidth: Int, shipHeight: Int): List<Bitmap> {
        val frames = mutableListOf<Bitmap>()
        val baseWidth = shipWidth * 0.28f
        val baseHeight = shipHeight * 0.5f
        val tailY = shipHeight.toFloat()
        val colors = intArrayOf(0xFFFF7A18.toInt(), 0xFFFF9E2C.toInt())
        val flamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                shipWidth / 2f,
                tailY,
                baseWidth * 1.2f,
                colors,
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val flickers = listOf(0.8f, 1f, 1.15f)
        for (i in flickers.indices) {
            val flicker = flickers[i]
            val bmp = Bitmap.createBitmap(shipWidth, (shipHeight * 1.6f).toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val flameWidth = baseWidth * flicker
            val flameHeight = baseHeight * flicker
            val path = android.graphics.Path()
            path.moveTo(shipWidth / 2f - flameWidth / 2f, tailY)
            path.lineTo(shipWidth / 2f + flameWidth / 2f, tailY)
            path.lineTo(shipWidth / 2f, tailY + flameHeight)
            path.close()
            canvas.drawPath(path, flamePaint)
            frames.add(bmp)
        }
        return frames
    }

    private fun createMeteorSprite(radius: Float, baseColor: Int): Bitmap {
        val diameter = (radius * 2f).toInt().coerceAtLeast(4)
        val bmp = Bitmap.createBitmap(diameter, diameter, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val center = radius
        val path = android.graphics.Path()
        val random = Random(System.nanoTime())
        val sides = random.nextInt(7, 13)
        val angleStep = (Math.PI * 2 / sides).toFloat()
        val jitter = 0.35f

        for (i in 0 until sides) {
            val angle = angleStep * i + random.nextFloat() * angleStep * 0.2f
            val r = radius * (1f - jitter + random.nextFloat() * jitter * 2f)
            val px = center + (kotlin.math.cos(angle.toDouble()) * r).toFloat()
            val py = center + (kotlin.math.sin(angle.toDouble()) * r).toFloat()
            if (i == 0) {
                path.moveTo(px, py)
            } else {
                path.lineTo(px, py)
            }
        }
        path.close()

        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFF4B2E1B.toInt()
            strokeWidth = radius * 0.12f
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                center,
                center,
                radius * 1.05f,
                intArrayOf(adjustColor(baseColor, 1.05f), adjustColor(baseColor, 0.8f)),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val craterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5F4230.toInt() }
        val cracksPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFFFFB169.toInt()
            strokeWidth = radius * 0.05f
        }

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, rimPaint)

        data class Crater(val x: Float, val y: Float, val r: Float)
        val craters = mutableListOf<Crater>()
        val craterCount = random.nextInt(3, 6)
        repeat(craterCount) {
            val r = radius * random.nextFloat() * 0.2f + radius * 0.08f
            val angle = random.nextFloat() * Math.PI * 2f
            val dist = radius * (0.2f + random.nextFloat() * 0.5f)
            val cx = center + (kotlin.math.cos(angle) * dist).toFloat()
            val cy = center + (kotlin.math.sin(angle) * dist).toFloat()
            craters.add(Crater(cx, cy, r))
        }
        for (i in craters.indices) {
            val crater = craters[i]
            canvas.drawCircle(crater.x, crater.y, crater.r, craterPaint)
        }

        data class Crack(val x1: Float, val y1: Float, val x2: Float, val y2: Float)
        val cracks = mutableListOf<Crack>()
        val crackCount = random.nextInt(3, 5)
        repeat(crackCount) {
            val startAngle = random.nextFloat() * Math.PI * 2f
            val endAngle = startAngle + (random.nextFloat() * 0.8f - 0.4f)
            val startRadius = radius * (0.1f + random.nextFloat() * 0.5f)
            val endRadius = radius * (0.3f + random.nextFloat() * 0.4f)
            val x1 = center + (kotlin.math.cos(startAngle) * startRadius).toFloat()
            val y1 = center + (kotlin.math.sin(startAngle) * startRadius).toFloat()
            val x2 = center + (kotlin.math.cos(endAngle) * endRadius).toFloat()
            val y2 = center + (kotlin.math.sin(endAngle) * endRadius).toFloat()
            cracks.add(Crack(x1, y1, x2, y2))
        }
        for (i in cracks.indices) {
            val crack = cracks[i]
            canvas.drawLine(crack.x1, crack.y1, crack.x2, crack.y2, cracksPaint)
        }

        return bmp
    }

    private fun createKrakenSprites(size: Int): List<Bitmap> {
        val frames = mutableListOf<Bitmap>()
        val frameCount = 4
        
        for (f in 0 until frameCount) {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val center = size / 2f
            val bodyRadius = size * 0.35f
            
            // Colors
            val headColor = 0xFF8E44AD.toInt() // Purple
            val spotColor = 0xFF9B59B6.toInt() // Lighter Purple
            val eyeColor = 0xFF2ECC71.toInt() // Green
            
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = headColor }
            val tentaclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
                color = headColor
                style = Paint.Style.STROKE
                strokeWidth = size * 0.08f
                strokeCap = Paint.Cap.ROUND
            }
            val spotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = spotColor }
            val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = eyeColor }

            // Draw Tentacles (Wiggly lines)
            val path = android.graphics.Path()
            val tentacleCount = 5
            val angleStep = Math.PI.toFloat() / (tentacleCount - 1)
            
            for (i in 0 until tentacleCount) {
                val angle = Math.PI.toFloat() + i * angleStep // Pointing down/back
                val startX = center + kotlin.math.cos(angle.toDouble()).toFloat() * bodyRadius * 0.5f
                val startY = center + kotlin.math.sin(angle.toDouble()).toFloat() * bodyRadius * 0.5f
                
                path.reset()
                path.moveTo(startX, startY)
                
                val endX = center + kotlin.math.cos(angle.toDouble()).toFloat() * size * 0.5f
                val endY = center + kotlin.math.sin(angle.toDouble()).toFloat() * size * 0.5f
                
                // Animate control point
                val phase = (f.toFloat() / frameCount) * Math.PI * 2
                val wiggle = kotlin.math.sin(phase + i).toFloat() * size * 0.1f
                
                val controlX = startX + (random.nextFloat() - 0.5f) * size * 0.2f + wiggle
                val controlY = startY + size * 0.1f
                
                path.quadTo(controlX, controlY, endX, endY)
                canvas.drawPath(path, tentaclePaint)
            }

            // Draw Head
            canvas.drawCircle(center, center, bodyRadius, bodyPaint)
            
            // Draw Spots (Deterministic for consistency across frames)
            val spotRandom = Random(12345)
            repeat(3) {
                val r = bodyRadius * 0.2f
                val angle = spotRandom.nextFloat() * Math.PI.toFloat() * 2
                val dist = spotRandom.nextFloat() * bodyRadius * 0.6f
                canvas.drawCircle(
                    center + kotlin.math.cos(angle.toDouble()).toFloat() * dist, 
                    center + kotlin.math.sin(angle.toDouble()).toFloat() * dist, 
                    r, spotPaint
                )
            }

            // Draw Eyes
            val eyeOffsetX = bodyRadius * 0.4f
            val eyeOffsetY = bodyRadius * 0.1f
            canvas.drawCircle(center - eyeOffsetX, center - eyeOffsetY, bodyRadius * 0.15f, eyePaint)
            canvas.drawCircle(center + eyeOffsetX, center - eyeOffsetY, bodyRadius * 0.15f, eyePaint)
            
            frames.add(bmp)
        }

        return frames
    }

    private fun adjustColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    private data class Star(
        var x: Float,
        var y: Float,
        val radius: Float,
        val speedMultiplier: Float,
        val alpha: Int
    )

    private data class Planet(
        val x: Float,
        val y: Float,
        val radius: Float,
        val paint: Paint
    )

    private data class MeteorSprite(
        val bitmap: Bitmap,
        val radius: Float
    )
}
