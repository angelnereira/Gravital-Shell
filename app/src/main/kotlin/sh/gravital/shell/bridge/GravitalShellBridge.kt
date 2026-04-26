package sh.gravital.shell.bridge

object GravitalShellBridge {

    init {
        System.loadLibrary("gravitalshell")
    }

    external fun initialize(filesDir: String)

    external fun createSession(name: String, policy: String): String

    external fun startSession(sessionId: String): String

    external fun stopSession(sessionId: String)

    external fun destroySession(sessionId: String)

    external fun listSessions(): String

    external fun resizePty(fd: Int, rows: Int, cols: Int)

    external fun exportSession(sessionId: String, destPath: String)

    external fun importSession(srcPath: String): String
}
