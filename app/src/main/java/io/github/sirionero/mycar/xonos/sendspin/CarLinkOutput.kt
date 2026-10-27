package io.github.sirionero.mycar.xonos.sendspin

import io.github.sirionero.mycar.audio.CarAudio
import io.github.sirionero.mycar.audio.DirectFeed

/**
 * L'uscita di Xonos dentro myCar quando l'audio va all'auto tramite myCar: il PCM entra dritto nel
 * canale musicale di Android Auto ([DirectFeed]), senza passare dalle casse del telefono, senza
 * cattura e senza la domanda di Android. Si comporta come l'AudioTrack per [AudioEngine]: scrive
 * bloccando finché c'è posto, dice quanto ha suonato e quando si sentirà un fotogramma.
 */
class CarLinkOutput private constructor(
    private val writer: DirectFeed.Writer,
) : PcmOutput {

    override val sampleRate: Int get() = writer.sampleRate
    override val channels: Int get() = writer.channelCount

    override val latencyUs: Long
        get() = DirectFeed.RING_MS * 1_000L + DirectFeed.CAR_DELAY_US

    override fun play() = Unit

    override fun write(data: ShortArray, offsetFrames: Int, frames: Int): Int = writer.write(data, offsetFrames, frames)

    override fun presentationTimeUs(frame: Long): Long = writer.presentationTimeUs(frame, System.nanoTime() / 1000)

    override fun playedFrames(): Long = writer.playedFrames

    override fun setGain(gain: Float) {
        writer.gain = gain
    }

    override fun interrupt() = writer.interrupt()

    override fun release() = writer.close()

    companion object {
        /** L'uscita verso l'auto, se l'audio va all'auto tramite myCar; null altrimenti (si suona sul telefono). */
        fun open(sampleRate: Int, channels: Int): CarLinkOutput? = CarAudio.openDirect(sampleRate, channels)?.let(::CarLinkOutput)
    }
}
