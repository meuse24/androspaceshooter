package info.meuse24.game.game

/**
 * Tunable gameplay constants for the vertical shooter.
 * Adjust these values to change pacing and difficulty.
 */
data class GameConfig(
    val targetFps: Int = 60,
    val playerWidthFraction: Float = 0.12f,
    val playerHeightFraction: Float = 0.06f,
    val playerFollowSpeed: Float = 8f, // how fast the ship lerps to the finger position
    val bulletSpeedPerSecond: Float = 0.9f, // fraction of screen height per second
    val bulletRadiusFraction: Float = 0.01f,
    val fireIntervalSeconds: Float = 0.18f,
    val meteorMinRadiusFraction: Float = 0.03f,
    val meteorMaxRadiusFraction: Float = 0.08f,
    val meteorBaseSpeedPerSecond: Float = 0.18f,
    val meteorSpeedRampPerSecond: Float = 0.02f,
    val spawnBaseIntervalSeconds: Float = 0.8f,
    val spawnMinIntervalSeconds: Float = 0.25f,
    val spawnAccelerationPerSecond: Float = 0.03f,
    val shockwaveCooldownSeconds: Float = 12f,
    val shockwaveDurationSeconds: Float = 0.65f,
    val shockwaveMaxRadiusFraction: Float = 0.8f,
    val shockwaveDestroyRadiusFraction: Float = 0.045f,
    val shockwavePushPerSecond: Float = 0.6f
)
