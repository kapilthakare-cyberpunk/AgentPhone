package com.kapil.agentphone.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore("agentphone")

private object Keys {
    val HOST = stringPreferencesKey("host")
    val PORT = intPreferencesKey("port")
    val TOKEN = stringPreferencesKey("token")
}

data class LinkPrefs(val host: String, val port: Int, val token: String)

class Prefs(private val ctx: Context) {
    val link: Flow<LinkPrefs> = ctx.dataStore.data.map {
        LinkPrefs(
            host = it[Keys.HOST] ?: "",
            port = it[Keys.PORT] ?: 9876,
            token = it[Keys.TOKEN] ?: ""
        )
    }

    suspend fun save(host: String, port: Int, token: String) {
        ctx.dataStore.edit {
            it[Keys.HOST] = host
            it[Keys.PORT] = port
            it[Keys.TOKEN] = token
        }
    }
}
