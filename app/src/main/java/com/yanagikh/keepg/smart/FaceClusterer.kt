package com.yanagikh.keepg.smart

import kotlin.math.sqrt

object FaceClusterer {
    data class Observation(val id: Long, val clusterId: Long?, val embedding: FloatArray)

    fun bestCluster(candidate: FloatArray, observations: List<Observation>, minimumSimilarity: Float = 0.90f): Long? {
        val clusters = observations.filter { it.clusterId != null }.groupBy { it.clusterId!! }
        var best: Pair<Long, Float>? = null
        for ((clusterId, members) in clusters) {
            val centroid = centroid(members.map { it.embedding })
            val similarity = cosineSimilarity(candidate, centroid)
            if (similarity >= minimumSimilarity && (best == null || similarity > best.second)) best = clusterId to similarity
        }
        return best?.first
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size && a.isNotEmpty())
        var dot = 0.0; var normA = 0.0; var normB = 0.0
        for (i in a.indices) { dot += a[i] * b[i]; normA += a[i] * a[i]; normB += b[i] * b[i] }
        if (normA == 0.0 || normB == 0.0) return 0f
        return (dot / (sqrt(normA) * sqrt(normB))).toFloat()
    }

    private fun centroid(values: List<FloatArray>): FloatArray {
        require(values.isNotEmpty())
        val result = FloatArray(values.first().size)
        values.forEach { vector -> vector.indices.forEach { result[it] += vector[it] } }
        for (i in result.indices) result[i] /= values.size.toFloat()
        return result
    }
}
