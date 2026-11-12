package io.github.sirionero.mycar.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.car.app.model.CarIcon
import androidx.compose.ui.graphics.toArgb
import io.github.sirionero.mycar.host.color
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A turn as a picture, for a cluster that wants one ([ClusterSpec.pictureSize]): the map app's own
 * icon for the manoeuvre, centred on a transparent PNG of the size the car asks for, or myCar's
 * plain white arrow ([ClusterArrow]) when the app gives none or it won't load. Drawn on the
 * cluster's own thread, once for each new turn.
 */
internal object ClusterImage {

    /** The PNG, and whether it shows the map app's icon (rather than myCar's arrow). */
    class Picture(val png: ByteArray, val appIcon: Boolean)

    /**
     * The turn at [width] x [height]: [icon] in the middle, or [arrow]. [opaque] for a cluster with
     * no room for transparency, which gets a black ground. Null if Android won't make the PNG.
     */
    fun picture(context: Context?, icon: CarIcon?, arrow: ClusterArrow, width: Int, height: Int, opaque: Boolean): Picture? {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val ground = if (opaque) Color.BLACK else Color.TRANSPARENT
            bitmap.eraseColor(ground)
            val canvas = Canvas(bitmap)
            val box = ClusterArrow.box(width, height)
            val appIcon = drawIcon(context, icon, canvas, box)
            if (!appIcon) {
                bitmap.eraseColor(ground)
                drawArrow(canvas, box, arrow)
            }
            if (opaque) bitmap.setHasAlpha(false)
            val out = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return null
            return Picture(out.toByteArray(), appIcon)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * The map app's icon, fitted in [box]. One that leaves its colour to the host is drawn white:
     * a cluster is dark.
     */
    private fun drawIcon(context: Context?, icon: CarIcon?, canvas: Canvas, box: ClusterArrow.Box): Boolean {
        val carIcon = icon ?: return false
        val source = carIcon.icon ?: return false
        val app = context ?: return false
        return runCatching {
            val drawable = source.loadDrawable(app)?.mutate() ?: return false
            val area = ClusterArrow.fit(box, drawable.intrinsicWidth, drawable.intrinsicHeight)
            drawable.setBounds(area.left, area.top, area.right, area.bottom)
            carIcon.tint?.let { drawable.setTint(it.color(true, androidx.compose.ui.graphics.Color.White).toArgb()) }
            drawable.draw(canvas)
        }.isSuccess
    }

    /**
     * myCar's own arrow, white, in [box]: a line that bends the way the turn goes, with a head at its
     * end; drawn in the box's own units, then moved so its ink sits in the middle of the box.
     */
    private fun drawArrow(canvas: Canvas, box: ClusterArrow.Box, arrow: ClusterArrow) {
        val s = box.side.toFloat()
        val ink = Ink(s * 0.06f)
        val mid = s * 0.5f
        when (arrow.kind) {
            ClusterArrow.Kind.DESTINATION -> {
                ink.ring(mid, mid, s * 0.3f)
                ink.dot(mid, mid, s * 0.12f)
            }
            ClusterArrow.Kind.U_TURN -> {
                // Up, round the top to the side it turns to, and down to the head.
                val way = if (arrow.bend < 0) -1f else 1f
                val r = s * 0.18f
                val top = s * 0.42f
                val up = mid - way * r
                val down = mid + way * r
                val end = s * 0.62f
                ink.strokes.moveTo(up, s * 0.92f)
                ink.strokes.lineTo(up, top)
                ink.strokes.arcTo(RectF(mid - r, top - r, mid + r, top + r), if (way > 0) 180f else 0f, 180f * way, false)
                ink.strokes.lineTo(down, end)
                ink.reach(up, s * 0.92f, ink.half)
                ink.reach(mid, top - r, ink.half)
                ink.reach(down, end, ink.half)
                ink.head(down, end, 180, s)
            }
            ClusterArrow.Kind.ROUNDABOUT -> {
                // The ring, the way in from below, and the way out where the exit leads.
                val r = s * 0.15f
                ink.ring(mid, mid, r)
                ink.line(mid, s * 0.92f, mid, mid + r)
                val (dx, dy) = direction(arrow.bend)
                val fromX = mid + dx * r
                val fromY = mid + dy * r
                val toX = fromX + dx * s * 0.1f
                val toY = fromY + dy * s * 0.1f
                ink.line(fromX, fromY, toX, toY)
                ink.head(toX, toY, arrow.bend, s)
            }
            ClusterArrow.Kind.ARROW -> {
                // A sharp turn bends back down: a longer arm keeps its head off the line it comes from.
                val sharp = abs(arrow.bend) > 90
                val pivot = s * (if (sharp) 0.45f else 0.55f)
                val arm = s * (if (sharp) 0.36f else 0.28f)
                val (dx, dy) = direction(arrow.bend)
                val endX = mid + dx * arm
                val endY = pivot + dy * arm
                ink.line(mid, s * 0.92f, mid, pivot, endX, endY)
                ink.head(endX, endY, arrow.bend, s)
            }
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = ink.half * 2
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.save()
        canvas.translate(box.left + mid - ink.midX, box.top + mid - ink.midY)
        canvas.drawPath(ink.strokes, line)
        canvas.drawPath(ink.fills, fill)
        canvas.restore()
    }

    /** What an arrow draws - lines, and filled heads and dots - and how far its ink reaches. [half]: half the lines' width. */
    private class Ink(val half: Float) {
        val strokes = Path()
        val fills = Path()
        private var left = Float.MAX_VALUE
        private var top = Float.MAX_VALUE
        private var right = -Float.MAX_VALUE
        private var bottom = -Float.MAX_VALUE
        val midX: Float get() = (left + right) / 2
        val midY: Float get() = (top + bottom) / 2

        /** Ink up to [r] around (x, y). */
        fun reach(x: Float, y: Float, r: Float) {
            left = minOf(left, x - r)
            right = maxOf(right, x + r)
            top = minOf(top, y - r)
            bottom = maxOf(bottom, y + r)
        }

        /** A line through the points, given as x, y pairs. */
        fun line(vararg xy: Float) {
            strokes.moveTo(xy[0], xy[1])
            for (i in 2 until xy.size step 2) strokes.lineTo(xy[i], xy[i + 1])
            for (i in xy.indices step 2) reach(xy[i], xy[i + 1], half)
        }

        fun ring(x: Float, y: Float, r: Float) {
            strokes.addCircle(x, y, r, Path.Direction.CW)
            reach(x, y, r + half)
        }

        fun dot(x: Float, y: Float, r: Float) {
            fills.addCircle(x, y, r, Path.Direction.CW)
            reach(x, y, r)
        }

        /** An arrow's head with its base across (x, y), pointing [bend] degrees clockwise from straight up. */
        fun head(x: Float, y: Float, bend: Int, s: Float) {
            val (dx, dy) = direction(bend)
            val length = s * 0.2f
            val wide = s * 0.17f
            val points = floatArrayOf(x + dx * length, y + dy * length, x - dy * wide, y + dx * wide, x + dy * wide, y - dx * wide)
            fills.moveTo(points[0], points[1])
            fills.lineTo(points[2], points[3])
            fills.lineTo(points[4], points[5])
            fills.close()
            for (i in points.indices step 2) reach(points[i], points[i + 1], 0f)
        }
    }
}

/** Where [bend] degrees clockwise from straight up points, as x and y on a picture (y grows down). */
private fun direction(bend: Int): Pair<Float, Float> {
    val a = Math.toRadians(bend.toDouble())
    return sin(a).toFloat() to -cos(a).toFloat()
}
