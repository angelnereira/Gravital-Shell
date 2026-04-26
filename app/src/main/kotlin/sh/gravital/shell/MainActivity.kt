package sh.gravital.shell

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import sh.gravital.shell.files.FileBridgeScreen
import sh.gravital.shell.service.ShellService
import sh.gravital.shell.session.SessionViewModel
import sh.gravital.shell.setup.FirstRunSetup
import sh.gravital.shell.ui.SessionListScreen
import sh.gravital.shell.ui.TerminalScreen
import sh.gravital.shell.ui.theme.GravitalShellTheme

private const val TAG = "MainActivity"

private const val ROUTE_SESSIONS = "sessions"
private const val ROUTE_TERMINAL = "terminal/{sessionId}/{policy}"
private const val ROUTE_FILES = "files/{sessionId}"

class MainActivity : ComponentActivity() {

    private val viewModel: SessionViewModel by viewModels()
    private var setupComplete by mutableStateOf(false)
    private var setupError by mutableStateOf<String?>(null)
    private var setupMessage by mutableStateOf("Preparing...")

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "ShellService connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(TAG, "ShellService disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startShellService()
        viewModel.nativeLibDir = FirstRunSetup.nativeLibDir(this)

        lifecycleScope.launch {
            runCatching {
                FirstRunSetup.run(this@MainActivity) { msg -> setupMessage = msg }
            }.onSuccess {
                setupComplete = true
                viewModel.loadSessions()
            }.onFailure { e ->
                Log.e(TAG, "Setup failed", e)
                setupError = e.message ?: "Setup failed"
            }
        }

        setContent {
            GravitalShellTheme {
                if (setupError != null) {
                    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Setup error: $setupError",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                } else if (!setupComplete) {
                    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = setupMessage,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                } else {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = ROUTE_SESSIONS) {
                        composable(ROUTE_SESSIONS) {
                            SessionListScreen(
                                viewModel = viewModel,
                                onOpenSession = { id, policy ->
                                    navController.navigate("terminal/$id/$policy")
                                },
                            )
                        }
                        composable(ROUTE_TERMINAL) { backStack ->
                            val sessionId = backStack.arguments?.getString("sessionId") ?: return@composable
                            val policy = backStack.arguments?.getString("policy") ?: "Persistent"
                            TerminalScreen(
                                sessionId = sessionId,
                                sessionPolicy = policy,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenFiles = { navController.navigate("files/$sessionId") },
                            )
                        }
                        composable(ROUTE_FILES) { backStack ->
                            val sessionId = backStack.arguments?.getString("sessionId") ?: return@composable
                            FileBridgeScreen(
                                sessionId = sessionId,
                                filesDir = filesDir,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unbindService(serviceConnection) }
    }

    private fun startShellService() {
        val intent = Intent(this, ShellService::class.java)
        startForegroundService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
}
