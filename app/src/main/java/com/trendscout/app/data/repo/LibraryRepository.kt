package com.trendscout.app.data.repo

import androidx.room.withTransaction
import com.trendscout.app.data.db.AppDatabase
import com.trendscout.app.data.db.CollectionEntity
import com.trendscout.app.data.db.CollectionItemEntity
import com.trendscout.app.data.db.IdeaEntity
import com.trendscout.app.data.db.RegionObservationEntity
import com.trendscout.app.data.db.SnapshotEntity
import com.trendscout.app.data.db.TARGET_IDEA
import com.trendscout.app.data.db.TARGET_TREND
import com.trendscout.app.data.db.TARGET_VIDEO
import com.trendscout.app.data.db.TrendEntity
import com.trendscout.app.data.db.TrendSnapshotEntity
import com.trendscout.app.data.db.toDomain
import com.trendscout.app.data.db.toEntity
import com.trendscout.core.ideas.Idea
import com.trendscout.core.ideas.IdeaStatus
import com.trendscout.core.ideas.LlmJson
import com.trendscout.core.io.ImportResult
import com.trendscout.core.io.TikTokUrl
import com.trendscout.core.model.PublishedAtSource
import com.trendscout.core.model.RegionObservation
import com.trendscout.core.model.StatSnapshot
import com.trendscout.core.model.Trend
import com.trendscout.core.model.TrendSnapshot
import com.trendscout.core.model.TrendType
import com.trendscout.core.model.Video
import com.trendscout.core.model.trendId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

data class Library(val videos: List<Video> = emptyList(), val trends: List<Trend> = emptyList(), val loaded: Boolean = false)

data class SavedIdea(
    val id: Long,
    val trendId: String?,
    val videoId: String?,
    val idea: Idea,
    val targetCountry: String?,
    val genre: String?,
    val status: IdeaStatus,
    val notes: String?,
    val publishedUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class Collection(val id: Long, val name: String, val items: List<Pair<String, String>>)

data class ImportSummary(val newVideos: Int, val updatedVideos: Int, val snapshots: Int, val regions: Int, val errors: Int)

class LibraryRepository(private val db: AppDatabase) {
    private val videos = db.videos()
    private val trends = db.trends()
    private val regions = db.regions()
    private val ideas = db.ideas()
    private val collections = db.collections()

    val library: Flow<Library> = combine(
        videos.observeAll(), videos.observeSnapshots(), regions.observeAll(), trends.observeAll(), trends.observeSnapshots(),
    ) { v, s, r, t, ts ->
        val snapsByVideo = s.groupBy { it.videoId }
        val regionsBy = r.groupBy { it.targetType to it.targetId }
        val tSnaps = ts.groupBy { it.trendId }
        Library(
            videos = v.map { it.toDomain(snapsByVideo[it.id].orEmpty(), regionsBy[TARGET_VIDEO to it.id].orEmpty()) },
            trends = t.map { it.toDomain(tSnaps[it.id].orEmpty(), regionsBy[TARGET_TREND to it.id].orEmpty()) },
            loaded = true,
        )
    }

    suspend fun snapshotLibrary(): Library {
        val s = videos.allSnapshots().groupBy { it.videoId }
        val r = regions.all().groupBy { it.targetType to it.targetId }
        val ts = trends.allSnapshots().groupBy { it.trendId }
        return Library(
            videos.all().map { it.toDomain(s[it.id].orEmpty(), r[TARGET_VIDEO to it.id].orEmpty()) },
            trends.all().map { it.toDomain(ts[it.id].orEmpty(), r[TARGET_TREND to it.id].orEmpty()) },
            true,
        )
    }

    // ---------------- Videos ----------------

    suspend fun findByUrl(url: String): Video? = videos.byUrl(url)?.let { it.toDomain(emptyList(), emptyList()) }

    /**
     * Saves a new or edited video. If a video with the same URL already exists, its id is reused so
     * history (snapshots, observations) is preserved.
     */
    suspend fun saveVideo(video: Video, initialSnapshot: StatSnapshot?, newRegions: List<RegionObservation>): String =
        db.withTransaction {
            val existing = videos.byUrl(video.url)
            val id = existing?.id ?: video.id
            val toSave = video.copy(id = id, addedAt = existing?.addedAt ?: video.addedAt, favorite = video.favorite || existing?.favorite == true)
            videos.upsert(toSave.toEntity())
            initialSnapshot?.takeIf { it.views != null || it.likes != null || it.comments != null || it.shares != null }?.let {
                videos.insertSnapshot(SnapshotEntity(0, id, it.capturedAt, it.views, it.likes, it.comments, it.shares, it.source))
            }
            newRegions.forEach { addRegionInternal(TARGET_VIDEO, id, it) }
            id
        }

    suspend fun updateVideo(video: Video) = videos.upsert(video.toEntity())

    suspend fun setFavorite(id: String, fav: Boolean) = videos.setFavorite(id, fav)

    suspend fun deleteVideo(id: String) = db.withTransaction {
        regions.deleteFor(TARGET_VIDEO, id)
        collections.removeEverywhere(TARGET_VIDEO, id)
        videos.delete(id)
    }

    suspend fun addSnapshot(videoId: String, s: StatSnapshot) =
        videos.insertSnapshot(SnapshotEntity(0, videoId, s.capturedAt, s.views, s.likes, s.comments, s.shares, s.source))

    suspend fun deleteSnapshot(id: Long) = videos.deleteSnapshot(id)

    // ---------------- Regions ----------------

    private suspend fun addRegionInternal(type: String, id: String, o: RegionObservation) {
        if (regions.count(type, id, o.countryCode, o.basis.name) > 0) return
        regions.insert(RegionObservationEntity(0, type, id, o.countryCode, o.basis.name, o.observedAt, o.rank, o.note))
    }

    suspend fun addVideoRegion(videoId: String, o: RegionObservation) = addRegionInternal(TARGET_VIDEO, videoId, o)

    suspend fun addTrendRegion(trendId: String, o: RegionObservation) = addRegionInternal(TARGET_TREND, trendId, o)

    suspend fun deleteRegion(id: Long) = regions.delete(id)

    // ---------------- Trends ----------------

    /** Creates the trend row if needed (trends are derived from tags and become rows when the user interacts). */
    suspend fun ensureTrend(type: TrendType, name: String, now: Long = System.currentTimeMillis()): String {
        val id = trendId(type, name)
        if (trends.byId(id) == null) {
            trends.upsert(TrendEntity(id, type.name, name.trim().removePrefix("#"), null, null, null, saved = false, used = false, createdAt = now))
        }
        return id
    }

    /** Ensures a row exists for an id like "HASHTAG:cat" (used when opening a derived trend). */
    suspend fun ensureTrendById(id: String, displayName: String? = null): String {
        if (trends.byId(id) != null) return id
        val type = TrendType.entries.firstOrNull { it.name == id.substringBefore(':') } ?: TrendType.HASHTAG
        val name = displayName ?: id.substringAfter(':')
        trends.upsert(TrendEntity(id, type.name, name.removePrefix("#"), null, null, null, false, false, System.currentTimeMillis()))
        return id
    }

    suspend fun updateTrend(t: Trend) = trends.upsert(
        TrendEntity(t.id, t.type.name, t.name, t.category, t.notes, t.hookNotes, t.saved, t.used, t.createdAt)
    )

    suspend fun deleteTrend(id: String) = db.withTransaction {
        regions.deleteFor(TARGET_TREND, id)
        collections.removeEverywhere(TARGET_TREND, id)
        trends.delete(id)
    }

    suspend fun addTrendSnapshot(trendId: String, s: TrendSnapshot) =
        trends.insertSnapshot(TrendSnapshotEntity(0, trendId, s.capturedAt, s.countryCode, s.posts, s.views, s.rank, s.source))

    suspend fun deleteTrendSnapshot(id: Long) = trends.deleteSnapshot(id)

    // ---------------- Import ----------------

    suspend fun import(result: ImportResult, source: String, now: Long = System.currentTimeMillis()): ImportSummary = db.withTransaction {
        var created = 0
        var updated = 0
        var snaps = 0
        var regs = 0
        for (item in result.items) {
            val existing = videos.byUrl(item.url)
            val parsed = TikTokUrl.parse(item.url)
            val base = existing?.toDomain(emptyList(), emptyList())
            val merged = Video(
                id = base?.id ?: UUID.randomUUID().toString(),
                url = item.url,
                platformVideoId = base?.platformVideoId ?: parsed?.videoId,
                title = item.title?.takeIf { it.isNotBlank() } ?: base?.title.orEmpty(),
                authorHandle = item.authorHandle ?: base?.authorHandle,
                authorFollowers = item.authorFollowers ?: base?.authorFollowers,
                authorCountry = item.authorCountry ?: base?.authorCountry,
                thumbnailUrl = item.thumbnailUrl ?: base?.thumbnailUrl,
                publishedAt = base?.publishedAt ?: item.publishedAt,
                publishedAtSource = base?.publishedAtSource ?: item.publishedAt?.let { PublishedAtSource.SOURCE },
                addedAt = base?.addedAt ?: now,
                category = item.category ?: base?.category,
                hashtags = (base?.hashtags.orEmpty() + item.hashtags).distinctBy { it.lowercase() },
                soundId = item.soundId ?: base?.soundId,
                soundTitle = item.soundTitle ?: base?.soundTitle,
                formatTags = (base?.formatTags.orEmpty() + item.formatTags).distinctBy { it.lowercase() },
                trendTypes = base?.trendTypes.orEmpty() + item.trendTypes,
                hookNotes = base?.hookNotes,
                notes = base?.notes,
                favorite = base?.favorite ?: false,
                source = base?.source ?: source,
            )
            videos.upsert(merged.toEntity())
            if (existing == null) created++ else updated++
            val hasStats = item.views != null || item.likes != null || item.comments != null || item.shares != null
            if (hasStats) {
                val at = item.capturedAt ?: now
                if (videos.snapshotExists(merged.id, at) == 0) {
                    videos.insertSnapshot(SnapshotEntity(0, merged.id, at, item.views, item.likes, item.comments, item.shares, source))
                    snaps++
                }
            }
            for ((country, rank) in item.popularIn) {
                val before = regions.count(TARGET_VIDEO, merged.id, country, item.popularBasis.name)
                addRegionInternal(TARGET_VIDEO, merged.id, RegionObservation(country, item.popularBasis, item.capturedAt ?: now, rank, source))
                if (before == 0) regs++
            }
        }
        ImportSummary(created, updated, snaps, regs, result.errors.size)
    }

    // ---------------- Ideas ----------------

    val savedIdeas: Flow<List<SavedIdea>> = ideas.observeAll().map { list -> list.mapNotNull { e -> e.toSaved() } }

    private fun IdeaEntity.toSaved(): SavedIdea? {
        val idea = LlmJson.decodeIdea(contentJson) ?: return null
        return SavedIdea(
            id, trendId, videoId, idea, targetCountry, genre,
            IdeaStatus.entries.firstOrNull { it.name == status } ?: IdeaStatus.IDEA,
            notes, publishedUrl, createdAt, updatedAt,
        )
    }

    suspend fun idea(id: Long): SavedIdea? = ideas.byId(id)?.toSaved()

    suspend fun saveIdea(idea: Idea, trendId: String?, videoId: String?, targetCountry: String?, genre: String?): Long {
        val now = System.currentTimeMillis()
        return ideas.insert(
            IdeaEntity(0, trendId, videoId, idea.title, LlmJson.encodeIdea(idea), targetCountry, genre, IdeaStatus.IDEA.name, idea.origin.name, null, null, now, now)
        )
    }

    suspend fun updateIdea(id: Long, status: IdeaStatus? = null, notes: String? = null, publishedUrl: String? = null) {
        val e = ideas.byId(id) ?: return
        ideas.upsert(
            e.copy(
                status = status?.name ?: e.status,
                notes = notes ?: e.notes,
                publishedUrl = publishedUrl ?: e.publishedUrl,
                updatedAt = System.currentTimeMillis(),
            )
        )
        // Publishing an idea marks its source trend as used (moves it to the archive of used trends).
        if (status == IdeaStatus.PUBLISHED && e.trendId != null) {
            trends.byId(e.trendId)?.let { trends.upsert(it.copy(used = true)) }
        }
    }

    suspend fun deleteIdea(id: Long) = db.withTransaction {
        collections.removeEverywhere(TARGET_IDEA, id.toString())
        ideas.delete(id)
    }

    // ---------------- Collections ----------------

    val collectionsFlow: Flow<List<Collection>> = combine(collections.observeAll(), collections.observeItems()) { cs, items ->
        val byC = items.groupBy { it.collectionId }
        cs.map { c -> Collection(c.id, c.name, byC[c.id].orEmpty().sortedByDescending { it.addedAt }.map { it.itemType to it.itemId }) }
    }

    suspend fun createCollection(name: String): Long = collections.insert(CollectionEntity(0, name.trim(), System.currentTimeMillis()))
    suspend fun renameCollection(id: Long, name: String) = collections.rename(id, name.trim())
    suspend fun deleteCollection(id: Long) = collections.delete(id)
    suspend fun addToCollection(cid: Long, type: String, itemId: String) =
        collections.addItem(CollectionItemEntity(cid, type, itemId, System.currentTimeMillis()))
    suspend fun removeFromCollection(cid: Long, type: String, itemId: String) = collections.removeItem(cid, type, itemId)

    fun splitTags(raw: String): List<String> =
        raw.split(',', ' ', '\n', ';').map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }
}
