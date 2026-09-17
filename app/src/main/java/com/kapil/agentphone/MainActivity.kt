package com.kapil.agentphone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kapil.agentphone.data.BridgeClient
import com.kapil.agentphone.data.Prefs
import com.kapil.agentphone.service.BridgeService
import com.kapil.agentphone.ui.AgentPhoneTheme
import com.kapil.agentphone.ui.App
import com.kapil.agentphone.util.CrashLog
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_HOST = "com.kapil.agentphone.HOST"
        const val EXTRA_PORT = "com.kapil.agentphone.PORT"
        const val EXTRA_TOKEN = "com.kapil.agentphone.TOKEN"
    }

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
        handleProvisionIntent(intent)
        setContent {
            AgentPhoneTheme {
                App(initialCrash = lastCrash)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleProvisionIntent(intent)
    }

    /** MDM-style provisioning: adb shell am start -n .../.MainActivity
     *  --es com.kapil.agentphone.HOST 192.168.1.5 --ei ...PORT 9876
     *  --es com.kapil.agentphone.TOKEN xxx */
    private fun handleProvisionIntent(intent: Intent?) {
        if (intent == null || !intent.hasExtra(EXTRA_HOST)) return
        val host = intent.getStringExtra(EXTRA_HOST)?.trim().orEmpty()
        val port = intent.getIntExtra(EXTRA_PORT, 9876)
        val token = intent.getStringExtra(EXTRA_TOKEN)?.trim().orEmpty()
        if (host.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "Provisioning needs HOST + TOKEN", Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            Prefs(this@MainActivity).save(host, port, token)
            BridgeClient.reconnectSoon()
            Toast.makeText(this@MainActivity, "Link provisioned: $host", Toast.LENGTH_LONG).show()
        }
    }
}
