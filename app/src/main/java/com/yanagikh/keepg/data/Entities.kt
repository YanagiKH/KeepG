package com.yanagikh.keepg.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "photos", indices = [Index("bucketId"), Index("dateTaken"), Index("sizeBytes")])
data class PhotoEntity(
    @PrimaryKey val mediaId: Long,
    val uri: String,
    val bucketId: Long,
    val bucketName: String,
    val displayName: String,
    val mimeType: String,
    val dateTaken: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lastScannedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "media_text_index", indices = [Index("indexedAt")])
data class MediaTextIndexEntity(
    @PrimaryKey val mediaId: Long,
    val text: String,
    val indexedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "locks", primaryKeys = ["targetType", "targetId"])
data class LockEntity(
    val targetType: String,
    val targetId: String,
    val authType: String,
    val passwordHash: String? = null,
    val passwordSalt: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "face_observations", indices = [Index("mediaId"), Index("clusterId")])
data class FaceObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val faceIndex: Int,
    val embedding: ByteArray,
    val smileProbability: Float? = null,
    val leftEyeOpenProbability: Float? = null,
    val rightEyeOpenProbability: Float? = null,
    val clusterId: Long? = null,
    val analyzedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "person_profiles", indices = [Index(value = ["clusterId"], unique = true)])
data class PersonProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clusterId: Long,
    val displayName: String,
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "smart_rules")
data class SmartRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val enabled: Boolean = true,
    val personClusterId: Long? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Double? = null,
    val expression: String? = null,
    val threshold: Float? = null,
)

@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalMediaId: Long?,
    val originalUri: String?,
    val displayName: String,
    val mimeType: String,
    val encryptedPath: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "collection_items", primaryKeys = ["collectionId", "mediaId"], indices = [Index("mediaId")])
data class CollectionItemEntity(
    val collectionId: Long,
    val mediaId: Long,
)
