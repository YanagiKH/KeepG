package com.yanagikh.keepg

import android.app.Application
import com.yanagikh.keepg.data.KeepGDatabase
import com.yanagikh.keepg.data.MediaStoreRepository
import com.yanagikh.keepg.data.VaultRepository
import com.yanagikh.keepg.security.VaultCipher
import com.yanagikh.keepg.smart.FaceAnalysisEngine

class KeepGApplication : Application() { val container by lazy { AppContainer(this) } }
class AppContainer(application: Application) {
    val database = KeepGDatabase.create(application)
    val dao = database.dao()
    val mediaStore = MediaStoreRepository(application, dao)
    val vault = VaultRepository(application, dao, VaultCipher())
    val faceAnalysis = FaceAnalysisEngine(application, dao)
}
