package com.yanagikh.keepg

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.yanagikh.keepg.security.DeviceAuthenticator
import com.yanagikh.keepg.ui.KeepGAppV2
import com.yanagikh.keepg.ui.KeepGTheme
import com.yanagikh.keepg.widget.KeepGWidgetProvider

class MainActivity : FragmentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as KeepGApplication
        viewModel = ViewModelProvider(this, MainViewModelFactory(app.container))[MainViewModel::class.java]
        val startDestination = intent.getStringExtra(KeepGWidgetProvider.EXTRA_START_DESTINATION)
        setContent {
            KeepGTheme {
                KeepGAppV2(
                    viewModel = viewModel,
                    requestDeviceAuthentication = { title, success, error ->
                        if (!DeviceAuthenticator.isAvailable(this)) error("No supported device credential is configured")
                        else DeviceAuthenticator.authenticate(this, title, success, error)
                    },
                    mediaPermissions = requiredMediaPermissions(),
                    initialDestination = startDestination,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Re-create the Compose tree so an identical widget shortcut is honored repeatedly.
        recreate()
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
