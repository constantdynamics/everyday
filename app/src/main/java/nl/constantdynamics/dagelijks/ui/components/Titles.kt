package nl.constantdynamics.dagelijks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import nl.constantdynamics.dagelijks.DagelijksApp
import nl.constantdynamics.dagelijks.ui.theme.AppColors
import nl.constantdynamics.dagelijks.ui.theme.AppType

/**
 * A title in the wide display letters.
 *
 * The text shrinks to fit the available width instead of being cut off, so a long
 * project name stays readable. Only when it would become too small is it shortened.
 */
@Composable
fun DisplayTitle(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = AppType.screenTitle,
    color: Color = AppColors.Text,
    maxLines: Int = 1,
    textAlign: TextAlign = TextAlign.Start,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val availableWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val lineRatio = style.lineHeight.value / style.fontSize.value

        val fittedSize = remember(text, style, availableWidth, maxLines, density) {
            var size = style.fontSize.value
            val smallest = size * 0.62f
            while (true) {
                val result = measurer.measure(
                    text = text,
                    style = style.copy(fontSize = size.sp, lineHeight = (size * lineRatio).sp),
                    overflow = TextOverflow.Ellipsis,
                    softWrap = maxLines > 1,
                    maxLines = maxLines,
                    constraints = Constraints(maxWidth = availableWidth),
                )
                if (!result.hasVisualOverflow || size <= smallest) break
                size = (size - 1f).coerceAtLeast(smallest)
            }
            size
        }

        Text(
            text = text,
            style = style.copy(fontSize = fittedSize.sp, lineHeight = (fittedSize * lineRatio).sp),
            color = color,
            maxLines = maxLines,
            softWrap = maxLines > 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * Text that makes itself a little smaller when it does not fit: when it would be cut
 * off, or when a word would be broken in two. A narrow phone or a large system font then
 * gives slightly smaller letters instead of half a word. It never goes below [smallest]
 * times its own size; after that it is shortened with an ellipsis.
 *
 * Unlike [DisplayTitle] this measures nothing in advance, so it can be used anywhere,
 * also where the layout asks its children how big they would like to be.
 */
@Composable
fun FittedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
    smallest: Float = 0.7f,
) {
    var scale by remember(text, style, maxLines) { mutableFloatStateOf(1f) }
    // The width the current scale was found for; more or less room means trying again.
    var fittedFor by remember(text, style, maxLines) { mutableIntStateOf(-1) }

    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = if (scale == 1f) {
            style
        } else {
            style.copy(
                fontSize = style.fontSize * scale,
                lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * scale else style.lineHeight,
            )
        },
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        onTextLayout = { layout ->
            val room = layout.layoutInput.constraints.maxWidth
            if (room != fittedFor && scale < 1f) {
                fittedFor = room
                scale = 1f
            } else {
                fittedFor = room
                if (scale > smallest && (layout.hasVisualOverflow || layout.breaksAWord(text))) {
                    scale = (scale - 0.06f).coerceAtLeast(smallest)
                }
            }
        },
    )
}

/** True when a line ends in the middle of a word, which only happens when the word is too wide. */
private fun TextLayoutResult.breaksAWord(text: String): Boolean {
    for (line in 0 until lineCount - 1) {
        val end = getLineEnd(line)
        if (end <= 0 || end >= text.length) continue
        val before = text[end - 1]
        val after = text[end]
        if (!before.isWhitespace() && !after.isWhitespace() && before != '-') return true
    }
    return false
}

/**
 * Small spaced capitals above a group of things ("NOVEMBER 2026"), optionally followed
 * by a hairline that runs to the edge.
 *
 * It is shown in capitals but read out as written, so a screen reader does not spell it.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AppColors.Amber,
    style: TextStyle = AppType.overline,
    rule: Boolean = false,
) {
    Row(
        modifier = modifier.clearAndSetSemantics {
            this.text = AnnotatedString(text)
            heading()
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(DagelijksApp.DUTCH),
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (rule) Modifier else Modifier.weight(1f, fill = false),
        )
        if (rule) {
            Spacer(Modifier.width(14.dp))
            Box(Modifier.weight(1f).height(1.dp).background(AppColors.Hairline))
        }
    }
}
