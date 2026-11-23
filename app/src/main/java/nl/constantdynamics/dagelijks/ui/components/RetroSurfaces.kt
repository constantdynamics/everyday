package nl.constantdynamics.dagelijks.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import nl.constantdynamics.dagelijks.ui.theme.RetroColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Sizes shared by everything that looks like a cut-out piece of card. */
object RetroDefaults {
    val Corner = 20.dp
    val Border = 2.5.dp
    val Shadow = DpOffset(4.dp, 5.dp)
}

/**
 * The print-like look of the whole app: a flat fill, a dark outline and a hard
 * shadow that is simply the same shape shifted down and to the right.
 *
 * Room for the shadow is reserved inside the modifier, so neighbours never overlap it.
 * [pressed] moves the piece into its shadow, the way a real button goes down. The face
 * gets the same fine [grain] as the background, like printed card.
 */
fun Modifier.retroBox(
    fill: Color,
    corner: Dp = RetroDefaults.Corner,
    border: Dp = RetroDefaults.Border,
    borderColor: Color = RetroColors.Navy,
    shadow: DpOffset = RetroDefaults.Shadow,
    shadowColor: Color = RetroColors.Navy,
    pressed: Float = 0f,
    grain: Boolean = true,
): Modifier = this
    .padding(end = shadow.x, bottom = shadow.y)
    .drawBehind {
        val radius = CornerRadius(corner.toPx())
        val shadowOffset = Offset(shadow.x.toPx(), shadow.y.toPx())
        val faceOffset = shadowOffset * pressed
        if (shadowColor.alpha > 0f) drawRoundRect(shadowColor, topLeft = shadowOffset, size = size, cornerRadius = radius)
        drawRoundRect(fill, topLeft = faceOffset, size = size, cornerRadius = radius)
        if (grain) drawRoundRect(grainBrush, topLeft = faceOffset, size = size, cornerRadius = radius)
        val stroke = border.toPx()
        if (stroke > 0f) {
            drawRoundRect(
                color = borderColor,
                topLeft = faceOffset + Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius((corner.toPx() - stroke / 2).coerceAtLeast(0f)),
                style = Stroke(stroke),
            )
        }
    }

/** A cream card with outline and hard shadow. */
@Composable
fun RetroCard(
    modifier: Modifier = Modifier,
    fill: Color = RetroColors.Cream,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.retroBox(fill).padding(contentPadding),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/**
 * Background of the regular screens: mint, an optional soft sunburst and a fine grain,
 * like slightly rough printed paper.
 */
@Composable
fun RetroBackground(
    modifier: Modifier = Modifier,
    color: Color = RetroColors.Mint,
    rayColor: Color = RetroColors.MintLight,
    sunburst: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.drawBehind {
            drawRect(color)
            if (sunburst) {
                // The rays fan out from above the title, so they frame it without crossing the text.
                drawSunburst(Offset(size.width * 0.5f, size.height * 0.16f), rayColor)
            }
            // The grain lies on the paper, not on what is put on top of it: photos stay clean.
            drawRect(grainBrush)
        },
        content = content,
    )
}

private fun DrawScope.drawSunburst(center: Offset, color: Color, rays: Int = 14) {
    val radius = (size.width + size.height) * 1.2f
    val step = (2 * PI / rays).toFloat()
    for (ray in 0 until rays) {
        val start = ray * step + step * 0.25f
        val end = start + step * 0.5f
        val path = Path().apply {
            moveTo(center.x, center.y)
            lineTo(center.x + cos(start) * radius, center.y + sin(start) * radius)
            lineTo(center.x + cos(end) * radius, center.y + sin(end) * radius)
            close()
        }
        drawPath(path, color)
    }
}

/** The grain as a repeating pattern, to be drawn over a flat colour. */
private val grainBrush: ShaderBrush by lazy {
    ShaderBrush(ImageShader(grainTile, TileMode.Repeated, TileMode.Repeated))
}

/** A small tile of random light and dark specks, made once and repeated across the screen. */
private val grainTile: ImageBitmap by lazy {
    val size = 192
    val random = Random(1970)
    val pixels = IntArray(size * size)
    // Specks of two by two pixels stay visible on today's very sharp screens.
    for (y in 0 until size step 2) {
        for (x in 0 until size step 2) {
            val alpha = random.nextInt(0, 30)
            val tone = if (random.nextBoolean()) 0x00FFFFFF else 0x00102040
            val pixel = (alpha shl 24) or tone
            pixels[y * size + x] = pixel
            pixels[y * size + x + 1] = pixel
            pixels[(y + 1) * size + x] = pixel
            pixels[(y + 1) * size + x + 1] = pixel
        }
    }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}
