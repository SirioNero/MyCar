package io.github.sirionero.mycar

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Android Auto frame layout, transcribed from aasdk.
 *
 * FrameHeader::getData (src/Messenger/FrameHeader.cpp:47-55),
 * FrameSize::getData (src/Messenger/FrameSize.cpp:49-62) and
 * MessageOutStream::compoundFrame (src/Messenger/MessageOutStream.cpp:100-120):
 *
 *   byte 0     channel id
 *   byte 1     frameType | encryption | messageType
 *   bytes 2-3  uint16 BE  payload length of THIS frame
 *   bytes 4-7  uint32 BE  total message length - present ONLY when frameType == FIRST
 *   payload    uint16 BE message id, then the body
 */
object Frames {

    const val CHANNEL_CONTROL = 0

    const val TYPE_MIDDLE = 0
    const val TYPE_FIRST = 1
    const val TYPE_LAST = 2
    const val TYPE_BULK = 3

    const val ENCRYPTED = 1 shl 3

    /**
     * Message type bit. Channel-open messages are CONTROL even when sent on a service
     * channel - aasdk sets MessageType::CONTROL only in sendChannelOpenResponse and
     * MessageType::SPECIFIC everywhere else (e.g.
     * src/Channel/MediaSink/Video/VideoMediaSinkService.cpp:52 vs :66). Getting this wrong
     * makes the head unit drop the USB link outright rather than answer.
     */
    const val MESSAGE_SPECIFIC = 0
    const val MESSAGE_CONTROL = 1 shl 2

    /**
     * How big a frame the phone sends, header included, before TLS: Android Auto's own budget.
     * Split this way, the first frame of a long message carries 16120 bytes and the others 16124,
     * as a real phone's do; at aasdk's 0x4000 plus the header and TLS, frames came out larger
     * than any the car sees from Android Auto.
     */
    private const val FRAGMENT_SIZE = 0x3f00
    private const val HEADER_SIZE = 4
    private const val FIRST_HEADER_SIZE = 8

    /** The longest message that goes out in one frame. */
    const val MAX_FRAME_PAYLOAD = FRAGMENT_SIZE - HEADER_SIZE

    class Frame(
        val channel: Int,
        val frameType: Int,
        val encrypted: Boolean,
        val messageType: Int,
        val payload: ByteArray,
    )

    /**
     * Builds one frame. [totalSize] is the full plaintext message length and is written only
     * for FIRST frames, matching aasdk's MessageOutStream::setFrameSize
     * (`FrameSize(payloadSize, totalSize)` for FIRST, `FrameSize(payloadSize)` otherwise).
     */
    fun build(
        channel: Int,
        encrypted: Boolean,
        payload: ByteArray,
        messageType: Int = MESSAGE_SPECIFIC,
        frameType: Int = TYPE_BULK,
        totalSize: Int? = null,
    ): ByteArray {
        val flags = frameType or messageType or (if (encrypted) ENCRYPTED else 0)
        val header = byteArrayOf(
            channel.toByte(),
            flags.toByte(),
            ((payload.size shr 8) and 0xFF).toByte(),
            (payload.size and 0xFF).toByte(),
        )
        if (frameType != TYPE_FIRST || totalSize == null) return header + payload
        val extended = byteArrayOf(
            ((totalSize shr 24) and 0xFF).toByte(),
            ((totalSize shr 16) and 0xFF).toByte(),
            ((totalSize shr 8) and 0xFF).toByte(),
            (totalSize and 0xFF).toByte(),
        )
        return header + extended + payload
    }

    /**
     * Writes one message as frames, as aasdk does: the *plaintext* is split, each piece is
     * encrypted on its own, and the FIRST frame carries the whole plaintext length. Returns how
     * many frames went out.
     */
    fun writeMessage(
        output: OutputStream,
        channel: Int,
        encrypted: Boolean,
        messageType: Int,
        message: ByteArray,
        encrypt: (ByteArray) -> ByteArray,
    ): Int {
        val fragmented = message.size > MAX_FRAME_PAYLOAD
        var offset = 0
        var frames = 0
        do {
            val first = fragmented && offset == 0
            val end = minOf(offset + FRAGMENT_SIZE - if (first) FIRST_HEADER_SIZE else HEADER_SIZE, message.size)
            val type = when {
                !fragmented -> TYPE_BULK
                first -> TYPE_FIRST
                end == message.size -> TYPE_LAST
                else -> TYPE_MIDDLE
            }
            val chunk = message.copyOfRange(offset, end)
            output.write(
                build(
                    channel, encrypted, if (encrypted) encrypt(chunk) else chunk, messageType, type,
                    if (type == TYPE_FIRST) message.size else null,
                ),
            )
            offset = end
            frames++
        } while (offset < message.size)
        return frames
    }

    /**
     * Pulls whole frames out of an accumulating buffer.
     *
     * A read() on the accessory descriptor returns one bulk transfer, which is usually one
     * frame - but nothing guarantees it, so this never assumes alignment.
     */
    class Parser {
        private var pending = ByteArray(0)

        fun append(data: ByteArray, length: Int) {
            val grown = ByteArray(pending.size + length)
            System.arraycopy(pending, 0, grown, 0, pending.size)
            System.arraycopy(data, 0, grown, pending.size, length)
            pending = grown
        }

        fun next(): Frame? {
            if (pending.size < 4) return null
            val channel = pending[0].toInt() and 0xFF
            val flags = pending[1].toInt() and 0xFF
            val frameType = flags and TYPE_BULK
            // Extended size is written only for FIRST - BULK (3) keeps the short header.
            val headerLength = if (frameType == TYPE_FIRST) 8 else 4
            if (pending.size < headerLength) return null

            val frameSize = ((pending[2].toInt() and 0xFF) shl 8) or (pending[3].toInt() and 0xFF)
            val total = headerLength + frameSize
            if (pending.size < total) return null

            val payload = pending.copyOfRange(headerLength, total)
            pending = pending.copyOfRange(total, pending.size)
            return Frame(
                channel, frameType, (flags and ENCRYPTED) != 0, flags and MESSAGE_CONTROL, payload,
            )
        }
    }

    /** Rejoins FIRST/MIDDLE/LAST runs. Payloads arrive already decrypted. */
    class Assembler {
        private val partial = HashMap<Int, ByteArrayOutputStream>()

        fun offer(frame: Frame, payload: ByteArray): ByteArray? = when (frame.frameType) {
            TYPE_BULK -> payload
            TYPE_FIRST -> {
                partial[frame.channel] = ByteArrayOutputStream().apply { write(payload) }
                null
            }
            TYPE_MIDDLE -> {
                partial[frame.channel]?.write(payload)
                null
            }
            TYPE_LAST -> partial.remove(frame.channel)?.apply { write(payload) }?.toByteArray()
            else -> null
        }
    }
}
