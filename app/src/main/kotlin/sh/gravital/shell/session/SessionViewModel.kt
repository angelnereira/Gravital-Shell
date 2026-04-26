package sh.gravital.shell.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sh.gravital.shell.bridge.GravitalShellBridge
import java.util.concurrent.ConcurrentHashMap

class SessionViewModel : ViewModel() {

    private val gson = Gson()
    private val _uiState = MutableStateFlow<SessionUiState>(SessionUiState.Loading)
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private val _activeSessions = ConcurrentHashMap<String, TerminalSession>()

    var nativeLibDir: String = ""

    fun loadSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val json = GravitalShellBridge.listSessions()
                val type = object : TypeToken<List<SessionModel>>() {}.type
                gson.fromJson<List<SessionModel>>(json, type) ?: emptyList()
            }.onSuccess { sessions ->
                _uiState.value = SessionUiState.Ready(sessions)
            }.onFailure { e ->
                _uiState.value = SessionUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun createSession(
        name: String,
        policy: SessionPolicy,
        template: SessionTemplate = SessionTemplate.Base,
        onComplete: (String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = GravitalShellBridge.createSession(name, policy.name)
            if (id.isNotEmpty()) {
                if (template != SessionTemplate.Base) {
                    writeBootstrapScript(id, template)
                }
                loadSessions()
                onComplete(id)
            }
        }
    }

    private fun writeBootstrapScript(sessionId: String, template: SessionTemplate) {
        val script = template.bootstrapScript ?: return
        runCatching {
            val tmp = java.io.File.createTempFile("init", ".sh")
            tmp.writeText(script)
            GravitalShellBridge.importFileToSession(sessionId, tmp.absolutePath, "/root/.init.sh")
            tmp.delete()
        }
    }

    // Step 1 — run on Dispatchers.IO: heavy work (rootfs copy, proot args via JNI)
    fun prepareSession(sessionId: String): List<String> {
        val argsJson = GravitalShellBridge.startSession(sessionId)
        val args: List<String> = gson.fromJson(argsJson, object : TypeToken<List<String>>() {}.type)
        if (args.isEmpty()) {
            android.util.Log.e("SessionViewModel", "startSession returned empty args for $sessionId")
            throw RuntimeException("Failed to build proot args for session $sessionId")
        }
        android.util.Log.i("SessionViewModel", "proot args ready for $sessionId: ${args[0]}")
        return args
    }

    // Step 2 — MUST run on Main thread: TerminalSession creates an Android Handler internally
    fun createTerminalSession(
        sessionId: String,
        args: List<String>,
        client: TerminalSessionClient,
    ): TerminalSession {
        _activeSessions[sessionId]?.let { return it }

        val shellPath = args[0]
        val shellArgs = args.drop(1).toTypedArray()
        val ldPath = if (nativeLibDir.isNotEmpty()) nativeLibDir else "/system/lib64"
        val env = arrayOf(
            "TERM=xterm-256color",
            "HOME=/root",
            "USER=root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "LANG=en_US.UTF-8",
            "LD_LIBRARY_PATH=$ldPath",
        )

        val session = TerminalSession(shellPath, "/", shellArgs, env, 2000, client)
        _activeSessions[sessionId] = session
        return session
    }

    fun getActiveSession(sessionId: String): TerminalSession? = _activeSessions[sessionId]

    fun stopSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _activeSessions.remove(sessionId)?.let { it.finishIfRunning() }
            GravitalShellBridge.stopSession(sessionId)
            loadSessions()
        }
    }

    fun destroySession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _activeSessions.remove(sessionId)?.let { it.finishIfRunning() }
            GravitalShellBridge.destroySession(sessionId)
            loadSessions()
        }
    }
}
