package sh.gravital.shell.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.gravital.shell.bridge.GravitalShellBridge
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBridgeScreen(
    sessionId: String,
    filesDir: File,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var currentPath by remember { mutableStateOf("/root") }
    var entries by remember { mutableStateOf<List<String>>(emptyList()) }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val json = GravitalShellBridge.listSessionFiles(sessionId, currentPath)
            val list = parseJsonStringList(json)
            withContext(Dispatchers.Main) { entries = list }
        }
    }

    LaunchedEffect(currentPath) { refresh() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val fileName = resolveFileName(context, uri) ?: "imported_file"
            val tmp = File(context.cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { ins ->
                tmp.outputStream().use { out -> ins.copyTo(out) }
            }
            val destRel = "$currentPath/$fileName"
            val result = GravitalShellBridge.importFileToSession(sessionId, tmp.absolutePath, destRel)
            tmp.delete()
            withContext(Dispatchers.Main) {
                if (result == 0) {
                    snackbar.showSnackbar("Imported $fileName to $destRel")
                    refresh()
                } else {
                    snackbar.showSnackbar("Import failed")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("File Manager", style = MaterialTheme.typography.titleMedium)
                        Text(
                            currentPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { importLauncher.launch("*/*") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Upload, contentDescription = "Import file")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Empty directory",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (currentPath != "/") {
                    item {
                        FileEntry(
                            name = "..",
                            isDir = true,
                            onOpen = {
                                currentPath = currentPath.substringBeforeLast('/').ifEmpty { "/" }
                            },
                            onExport = {},
                            showExport = false,
                        )
                    }
                }
                items(entries, key = { it }) { entry ->
                    val isDir = entry.endsWith("/")
                    val name = entry.trimEnd('/')
                    FileEntry(
                        name = name,
                        isDir = isDir,
                        onOpen = {
                            if (isDir) {
                                currentPath = if (currentPath == "/") "/$name" else "$currentPath/$name"
                            }
                        },
                        onExport = {
                            scope.launch(Dispatchers.IO) {
                                val srcRel = if (currentPath == "/") "/$name" else "$currentPath/$name"
                                val dest = File(context.getExternalFilesDir(null), name)
                                val result = GravitalShellBridge.exportFileFromSession(
                                    sessionId, srcRel, dest.absolutePath
                                )
                                withContext(Dispatchers.Main) {
                                    if (result == 0) {
                                        shareFile(context, dest)
                                        snackbar.showSnackbar("Exported $name")
                                    } else {
                                        snackbar.showSnackbar("Export failed")
                                    }
                                }
                            }
                        },
                        showExport = !isDir,
                    )
                }
            }
        }
    }
}

@Composable
private fun FileEntry(
    name: String,
    isDir: Boolean,
    onOpen: () -> Unit,
    onExport: () -> Unit,
    showExport: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = if (isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = if (isDir) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (showExport) {
                IconButton(onClick = onExport) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Export",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun parseJsonStringList(json: String): List<String> =
    runCatching {
        json.trim().trimStart('[').trimEnd(']')
            .split(",")
            .map { it.trim().trim('"').replace("\\n", "") }
            .filter { it.isNotEmpty() }
    }.getOrDefault(emptyList())

private fun resolveFileName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
    } ?: uri.lastPathSegment

private fun shareFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = context.contentResolver.getType(uri) ?: "*/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export file"))
}
