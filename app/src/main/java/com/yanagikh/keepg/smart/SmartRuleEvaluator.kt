package com.yanagikh.keepg.smart

import com.yanagikh.keepg.data.FaceObservationEntity
import com.yanagikh.keepg.data.PhotoEntity
import com.yanagikh.keepg.data.SmartRuleEntity
import kotlin.math.*

object SmartRuleEvaluator {
    fun matches(photo: PhotoEntity, faces: List<FaceObservationEntity>, rule: SmartRuleEntity): Boolean {
        if (!rule.enabled) return false
        return when (rule.kind) {
            "PERSON" -> rule.personClusterId != null && faces.any { it.clusterId == rule.personClusterId }
            "TIME" -> photo.dateTaken in (rule.startTime ?: Long.MIN_VALUE)..(rule.endTime ?: Long.MAX_VALUE)
            "LOCATION" -> {
                val lat = photo.latitude ?: return false; val lon = photo.longitude ?: return false
                val targetLat = rule.latitude ?: return false; val targetLon = rule.longitude ?: return false
                haversineMeters(lat, lon, targetLat, targetLon) <= (rule.radiusMeters ?: 1000.0)
            }
            "EXPRESSION" -> faces.any { ExpressionClassifier.matches(rule.expression ?: return@any false, rule.threshold ?: 0.7f, it.smileProbability, it.leftEyeOpenProbability, it.rightEyeOpenProbability) }
            else -> false
        }
    }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earth = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * earth * atan2(sqrt(a), sqrt(1 - a))
    }
}
