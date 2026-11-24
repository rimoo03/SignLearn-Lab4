package com.example.signlearn

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/** Short sound effects (generated chimes stored in res/raw). */
object Sfx {
    private var pool: SoundPool? = null
    private val ids = HashMap<Int, Int>()

    fun init(context: Context) {
        if (pool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
        val app = context.applicationContext
        for (res in listOf(R.raw.sfx_correct, R.raw.sfx_wrong, R.raw.sfx_complete, R.raw.sfx_pop, R.raw.sfx_tick)) {
            ids[res] = p.load(app, res, 1)
        }
        pool = p
    }

    private fun play(res: Int, volume: Float = 1f) {
        if (!ProgressStore.soundOn) return
        val id = ids[res] ?: return
        pool?.play(id, volume, volume, 1, 0, 1f)
    }

    fun correct() = play(R.raw.sfx_correct)
    fun wrong() = play(R.raw.sfx_wrong, 0.8f)
    fun complete() = play(R.raw.sfx_complete)
    fun pop() = play(R.raw.sfx_pop, 0.6f)
    fun tick() = play(R.raw.sfx_tick, 0.5f)
}
