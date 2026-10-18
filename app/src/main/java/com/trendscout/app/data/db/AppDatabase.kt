package com.trendscout.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        VideoEntity::class, SnapshotEntity::class, RegionObservationEntity::class,
        TrendEntity::class, TrendSnapshotEntity::class, IdeaEntity::class,
        CollectionEntity::class, CollectionItemEntity::class, NotifiedEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videos(): VideoDao
    abstract fun trends(): TrendDao
    abstract fun regions(): RegionDao
    abstract fun ideas(): IdeaDao
    abstract fun collections(): CollectionDao
    abstract fun notified(): NotifiedDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "trendscout.db").build()
    }
}
