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
        val bitmap = decodeSampledBitmap(uri, photo.uri)
        val analyzedFaces = try {
            detector.process(InputImage.fromBitmap(bitmap, 0)).await().mapIndexedNotNull { index, face ->
                val crop = cropFace(bitmap, face) ?: return@mapIndexedNotNull null
                try {
                    AnalyzedFace(
                        index = index,
                        descriptor = FaceDescriptor.extract(crop, face),
                        smileProbability = face.smilingProbability,
                        leftEyeOpenProbability = face.leftEyeOpenProbability,
                        rightEyeOpenProbability = face.rightEyeOpenProbability,
                    )
                } finally {
                    if (crop !== bitmap) crop.recycle()
                }
            }
        } finally {
            bitmap.recycle()
        }
        val existing = dao.getFaces().filterNot { it.mediaId == photo.mediaId }
        val observations = existing.map { FaceClusterer.Observation(it.id, it.clusterId, EmbeddingCodec.decode(it.embedding)) }
        var nextCluster = (existing.mapNotNull { it.clusterId }.maxOrNull() ?: 0L) + 1L
        val newFaces = analyzedFaces.map { analyzed ->
            val cluster = FaceClusterer.bestCluster(analyzed.descriptor, observations) ?: nextCluster++
            FaceObservationEntity(
                mediaId = photo.mediaId,
                faceIndex = analyzed.index,
                embedding = EmbeddingCodec.encode(analyzed.descriptor),
                smileProbability = analyzed.smileProbability,
                leftEyeOpenProbability = analyzed.leftEyeOpenProbability,
                rightEyeOpenProbability = analyzed.rightEyeOpenProbability,
                clusterId = cluster,
            )
        }
        dao.replaceFaceAnalysis(photo.mediaId, newFaces)
        updateLocation(photo)
        newFaces.size
    }

    private fun decodeSampledBitmap(uri: Uri, displayUri: String): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "Cannot open $displayUri" }
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Cannot read image bounds for $displayUri" }

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        return context.contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "Cannot reopen $displayUri" }
            BitmapFactory.decodeStream(stream, null, options)
        } ?: error("Cannot decode $displayUri")
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (sampleSize <= Int.MAX_VALUE / 2) {
            val sampledWidth = (width.toLong() + sampleSize - 1L) / sampleSize
            val sampledHeight = (height.toLong() + sampleSize - 1L) / sampleSize
            val withinDimensionLimit = sampledWidth <= MAX_ANALYSIS_DIMENSION && sampledHeight <= MAX_ANALYSIS_DIMENSION
            val withinPixelLimit = sampledWidth * sampledHeight <= MAX_ANALYSIS_PIXELS
            if (withinDimensionLimit && withinPixelLimit) break
            sampleSize *= 2
        }
        return sampleSize
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

    private data class AnalyzedFace(
        val index: Int,
        val descriptor: FloatArray,
        val smileProbability: Float?,
        val leftEyeOpenProbability: Float?,
        val rightEyeOpenProbability: Float?,
    )

    private companion object {
        const val MAX_ANALYSIS_DIMENSION = 4_096L
        const val MAX_ANALYSIS_PIXELS = 4_000_000L
    }
}
