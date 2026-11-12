package io.github.sirionero.mycar.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.R
import java.io.File

/**
 * Debug only (CarPreviewActivity --ez clusterTurns true): the pictures myCar makes for a cluster
 * that wants turns as images, written as cluster-*.png beside the preview's shots - there is no
 * instrument cluster in the emulator to look at. myCar's arrows at the size the 2024 MG ZS of
 * issue #23 asks for (1920x720, 32-bit); a map app's icons, a vector that leaves its colour to the
 * host and a bitmap like Vela's; a small square cluster and a 16-bit one.
 */
internal object ClusterPreview {

    fun write(context: Context, dir: File) {
        listOf(
            "depart" to ClusterArrow.of(1, 3),
            "turn-right" to ClusterArrow.of(4, 2),
            "turn-left" to ClusterArrow.of(4, 1),
            "slight-left" to ClusterArrow.of(3, 1),
            "sharp-right" to ClusterArrow.of(5, 2),
            "u-turn-left" to ClusterArrow.of(6, 1),
            "roundabout-first-right" to ClusterArrow.of(13, 3, 90),
            "roundabout-third-left" to ClusterArrow.of(13, 3, 270),
            "destination" to ClusterArrow.of(19, 3),
        ).forEach { (name, arrow) -> save(dir, "cluster-$name", ClusterImage.picture(context, null, arrow, 1920, 720, false)) }

        val vector = IconCompat.createWithResource(context, R.drawable.sym_navigation)
        val tinted = CarIcon.Builder(vector).setTint(CarColor.DEFAULT).build()
        save(dir, "cluster-icon-vector", ClusterImage.picture(context, tinted, ClusterArrow.of(14, 3), 1920, 720, false))
        val bitmap = CarIcon.Builder(IconCompat.createWithBitmap(arrowBitmap())).build()
        save(dir, "cluster-icon-bitmap", ClusterImage.picture(context, bitmap, ClusterArrow.of(4, 2), 1920, 720, false))

        save(dir, "cluster-256-turn-right", ClusterImage.picture(context, null, ClusterArrow.of(4, 2), 256, 256, false))
        save(dir, "cluster-16bit-turn-left", ClusterImage.picture(context, null, ClusterArrow.of(4, 1), 256, 128, true))
    }

    /** A white right-turn arrow on 96 px, as a map app might hand one over. */
    private fun arrowBitmap(): Bitmap {
        val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        Canvas(b).drawPath(
            Path().apply {
                moveTo(20f, 90f); lineTo(20f, 40f); lineTo(60f, 40f); lineTo(60f, 22f)
                lineTo(90f, 50f); lineTo(60f, 78f); lineTo(60f, 58f); lineTo(36f, 58f); lineTo(36f, 90f); close()
            },
            paint,
        )
        return b
    }

    private fun save(dir: File, name: String, picture: ClusterImage.Picture?) {
        if (picture == null) {
            MyCarLog.w("cluster preview: $name not made")
            return
        }
        File(dir, "$name.png").writeBytes(picture.png)
        MyCarLog.i("cluster preview: $name, ${picture.png.size} bytes, ${if (picture.appIcon) "the app's icon" else "myCar's arrow"}")
    }
}
