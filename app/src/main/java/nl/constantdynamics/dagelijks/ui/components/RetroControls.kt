package nl.constantdynamics.dagelijks.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLengthTrim
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import nl.constantdynamics.dagelijks.ui.theme.RetroColors
import nl.constantdynamics.dagelijks.ui.theme.RetroType
import kotlin.math.roundToInt

/**
 * The standard button: large, easy to hit, and it visibly goes down into its
 * shadow when pressed.
 */
@Composable
fun RetroButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = RetroColors.Coral,
    contentColor: Color = RetroColors.Cream,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val pressed by animateFloatAsState(if (isPressed && enabled) 1f else 0f, tween(70), label = "press")
    val shadow = RetroDefaults.Shadow

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 58.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .retroBox(
                fill = if (enabled) color else RetroColors.CreamDark,
                corner = 18.dp,
                borderColor = if (enabled) RetroColors.Navy else RetroColors.Navy.copy(alpha = 0.45f),
                shadowColor = if (enabled) RetroColors.Navy else RetroColors.Navy.copy(alpha = 0.3f),
                pressed = pressed,
            )
            .offset { IntOffset((shadow.x * pressed).roundToPx(), (shadow.y * pressed).roundToPx()) }
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        // A button that cannot be used yet looks like plain card with faded lettering.
        val shownColor = if (enabled) contentColor else RetroColors.Navy.copy(alpha = 0.45f)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = shownColor, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = text,
                style = RetroType.button,
                color = shownColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A round button with only a pictogram; [description] is what a screen reader says. */
@Composable
fun RetroIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = RetroColors.Cream,
    contentColor: Color = RetroColors.Navy,
    size: Dp = 52.dp,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val pressed by animateFloatAsState(if (isPressed && enabled) 1f else 0f, tween(70), label = "press")
    val shadow = DpOffset(3.dp, 4.dp)

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description }
            .retroBox(fill = color, corner = size / 2, shadow = shadow, pressed = pressed)
            .size(size)
            .offset { IntOffset((shadow.x * pressed).roundToPx(), (shadow.y * pressed).roundToPx()) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(size * 0.5f))
    }
}

/** A tile to choose one option out of a few (camera, aspect ratio). Selected turns mustard. */
@Composable
fun ChoiceTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .retroBox(
                fill = if (selected) RetroColors.Mustard else RetroColors.Cream,
                corner = 16.dp,
                shadow = if (selected) DpOffset(4.dp, 5.dp) else DpOffset(2.dp, 3.dp),
            )
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

/** A small rounded label, for example "Vandaag gelukt". */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = RetroColors.Mustard,
    textColor: Color = RetroColors.Navy,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .retroBox(fill = color, corner = 14.dp, border = 2.dp, shadow = DpOffset(2.dp, 2.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = textColor, maxLines = 1)
    }
}

/**
 * Slider with a chunky track and a big round thumb. Drag the thumb or tap anywhere
 * on the track. Inside a scrolling page an up-or-down swipe that starts on the
 * slider still scrolls the page; only a sideways drag moves the thumb.
 *
 * [steps] is the number of positions between the two ends, 0 for a smooth slider.
 * [onDark] switches to colours that stand out on the dark camera screen.
 */
@Composable
fun RetroSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    onDark: Boolean = false,
    description: String? = null,
) {
    val thumbSize = 30.dp
    val trackHeight = 14.dp
    val outline = if (onDark) RetroColors.Cream else RetroColors.Navy
    val inactive = if (onDark) RetroColors.Cream.copy(alpha = 0.22f) else RetroColors.CreamDark
    val start = valueRange.start
    val span = (valueRange.endInclusive - start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - start) / span).coerceIn(0f, 1f)
    val latestChange by rememberUpdatedState(onValueChange)
    val latestFinished by rememberUpdatedState(onValueChangeFinished)

    fun snap(raw: Float): Float {
        val clamped = raw.coerceIn(start, valueRange.endInclusive)
        if (steps <= 0) return clamped
        val intervals = steps + 1
        return start + ((clamped - start) / span * intervals).roundToInt() * span / intervals
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .semantics {
                if (description != null) contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange, steps)
                setProgress { target ->
                    latestChange(snap(target))
                    latestFinished?.invoke()
                    true
                }
            }
            .pointerInput(start, span, steps) {
                val thumb = thumbSize.toPx()
                fun valueAt(x: Float): Float = snap(start + ((x - thumb / 2) / (size.width - thumb)).coerceIn(0f, 1f) * span)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    if (drag != null) {
                        latestChange(valueAt(drag.position.x))
                        horizontalDrag(drag.id) { change ->
                            latestChange(valueAt(change.position.x))
                            change.consume()
                        }
                        latestFinished?.invoke()
                    } else {
                        // Lifted without dragging: a tap on the track, unless the page scrolled instead.
                        val released = currentEvent.changes.firstOrNull { it.id == down.id }
                        if (released != null && !released.pressed && !released.isConsumed) {
                            latestChange(valueAt(released.position.x))
                            latestFinished?.invoke()
                        }
                    }
                }
            }
            .drawBehind {
                val thumb = thumbSize.toPx()
                val track = trackHeight.toPx()
                val top = (size.height - track) / 2
                val radius = CornerRadius(track / 2)
                val border = 2.dp.toPx()
                // The thumb's centre travels between half a thumb from either end.
                val centre = Offset(thumb / 2 + fraction * (size.width - thumb), size.height / 2)

                drawRoundRect(inactive, Offset(0f, top), Size(size.width, track), radius)
                drawRoundRect(RetroColors.Mustard, Offset(0f, top), Size(centre.x.coerceAtLeast(track), track), radius)
                drawRoundRect(
                    outline,
                    Offset(border / 2, top + border / 2),
                    Size(size.width - border, track - border),
                    radius,
                    style = Stroke(border),
                )

                val thumbRadius = thumb / 2
                val thumbBorder = 2.5.dp.toPx()
                drawCircle(RetroColors.NavyDeep, thumbRadius, centre + Offset(0f, 3.dp.toPx()))
                drawCircle(RetroColors.Cream, thumbRadius, centre)
                drawCircle(RetroColors.Navy, thumbRadius - thumbBorder / 2, centre, style = Stroke(thumbBorder))
                drawCircle(RetroColors.Coral, thumbRadius * 0.32f, centre)
            },
    )
}

/** Single-line text input in the card style. */
@Composable
fun RetroTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    maxLength: Int = 60,
    onDone: () -> Unit = {},
) {
    BasicTextField(
        state = state,
        modifier = modifier,
        lineLimits = TextFieldLineLimits.SingleLine,
        inputTransformation = InputTransformation.maxLengthTrim(maxLength),
        textStyle = MaterialTheme.typography.titleMedium.copy(color = RetroColors.Navy),
        cursorBrush = SolidColor(RetroColors.Coral),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        onKeyboardAction = { onDone() },
        decorator = { field ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .retroBox(fill = Color.White, corner = 14.dp, shadow = DpOffset(3.dp, 4.dp))
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (state.text.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.titleMedium,
                        color = RetroColors.Navy.copy(alpha = 0.4f),
                        maxLines = 1,
                    )
                }
                field()
            }
        },
    )
}

/**
 * A question in a card. Used for everything that needs a deliberate answer, such as
 * deleting a photo or replacing today's photo.
 */
@Composable
fun RetroDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String?,
    modifier: Modifier = Modifier,
    confirmColor: Color = RetroColors.Coral,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .widthIn(max = 420.dp)
                .retroBox(fill = RetroColors.Cream, corner = 24.dp, shadow = DpOffset(6.dp, 7.dp))
                .padding(22.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = RetroColors.Navy)
            Spacer(Modifier.height(12.dp))
            content()
            Spacer(Modifier.height(20.dp))
            RetroButton(
                text = confirmText,
                onClick = onConfirm,
                color = confirmColor,
                contentColor = if (confirmColor == RetroColors.Mustard) RetroColors.Navy else RetroColors.Cream,
                modifier = Modifier.fillMaxWidth(),
            )
            if (dismissText != null) {
                Spacer(Modifier.height(10.dp))
                RetroButton(
                    text = dismissText,
                    onClick = onDismiss,
                    color = RetroColors.Cream,
                    contentColor = RetroColors.Navy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Clips content to a circle; small helper for round thumbnails and dots. */
fun Modifier.circle(): Modifier = clip(CircleShape)
