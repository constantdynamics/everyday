package nl.constantdynamics.dagelijks.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.constantdynamics.dagelijks.ui.theme.RetroColors
import nl.constantdynamics.dagelijks.ui.theme.RetroFonts
import nl.constantdynamics.dagelijks.ui.theme.RetroType
import kotlin.math.ceil

/**
 * A title in fat display letters with a thin cream outline and a 3D extrusion: the
 * letters are stacked diagonally in navy, like a sign cut from thick board.
 *
 * The text shrinks to fit the available width instead of being cut off, so long
 * project names stay readable.
 */
@Composable
fun RetroTitle(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 40.sp,
    fill: Color = RetroColors.Coral,
    outline: Color = RetroColors.Cream,
    extrusion: Color = RetroColors.Navy,
    depth: Dp = 5.dp,
    outlineWidth: Dp = 2.dp,
    maxLines: Int = 1,
    textAlign: TextAlign = TextAlign.Start,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val depthPx = with(density) { depth.toPx() }
        val outlinePx = with(density) { outlineWidth.toPx() }
        // The letters lean to the right; a little extra room keeps the last one whole.
        val leanPx = with(density) { fontSize.toPx() } * 0.1f
        val extraWidth = ceil(outlinePx * 2 + depthPx + leanPx).toInt()
        val extraHeight = ceil(outlinePx * 2 + depthPx).toInt()
        val availableWidth = if (constraints.hasBoundedWidth) {
            (constraints.maxWidth - extraWidth).coerceAtLeast(1)
        } else {
            Constraints.Infinity
        }

        val layout: TextLayoutResult = remember(text, fontSize, availableWidth, maxLines, textAlign, density) {
            var size = fontSize.value
            val smallest = fontSize.value * 0.5f
            var result: TextLayoutResult
            while (true) {
                result = measurer.measure(
                    text = text,
                    style = TextStyle(
                        fontFamily = RetroFonts.Display,
                        fontSize = size.sp,
                        lineHeight = (size * 1.12f).sp,
                        textAlign = textAlign,
                    ),
                    maxLines = maxLines,
                    softWrap = maxLines > 1,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = availableWidth),
                )
                if (!result.hasVisualOverflow || size <= smallest) break
                size = (size - 2f).coerceAtLeast(smallest)
            }
            result
        }

        val width = with(density) { (layout.size.width + extraWidth).toDp() }
        val height = with(density) { (layout.size.height + extraHeight).toDp() }
        Canvas(
            Modifier
                .size(width, height)
                .semantics {
                    heading()
                    this.text = AnnotatedString(text)
                },
        ) {
            val origin = Offset(outlinePx, outlinePx)
            val block = Stroke(width = outlinePx * 2, join = StrokeJoin.Round)
            // The extrusion: the outlined shape repeated along the diagonal.
            var step = depthPx
            while (step > 0f) {
                val offset = origin + Offset(step, step)
                drawText(layout, color = extrusion, topLeft = offset, drawStyle = block)
                drawText(layout, color = extrusion, topLeft = offset)
                step -= 1.5f
            }
            drawText(layout, color = outline, topLeft = origin, drawStyle = block)
            drawText(layout, color = fill, topLeft = origin)
        }
    }
}

/**
 * A small banner label ("lint") with swallow-tail ends and script lettering, to sit
 * above a title or mark something as done.
 */
@Composable
fun Ribbon(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = RetroColors.Coral,
    textColor: Color = RetroColors.Cream,
    style: TextStyle = RetroType.script,
    tilt: Float = -3f,
) {
    Box(
        modifier = modifier
            .rotate(tilt)
            .drawBehind {
                val notch = size.height * 0.36f
                val shape = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width - notch, size.height / 2)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    lineTo(notch, size.height / 2)
                    close()
                }
                val shadow = 3.dp.toPx()
                translate(shadow, shadow) { drawPath(shape, RetroColors.Navy) }
                drawPath(shape, color)
                drawPath(shape, RetroColors.Navy, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
            }
            .padding(horizontal = 26.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = style, color = textColor, maxLines = 1)
    }
}
