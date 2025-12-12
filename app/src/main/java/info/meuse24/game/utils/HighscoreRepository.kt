package info.meuse24.game.utils

import android.content.Context

class HighscoreRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): Int = prefs.getInt(KEY_HIGHSCORE, 0)

    fun saveIfHigher(score: Int): Int {
        val current = get()
        if (score > current) {
            prefs.edit().putInt(KEY_HIGHSCORE, score).apply()
            return score
        }
        return current
    }

    private companion object {
        const val PREFS_NAME = "game_highscores"
        const val KEY_HIGHSCORE = "highscore"
    }
}
