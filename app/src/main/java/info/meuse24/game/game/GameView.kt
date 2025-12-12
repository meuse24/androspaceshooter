package info.meuse24.game.game

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.atomic.AtomicBoolean

class GameView(
    context: Context,
    private val externalListener: GameEventListener,
    private val config: GameConfig = GameConfig()
) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private val threadRunning = AtomicBoolean(false)
    private val surfaceReady = AtomicBoolean(false)
    private var thread: Thread? = null
    private var fpsAccumulator = 0f
    private var fpsFrames = 0
    private val soundEffects = SoundEffects()
    private var lastTapTime = 0L
    private val doubleTapInterval = 300L // milliseconds
    private val engine: GameEngine = GameEngine(object : GameEventListener {
        override fun onScoreChanged(score: Int) {
            externalListener.onScoreChanged(score)
        }

        override fun onGameOver(finalScore: Int) {
            gameState = GameState.GAME_OVER
            externalListener.onGameOver(finalScore)
        }

        override fun onFpsChanged(fps: Int) {
            externalListener.onFpsChanged(fps)
        }

        override fun onShockwaveState(ready: Boolean, cooldownFraction: Float) {
            externalListener.onShockwaveState(ready, cooldownFraction)
        }
    }, config, soundEffects)

    private var gameState: GameState = GameState.WAITING

    init {
        holder.addCallback(this)
        isFocusable = true
        holder.setFormat(android.graphics.PixelFormat.RGBA_8888)
    }

    override fun run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)

        var lastTime = System.nanoTime()
        val targetFrameNanos = 1_000_000_000L / config.targetFps
        val targetDelta = 1f / config.targetFps

        while (threadRunning.get()) {
            val frameStartTime = System.nanoTime()
            val deltaSeconds = ((frameStartTime - lastTime).coerceAtMost(50_000_000L)) / 1_000_000_000f
            lastTime = frameStartTime

            fpsAccumulator += deltaSeconds
            fpsFrames++
            if (fpsAccumulator >= 1f) {
                val fps = (fpsFrames / fpsAccumulator).toInt()
                externalListener.onFpsChanged(fps)
                fpsAccumulator = 0f
                fpsFrames = 0
            }

            // Update game state
            if (gameState == GameState.RUNNING) {
                engine.update(deltaSeconds.coerceAtMost(targetDelta * 2f))
            }

            // Render only if surface is ready
            if (surfaceReady.get() && holder.surface.isValid) {
                var canvas: Canvas? = null
                try {
                    canvas = holder.lockCanvas()
                    if (canvas != null) {
                        engine.draw(canvas)
                    }
                } catch (e: Exception) {
                    // Surface might have been destroyed
                } finally {
                    if (canvas != null) {
                        try {
                            holder.unlockCanvasAndPost(canvas)
                        } catch (e: Exception) {
                            // Ignore if surface was already destroyed
                        }
                    }
                }
            }

            // Frame rate limiting with more precise timing
            val frameDuration = System.nanoTime() - frameStartTime
            val sleepNanos = targetFrameNanos - frameDuration

            if (sleepNanos > 0) {
                val sleepMillis = sleepNanos / 1_000_000L
                val sleepNanosRemainder = (sleepNanos % 1_000_000L).toInt()

                try {
                    if (sleepMillis > 0) {
                        Thread.sleep(sleepMillis, sleepNanosRemainder)
                    } else {
                        // For very short sleeps, just yield
                        Thread.yield()
                    }
                } catch (_: InterruptedException) {
                    // exit smoothly
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameState != GameState.RUNNING) return true

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTapTime <= doubleTapInterval) {
                    // Double tap detected - trigger shockwave
                    engine.activateShockwave()
                    lastTapTime = 0L // Reset to prevent triple-tap issues
                } else {
                    lastTapTime = currentTime
                }
                engine.setPlayerTarget(event.x)
            }
            MotionEvent.ACTION_MOVE -> {
                engine.setPlayerTarget(event.x)
            }
        }
        return true
    }

    fun startGame() {
        if (holder.surface.isValid) {
            engine.resize(width, height)
        }
        gameState = GameState.RUNNING
        engine.reset()
        if (thread?.isAlive != true) {
            threadRunning.set(true)
            thread = Thread(this).also {
                it.priority = Thread.MAX_PRIORITY
                it.start()
            }
        }
    }

    fun restartGame() {
        if (holder.surface.isValid) {
            engine.resize(width, height)
        }
        gameState = GameState.RUNNING
        engine.reset()
    }

    fun updateHighscore(highscore: Int) {
        engine.updateHighscore(highscore)
    }

    fun triggerShockwave() {
        engine.activateShockwave()
    }

    fun pauseGame() {
        gameState = GameState.WAITING
    }

    fun stopGameLoop() {
        threadRunning.set(false)
        surfaceReady.set(false)
        try {
            thread?.join(500)
        } catch (e: InterruptedException) {
            // Thread was interrupted, that's ok
        }
        thread = null
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady.set(true)
        engine.resize(width, height)
        soundEffects.initialize() // Restore audio resources if they were released

        // Start game loop if not already running
        if (thread?.isAlive != true && threadRunning.get()) {
            thread = Thread(this).also {
                it.priority = Thread.MAX_PRIORITY
                it.start()
            }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        engine.resize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady.set(false)
        var retry = true
        var retryCount = 0

        // Stop the game loop thread safely
        while (retry && retryCount < 3) {
            try {
                thread?.join(100)
                retry = false
            } catch (e: InterruptedException) {
                retryCount++
            }
        }

        engine.onSurfaceDestroyed()
        soundEffects.release()
    }
}
