package com.kapil.agentphone.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the persistent link after reboot. On newer Android the system may
 * refuse a background foreground-service start here — opening the app once
 * after reboot always works.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            try {
                BridgeService.start(ctx)
            } catch (_: Exception) {
            }
        }
    }
}
