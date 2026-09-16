package com.kapil.agentphone

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.kapil.agentphone.service.BridgeService
import com.kapil.agentphone.ui.AgentPhoneTheme
import com.kapil.agentphone.ui.App

class MainActivity : ComponentActivity() {

    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        BridgeService.start(this)
        setContent {
            AgentPhoneTheme {
                App()
            }
        }
    }
}
