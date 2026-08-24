package com.yanagikh.keepg

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import com.yanagikh.keepg.advanced.AdvancedToolsFactory
import com.yanagikh.keepg.data.GalleryPreferences
import com.yanagikh.keepg.data.KeepGDatabase
import com.yanagikh.keepg.data.MediaActionRepository
import com.yanagikh.keepg.data.MediaStoreRepository
import com.yanagikh.keepg.data.VaultRepository
import com.yanagikh.keepg.debug.KeepGLog
import com.yanagikh.keepg.media.SensitiveContentClassifier
import com.yanagikh.keepg.security.VaultCipher
import com.yanagikh.keepg.smart.FaceAnalysisEngine

class KeepGApplication : Application(), ImageLoaderFactory {
    val container by lazy { AppContainer(this) }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                else add(GifDecoder.Factory())
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
}

class AppContainer(application: Application) {
    val log = KeepGLog(application)
    val database = KeepGDatabase.create(application)
    val dao = database.dao()
    val preferences = GalleryPreferences(application)
    val mediaStore = MediaStoreRepository(application, dao, log)
    val mediaActions = MediaActionRepository(application)
    val sensitiveContent = SensitiveContentClassifier(application)
    val vault = VaultRepository(application, dao, VaultCipher())
    val faceAnalysis = FaceAnalysisEngine(application, dao)
    val advanced = AdvancedToolsFactory.create(application)
}
