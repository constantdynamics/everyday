package nl.constantdynamics.dagelijks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.constantdynamics.dagelijks.ui.theme.AppColors

/**
 * A quiet panel: a fill that is only slightly lighter than the ground, with a thin
 * edge. No shadow; depth comes from the difference in tone.
 */
fun Modifier.panel(
    fill: Color = AppColors.Surface,
    corner: Dp = 20.dp,
    edge: Color = AppColors.Hairline,
    edgeWidth: Dp = 1.dp,
): Modifier {
    val shape = RoundedCornerShape(corner)
    return this
        .clip(shape)
        .background(fill)
        .then(if (edgeWidth > 0.dp) Modifier.border(edgeWidth, edge, shape) else Modifier)
}

/** A card holding a group of things that belong together. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    fill: Color = AppColors.Surface,
    edge: Color = AppColors.Hairline,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.panel(fill = fill, edge = edge).padding(contentPadding),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** A card that asks for attention without shouting: something went wrong, or a result. */
@Composable
fun NoticeCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    AppCard(
        modifier = modifier,
        fill = AppColors.AmberTint,
        edge = AppColors.Amber.copy(alpha = 0.45f),
        contentPadding = contentPadding,
        content = content,
    )
}

/** The ground of every screen: one calm, dark colour. */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.background(AppColors.Ground), content = content)
}

/** A thin line across the available width. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(AppColors.Hairline))
}

/**
 * The line under the app's name: a short amber stroke, a shorter coral one and then a
 * hairline, like the leader of a film title.
 */
@Composable
fun TitleRule(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(28.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(AppColors.Amber))
        Spacer(Modifier.width(4.dp))
        Box(Modifier.width(12.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(AppColors.Coral))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(AppColors.Hairline))
    }
}
