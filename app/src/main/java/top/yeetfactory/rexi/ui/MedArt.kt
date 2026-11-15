package top.yeetfactory.rexi.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withRotation
import androidx.core.graphics.withSave
import top.yeetfactory.rexi.core.MedColor
import top.yeetfactory.rexi.core.MedKind
import androidx.compose.foundation.Canvas as ComposeCanvas

/**
 * The twelve medicine drawings. They're drawn with Android's own canvas on a 40 point grid, so the app,
 * the notifications and the home screen widgets all show exactly the same thing.
 */
object MedArt {
    private const val GRID = 40f
    private const val DARK = 0xFF3A4A5E.toInt()
    private const val METAL = 0xFF7D8896.toInt()
    private const val LIGHT_METAL = 0xFFC9D1DB.toInt()
    private const val GLASS = 0xFFF3F5F8.toInt()
    private const val PALE_EDGE = 0xFFB8C0CB.toInt()
    private const val EDGE = 0x3312161C
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val RED_CROSS = 0xFFD9473B.toInt()

    fun bitmap(
        kind: MedKind,
        color1: MedColor,
        color2: MedColor,
        sizePx: Int,
    ): Bitmap =
        createBitmap(sizePx, sizePx).also { bitmap ->
            draw(Canvas(bitmap), sizePx.toFloat(), kind, color1, color2)
        }

    fun draw(
        canvas: Canvas,
        size: Float,
        kind: MedKind,
        color1: MedColor,
        color2: MedColor,
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        val c1 = color1.argb.toInt()
        val c2 = color2.argb.toInt()
        val edge1 = if (color1.pale) PALE_EDGE else EDGE
        val edge2 = if (color2.pale) PALE_EDGE else EDGE

        fun rr(
            l: Float,
            t: Float,
            r: Float,
            b: Float,
            radius: Float,
            color: Int,
            edge: Int? = null,
            alpha: Int = 255,
        ) {
            val rect = RectF(l, t, r, b)
            fill.color = color
            fill.alpha = alpha
            canvas.drawRoundRect(rect, radius, radius, fill)
            fill.alpha = 255
            if (edge != null) {
                stroke.color = edge
                canvas.drawRoundRect(rect, radius, radius, stroke)
            }
        }

        fun oval(
            l: Float,
            t: Float,
            r: Float,
            b: Float,
            color: Int,
            edge: Int?,
            alpha: Int = 255,
        ) {
            val rect = RectF(l, t, r, b)
            fill.color = color
            fill.alpha = alpha
            canvas.drawOval(rect, fill)
            fill.alpha = 255
            if (edge != null) {
                stroke.color = edge
                canvas.drawOval(rect, stroke)
            }
        }

        fun line(
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
            color: Int,
            width: Float = 1f,
        ) {
            stroke.color = color
            stroke.strokeWidth = width
            canvas.drawLine(x1, y1, x2, y2, stroke)
            stroke.strokeWidth = 1f
        }

        canvas.withSave {
            scale(size / GRID, size / GRID)
            when (kind) {
                MedKind.TABLET -> {
                    withRotation(-25f, 20f, 20f) {
                        rr(6f, 13f, 34f, 27f, 7f, c1, edge1)
                        line(20f, 15.5f, 20f, 24.5f, edge1)
                    }
                }

                MedKind.OVAL -> {
                    withRotation(-25f, 20f, 20f) { oval(8f, 11.5f, 32f, 28.5f, c1, edge1) }
                }

                MedKind.CAPSULE -> {
                    withRotation(-35f, 20f, 20f) {
                        withClip(0f, 0f, 20f, GRID) { rr(7f, 13.5f, 33f, 26.5f, 6.5f, c1) }
                        withClip(20f, 0f, GRID, GRID) { rr(7f, 13.5f, 33f, 26.5f, 6.5f, c2) }
                        stroke.color = if (color1.pale || color2.pale) PALE_EDGE else EDGE
                        drawRoundRect(RectF(7f, 13.5f, 33f, 26.5f), 6.5f, 6.5f, stroke)
                        line(20f, 13.5f, 20f, 26.5f, edge2)
                        rr(10f, 15.6f, 18f, 17.8f, 1.1f, WHITE, alpha = 110)
                    }
                }

                MedKind.SOFTGEL -> {
                    withRotation(-30f, 20f, 20f) {
                        oval(7f, 12f, 33f, 28f, c1, edge1)
                        oval(11f, 16f, 20f, 19.2f, WHITE, null, alpha = 128)
                    }
                }

                MedKind.LIQUID -> {
                    rr(15f, 4.5f, 25f, 10f, 1.5f, DARK)
                    rr(16.5f, 10f, 23.5f, 13.5f, 0f, LIGHT_METAL)
                    val bottle = Path().apply { addRoundRect(RectF(11f, 13.5f, 29f, 36f), 4f, 4f, Path.Direction.CW) }
                    fill.color = GLASS
                    drawPath(bottle, fill)
                    withClip(bottle) { rr(11f, 22f, 29f, 36f, 0f, c1) }
                    stroke.color = PALE_EDGE
                    drawPath(bottle, stroke)
                    rr(14.5f, 16f, 25.5f, 20f, 1f, WHITE, PALE_EDGE)
                }

                MedKind.DROPS -> {
                    rr(16f, 3.5f, 24f, 13f, 4f, DARK)
                    rr(14.5f, 12f, 25.5f, 16f, 1f, METAL)
                    rr(12.5f, 16f, 27.5f, 36f, 4f, c1, edge1)
                    rr(15.5f, 21.5f, 24.5f, 29.5f, 1.5f, WHITE, alpha = 217)
                }

                MedKind.SPRAY -> {
                    rr(12.5f, 16f, 27.5f, 37f, 3.5f, c1, edge1)
                    rr(15.5f, 21.5f, 24.5f, 30.5f, 1.5f, WHITE, alpha = 217)
                    rr(16.5f, 11.5f, 23.5f, 16f, 1f, METAL)
                    rr(14.5f, 6f, 25.5f, 11.5f, 1.8f, DARK)
                    rr(25.5f, 7.6f, 30f, 9.9f, 1f, DARK)
                    line(31.5f, 6.2f, 34.5f, 4.8f, METAL)
                    line(32f, 8.75f, 35.5f, 8.75f, METAL)
                    line(31.5f, 11.3f, 34.5f, 12.7f, METAL)
                }

                MedKind.INHALER -> {
                    rr(20f, 3.5f, 28f, 13.5f, 2.5f, LIGHT_METAL, METAL)
                    rr(17.5f, 10f, 30.5f, 33f, 4.5f, c1, edge1)
                    rr(7.5f, 24f, 22.5f, 33f, 3.5f, c1, edge1)
                    rr(8.5f, 26f, 11.5f, 31f, 1.2f, DARK, alpha = 140)
                }

                MedKind.INJECTION -> {
                    withRotation(-45f, 20f, 20f) {
                        rr(3f, 17f, 7f, 23f, 1.5f, DARK)
                        rr(6.5f, 15.5f, 27.5f, 24.5f, 3f, c1, edge1)
                        rr(12f, 18f, 20f, 22f, 1f, WHITE, alpha = 191)
                        rr(27.5f, 17f, 32.5f, 23f, 1.2f, LIGHT_METAL)
                        line(32.5f, 20f, 37f, 20f, METAL, 0.9f)
                    }
                }

                MedKind.CREAM -> {
                    withRotation(-30f, 20f, 20f) {
                        rr(4.5f, 12.5f, 8f, 27.5f, 1f, LIGHT_METAL)
                        val tube =
                            Path().apply {
                                moveTo(8f, 12.5f)
                                lineTo(25f, 12.5f)
                                lineTo(29f, 17f)
                                lineTo(29f, 23f)
                                lineTo(25f, 27.5f)
                                lineTo(8f, 27.5f)
                                close()
                            }
                        fill.color = c1
                        drawPath(tube, fill)
                        stroke.color = edge1
                        drawPath(tube, stroke)
                        rr(29f, 16.5f, 35.5f, 23.5f, 1.5f, DARK)
                    }
                }

                MedKind.PATCH -> {
                    withRotation(-15f, 20f, 20f) {
                        rr(8.5f, 8.5f, 31.5f, 31.5f, 6f, c1, edge1)
                        rr(14f, 14f, 26f, 26f, 3f, WHITE, alpha = 153)
                    }
                }

                MedKind.OTHER -> {
                    val top =
                        Path().apply {
                            moveTo(8f, 14f)
                            lineTo(13f, 9f)
                            lineTo(33f, 9f)
                            lineTo(28f, 14f)
                            close()
                        }
                    val side =
                        Path().apply {
                            moveTo(28f, 14f)
                            lineTo(33f, 9f)
                            lineTo(33f, 28f)
                            lineTo(28f, 33f)
                            close()
                        }
                    fill.color = c1
                    drawPath(top, fill)
                    drawPath(side, fill)
                    fill.color = WHITE
                    fill.alpha = 90
                    drawPath(top, fill)
                    fill.color = 0xFF000000.toInt()
                    fill.alpha = 31
                    drawPath(side, fill)
                    fill.alpha = 255
                    stroke.color = edge1
                    drawPath(top, stroke)
                    drawPath(side, stroke)
                    rr(8f, 14f, 28f, 33f, 1f, c1, edge1)
                    val cross = if (color1.pale) RED_CROSS else WHITE
                    line(18f, 19.5f, 18f, 27.5f, cross, 2.2f)
                    line(14f, 23.5f, 22f, 23.5f, cross, 2.2f)
                }
            }
        }
    }
}

/** A medicine's drawing at any size. It's decoration: the name next to it is what screen readers announce. */
@Composable
fun MedIcon(
    kind: MedKind,
    color1: MedColor,
    color2: MedColor,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    ComposeCanvas(modifier.size(size)) {
        drawIntoCanvas { MedArt.draw(it.nativeCanvas, this.size.minDimension, kind, color1, color2) }
    }
}
