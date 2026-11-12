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

    /** myCar's own arrow, white, in [box]: a line that bends the way the turn goes, with a head at its end. */
    private fun drawArrow(canvas: Canvas, box: ClusterArrow.Box, arrow: ClusterArrow) {
        val s = box.side.toFloat()
        val x = box.left.toFloat()
        val y = box.top.toFloat()
        val cx = x + s / 2
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = s * 0.12f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        when (arrow.kind) {
            ClusterArrow.Kind.DESTINATION -> {
                canvas.drawCircle(cx, y + s * 0.5f, s * 0.3f, line)
                canvas.drawCircle(cx, y + s * 0.5f, s * 0.12f, fill)
            }
            ClusterArrow.Kind.U_TURN -> {
                // Up, round the top to the side it turns to, and down to the head.
                val way = if (arrow.bend < 0) -1f else 1f
                val r = s * 0.18f
                val top = y + s * 0.42f
                val down = cx + way * r
                val end = y + s * 0.6f
                val path = Path().apply {
                    moveTo(cx - way * r, y + s * 0.92f)
                    lineTo(cx - way * r, top)
                    arcTo(RectF(cx - r, top - r, cx + r, top + r), if (way > 0) 180f else 0f, 180f * way, false)
                    lineTo(down, end)
                }
                canvas.drawPath(path, line)
                head(canvas, down, end, 180, s, fill)
            }
            ClusterArrow.Kind.ROUNDABOUT -> {
                // The ring, the way in from below, and the way out where the exit leads.
                val r = s * 0.15f
                val cy = y + s * 0.5f
                canvas.drawCircle(cx, cy, r, line)
                canvas.drawLine(cx, y + s * 0.92f, cx, cy + r, line)
                val (dx, dy) = direction(arrow.bend)
                val fromX = cx + dx * r
                val fromY = cy + dy * r
                val toX = fromX + dx * s * 0.1f
                val toY = fromY + dy * s * 0.1f
                canvas.drawLine(fromX, fromY, toX, toY, line)
                head(canvas, toX, toY, arrow.bend, s, fill)
            }
            ClusterArrow.Kind.ARROW -> {
                val pivot = y + s * 0.55f
                val (dx, dy) = direction(arrow.bend)
                val endX = cx + dx * s * 0.28f
                val endY = pivot + dy * s * 0.28f
                val path = Path().apply {
                    moveTo(cx, y + s * 0.92f)
                    lineTo(cx, pivot)
                    lineTo(endX, endY)
                }
                canvas.drawPath(path, line)
                head(canvas, endX, endY, arrow.bend, s, fill)
            }
        }
    }

    /** Where [bend] degrees clockwise from straight up points, as x and y on a picture (y grows down). */
    private fun direction(bend: Int): Pair<Float, Float> {
        val a = Math.toRadians(bend.toDouble())
        return sin(a).toFloat() to -cos(a).toFloat()
    }

    /** An arrow's head with its base across (x, y), pointing [bend] degrees clockwise from straight up. */
    private fun head(canvas: Canvas, x: Float, y: Float, bend: Int, s: Float, paint: Paint) {
        val (dx, dy) = direction(bend)
        val length = s * 0.2f
        val half = s * 0.17f
        val path = Path().apply {
            moveTo(x + dx * length, y + dy * length)
            lineTo(x - dy * half, y + dx * half)
            lineTo(x + dy * half, y - dx * half)
            close()
        }
        canvas.drawPath(path, paint)
    }
}
