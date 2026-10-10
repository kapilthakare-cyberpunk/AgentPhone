package com.kapil.agentphone.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore("agentphone")

private object Keys {
    val HOST = stringPreferencesKey("host")
    val PORT = intPreferencesKey("port")
}

private fun Context.encryptedPrefs(): android.content.SharedPreferences {
    val masterKey = MasterKey.Builder(this)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        this,
        "agentphone_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

data class LinkPrefs(val host: String, val port: Int, val token: String)

class Prefs(private val ctx: Context) {
    val link: Flow<LinkPrefs> = ctx.dataStore.data.map {
        val secure = ctx.encryptedPrefs()
        LinkPrefs(
            host = it[Keys.HOST] ?: "",
            port = it[Keys.PORT] ?: 9876,
            token = secure.getString("token", "") ?: ""
        )
    }

    suspend fun save(host: String, port: Int, token: String) {
        ctx.dataStore.edit {
            it[Keys.HOST] = host
            it[Keys.PORT] = port
        }
        ctx.encryptedPrefs().edit().putString("token", token).apply()
    }
}
