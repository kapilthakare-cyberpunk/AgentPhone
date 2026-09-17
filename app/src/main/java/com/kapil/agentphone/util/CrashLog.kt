package com.kapil.agentphone.util

import android.content.Context
import java.io.File

/**
 * Last-resort crash capture. Installed first thing in MainActivity so a
 * launch crash still leaves its stack trace behind for the next run.
 */
object CrashLog {
    private fun file(ctx: Context): File = File(ctx.filesDir, "crash-last.txt")

    fun install(ctx: Context) {
        val appCtx = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                file(appCtx).writeText(
                    "${e::class.java.name}: ${e.message}\n${e.stackTraceToString().take(6000)}"
                )
            } catch (_: Exception) {
            }
            prev?.uncaughtException(t, e)
        }
    }

    fun read(ctx: Context): String? =
        file(ctx).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(ctx: Context) {
        file(ctx).delete()
    }
}
