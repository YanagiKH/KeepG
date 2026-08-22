package com.yanagikh.keepg.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PhotoEntity::class, LockEntity::class, FaceObservationEntity::class, PersonProfileEntity::class, SmartRuleEntity::class, VaultItemEntity::class, CollectionEntity::class, CollectionItemEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class KeepGDatabase : RoomDatabase() {
    abstract fun dao(): KeepGDao

    companion object {
        fun create(context: Context): KeepGDatabase = Room.databaseBuilder(context.applicationContext, KeepGDatabase::class.java, "keepg.db").build()
    }
}
