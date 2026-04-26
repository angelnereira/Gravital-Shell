package sh.gravital.shell.ui

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import sh.gravital.shell.session.SessionPolicy
import sh.gravital.shell.session.SessionViewModel
import sh.gravital.shell.ui.theme.TerminalBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    sessionId: String,
    sessionPolicy: String,
    viewModel: SessionViewModel,
    onBack: () -> Unit,
    onOpenFiles: () -> Unit,
) {
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }

    val sessionClient = remember(sessionId) {
        object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession) {
                terminalView?.onScreenUpdated()
            }

            override fun onTitleChanged(changedSession: TerminalSession) {}

            override fun onSessionFinished(finishedSession: TerminalSession) {}

            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}

            override fun onPasteTextFromClipboard(session: TerminalSession?) {}

            override fun onBell(session: TerminalSession) {}

            override fun onColorsChanged(session: TerminalSession) {}

            override fun onTerminalCursorStateChange(state: Boolean) {}

            override fun getTerminalCursorStyle(): Int = 0

            override fun logError(tag: String?, message: String?) {}

            override fun logWarn(tag: String?, message: String?) {}

            override fun logInfo(tag: String?, message: String?) {}

            override fun logDebug(tag: String?, message: String?) {}

            override fun logVerbose(tag: String?, message: String?) {}

            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}

            override fun logStackTrace(tag: String?, e: Exception?) {}
        }
    }

    val terminalSession = remember(sessionId) {
        viewModel.getActiveSession(sessionId)
            ?: viewModel.buildTerminalSession(sessionId, sessionClient)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenFiles) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "File manager",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TerminalBackground,
                ),
            )
        },
        containerColor = Color(TerminalBackground.toArgb()),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(TerminalBackground),
        ) {
            if (terminalSession != null) {
                AndroidView(
                    factory = { context ->
                        TerminalView(context, null).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            setTerminalViewClient(object : TerminalViewClient {
                                override fun onScale(scale: Float): Float = scale
                                override fun onSingleTapUp(e: android.view.MotionEvent?) {}
                                override fun shouldBackButtonBeMappedToEscape(): Boolean = true
                                override fun shouldEnforceCharBasedInput(): Boolean = false
                                override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
                                override fun isTerminalViewSelected(): Boolean = true
                                override fun copyModeChanged(copyMode: Boolean) {}
                                override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean = false
                                override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false
                                override fun onLongPress(e: android.view.MotionEvent?): Boolean = false
                                override fun readControlKey(): Boolean = false
                                override fun readAltKey(): Boolean = false
                                override fun readShiftKey(): Boolean = false
                                override fun readFnKey(): Boolean = false
                                override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false
                                override fun onEmulatorSet() {}
                                override fun logError(tag: String?, message: String?) {}
                                override fun logWarn(tag: String?, message: String?) {}
                                override fun logInfo(tag: String?, message: String?) {}
                                override fun logDebug(tag: String?, message: String?) {}
                                override fun logVerbose(tag: String?, message: String?) {}
                                override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
                                override fun logStackTrace(tag: String?, e: Exception?) {}
                            })
                            attachSession(terminalSession)
                            requestFocus()
                            terminalView = this
                        }
                    },
                    update = { view ->
                        if (view.currentSession != terminalSession) {
                            view.attachSession(terminalSession)
                        }
                        terminalView = view
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    DisposableEffect(sessionId) {
        onDispose {
            terminalView = null
            if (sessionPolicy == SessionPolicy.Ephemeral.name) {
                viewModel.destroySession(sessionId)
            }
        }
    }
}
