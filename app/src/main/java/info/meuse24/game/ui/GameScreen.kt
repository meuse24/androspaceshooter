package info.meuse24.game.ui

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import info.meuse24.game.game.GameEventListener
import info.meuse24.game.game.GameState
import info.meuse24.game.game.GameView

@Composable
fun GameScreen(
    scoreState: State<Int>,
    fpsState: State<Int>,
    highscoreState: State<Int>,
    shockwaveReady: State<Boolean>,
    shockwaveCharge: State<Float>,
    gameState: MutableState<GameState>,
    eventListener: GameEventListener,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    onShockwave: () -> Unit
) {
    val gameView = remember { mutableStateOf<GameView?>(null) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose { gameView.value?.stopGameLoop() }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = {
                    GameView(context, eventListener).also { view ->
                        view.layoutParams = android.widget.FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                        gameView.value = view
                    }
                },
                update = { view -> 
                    gameView.value = view
                    view.updateHighscore(highscoreState.value)
                },
                modifier = Modifier.fillMaxSize()
            )

            ScoreHud(
                score = scoreState.value,
                highscore = highscoreState.value,
                fps = fpsState.value,
                modifier = Modifier.align(Alignment.TopCenter)
            )


            AnimatedVisibility(visible = gameState.value == GameState.WAITING) {
                OverlayCard(
                    title = "Space Shooter",
                    buttonText = "Start Game",
                    highscore = highscoreState.value
                ) {
                    onStart()
                    gameView.value?.startGame()
                }
            }

            AnimatedVisibility(visible = gameState.value == GameState.GAME_OVER) {
                OverlayCard(
                    title = "Game Over",
                    buttonText = "Restart",
                    score = scoreState.value,
                    highscore = highscoreState.value
                ) {
                    onRestart()
                    gameView.value?.restartGame()
                }
            }
        }
    }
}

@Composable
private fun ScoreHud(score: Int, highscore: Int, fps: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 48.dp)
            .padding(horizontal = 12.dp)
    ) {
        Text(
            text = "SC:$score High:$highscore FPS:$fps",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .background(color = Color(0x66000000), shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun OverlayCard(
    title: String,
    buttonText: String,
    score: Int? = null,
    highscore: Int? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .background(Color(0xFF111827), shape = RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
            )
            score?.let {
                Spacer(Modifier.height(12.dp))
                Text("Score: $it", style = MaterialTheme.typography.titleMedium, color = Color.LightGray)
            }
            highscore?.let {
                Spacer(Modifier.height(8.dp))
                Text("HIGH: $it", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF94A3B8))
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClick) {
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ShockwaveButton(
    ready: Boolean,
    charge: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clamped = charge.coerceIn(0f, 1f)
    val bgColor = if (ready) Color(0xFF38BDF8) else Color(0xFF1E293B)
    val text = if (ready) "Shockwave Ready" else "Charging"
    Box(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(0.6f)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .background(Color(0x99000000), shape = RoundedCornerShape(14.dp))
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onClick,
                enabled = ready,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Color(0x33FFFFFF), shape = RoundedCornerShape(12.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(clamped)
                        .height(8.dp)
                        .background(bgColor, shape = RoundedCornerShape(12.dp))
                )
            }
        }
    }
}
