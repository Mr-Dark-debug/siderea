package io.github.mrdarkdebug.siderea.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaDimens
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaShapes
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaSpacing
import java.util.Locale

/** One camera value: a mono number over a tracked caps label (SS / ISO / EV). */
@Composable
fun ReadoutCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val palette = Siderea.palette
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$label $value" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Long values ("1/120 A") drop to the smaller style instead of wrapping when text is enlarged.
        Text(
            text = value,
            style = if (value.length > LONG_VALUE) Siderea.text.readoutSmall else Siderea.text.readout,
            color = if (highlighted) palette.accent else palette.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = label.uppercase(Locale.ROOT),
            style = Siderea.text.caption,
            color = palette.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
    }
}

private const val LONG_VALUE = 5

/** A rounded bar of [ReadoutCell]s, like the ISO / SS / EV strip under a viewfinder. */
@Composable
fun ReadoutBar(
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    val palette = Siderea.palette
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(palette.surface, SideriaShapes.medium)
                .border(SideriaDimens.hairline, palette.outline, SideriaShapes.medium)
                .padding(vertical = SideriaSpacing.md, horizontal = SideriaSpacing.lg),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (label, value) -> ReadoutCell(label = label, value = value) }
    }
}

/** Compact rounded pill carrying a single line of mono text, e.g. `48MP · 16:9 · RAW+JPG`. */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val palette = Siderea.palette
    Text(
        text = text,
        style = Siderea.text.caption,
        color = if (highlighted) palette.accent else palette.onSurfaceMuted,
        modifier =
            modifier
                .background(palette.surfaceRaised, SideriaShapes.pill)
                .padding(horizontal = SideriaSpacing.md, vertical = SideriaSpacing.xs + SideriaSpacing.xxs),
    )
}
