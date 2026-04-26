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
// AAPT2 decompresses .gz assets and strips the extension when bundling
private const val UBUNTU_ASSET_TAR = "ubuntu-base.tar"

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

        templateDir.mkdirs()

        // AAPT2 decompresses .gz assets on packaging; check for decompressed name first
        val (assetName, tarFlags) = when {
            runCatching { context.assets.open(UBUNTU_ASSET_TAR).close() }.isSuccess ->
                UBUNTU_ASSET_TAR to "-xf"
            runCatching { context.assets.open(UBUNTU_ASSET).close() }.isSuccess ->
                UBUNTU_ASSET to "-xzf"
            else -> null to null
        }

        val tarball = File(filesDir, assetName ?: UBUNTU_ASSET)

        if (assetName != null && tarFlags != null) {
            onProgress("Extracting Ubuntu rootfs (this may take a minute)...")
            context.assets.open(assetName).use { src ->
                FileOutputStream(tarball).use { out -> src.copyTo(out) }
            }
            Log.i(TAG, "Copied asset $assetName to ${tarball.absolutePath}")
        } else {
            val arch = if (Build.SUPPORTED_ABIS.firstOrNull()?.startsWith("x86_64") == true) "amd64" else "arm64"
            val url = "https://cdimage.ubuntu.com/ubuntu-base/releases/22.04/release/ubuntu-base-22.04.5-base-${arch}.tar.gz"
            onProgress("Downloading Ubuntu 22.04 LTS (~27 MB)...")
            Log.i(TAG, "Downloading Ubuntu base from $url")
            java.net.URL(url).openStream().use { src ->
                FileOutputStream(tarball).use { out -> src.copyTo(out) }
            }
            onProgress("Extracting Ubuntu rootfs (this may take a minute)...")
        }

        val extractFlags = tarFlags ?: "-xzf"
        val proc = Runtime.getRuntime()
            .exec(arrayOf("/system/bin/tar", extractFlags, tarball.absolutePath, "-C", templateDir.absolutePath))
        val err = proc.errorStream.bufferedReader().readText()
        val result = proc.waitFor()

        tarball.delete()

        // exit 1 = non-fatal warnings (hard link creation denied on Android /data is expected)
        // exit 2 = fatal error, tar aborted
        if (result > 1) {
            throw RuntimeException("tar failed (exit $result): $err")
        }
        if (result == 1) {
            Log.w(TAG, "tar completed with warnings (hard links may have been skipped): $err")
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
