package nl.constantdynamics.dagelijks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import nl.constantdynamics.dagelijks.ui.theme.AppColors
import nl.constantdynamics.dagelijks.ui.theme.AppType
import kotlin.math.roundToInt

/**
 * How much a button asks for attention. A screen has at most one [Primary] button:
 * the action that counts at that moment. Everything else is [Secondary].
 */
enum class ButtonKind { Primary, Secondary }

/** The standard button: a wide pill, large and easy to hit. */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val filled = kind == ButtonKind.Primary && enabled
    val textColor = when {
        !enabled -> AppColors.Outline
        filled -> AppColors.OnAccent
        else -> AppColors.Text
    }
    val iconColor = when {
        !enabled -> AppColors.Outline
        filled -> AppColors.OnAccent
        else -> AppColors.Amber
    }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .clip(CircleShape)
            .background(if (filled) AppColors.Coral else Color.Transparent)
            .then(
                if (filled) Modifier else Modifier.border(1.dp, if (enabled) AppColors.Outline else AppColors.Hairline, CircleShape),
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        FittedText(
            text = text,
            style = AppType.button,
            color = textColor,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

/** [Outlined] is the ordinary round button, [Primary] the one hot action, [Active] something switched on. */
enum class IconButtonKind { Outlined, Primary, Active }

/** A round button with only a pictogram; [description] is what a screen reader says. */
@Composable
fun AppIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: IconButtonKind = IconButtonKind.Outlined,
    size: Dp = 48.dp,
    enabled: Boolean = true,
) {
    val fill = when (kind) {
        IconButtonKind.Outlined -> AppColors.Surface
        IconButtonKind.Primary -> AppColors.Coral
        IconButtonKind.Active -> AppColors.Amber
    }
    val tint = if (kind == IconButtonKind.Outlined) AppColors.Text else AppColors.OnAccent
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .then(if (kind == IconButtonKind.Outlined) Modifier.border(1.dp, AppColors.Outline, CircleShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

/** A tile to choose one option out of a few (camera, aspect ratio). The chosen one gets an amber edge. */
@Composable
fun ChoiceTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(if (selected) AppColors.AmberTint else AppColors.Raised)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) AppColors.Amber else AppColors.Hairline, shape)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

/** [Done] is amber on a dark amber fill, [Neutral] just an outline. */
enum class PillTone { Done, Neutral }

/** A small rounded label, for example "Vandaag gelukt". */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    tone: PillTone = PillTone.Neutral,
    icon: ImageVector? = null,
) {
    val color = if (tone == PillTone.Done) AppColors.Amber else AppColors.Text
    Row(
        modifier = modifier
            .clip(CircleShape)
            .then(
                if (tone == PillTone.Done) {
                    Modifier.background(AppColors.AmberTint)
                } else {
                    Modifier.border(1.dp, AppColors.Outline, CircleShape)
                },
            )
            .padding(horizontal = 11.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        FittedText(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/**
 * Slider with a thin track and a big round thumb. Drag the thumb or tap anywhere on
 * the track. Inside a scrolling page an up-or-down swipe that starts on the slider
 * still scrolls the page; only a sideways drag moves the thumb.
 *
 * [steps] is the number of positions between the two ends, 0 for a smooth slider.
 */
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    description: String? = null,
) {
    val thumbSize = 28.dp
    val trackHeight = 4.dp
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
                // The thumb's centre travels between half a thumb from either end.
                val centre = Offset(thumb / 2 + fraction * (size.width - thumb), size.height / 2)

                drawRoundRect(AppColors.Track, Offset(0f, top), Size(size.width, track), radius)
                drawRoundRect(AppColors.Amber, Offset(0f, top), Size(centre.x, track), radius)

                val edge = 2.dp.toPx()
                drawCircle(AppColors.Text, thumb / 2, centre)
                drawCircle(AppColors.Amber, thumb / 2 - edge / 2, centre, style = Stroke(edge))
            },
    )
}

/** Single-line text input. */
@Composable
fun AppTextField(
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
        textStyle = MaterialTheme.typography.titleMedium.copy(color = AppColors.Text),
        cursorBrush = SolidColor(AppColors.Amber),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        // The default action is what closes the keyboard on "done"; it has to be asked for.
        onKeyboardAction = { performDefaultAction ->
            onDone()
            performDefaultAction()
        },
        decorator = { field ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .panel(fill = AppColors.Raised, corner = 14.dp, edge = AppColors.Outline)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (state.text.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.TextMuted,
                        maxLines = 1,
                    )
                }
                field()
            }
        },
    )
}

/**
 * A question in a panel. Used for everything that needs a deliberate answer, such as
 * deleting a photo or replacing today's photo.
 */
@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String?,
    modifier: Modifier = Modifier,
    confirmKind: ButtonKind = ButtonKind.Primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .widthIn(max = 420.dp)
                .panel(corner = 24.dp, edge = AppColors.Outline)
                .padding(22.dp),
        ) {
            // With the phone on its side or a large system font the text may not fit. Then
            // the text scrolls, and the buttons stay where they can be reached.
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                DisplayTitle(title, style = MaterialTheme.typography.headlineMedium, maxLines = 2)
                Spacer(Modifier.height(12.dp))
                content()
            }
            Spacer(Modifier.height(20.dp))
            AppButton(text = confirmText, onClick = onConfirm, kind = confirmKind, modifier = Modifier.fillMaxWidth())
            if (dismissText != null) {
                Spacer(Modifier.height(10.dp))
                AppButton(text = dismissText, onClick = onDismiss, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Clips content to a circle; small helper for round thumbnails and dots. */
fun Modifier.circle(): Modifier = clip(CircleShape)
