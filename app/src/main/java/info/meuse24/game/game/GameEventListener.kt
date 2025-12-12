package info.meuse24.game.game

/**
 * Callbacks used by the engine to inform the UI about score and lifecycle events.
 */
interface GameEventListener {
    fun onScoreChanged(score: Int)
    fun onGameOver(finalScore: Int)
    fun onFpsChanged(fps: Int)
    fun onShockwaveState(ready: Boolean, cooldownFraction: Float)
}
