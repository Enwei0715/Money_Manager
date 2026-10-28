package com.enwei.moneymanager.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.enwei.moneymanager.BuildConfig
import com.enwei.moneymanager.core.VersionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import okhttp3.Request
import java.io.File
import java.io.IOException

data class ReleaseInfo(
    val tag: String,
    val versionCode: Int,
    val notes: String,
    val assetApiUrl: String,
    val assetBrowserUrl: String,
    val sizeBytes: Long,
)

/**
 * 從 GitHub Releases 檢查並下載新版 APK。
 * 私人儲存庫需要在設定中填入具 Contents 讀取權限的 GitHub Token。
 */
object Updater {
    private fun authHeaders(token: String) = buildMap {
        put("Accept", "application/vnd.github+json")
        if (token.isNotBlank()) put("Authorization", "Bearer ${token.trim()}")
    }

    suspend fun latest(token: String): ReleaseInfo? {
        val root = Http.getJson("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest", authHeaders(token))
        val tag = root.str("tag_name") ?: return null
        val asset = (root.obj("assets") as? JsonArray)?.firstOrNull { it.str("name")?.endsWith(".apk") == true } ?: return null
        return ReleaseInfo(
            tag = tag,
            versionCode = VersionUtil.codeFromTag(tag) ?: 0,
            notes = root.str("body").orEmpty(),
            assetApiUrl = asset.str("url").orEmpty(),
            assetBrowserUrl = asset.str("browser_download_url").orEmpty(),
            sizeBytes = asset.num("size")?.toLong() ?: 0,
        )
    }

    fun isNewer(r: ReleaseInfo) = r.versionCode > BuildConfig.VERSION_CODE

    suspend fun download(context: Context, r: ReleaseInfo, token: String, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "MoneyManager-${r.tag}.apk")

        // 私人庫：先以 API 取得簽名下載網址（302），再不帶 Token 下載，避免 S3 拒絕
        val url = if (token.isNotBlank()) {
            val noRedirect = Http.client.newBuilder().followRedirects(false).followSslRedirects(false).build()
            val req = Request.Builder().url(r.assetApiUrl)
                .header("Authorization", "Bearer ${token.trim()}")
                .header("Accept", "application/octet-stream")
                .header("User-Agent", Http.USER_AGENT)
                .build()
            noRedirect.newCall(req).execute().use { resp ->
                when {
                    resp.isRedirect -> resp.header("Location") ?: throw IOException("缺少下載位置")
                    resp.isSuccessful -> {
                        writeBody(resp.body!!, target, r.sizeBytes, onProgress)
                        return@withContext target
                    }
                    else -> throw IOException("下載失敗 HTTP ${resp.code}")
                }
            }
        } else r.assetBrowserUrl

        Http.client.newCall(Request.Builder().url(url).header("User-Agent", Http.USER_AGENT).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("下載失敗 HTTP ${resp.code}")
            writeBody(resp.body!!, target, r.sizeBytes, onProgress)
        }
        target
    }

    private fun writeBody(body: okhttp3.ResponseBody, target: File, expected: Long, onProgress: (Float) -> Unit) {
        val total = body.contentLength().takeIf { it > 0 } ?: expected
        body.byteStream().use { input ->
            target.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var read = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    read += n
                    if (total > 0) onProgress(read.toFloat() / total)
                }
            }
        }
    }

    fun canInstall(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
