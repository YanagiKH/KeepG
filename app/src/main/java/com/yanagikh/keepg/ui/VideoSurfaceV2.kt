package com.yanagikh.keepg.ui

import android.view.TextureView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize

/**
 * A TextureView-backed Media3 surface that always fits the decoded video without stretching it.
 * TextureView is kept because KeepG applies uniform zoom/pan transforms to the preview surface.
 */
@Composable
internal fun FittedVideoTextureSurfaceV2(
    player: Player,
    fallbackWidth: Int,
    fallbackHeight: Int,
    modifier: Modifier = Modifier,
    fillContainer: Boolean = false,
) {
    var videoAspect by remember(player, fallbackWidth, fallbackHeight) {
        mutableFloatStateOf(safeMediaAspectRatio(fallbackWidth, fallbackHeight))
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspect = safeMediaAspectRatio(
                        videoSize.width,
                        videoSize.height,
                        videoSize.pixelWidthHeightRatio,
                    )
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    BoxWithConstraints(modifier = modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val containerAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 1f
        val fittedModifier = if ((videoAspect >= containerAspect) xor fillContainer) {
            Modifier.fillMaxWidth().aspectRatio(videoAspect)
        } else {
            Modifier.fillMaxHeight().aspectRatio(videoAspect)
        }
        AndroidView(
            modifier = fittedModifier.clipToBounds(),
            factory = { context -> TextureView(context).also(player::setVideoTextureView) },
            update = player::setVideoTextureView,
            onRelease = player::clearVideoTextureView,
        )
    }
}
