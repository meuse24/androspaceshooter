package info.meuse24.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import info.meuse24.game.game.GameEventListener
import info.meuse24.game.game.GameState
import info.meuse24.game.ui.GameScreen
import info.meuse24.game.ui.theme.GameTheme
import info.meuse24.game.utils.HighscoreRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GameTheme {
                val scoreState = remember { mutableIntStateOf(0) }
                val fpsState = remember { mutableIntStateOf(0) }
                val gameState = remember { mutableStateOf(GameState.WAITING) }
                val highscoreRepo = remember { HighscoreRepository(applicationContext) }
                val highscoreState = remember { mutableIntStateOf(highscoreRepo.get()) }
                val shockwaveReady = remember { mutableStateOf(true) }
                val shockwaveCharge = remember { mutableFloatStateOf(1f) }

                val listener = remember {
                    object : GameEventListener {
                        override fun onScoreChanged(score: Int) {
                            scoreState.intValue = score
                        }

                        override fun onGameOver(finalScore: Int) {
                            scoreState.intValue = finalScore
                            highscoreState.intValue = highscoreRepo.saveIfHigher(finalScore)
                            gameState.value = GameState.GAME_OVER
                        }

                        override fun onFpsChanged(fps: Int) {
                            fpsState.intValue = fps
                        }

                        override fun onShockwaveState(ready: Boolean, cooldownFraction: Float) {
                            shockwaveReady.value = ready
                            shockwaveCharge.floatValue = cooldownFraction.coerceIn(0f, 1f)
                        }
                    }
                }

                GameScreen(
                    scoreState = scoreState,
                    fpsState = fpsState,
                    highscoreState = highscoreState,
                    shockwaveReady = shockwaveReady,
                    shockwaveCharge = shockwaveCharge,
                    gameState = gameState,
                    eventListener = listener,
                    onStart = {
                        scoreState.intValue = 0
                        gameState.value = GameState.RUNNING
                    },
                    onRestart = {
                        scoreState.intValue = 0
                        gameState.value = GameState.RUNNING
                    },
                    onShockwave = { /* handled by GameView trigger */ }
                )
            }
        }
    }
}
