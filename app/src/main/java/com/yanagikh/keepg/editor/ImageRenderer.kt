package com.yanagikh.keepg.editor

import android.graphics.*
import com.yanagikh.keepg.advanced.AdvancedEditRequest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Shared drawing path: the editor's preview and exported file use identical transforms. */
object ImageRenderer {
    fun render(source: Bitmap, request: AdvancedEditRequest): Bitmap {
        request.validate()
        val viewport = viewport(source, request.viewportAspectRatio)
        val crop = request.crop
        val rawWidth = viewport.first * crop.width
        val rawHeight = viewport.second * crop.height
        val scale = min(1f, min(request.maxEdge / max(rawWidth, rawHeight), sqrt(8_388_608f / (rawWidth * rawHeight))))
        val bitmap = Bitmap.createBitmap(max(1, (rawWidth * scale).toInt()), max(1, (rawHeight * scale).toInt()), Bitmap.Config.ARGB_8888)
        try { draw(Canvas(bitmap), source, request, bitmap.width.toFloat(), bitmap.height.toFloat(), crop) }
        catch (error: Throwable) { bitmap.recycle(); throw error }
        return bitmap
    }
    fun draw(canvas: Canvas, source: Bitmap, request: AdvancedEditRequest, width: Float, height: Float, crop: CropBounds = CropBounds()) {
        if (width <= 0 || height <= 0 || source.isRecycled) return
        val (vw, vh) = viewport(source, request.viewportAspectRatio)
        val factorX = width / (vw * crop.width)
        val factorY = height / (vh * crop.height)
        val save = canvas.save()
        try {
            canvas.clipRect(0f, 0f, width, height)
            canvas.scale(factorX, factorY)
            canvas.translate(-crop.left * vw, -crop.top * vh)
            val matrix = Matrix().apply {
                postTranslate(-source.width / 2f, -source.height / 2f)
                postScale(request.scale * if (request.flipX) -1f else 1f, request.scale * if (request.flipY) -1f else 1f)
                postRotate(request.rotation)
                postTranslate(vw * (.5f + request.offsetX), vh * (.5f + request.offsetY))
            }
            val colorMatrix = ColorMatrix().apply { setSaturation(request.saturation) }
            val contrast = request.contrast
            val shift = (1 - contrast) * 128 + request.brightness * 255
            colorMatrix.postConcat(ColorMatrix(floatArrayOf(contrast, 0f, 0f, 0f, shift, 0f, contrast, 0f, 0f, shift, 0f, 0f, contrast, 0f, shift, 0f, 0f, 0f, 1f, 0f)))
            canvas.drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(colorMatrix) })
            request.textLayers.forEach { layer ->
                val x = layer.x * vw
                val y = layer.y * vh
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = layer.colorArgb
                    typeface = if (layer.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    textSize = min(vw, vh) * .075f * layer.scale
                    if (layer.shadow) setShadowLayer(max(1f, textSize * .04f), 0f, textSize * .02f, Color.BLACK)
                }
                canvas.save()
                canvas.rotate(layer.rotation, x, y)
                canvas.drawText(layer.text, x, y - paint.fontMetrics.top, paint)
                canvas.restore()
            }
        } finally { canvas.restoreToCount(save) }
    }
    private fun viewport(source: Bitmap, aspect: Float): Pair<Float, Float> =
        if (aspect >= source.width.toFloat() / source.height) source.height * aspect to source.height.toFloat()
        else source.width.toFloat() to source.width / aspect
}
