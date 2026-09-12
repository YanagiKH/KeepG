package com.yanagikh.keepg.editor

/** All UI and exporter bounds are validated again at the storage boundary. */
data class VideoEditSpec(
    val startMs: Long = 0, val endMs: Long,
    val crop: CropBounds = CropBounds(),
    val rotation: Float = 0f, val flipX: Boolean = false, val flipY: Boolean = false,
    val speed: Float = 1f, val mute: Boolean = false, val height: Int = 0,
    val outputName: String = "KeepG_video_edit",
) {
    fun validate(duration: Long) {
        require(startMs >= 0 && endMs > startMs && endMs <= duration && endMs <= 86_400_000L)
        crop.validate()
        require(rotation.isFinite() && rotation in -360f..360f)
        require(speed.isFinite() && speed in .25f..4f)
        require(height in setOf(0, 480, 720, 1080))
        require(outputName.isNotBlank() && outputName.length <= 100)
    }
}
