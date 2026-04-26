package sh.gravital.shell.setup

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.gravital.shell.bridge.GravitalShellBridge
import java.io.File
import java.io.FileOutputStream

private const val TAG = "FirstRunSetup"
private const val MARKER = ".setup_complete"
private const val UBUNTU_ASSET = "ubuntu-base.tar.gz"

object FirstRunSetup {

    suspend fun run(context: Context, onProgress: (String) -> Unit = {}) {
        withContext(Dispatchers.IO) {
            val filesDir = context.filesDir

            if (File(filesDir, MARKER).exists()) {
                GravitalShellBridge.initialize(filesDir.absolutePath)
                return@withContext
            }

            onProgress("Preparing terminal environment...")

            extractProot(context, filesDir, onProgress)
            extractUbuntu(context, filesDir, onProgress)
            writeResolvConf(filesDir)

            GravitalShellBridge.initialize(filesDir.absolutePath)

            File(filesDir, MARKER).createNewFile()
            Log.i(TAG, "First-run setup complete")
        }
    }

    private fun prootAssetName(): String {
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return if (primaryAbi.startsWith("x86_64")) "proot-x86_64" else "proot-arm64"
    }

    private fun extractProot(context: Context, filesDir: File, onProgress: (String) -> Unit) {
        val dest = File(filesDir, "proot")
        if (dest.exists()) return

        onProgress("Installing proot...")
        val assetName = prootAssetName()
        context.assets.open(assetName).use { src ->
            FileOutputStream(dest).use { out -> src.copyTo(out) }
        }
        dest.setExecutable(true, false)
        Log.i(TAG, "proot ($assetName) extracted to ${dest.absolutePath}")
    }

    private fun extractUbuntu(context: Context, filesDir: File, onProgress: (String) -> Unit) {
        val templateDir = File(filesDir, "ubuntu-template")
        if (templateDir.exists() && templateDir.list()?.isNotEmpty() == true) return

        onProgress("Extracting Ubuntu rootfs (this may take a minute)...")
        templateDir.mkdirs()

        val tarball = File(filesDir, UBUNTU_ASSET)
        context.assets.open(UBUNTU_ASSET).use { src ->
            FileOutputStream(tarball).use { out -> src.copyTo(out) }
        }

        val result = Runtime.getRuntime()
            .exec(arrayOf("tar", "-xzf", tarball.absolutePath, "-C", templateDir.absolutePath))
            .waitFor()

        tarball.delete()

        if (result != 0) {
            throw RuntimeException("tar exited $result while extracting Ubuntu rootfs")
        }
        Log.i(TAG, "Ubuntu rootfs extracted to ${templateDir.absolutePath}")
    }

    private fun writeResolvConf(filesDir: File) {
        val resolv = File(filesDir, "resolv.conf")
        if (!resolv.exists()) {
            resolv.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
            Log.i(TAG, "resolv.conf written")
        }
    }

    fun nativeLibDir(context: Context): String =
        context.applicationInfo.nativeLibraryDir
}
