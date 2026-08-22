package com.yanagikh.keepg.smart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceClustererTest {
    @Test fun cosineSimilarityRecognizesEquivalentDirection() {
        assertTrue(FaceClusterer.cosineSimilarity(floatArrayOf(1f, 2f, 3f), floatArrayOf(2f, 4f, 6f)) > 0.999f)
    }
    @Test fun bestClusterReturnsClosestMatchingCluster() {
        val observations = listOf(FaceClusterer.Observation(1, 10, floatArrayOf(1f, 0f, 0f)), FaceClusterer.Observation(2, 10, floatArrayOf(0.98f, 0.02f, 0f)), FaceClusterer.Observation(3, 20, floatArrayOf(0f, 1f, 0f)))
        assertEquals(10L, FaceClusterer.bestCluster(floatArrayOf(0.99f, 0.01f, 0f), observations, 0.95f))
        assertNull(FaceClusterer.bestCluster(floatArrayOf(0f, 0f, 1f), observations, 0.95f))
    }
}
