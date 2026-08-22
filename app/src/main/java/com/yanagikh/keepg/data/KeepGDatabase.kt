package com.yanagikh.keepg.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PhotoEntity::class, LockEntity::class, FaceObservationEntity::class, PersonProfileEntity::class, SmartRuleEntity::class, VaultItemEntity::class, CollectionEntity::class, CollectionItemEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class KeepGDatabase : RoomDatabase() {
    abstract fun dao(): KeepGDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN sizeBytes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE photos ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_photos_sizeBytes ON photos(sizeBytes)")
            }
        }

        fun create(context: Context): KeepGDatabase = Room.databaseBuilder(
            context.applicationContext,
            KeepGDatabase::class.java,
            "keepg.db",
        ).addMigrations(MIGRATION_1_2).build()
    }
}
