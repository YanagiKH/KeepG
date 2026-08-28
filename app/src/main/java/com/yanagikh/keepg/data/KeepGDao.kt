package com.yanagikh.keepg.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface KeepGDao {
    @Query("SELECT * FROM photos ORDER BY dateTaken DESC, mediaId DESC")
    fun observePhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE bucketId = :bucketId ORDER BY dateTaken DESC")
    fun observeAlbum(bucketId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM media_text_index")
    fun observeMediaTextIndex(): Flow<List<MediaTextIndexEntity>>

    @Query("SELECT COUNT(*) FROM media_text_index")
    fun observeMediaTextIndexCount(): Flow<Int>

    @Query("SELECT mediaId FROM media_text_index")
    suspend fun getIndexedMediaIds(): List<Long>

    @Query("SELECT media_text_index.mediaId FROM media_text_index INNER JOIN photos ON photos.mediaId = media_text_index.mediaId WHERE (:bucketId IS NULL OR photos.bucketId = :bucketId) AND instr(lower(media_text_index.text), lower(:query)) > 0")
    suspend fun searchMediaText(query: String, bucketId: Long?): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhotos(photos: List<PhotoEntity>)

    @Query("DELETE FROM photos WHERE lastScannedAt < :scanStartedAt")
    suspend fun prunePhotos(scanStartedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMediaTextIndex(index: MediaTextIndexEntity)

    @Query("DELETE FROM media_text_index WHERE mediaId NOT IN (SELECT mediaId FROM photos)")
    suspend fun pruneMediaTextIndex()

    @Query("DELETE FROM locks WHERE targetType = 'PHOTO' AND targetId NOT IN (SELECT CAST(mediaId AS TEXT) FROM photos)")
    suspend fun prunePhotoLocks()

    @Query("DELETE FROM face_observations WHERE mediaId NOT IN (SELECT mediaId FROM photos)")
    suspend fun pruneFaceObservations()

    @Query("DELETE FROM collection_items WHERE mediaId NOT IN (SELECT mediaId FROM photos)")
    suspend fun pruneCollectionItems()

    @Query("DELETE FROM media_text_index")
    suspend fun clearMediaTextIndex()

    @Query("DELETE FROM media_text_index WHERE mediaId = :mediaId")
    suspend fun deleteMediaTextIndex(mediaId: Long)

    @Query("DELETE FROM media_text_index WHERE mediaId IN (SELECT mediaId FROM photos WHERE bucketId = :bucketId)")
    suspend fun deleteAlbumTextIndex(bucketId: Long)

    @Query("UPDATE photos SET latitude = :latitude, longitude = :longitude WHERE mediaId = :mediaId")
    suspend fun updateLocation(mediaId: Long, latitude: Double?, longitude: Double?)

    @Query("SELECT * FROM locks")
    fun observeLocks(): Flow<List<LockEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLock(lock: LockEntity)

    @Query("DELETE FROM locks WHERE targetType = :targetType AND targetId = :targetId")
    suspend fun deleteLock(targetType: String, targetId: String)

    @Query("SELECT * FROM face_observations ORDER BY analyzedAt DESC")
    fun observeFaces(): Flow<List<FaceObservationEntity>>

    @Query("SELECT * FROM face_observations")
    suspend fun getFaces(): List<FaceObservationEntity>

    @Query("SELECT * FROM face_observations WHERE mediaId = :mediaId")
    suspend fun getFacesForPhoto(mediaId: Long): List<FaceObservationEntity>

    @Query("DELETE FROM face_observations WHERE mediaId = :mediaId")
    suspend fun deleteFacesForPhoto(mediaId: Long)

    @Insert
    suspend fun insertFaces(faces: List<FaceObservationEntity>)

    @Query("UPDATE face_observations SET clusterId = :clusterId WHERE id = :faceId")
    suspend fun updateFaceCluster(faceId: Long, clusterId: Long)

    @Query("SELECT * FROM person_profiles ORDER BY pinned DESC, displayName COLLATE NOCASE")
    fun observePeople(): Flow<List<PersonProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPerson(person: PersonProfileEntity): Long

    @Query("SELECT * FROM person_profiles WHERE clusterId = :clusterId LIMIT 1")
    suspend fun getPersonForCluster(clusterId: Long): PersonProfileEntity?

    @Query("SELECT * FROM smart_rules ORDER BY id DESC")
    fun observeRules(): Flow<List<SmartRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: SmartRuleEntity): Long

    @Query("DELETE FROM smart_rules WHERE id = :id")
    suspend fun deleteRule(id: Long)

    @Query("SELECT * FROM vault_items ORDER BY createdAt DESC")
    fun observeVault(): Flow<List<VaultItemEntity>>

    @Insert
    suspend fun insertVaultItem(item: VaultItemEntity): Long

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteVaultItem(id: Long)

    @Query("SELECT * FROM collections ORDER BY name COLLATE NOCASE")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collection_items")
    fun observeCollectionItems(): Flow<List<CollectionItemEntity>>

    @Insert
    suspend fun insertCollection(collection: CollectionEntity): Long

    @Query("UPDATE collections SET name = :name WHERE id = :collectionId")
    suspend fun renameCollection(collectionId: Long, name: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCollectionItem(item: CollectionItemEntity)

    @Query("DELETE FROM collection_items WHERE collectionId = :collectionId AND mediaId = :mediaId")
    suspend fun removeCollectionItem(collectionId: Long, mediaId: Long)

    @Query("DELETE FROM collection_items WHERE collectionId = :collectionId")
    suspend fun clearCollectionItems(collectionId: Long)

    @Query("DELETE FROM collections WHERE id = :collectionId")
    suspend fun deleteCollectionRow(collectionId: Long)

    @Query("SELECT photos.* FROM photos INNER JOIN collection_items ON photos.mediaId = collection_items.mediaId WHERE collection_items.collectionId = :collectionId ORDER BY photos.dateTaken DESC")
    fun observeCollectionPhotos(collectionId: Long): Flow<List<PhotoEntity>>

    @Transaction
    suspend fun deleteCollection(collectionId: Long) {
        clearCollectionItems(collectionId)
        deleteCollectionRow(collectionId)
    }

    @Transaction
    suspend fun replaceScannedPhotos(photos: List<PhotoEntity>, scanStartedAt: Long, pruneMissing: Boolean) {
        if (photos.isNotEmpty()) upsertPhotos(photos)
        if (pruneMissing) {
            prunePhotos(scanStartedAt)
            pruneMediaTextIndex()
            prunePhotoLocks()
            pruneFaceObservations()
            pruneCollectionItems()
        }
    }

    @Transaction
    suspend fun replaceFaceAnalysis(mediaId: Long, faces: List<FaceObservationEntity>) {
        deleteFacesForPhoto(mediaId)
        if (faces.isNotEmpty()) insertFaces(faces)
    }
}
