package com.trendscout.app.data.db

import com.trendscout.core.model.PublishedAtSource
import com.trendscout.core.model.RegionBasis
import com.trendscout.core.model.RegionObservation
import com.trendscout.core.model.StatSnapshot
import com.trendscout.core.model.Trend
import com.trendscout.core.model.TrendSnapshot
import com.trendscout.core.model.TrendType
import com.trendscout.core.model.Video

const val TARGET_VIDEO = "video"
const val TARGET_TREND = "trend"
const val TARGET_IDEA = "idea"

private fun String.lines0(): List<String> = split('\n').map { it.trim() }.filter { it.isNotEmpty() }

fun List<String>.joinLines(): String = map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")

fun Set<TrendType>.joinTypes(): String = joinToString(",") { it.name }

private fun String.toTypes(): Set<TrendType> =
    split(',').mapNotNull { n -> TrendType.entries.firstOrNull { it.name == n.trim() } }.toSet()

fun RegionObservationEntity.toDomain() = RegionObservation(
    countryCode = countryCode,
    basis = RegionBasis.entries.firstOrNull { it.name == basis } ?: RegionBasis.USER_OBSERVATION,
    observedAt = observedAt,
    rank = rank,
    note = note,
    id = id,
)

fun SnapshotEntity.toDomain() = StatSnapshot(capturedAt, views, likes, comments, shares, source, id)

fun TrendSnapshotEntity.toDomain() = TrendSnapshot(capturedAt, countryCode, posts, views, rank, source, id)

fun VideoEntity.toDomain(snaps: List<SnapshotEntity>, regions: List<RegionObservationEntity>) = Video(
    id = id,
    url = url,
    platformVideoId = platformVideoId,
    title = title,
    authorHandle = authorHandle,
    authorFollowers = authorFollowers,
    authorCountry = authorCountry,
    thumbnailUrl = thumbnailUrl,
    publishedAt = publishedAt,
    publishedAtSource = publishedAtSource?.let { s -> PublishedAtSource.entries.firstOrNull { it.name == s } },
    addedAt = addedAt,
    category = category,
    hashtags = hashtags.lines0(),
    soundId = soundId,
    soundTitle = soundTitle,
    formatTags = formatTags.lines0(),
    trendTypes = trendTypes.toTypes(),
    hookNotes = hookNotes,
    notes = notes,
    favorite = favorite,
    source = source,
    snapshots = snaps.map { it.toDomain() },
    popularIn = regions.map { it.toDomain() },
)

fun Video.toEntity() = VideoEntity(
    id = id,
    url = url,
    platformVideoId = platformVideoId,
    title = title,
    authorHandle = authorHandle,
    authorFollowers = authorFollowers,
    authorCountry = authorCountry,
    thumbnailUrl = thumbnailUrl,
    publishedAt = publishedAt,
    publishedAtSource = publishedAtSource?.name,
    addedAt = addedAt,
    category = category,
    hashtags = hashtags.joinLines(),
    soundId = soundId,
    soundTitle = soundTitle,
    formatTags = formatTags.joinLines(),
    trendTypes = trendTypes.joinTypes(),
    hookNotes = hookNotes,
    notes = notes,
    favorite = favorite,
    source = source,
)

fun TrendEntity.toDomain(snaps: List<TrendSnapshotEntity>, regions: List<RegionObservationEntity>) = Trend(
    id = id,
    type = TrendType.entries.firstOrNull { it.name == type } ?: TrendType.HASHTAG,
    name = name,
    category = category,
    notes = notes,
    hookNotes = hookNotes,
    saved = saved,
    used = used,
    createdAt = createdAt,
    snapshots = snaps.map { it.toDomain() },
    popularIn = regions.map { it.toDomain() },
)
