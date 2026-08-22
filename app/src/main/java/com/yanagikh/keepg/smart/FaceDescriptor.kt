package com.yanagikh.keepg.smart

import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face
import kotlin.math.sqrt

object FaceDescriptor {
    private const val GRID = 8
    fun extract(faceBitmap: Bitmap, face: Face): FloatArray {
        val scaled = Bitmap.createScaledBitmap(faceBitmap, GRID, GRID, true)
        val luminance = FloatArray(GRID * GRID)
        var mean = 0f; var p = 0
        for (y in 0 until GRID) for (x in 0 until GRID) {
            val color = scaled.getPixel(x, y)
            val r = (color shr 16) and 0xff; val g = (color shr 8) and 0xff; val b = color and 0xff
            val value = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
            luminance[p++] = value; mean += value
        }
        mean /= luminance.size
        var variance = 0f
        luminance.indices.forEach { i -> luminance[i] -= mean; variance += luminance[i] * luminance[i] }
        val std = sqrt((variance / luminance.size).coerceAtLeast(1e-6f))
        luminance.indices.forEach { i -> luminance[i] /= std }
        val geometry = floatArrayOf(face.headEulerAngleX / 45f, face.headEulerAngleY / 45f, face.headEulerAngleZ / 45f, face.smilingProbability ?: -1f, face.leftEyeOpenProbability ?: -1f, face.rightEyeOpenProbability ?: -1f)
        return luminance + geometry
    }
}
