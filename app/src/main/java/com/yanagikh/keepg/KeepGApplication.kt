package com.yanagikh.keepg

import android.app.Application
import com.yanagikh.keepg.advanced.AdvancedToolsFactory
import com.yanagikh.keepg.data.KeepGDatabase
import com.yanagikh.keepg.data.MediaStoreRepository
import com.yanagikh.keepg.data.VaultRepository
import com.yanagikh.keepg.debug.KeepGLog
import com.yanagikh.keepg.security.VaultCipher
import com.yanagikh.keepg.smart.FaceAnalysisEngine

class KeepGApplication : Application() {
    val container by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    val log = KeepGLog(application)
    val database = KeepGDatabase.create(application)
    val dao = database.dao()
    val mediaStore = MediaStoreRepository(application, dao, log)
    val vault = VaultRepository(application, dao, VaultCipher())
    val faceAnalysis = FaceAnalysisEngine(application, dao)
    val advanced = AdvancedToolsFactory.create(application)
}
