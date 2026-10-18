package com.trendscout.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos") fun observeAll(): Flow<List<VideoEntity>>
    @Query("SELECT * FROM videos") suspend fun all(): List<VideoEntity>
    @Query("SELECT * FROM videos WHERE id = :id") suspend fun byId(id: String): VideoEntity?
    @Query("SELECT * FROM videos WHERE url = :url") suspend fun byUrl(url: String): VideoEntity?
    @Upsert suspend fun upsert(v: VideoEntity)
    @Query("DELETE FROM videos WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE videos SET favorite = :fav WHERE id = :id") suspend fun setFavorite(id: String, fav: Boolean)

    @Query("SELECT * FROM snapshots") fun observeSnapshots(): Flow<List<SnapshotEntity>>
    @Query("SELECT * FROM snapshots") suspend fun allSnapshots(): List<SnapshotEntity>
    @Insert suspend fun insertSnapshot(s: SnapshotEntity)
    @Query("DELETE FROM snapshots WHERE id = :id") suspend fun deleteSnapshot(id: Long)
    @Query("SELECT COUNT(*) FROM snapshots WHERE videoId = :videoId AND capturedAt = :at") suspend fun snapshotExists(videoId: String, at: Long): Int
}

@Dao
interface TrendDao {
    @Query("SELECT * FROM trends") fun observeAll(): Flow<List<TrendEntity>>
    @Query("SELECT * FROM trends") suspend fun all(): List<TrendEntity>
    @Query("SELECT * FROM trends WHERE id = :id") suspend fun byId(id: String): TrendEntity?
    @Upsert suspend fun upsert(t: TrendEntity)
    @Query("DELETE FROM trends WHERE id = :id") suspend fun delete(id: String)

    @Query("SELECT * FROM trend_snapshots") fun observeSnapshots(): Flow<List<TrendSnapshotEntity>>
    @Query("SELECT * FROM trend_snapshots") suspend fun allSnapshots(): List<TrendSnapshotEntity>
    @Insert suspend fun insertSnapshot(s: TrendSnapshotEntity)
    @Query("DELETE FROM trend_snapshots WHERE id = :id") suspend fun deleteSnapshot(id: Long)
}

@Dao
interface RegionDao {
    @Query("SELECT * FROM region_observations") fun observeAll(): Flow<List<RegionObservationEntity>>
    @Query("SELECT * FROM region_observations") suspend fun all(): List<RegionObservationEntity>
    @Insert suspend fun insert(o: RegionObservationEntity)
    @Query("DELETE FROM region_observations WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM region_observations WHERE targetType = :type AND targetId = :id") suspend fun deleteFor(type: String, id: String)
    @Query("SELECT COUNT(*) FROM region_observations WHERE targetType = :type AND targetId = :id AND countryCode = :country AND basis = :basis")
    suspend fun count(type: String, id: String, country: String, basis: String): Int
}

@Dao
interface IdeaDao {
    @Query("SELECT * FROM ideas ORDER BY updatedAt DESC") fun observeAll(): Flow<List<IdeaEntity>>
    @Query("SELECT * FROM ideas WHERE id = :id") suspend fun byId(id: Long): IdeaEntity?
    @Insert suspend fun insert(i: IdeaEntity): Long
    @Upsert suspend fun upsert(i: IdeaEntity)
    @Query("DELETE FROM ideas WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY createdAt DESC") fun observeAll(): Flow<List<CollectionEntity>>
    @Query("SELECT * FROM collection_items") fun observeItems(): Flow<List<CollectionItemEntity>>
    @Insert suspend fun insert(c: CollectionEntity): Long
    @Query("DELETE FROM collections WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE collections SET name = :name WHERE id = :id") suspend fun rename(id: Long, name: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addItem(i: CollectionItemEntity)
    @Query("DELETE FROM collection_items WHERE collectionId = :cid AND itemType = :type AND itemId = :itemId")
    suspend fun removeItem(cid: Long, type: String, itemId: String)
    @Query("DELETE FROM collection_items WHERE itemType = :type AND itemId = :itemId") suspend fun removeEverywhere(type: String, itemId: String)
}

@Dao
interface NotifiedDao {
    @Query("SELECT COUNT(*) FROM notified WHERE `key` = :key") suspend fun exists(key: String): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(n: NotifiedEntity)
    @Query("DELETE FROM notified WHERE at < :before") suspend fun prune(before: Long)
}
