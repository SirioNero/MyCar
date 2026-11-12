package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.Wire

/**
 * What a car's instrument cluster asks of the phone, from its NavigationStatusService (Service
 * field 8) in the ServiceDiscoveryResponse:
 *
 *   NavigationStatusService { required int32 minimum_interval_ms = 1;
 *                             required InstrumentClusterType type = 2;    // IMAGE = 1, ENUM = 2
 *                             optional ImageOptions image_options = 3; }
 *   ImageOptions { required int32 height = 1; required int32 width = 2; required int32 colour_depth_bits = 3; }
 *
 * Height comes before width, as aasdk and aa-proxy-rs have it from Android Auto's own definitions
 * (the older f1x aasdk guessed width first): the 2024 MG ZS of issue #23 asks for 720 then 1920,
 * the shape of its 1920x720 screen. A cluster of type IMAGE takes each turn as a picture as well
 * as its code; that MG got only the code, and hung up.
 */
class ClusterSpec(
    val channel: Int,
    /** The least time the car wants between two messages; 0 when it doesn't say. */
    val minIntervalMs: Int = 0,
    /** [IMAGE], [ENUM], or 0 when the car doesn't say. */
    val type: Int = 0,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val colourDepthBits: Int = 0,
) {
    /**
     * The time myCar leaves between two messages: the car's, never less than it always left, nor
     * more than [MAX_INTERVAL_MS].
     */
    val intervalMs: Long get() = minIntervalMs.toLong().coerceIn(FLOOR_MS, MAX_INTERVAL_MS)

    /**
     * The size of a turn's picture, width to height: what the car asks for, or the same shape within
     * [MAX_PIXELS] for a car that asks for something absurd. Null for a car that wants no pictures
     * or names no usable size, as Android Auto then sends none either.
     */
    fun pictureSize(): Pair<Int, Int>? {
        if (type != IMAGE || imageWidth <= 0 || imageHeight <= 0) return null
        val scale = minOf(1.0, kotlin.math.sqrt(MAX_PIXELS / (imageWidth.toDouble() * imageHeight)))
        return maxOf(1, (imageWidth * scale).toInt()) to maxOf(1, (imageHeight * scale).toInt())
    }

    /** Fewer than 32 bits of colour leave no room for transparency: the picture gets a black ground then. */
    val opaque: Boolean get() = colourDepthBits in 1..31

    /** For the log: what the cluster takes, and how often. */
    fun describe(): String {
        val pictures = pictureSize()
        val what = when {
            pictures != null -> {
                val (w, h) = pictures
                val asked = if (w != imageWidth || h != imageHeight) ", asked for ${imageWidth}x$imageHeight" else ""
                "the cluster wants each turn as a ${w}x$h picture too ($colourDepthBits-bit$asked)"
            }
            type == IMAGE -> "the cluster wants pictures but names no size (${imageWidth}x$imageHeight): turn codes only"
            type == ENUM -> "the cluster takes turn codes"
            else -> "the cluster doesn't say how it shows turns: codes, as always"
        }
        val pace = when {
            minIntervalMs > MAX_INTERVAL_MS -> "the car asks for $minIntervalMs ms, myCar keeps to $MAX_INTERVAL_MS"
            minIntervalMs > FLOOR_MS -> "the car asks for $minIntervalMs ms"
            else -> "the car asks for ${if (minIntervalMs > 0) "$minIntervalMs ms" else "nothing"}, myCar leaves $FLOOR_MS ms"
        }
        return "$what; one message at a time, ${intervalMs}ms apart ($pace)"
    }

    companion object {
        const val IMAGE = 1
        const val ENUM = 2

        /** What myCar always left between two looks at the map app: no car gets messages faster than before. */
        const val FLOOR_MS = 500L

        /** The most myCar leaves between two messages, whatever the car asks: longer, the cluster lags behind the road. */
        const val MAX_INTERVAL_MS = 10_000L

        /** A Full HD picture at most: an absurd size would only cost memory. */
        const val MAX_PIXELS = 1920.0 * 1080

        /** The car's instrument cluster, or null when the car has none. */
        fun from(response: ByteArray): ClusterSpec? {
            for (channel in Wire.allBytes(Wire.fields(response), 1)) {
                val service = Wire.fields(channel)
                val navigation = Wire.bytes(service, 8) ?: continue
                val id = Wire.varint(service, 1)?.toInt() ?: continue
                val fields = Wire.fields(navigation)
                val image = Wire.bytes(fields, 3)?.let { Wire.fields(it) }.orEmpty()
                return ClusterSpec(
                    channel = id,
                    minIntervalMs = number(fields, 1),
                    type = number(fields, 2),
                    imageWidth = number(image, 2),
                    imageHeight = number(image, 1),
                    colourDepthBits = number(image, 3),
                )
            }
            return null
        }

        /** An int32 field, 0 when missing or out of range (a negative one comes as a huge varint). */
        private fun number(fields: List<Wire.Field>, field: Int): Int =
            Wire.varint(fields, field)?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt() ?: 0
    }
}
