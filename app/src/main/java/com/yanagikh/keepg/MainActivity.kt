package com.yanagikh.keepg

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.yanagikh.keepg.security.DeviceAuthenticator
import com.yanagikh.keepg.ui.KeepGApp
import com.yanagikh.keepg.ui.KeepGTheme

class MainActivity : FragmentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as KeepGApplication
        viewModel = ViewModelProvider(this, MainViewModelFactory(app.container))[MainViewModel::class.java]
        setContent {
            KeepGTheme {
                KeepGApp(
                    viewModel,
                    { title, success, error ->
                        if (!DeviceAuthenticator.isAvailable(this)) error("No supported device credential is configured")
                        else DeviceAuthenticator.authenticate(this, title, success, error)
                    },
                    requiredMediaPermissions(),
                )
            }
        }
    }

    private fun requiredMediaPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            if (Build.VERSION.SDK_INT >= 34) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }.toTypedArray()
}
