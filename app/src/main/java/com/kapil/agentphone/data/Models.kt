package com.kapil.agentphone.data

import org.json.JSONObject

data class SessionInfo(
    val id: String,
    val command: String,
    val cwd: String,
    val status: String,
    val exitCode: Int?,
    val idleSec: Int
)

data class ChatMessage(
    val id: Long,
    val sessionId: String,
    val text: String,
    val mine: Boolean
)

data class Approval(
    val rid: String,
    val title: String,
    val detail: String,
    val session: String
)

sealed interface ConnState {
    data object Disconnected : ConnState
    data object Connecting : ConnState
    data object Connected : ConnState
}

fun JSONObject.toSession(): SessionInfo = SessionInfo(
    id = optString("id"),
    command = optString("command"),
    cwd = optString("cwd"),
    status = optString("status", "running"),
    exitCode = if (isNull("exit_code")) null else optInt("exit_code"),
    idleSec = optInt("idleSec")
)

fun JSONObject.toApproval(): Approval = Approval(
    rid = optString("rid"),
    title = optString("title", "Approval"),
    detail = optString("detail"),
    session = optString("session")
)
