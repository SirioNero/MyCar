package io.github.sirionero.mycar.car.design

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * A superellipse, |x|^n + |y|^n = 1: a square whose sides flow into its corners with no visible
 * seam, the frame every app icon in the car UI sits in. [exponent] sets how square it is - 2 is a
 * circle, higher is squarer; [MyCarRadius.Icon] is the house setting.
 */
class SquircleShape(private val exponent: Float = MyCarRadius.Icon) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val halfWidth = size.width / 2f
        val halfHeight = size.height / 2f
        val power = 2.0 / exponent
        val path = Path()
        for (i in 0..STEPS) {
            val t = 2.0 * PI * i / STEPS
            val c = cos(t)
            val s = sin(t)
            val x = halfWidth + halfWidth * (sign(c) * abs(c).pow(power)).toFloat()
            val y = halfHeight + halfHeight * (sign(s) * abs(s).pow(power)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?) = other is SquircleShape && other.exponent == exponent

    override fun hashCode() = exponent.hashCode()

    private companion object {
        /** Enough points that no corner shows a facet even on a large icon. */
        const val STEPS = 128
    }
}

/** The house squircle, shared so every icon frame is the same object. */
val AppIconShape = SquircleShape()
