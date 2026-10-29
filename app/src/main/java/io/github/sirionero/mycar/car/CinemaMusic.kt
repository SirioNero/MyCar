package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.media.CarMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The music that was playing when Cinema opened, started again when Cinema closes. A film takes
 * the speakers, and the music app stops for good, as it does for any player that takes them; so
 * leaving Cinema, after a film or a site's video, the music came back only with a tap.
 */
internal object CinemaMusic {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** What [opened] saw: the music app, while it played. */
    class Playing internal constructor(val component: android.content.ComponentName)

    /** Cinema opens: the music app, if it's playing now. */
    fun opened(): Playing? {
        val app = CarServices.mediaApp.value ?: return null
        return if (CarServices.media.now.value.playing) Playing(app.component) else null
    }

    /** Cinema closed: the music [was] playing goes on, if it stopped meanwhile and is still the car's. */
    fun closed(was: Playing?) {
        was ?: return
        scope.launch {
            // The film's player lets the speakers go as it closes: the music asks for them after.
            delay(RESUME_AFTER_MS)
            val media = CarServices.media
            if (CarServices.mediaApp.value?.component != was.component || media.phase.value != CarMedia.Phase.READY) return@launch
            if (media.now.value.playing) return@launch
            MyCarLog.i("cinema: closed - the music that was playing goes on")
            media.play()
        }
    }

    private const val RESUME_AFTER_MS = 500L
}
