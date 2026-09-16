package com.kapil.agentphone.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * App-wide bridge link. Owned by [com.kapil.agentphone.service.BridgeService],
 * observed directly by the UI. Reconnects forever with backoff.
 */
object BridgeClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).build()
    }

    private var job: Job? = null
    private var ws: WebSocket? = null
    private var cfg: LinkPrefs = LinkPrefs("", 9876, "")
    private var lastSeen = 0L
    private var msgId = 0L
    private var netWatching = false

    private val _state = MutableStateFlow<ConnState>(ConnState.Disconnected)
    val state: StateFlow<ConnState> = _state.asStateFlow()
    private val _sessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessions: StateFlow<List<SessionInfo>> = _sessions.asStateFlow()
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    private val _approvals = MutableStateFlow<List<Approval>>(emptyList())
    val approvals: StateFlow<List<Approval>> = _approvals.asStateFlow()
    private val _notices = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 32)
    val notices: SharedFlow<Pair<String, String>> = _notices.asSharedFlow()

    /** (title, body, approval?) — the service turns these into notifications. */
    var onAgentAlert: ((String, String, Approval?) -> Unit)? = null

    fun configure(p: LinkPrefs) {
        val changed = p != cfg
        cfg = p
        if (changed) reconnectSoon()
    }

    fun start(ctx: Context) {
        if (job != null) return
        watchNetwork(ctx.applicationContext)
        job = scope.launch { loop() }
    }

    fun reconnectSoon() {
        ws?.close(1000, "reconfig")
    }

    // MARK: - loop

    private suspend fun loop() {
        var backoff = 1000L
        while (scope.isActive) {
            if (cfg.host.isBlank() || cfg.token.isBlank()) {
                _state.value = ConnState.Disconnected
                delay(3000)
                continue
            }
            _state.value = ConnState.Connecting
            val onlineMs = runSession()
            backoff = if (onlineMs > 60_000) 1000L else minOf(backoff * 2, 30_000L)
            delay(backoff)
        }
    }

    /** Connects, suspends until the socket drops. Returns ms spent online. */
    private suspend fun runSession(): Long {
        val opened = CompletableDeferred<Boolean>()
        val closed = CompletableDeferred<Unit>()
        val req = Request.Builder().url("ws://${cfg.host}:${cfg.port}/").build()
        val socket = client.newWebSocket(req, listener(opened, closed))
        ws = socket
        val ok = withTimeoutOrNull(10_000) { opened.await() } == true
        if (!ok) {
            socket.cancel()
            ws = null
            return 0
        }
        val t0 = System.currentTimeMillis()
        withTimeoutOrNull(90_000L * 60) { closed.await() }
        ws = null
        scope.launch { watchdog() }.cancel()
        return System.currentTimeMillis() - t0
    }

    private fun listener(
        opened: CompletableDeferred<Boolean>,
        closed: CompletableDeferred<Unit>
    ) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            lastSeen = System.currentTimeMillis()
            sendRaw(JSONObject().put("type", "hello").put("token", cfg.token))
            scope.launch { watchdog() }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            lastSeen = System.currentTimeMillis()
            route(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = Unit

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
            opened.complete(false)
            if (!closed.isCompleted) closed.complete(Unit)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _state.value = ConnState.Disconnected
            opened.complete(false)
            if (!closed.isCompleted) closed.complete(Unit)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _state.value = ConnState.Disconnected
            opened.complete(false)
            if (!closed.isCompleted) closed.complete(Unit)
        }
    }

    private suspend fun watchdog() {
        while (scope.isActive) {
            delay(15_000)
            if (_state.value == ConnState.Connected &&
                System.currentTimeMillis() - lastSeen > 90_000
            ) {
                ws?.close(1000, "stale")
                return
            }
            if (_state.value != ConnState.Connected) return
        }
    }

    private fun watchNetwork(ctx: Context) {
        if (netWatching) return
        netWatching = true
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(req, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (_state.value != ConnState.Connected) ws?.close(1000, "net")
                }
            })
        } catch (_: Exception) {
        }
    }

    // MARK: - inbound routing

    private fun route(text: String) {
        val msg = try {
            JSONObject(text)
        } catch (_: Exception) {
            return
        }
        when (msg.optString("type")) {
            "hello-ok" -> {
                _state.value = ConnState.Connected
                val arr = msg.optJSONArray("sessions")
                if (arr != null) {
                    _sessions.value = (0 until arr.length()).map { arr.getJSONObject(it).toSession() }
                }
                val pend = msg.optJSONArray("pending")
                if (pend != null) {
                    for (i in 0 until pend.length()) {
                        val r = pend.getJSONObject(i)
                        if (r.optString("kind") == "approval") {
                            addApproval(r.toApproval())
                        } else {
                            alert(r.optString("title", "Agent"), r.optString("detail"), null)
                        }
                    }
                }
            }
            "session-list" -> {
                val arr = msg.optJSONArray("sessions") ?: return
                _sessions.value = (0 until arr.length()).map { arr.getJSONObject(it).toSession() }
            }
            "spawned" -> {
                val s = msg.optJSONObject("session")?.toSession() ?: return
                _sessions.update { it + s }
            }
            "output" -> {
                append(msg.optString("id"), msg.optString("chunk"), mine = false)
            }
            "session-ended" -> {
                val id = msg.optString("id")
                _sessions.update { list ->
                    list.map { if (it.id == id) it.copy(status = "exited") else it }
                }
                notice("Agent finished", "Session $id exited (${msg.optInt("code")})")
            }
            "session-idle" -> {
                notice("Agent idle", "Session ${msg.optString("id")} quiet ${msg.optInt("idleSec")}s")
            }
            "watcher-output" -> {
                val lines = msg.optJSONArray("lines") ?: return
                val body = (0 until lines.length()).joinToString("\n") { lines.optString(it) }
                if (body.isNotBlank()) alert("Watcher: ${msg.optString("source")}", body, null)
            }
            "approval-request" -> {
                val a = msg.toApproval()
                addApproval(a)
                onAgentAlert?.invoke(a.title, a.detail, a)
            }
            "approval-resolved" -> {
                _approvals.update { list -> list.filterNot { it.rid == msg.optString("rid") } }
            }
            "notify" -> {
                val kind = msg.optString("kind")
                val title = msg.optString("title", "Agent")
                alert(title, msg.optString("body"), null)
                if (kind == "done") notice(title, msg.optString("body"))
            }
            "error" -> {
                notice("Bridge error", msg.optString("message"))
            }
            "pong" -> Unit
        }
    }

    private fun addApproval(a: Approval) {
        _approvals.update { list -> (list.filterNot { it.rid == a.rid } + a) }
    }

    private fun append(sessionId: String, text: String, mine: Boolean) {
        if (text.isEmpty()) return
        _messages.update { (it + ChatMessage(++msgId, sessionId, text, mine)).takeLast(1500) }
    }

    private fun notice(title: String, body: String) {
        _notices.tryEmit(title to body)
        onAgentAlert?.invoke(title, body, null)
    }

    private fun alert(title: String, body: String, approval: Approval?) {
        if (title.isNotBlank()) onAgentAlert?.invoke(title, body, approval)
    }

    // MARK: - outbound

    private fun sendRaw(obj: JSONObject) {
        ws?.send(obj.toString())
    }

    fun spawn(command: String, cwd: String) {
        sendRaw(JSONObject().put("type", "spawn").put("command", command).put("cwd", cwd))
    }

    fun input(id: String, text: String) {
        append(id, text, mine = true)
        sendRaw(JSONObject().put("type", "input").put("id", id).put("text", text))
    }

    fun kill(id: String) {
        sendRaw(JSONObject().put("type", "kill").put("id", id))
    }

    fun verdict(rid: String, approve: Boolean) {
        sendRaw(
            JSONObject().put("type", "approval-verdict").put("rid", rid)
                .put("verdict", if (approve) "approve" else "deny")
        )
        _approvals.update { list -> list.filterNot { it.rid == rid } }
    }

    fun refresh() {
        sendRaw(JSONObject().put("type", "list"))
    }
}
