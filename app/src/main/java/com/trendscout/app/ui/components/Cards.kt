@file:OptIn(ExperimentalLayoutApi::class)

package com.trendscout.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trendscout.app.R
import com.trendscout.app.ui.theme.Palette
import com.trendscout.core.feed.FeedEntry
import com.trendscout.core.feed.TrendEntry
import com.trendscout.core.model.PublishedAtSource
import com.trendscout.core.model.RegionBasis
import com.trendscout.core.model.TrendType
import com.trendscout.core.scoring.ScoreLabel

@Composable
fun StatItem(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(3.dp))
        Text(value, style = MaterialTheme.typography.labelMedium)
    }
}

/** Geography lines: the author's country and "popular in" evidence are always shown separately. */
@Composable
fun GeoLines(authorCountry: String?, popular: Map<String, RegionBasis>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        if (popular.isEmpty()) {
            Text(stringResource(R.string.geo_no_view_data), style = MaterialTheme.typography.labelSmall, color = Palette.Amber)
        } else {
            val onlyUser = popular.values.all { it == RegionBasis.USER_OBSERVATION }
            Text(
                stringResource(R.string.geo_popular_in, popular.keys.joinToString(" ") { Fmt.countryShort(it) }) +
                    if (onlyUser) " · " + stringResource(R.string.basis_user_short) else "",
                style = MaterialTheme.typography.labelSmall, color = if (onlyUser) Palette.Amber else Palette.Cyan,
            )
        }
        if (authorCountry != null) {
            Text(stringResource(R.string.geo_author_is, Fmt.countryShort(authorCountry)), style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}

@Composable
fun VideoCard(entry: FeedEntry, onOpen: () -> Unit, onToggleFavorite: () -> Unit) {
    val ctx = LocalContext.current
    val r = ctx.resources
    val v = entry.video
    val m = entry.metrics
    AppCard(onClick = onOpen) {
        Column {
            Row {
                Thumbnail(v.thumbnailUrl, Modifier.width(84.dp).aspectRatio(9f / 14f))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (entry.score.label == ScoreLabel.PROMISING || entry.score.label == ScoreLabel.WATCH) {
                            ScoreBadge(entry.score)
                            Spacer(Modifier.width(6.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (v.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                stringResource(R.string.action_favorite),
                                tint = if (v.favorite) Palette.Amber else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { openUrl(ctx, v.url) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.OpenInNew, stringResource(R.string.action_open_original), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        v.title.ifBlank { stringResource(R.string.no_title) },
                        style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    val author = buildString {
                        append("@").append(v.authorHandle ?: "?")
                        v.authorFollowers?.let { append(" · ").append(r.getString(R.string.followers_fmt, Fmt.count(r, it))) }
                    }
                    Text(author, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    val date = when {
                        v.publishedAt == null -> stringResource(R.string.published_unknown)
                        v.publishedAtSource == PublishedAtSource.VIDEO_ID -> stringResource(R.string.published_fmt, Fmt.relative(v.publishedAt)) + " " + stringResource(R.string.from_video_id)
                        else -> stringResource(R.string.published_fmt, Fmt.relative(v.publishedAt))
                    }
                    Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val popular = v.popularIn.groupBy { it.countryCode }.mapValues { (_, l) -> l.maxBy { it.basis.reliability }.basis }
                    GeoLines(v.authorCountry, popular)
                }
            }
            Spacer(Modifier.height(10.dp))
            val s = m.latest
            if (s == null) {
                Text(stringResource(R.string.stats_none), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    StatItem(Icons.Outlined.Visibility, Fmt.count(r, s.views))
                    StatItem(Icons.Outlined.FavoriteBorder, Fmt.count(r, s.likes))
                    StatItem(Icons.Outlined.ChatBubbleOutline, Fmt.count(r, s.comments))
                    StatItem(Icons.Outlined.Reply, Fmt.count(r, s.shares))
                    Text(stringResource(R.string.as_of_fmt, Fmt.relative(s.capturedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(6.dp))
            val growth = when {
                m.currentViewsPerHour != null -> stringResource(R.string.growth_measured, Fmt.rate(r, m.currentViewsPerHour), Fmt.hours(r, m.lastWindow?.hours))
                m.avgViewsPerHourSincePublish != null -> stringResource(R.string.growth_avg_only, Fmt.rate(r, m.avgViewsPerHourSincePublish))
                else -> stringResource(R.string.growth_unknown)
            }
            Text(growth, style = MaterialTheme.typography.labelMedium, color = if (m.currentViewsPerHour != null) Palette.Green else MaterialTheme.colorScheme.onSurfaceVariant)
            val tags = v.hashtags.take(4).map { "#$it" } + listOfNotNull(v.soundTitle?.let { "♪ $it" })
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { Pill(it) }
                }
            }
        }
    }
}

@Composable
fun TrendRow(entry: TrendEntry, onOpen: () -> Unit) {
    val t = entry.trend
    AppCard(onClick = onOpen) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val prefix = when (t.type) {
                    TrendType.HASHTAG -> "#"
                    TrendType.SOUND -> "♪ "
                    else -> ""
                }
                Column(Modifier.weight(1f)) {
                    Text(prefix + t.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(L.type(t.type)) + " · " + stringResource(R.string.trend_counts, entry.videoCount, entry.authors),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (t.saved) Icon(Icons.Filled.Star, null, tint = Palette.Amber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                ScoreBadge(entry.score, compact = true)
            }
            Spacer(Modifier.height(6.dp))
            GeoLines(null, entry.countries)
        }
    }
}
