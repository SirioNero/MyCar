package io.github.sirionero.mycar.xonos.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Il diritto di suonare, condiviso con le altre app. Le indicazioni del navigatore abbassano la
 * musica da sole (ci pensa Android); una telefonata o l'assistente la mettono in pausa, e la
 * musica riprende quando finiscono; un'altra app musicale la ferma.
 */
class AudioFocus(context: Context, private val onChange: (Change) -> Unit) {

    enum class Change { LOST, PAUSED, RESUMED }

    private val manager = context.getSystemService(AudioManager::class.java)
    private var held = false

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setOnAudioFocusChangeListener({ change -> handle(change) }, Handler(Looper.getMainLooper()))
        .build()

    /** Chiede di suonare; false se in questo momento non si può (per esempio durante una telefonata). */
    fun request(): Boolean {
        if (held) return true
        held = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return held
    }

    fun abandon() {
        if (!held) return
        manager.abandonAudioFocusRequest(request)
        held = false
    }

    private fun handle(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                held = false
                onChange(Change.LOST)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onChange(Change.PAUSED)
            AudioManager.AUDIOFOCUS_GAIN -> onChange(Change.RESUMED)
        }
    }
}
