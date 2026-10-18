package com.trendscout.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "videos", indices = [Index(value = ["url"], unique = true)])
data class VideoEntity(
    @PrimaryKey val id: String,
    val url: String,
    val platformVideoId: String?,
    val title: String,
    val authorHandle: String?,
    val authorFollowers: Long?,
    val authorCountry: String?,
    val thumbnailUrl: String?,
    val publishedAt: Long?,
    val publishedAtSource: String?,
    val addedAt: Long,
    val category: String?,
    /** Newline-separated. */
    val hashtags: String,
    val soundId: String?,
    val soundTitle: String?,
    val formatTags: String,
    /** Comma-separated TrendType names. */
    val trendTypes: String,
    val hookNotes: String?,
    val notes: String?,
    val favorite: Boolean,
    val source: String,
)

@Entity(
    tableName = "snapshots",
    foreignKeys = [ForeignKey(entity = VideoEntity::class, parentColumns = ["id"], childColumns = ["videoId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("videoId")],
)
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val capturedAt: Long,
    val views: Long?,
    val likes: Long?,
    val comments: Long?,
    val shares: Long?,
    val source: String,
)

/** "Popular in country" evidence for a video or a trend. targetType: "video" | "trend". */
@Entity(tableName = "region_observations", indices = [Index(value = ["targetType", "targetId"])])
data class RegionObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetType: String,
    val targetId: String,
    val countryCode: String,
    val basis: String,
    val observedAt: Long,
    val rank: Int?,
    val note: String?,
)

@Entity(tableName = "trends")
data class TrendEntity(
    @PrimaryKey val id: String,
    val type: String,
    val name: String,
    val category: String?,
    val notes: String?,
    val hookNotes: String?,
    val saved: Boolean,
    val used: Boolean,
    val createdAt: Long,
)

@Entity(
    tableName = "trend_snapshots",
    foreignKeys = [ForeignKey(entity = TrendEntity::class, parentColumns = ["id"], childColumns = ["trendId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("trendId")],
)
data class TrendSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trendId: String,
    val capturedAt: Long,
    val countryCode: String?,
    val posts: Long?,
    val views: Long?,
    val rank: Int?,
    val source: String,
)

@Entity(tableName = "ideas")
data class IdeaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trendId: String?,
    val videoId: String?,
    val title: String,
    /** Serialized core Idea (JSON). */
    val contentJson: String,
    val targetCountry: String?,
    val genre: String?,
    val status: String,
    val origin: String,
    val notes: String?,
    /** Link to the user's own published video, if any. */
    val publishedUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/** itemType: "video" | "trend" | "idea". */
@Entity(
    tableName = "collection_items",
    primaryKeys = ["collectionId", "itemType", "itemId"],
    foreignKeys = [ForeignKey(entity = CollectionEntity::class, parentColumns = ["id"], childColumns = ["collectionId"], onDelete = ForeignKey.CASCADE)],
)
data class CollectionItemEntity(
    val collectionId: Long,
    val itemType: String,
    val itemId: String,
    val addedAt: Long,
)

/** Keys of notifications already sent, to avoid repeating them. */
@Entity(tableName = "notified")
data class NotifiedEntity(
    @PrimaryKey val key: String,
    val at: Long,
)
