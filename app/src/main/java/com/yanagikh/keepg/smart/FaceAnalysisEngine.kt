package com.yanagikh.keepg.smart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.yanagikh.keepg.data.FaceObservationEntity
import com.yanagikh.keepg.data.KeepGDao
import com.yanagikh.keepg.data.PhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

class FaceAnalysisEngine(private val context: Context, private val dao: KeepGDao) {
    private val detector by lazy {
        FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE).setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL).setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL).setMinFaceSize(0.08f).enableTracking().build())
    }

    suspend fun analyze(photo: PhotoEntity): Int = withContext(Dispatchers.IO) {
        val uri = Uri.parse(photo.uri)
        val bitmap = context.contentResolver.openInputStream(uri).use { stream -> requireNotNull(stream) { "Cannot open ${photo.uri}" }; BitmapFactory.decodeStream(stream) } ?: error("Cannot decode ${photo.uri}")
        val faces = detector.process(InputImage.fromBitmap(bitmap, 0)).await()
        val existing = dao.getFaces().filterNot { it.mediaId == photo.mediaId }
        val observations = existing.map { FaceClusterer.Observation(it.id, it.clusterId, EmbeddingCodec.decode(it.embedding)) }
        var nextCluster = (existing.mapNotNull { it.clusterId }.maxOrNull() ?: 0L) + 1L
        val newFaces = faces.mapIndexedNotNull { index, face ->
            val crop = cropFace(bitmap, face) ?: return@mapIndexedNotNull null
            val descriptor = FaceDescriptor.extract(crop, face)
            val cluster = FaceClusterer.bestCluster(descriptor, observations) ?: nextCluster++
            FaceObservationEntity(mediaId = photo.mediaId, faceIndex = index, embedding = EmbeddingCodec.encode(descriptor), smileProbability = face.smilingProbability, leftEyeOpenProbability = face.leftEyeOpenProbability, rightEyeOpenProbability = face.rightEyeOpenProbability, clusterId = cluster)
        }
        dao.replaceFaceAnalysis(photo.mediaId, newFaces)
        updateLocation(photo)
        newFaces.size
    }

    private fun cropFace(bitmap: Bitmap, face: Face): Bitmap? {
        val paddingX = face.boundingBox.width() / 5; val paddingY = face.boundingBox.height() / 5
        val left = max(0, face.boundingBox.left - paddingX); val top = max(0, face.boundingBox.top - paddingY)
        val right = min(bitmap.width, face.boundingBox.right + paddingX); val bottom = min(bitmap.height, face.boundingBox.bottom + paddingY)
        if (right <= left || bottom <= top) return null
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private suspend fun updateLocation(photo: PhotoEntity) {
        if (photo.latitude != null && photo.longitude != null) return
        val location = runCatching { context.contentResolver.openInputStream(Uri.parse(photo.uri)).use { input -> input ?: return@runCatching null; ExifInterface(input).latLong } }.getOrNull()
        dao.updateLocation(photo.mediaId, location?.getOrNull(0), location?.getOrNull(1))
    }
}
