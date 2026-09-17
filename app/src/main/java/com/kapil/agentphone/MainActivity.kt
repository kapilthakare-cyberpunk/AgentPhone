package com.kapil.agentphone

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.kapil.agentphone.service.BridgeService
import com.kapil.agentphone.ui.AgentPhoneTheme
import com.kapil.agentphone.ui.App
import com.kapil.agentphone.util.CrashLog

class MainActivity : ComponentActivity() {

    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        val lastCrash = CrashLog.read(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        try {
            BridgeService.start(this)
        } catch (e: Exception) {
            Toast.makeText(this, "Link service blocked: ${e.message}", Toast.LENGTH_LONG).show()
        }
        setContent {
            AgentPhoneTheme {
                App(initialCrash = lastCrash)
            }
        }
    }
}
