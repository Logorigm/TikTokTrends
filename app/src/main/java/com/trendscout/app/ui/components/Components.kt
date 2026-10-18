@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.trendscout.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.trendscout.app.R
import com.trendscout.app.ui.theme.Palette
import com.trendscout.core.model.Countries
import com.trendscout.core.model.Reliability
import com.trendscout.core.scoring.ScoreLabel
import com.trendscout.core.scoring.TrendScore

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun EmptyState(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) Button(onClick = onAction) { Text(actionLabel) }
        if (secondaryLabel != null && onSecondary != null) OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
    }
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null, onDismiss: (() -> Unit)? = null) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(10.dp))
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            if (onRetry != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            if (onDismiss != null) TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        }
    }
}

/** Neutral notice for data limitations. */
@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = Palette.Amber)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val m = modifier.fillMaxWidth()
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val shape = RoundedCornerShape(16.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = m, shape = shape, colors = colors) { Box(Modifier.padding(14.dp)) { content() } }
    } else {
        Card(modifier = m, shape = shape, colors = colors) { Box(Modifier.padding(14.dp)) { content() } }
    }
}

@Composable
fun Pill(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, bg: Color = MaterialTheme.colorScheme.surfaceContainerHigh, onClick: (() -> Unit)? = null) {
    val base = Modifier.clip(RoundedCornerShape(50)).background(bg)
    Text(
        text,
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base).padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun ScoreBadge(score: TrendScore, compact: Boolean = false) {
    val (fg, bg) = when (score.label) {
        ScoreLabel.PROMISING -> Palette.Bg to Palette.Cyan
        ScoreLabel.WATCH -> Palette.Bg to Palette.Amber
        ScoreLabel.LOW -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceContainerHigh
        ScoreLabel.INSUFFICIENT_DATA -> MaterialTheme.colorScheme.onSurfaceVariant to Color.Transparent
    }
    val label = stringResource(L.label(score.label))
    val text = if (score.score != null && !compact) "$label · ${score.score}" else label
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg)
            .then(if (score.label == ScoreLabel.INSUFFICIENT_DATA) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50)) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = fg,
    )
}

@Composable
fun ReliabilityText(r: Reliability) {
    val color = when (r) {
        Reliability.HIGH -> Palette.Green
        Reliability.MEDIUM -> Palette.Cyan
        Reliability.LOW -> Palette.Amber
        Reliability.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(stringResource(R.string.reliability_fmt, stringResource(L.reliability(r))), color = color, style = MaterialTheme.typography.labelMedium)
}

@Composable
fun Thumbnail(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Text("▶", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Compact line chart for a time series (x = epoch millis). Shows nothing misleading for < 2 points. */
@Composable
fun LineChart(points: List<Pair<Long, Double>>, modifier: Modifier = Modifier, height: Dp = 140.dp, color: Color = Palette.Cyan) {
    val r = LocalContext.current.resources
    val sorted = points.sortedBy { it.first }
    if (sorted.size < 2) {
        InfoNote(stringResource(R.string.chart_need_two), modifier)
        return
    }
    val minY = sorted.minOf { it.second }
    val maxY = sorted.maxOf { it.second }
    val minX = sorted.first().first
    val maxX = sorted.last().first
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(Fmt.countD(r, maxY), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(height).padding(vertical = 6.dp)) {
            val w = size.width
            val h = size.height
            val dx = (maxX - minX).coerceAtLeast(1).toFloat()
            val dy = (maxY - minY).let { if (it == 0.0) 1.0 else it }
            for (i in 0..3) {
                val y = h * i / 3f
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            val path = Path()
            sorted.forEachIndexed { i, (x, yv) ->
                val px = (x - minX) / dx * w
                val py = h - ((yv - minY) / dy * h).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, color, style = Stroke(width = 5f, cap = StrokeCap.Round))
            sorted.forEach { (x, yv) ->
                val px = (x - minX) / dx * w
                val py = h - ((yv - minY) / dy * h).toFloat()
                drawCircle(color, radius = 7f, center = Offset(px, py))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(Fmt.date(minX), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(Fmt.countD(r, minY) + " → " + Fmt.date(maxX), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun <T> ChipGroup(
    options: List<T>,
    selected: Set<T>,
    label: @Composable (T) -> String,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(selected = o in selected, onClick = { onToggle(o) }, label = { Text(label(o)) })
        }
    }
}

@Composable
fun <T> Dropdown(
    label: String,
    options: List<T>,
    selected: T?,
    text: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "—",
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text(if (selected != null) text(selected) else placeholder, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("▾")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { o ->
                    DropdownMenuItem(text = { Text(text(o)) }, onClick = { onSelect(o); open = false })
                }
            }
        }
    }
}

@Composable
fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> onChange(s.filter { it.isDigit() || it in ".,KkMm" }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = modifier,
    )
}

/** Multi- or single-select country picker. Any ISO code can be typed in addition to the list. */
@Composable
fun CountryPickerDialog(
    initial: Set<String>,
    multi: Boolean,
    onDismiss: () -> Unit,
    options: List<String> = Countries.ALL,
    onDone: (Set<String>) -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    var custom by remember { mutableStateOf("") }
    val all = (options + selected).distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (multi) R.string.pick_countries else R.string.pick_country)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        custom, { custom = it.uppercase().take(2) },
                        label = { Text(stringResource(R.string.iso_code)) }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        if (Countries.isValid(custom)) {
                            selected = if (multi) selected + custom else setOf(custom)
                            custom = ""
                        }
                    }) { Text(stringResource(R.string.action_add)) }
                }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(all) { code ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selected = if (multi) (if (code in selected) selected - code else selected + code) else setOf(code)
                            }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = code in selected, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(Fmt.country(code))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(selected) }) { Text(stringResource(R.string.action_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun TextInputDialog(title: String, initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit, label: String? = null, singleLine: Boolean = false) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it }, label = if (label != null) { { Text(label) } } else null, singleLine = singleLine, modifier = Modifier.fillMaxWidth(), minLines = if (singleLine) 1 else 3)
        },
        confirmButton = { TextButton(onClick = { onDone(text) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun KeyValue(key: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}

/** A simple horizontal bar (0..1) used in score breakdowns. */
@Composable
fun Bar(value: Double, modifier: Modifier = Modifier, color: Color = Palette.Cyan) {
    Box(modifier.height(6.dp).fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Box(Modifier.fillMaxWidth(value.toFloat().coerceIn(0f, 1f)).height(6.dp).background(color))
    }
}
