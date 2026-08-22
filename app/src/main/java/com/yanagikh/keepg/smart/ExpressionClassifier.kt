package com.yanagikh.keepg.smart

object ExpressionClassifier {
    fun classify(smile: Float?, leftEye: Float?, rightEye: Float?): Set<String> {
        val labels = linkedSetOf<String>()
        if (smile != null) labels += if (smile >= 0.72f) "smiling" else "neutral"
        if (leftEye != null && rightEye != null && leftEye <= 0.25f && rightEye <= 0.25f) labels += "eyes_closed"
        return labels
    }

    fun matches(expression: String, threshold: Float, smile: Float?, leftEye: Float?, rightEye: Float?): Boolean = when (expression) {
        "smiling" -> (smile ?: -1f) >= threshold
        "neutral" -> (smile ?: 1f) < threshold
        "eyes_closed" -> (leftEye ?: 1f) <= threshold && (rightEye ?: 1f) <= threshold
        else -> false
    }
}
