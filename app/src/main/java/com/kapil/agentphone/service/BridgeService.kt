package com.kapil.agentphone.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.Manifest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kapil.agentphone.MainActivity
import com.kapil.agentphone.R
import com.kapil.agentphone.data.Approval
import com.kapil.agentphone.data.BridgeClient
import com.kapil.agentphone.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps [BridgeClient] alive and turns agent events into notifications.
 * Approval verdicts work from the notification shade even with the UI dead.
 */
class BridgeService : Service() {

    companion object {
        const val CH_LINK = "link"
        const val CH_AGENT = "agent"
        const val ACTION_APPROVE = "com.kapil.agentphone.APPROVE"
        const val ACTION_DENY = "com.kapil.agentphone.DENY"
        const val EXTRA_RID = "rid"
        private const val LINK_ID = 1

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, BridgeService::class.java))
        }
    }

    private val svcScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var linkText = "Connecting…"

    override fun onCreate() {
        super.onCreate()
        createChannels()
        enterForeground()
        Log.d("Bridge", "service created")
        BridgeClient.onAgentAlert = { title, body, approval ->
            notifyAgent(title, body, approval)
        }
        val prefs = Prefs(applicationContext)
        svcScope.launch {
            prefs.link.collect {
                Log.d("Bridge", "prefs emitted hostLen=${it.host.length} tokenLen=${it.token.length}")
                BridgeClient.configure(it)
                BridgeClient.start(applicationContext)
            }
        }
        svcScope.launch {
            BridgeClient.state.collect { linkText = it.toString() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_APPROVE -> {
                val rid = intent.getStringExtra(EXTRA_RID) ?: return START_STICKY
                BridgeClient.verdict(rid, approve = true)
                NotificationManagerCompat.from(this).cancel(rid.hashCode())
            }
            ACTION_DENY -> {
                val rid = intent.getStringExtra(EXTRA_RID) ?: return START_STICKY
                BridgeClient.verdict(rid, approve = false)
                NotificationManagerCompat.from(this).cancel(rid.hashCode())
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        svcScope.cancel()
        super.onDestroy()
    }

    private fun enterForeground() {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CH_LINK)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(linkText)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                LINK_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(LINK_ID, notif)
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH_LINK, getString(R.string.channel_link),
                NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.channel_link_desc)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_AGENT, getString(R.string.channel_agent),
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = getString(R.string.channel_agent_desc)
            }
        )
    }

    private fun actionIntent(action: String, rid: String, code: Int): PendingIntent {
        val intent = Intent(this, BridgeService::class.java)
            .setAction(action)
            .putExtra(EXTRA_RID, rid)
        return PendingIntent.getService(
            this, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notifyAgent(title: String, body: String, approval: Approval?) {
        // POST_NOTIFICATIONS is user-revocable: check every time, and never
        // let the tray take the service down with it.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            doNotifyAgent(title, body, approval)
        } catch (_: SecurityException) {
            // Tray denied mid-run: stay alive, the in-app lists still update.
        }
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("MissingPermission")
    private fun doNotifyAgent(title: String, body: String, approval: Approval?) {
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CH_AGENT)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(title.ifBlank { getString(R.string.app_name) })
            .setContentText(body.take(240))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
        if (approval != null) {
            builder
                .addAction(0, "Approve", actionIntent(ACTION_APPROVE, approval.rid, 10))
                .addAction(0, "Deny", actionIntent(ACTION_DENY, approval.rid, 11))
            NotificationManagerCompat.from(this)
                .notify(approval.rid.hashCode(), builder.build())
        } else {
            NotificationManagerCompat.from(this)
                .notify((title + body).hashCode(), builder.build())
        }
    }
}
