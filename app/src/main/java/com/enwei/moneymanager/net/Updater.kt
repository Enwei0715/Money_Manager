package com.enwei.moneymanager.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.app.DownloadManager
import android.os.Environment
import com.enwei.moneymanager.BuildConfig
import com.enwei.moneymanager.core.VersionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import okhttp3.Request
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

    /**
     * 最新版。帶 Token 失敗（Token 過期、被撤銷時 GitHub 會回 401，連公開儲存庫也查不到）就改成不帶 Token 再查一次。
     */
    suspend fun latest(token: String): ReleaseInfo? =
        if (token.isBlank()) latestWith("") else runCatching { latestWith(token) }.getOrElse { latestWith("") }

    private suspend fun latestWith(token: String): ReleaseInfo? {
        val root = Http.getJson("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest", authHeaders(token))
        val tag = root.str("tag_name") ?: return null
        val asset = (root.obj("assets") as? JsonArray)?.firstOrNull { it.str("name")?.endsWith(".apk") == true } ?: return null
        return ReleaseInfo(
            tag = tag,
            versionCode = VersionUtil.codeFromTag(tag) ?: 0,
            // 拿掉 commit 訊息裡給開發用的標記行
            notes = root.str("body").orEmpty().lines().filterNot { it.startsWith("Co-Authored-By:") || it.startsWith("Claude-Session:") }.joinToString("\n").trim(),
            assetApiUrl = asset.str("url").orEmpty(),
            assetBrowserUrl = asset.str("browser_download_url").orEmpty(),
            sizeBytes = asset.num("size")?.toLong() ?: 0,
        )
    }

    fun isNewer(r: ReleaseInfo) = r.versionCode > BuildConfig.VERSION_CODE

    /**
     * 用系統的下載管理員下載新版（通知列顯示進度）；帶 Token 失敗時改用公開下載網址。
     * App 不直接安裝 APK（不需要「安裝不明應用程式」權限）：下載完成後打開「下載」，點 APK 由系統安裝。
     */
    suspend fun download(context: Context, r: ReleaseInfo, token: String, onProgress: (Float) -> Unit): Long =
        if (token.isBlank()) downloadWith(context, r, "", onProgress)
        else runCatching { downloadWith(context, r, token, onProgress) }.getOrElse { downloadWith(context, r, "", onProgress) }

    private suspend fun downloadWith(context: Context, r: ReleaseInfo, token: String, onProgress: (Float) -> Unit): Long = withContext(Dispatchers.IO) {
        // 私人庫：先以 API 取得簽名下載網址（302），下載時不帶 Token，避免 S3 拒絕
        val url = if (token.isNotBlank()) {
            val noRedirect = Http.client.newBuilder().followRedirects(false).followSslRedirects(false).build()
            val req = Request.Builder().url(r.assetApiUrl)
                .header("Authorization", "Bearer ${token.trim()}")
                .header("Accept", "application/octet-stream")
                .header("User-Agent", Http.USER_AGENT)
                .build()
            noRedirect.newCall(req).execute().use { resp ->
                if (resp.isRedirect) resp.header("Location") ?: throw IOException("缺少下載位置") else throw IOException("下載失敗 HTTP ${resp.code}")
            }
        } else r.assetBrowserUrl
        val dm = context.getSystemService(DownloadManager::class.java)
        val id = dm.enqueue(
            DownloadManager.Request(Uri.parse(url))
                .setTitle("帳理 ${r.tag}")
                .setDescription("下載完成後點這裡安裝")
                .setMimeType("application/vnd.android.package-archive")
                .addRequestHeader("User-Agent", Http.USER_AGENT)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Ledgerly-${r.tag}.apk"),
        )
        while (true) {
            val (status, done, total) = dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                if (!c.moveToFirst()) throw IOException("下載已取消")
                Triple(
                    c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                )
            }
            val size = total.takeIf { it > 0 } ?: r.sizeBytes
            if (size > 0) onProgress((done.toFloat() / size).coerceIn(0f, 1f))
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> return@withContext id
                DownloadManager.STATUS_FAILED -> throw IOException("下載失敗")
            }
            delay(500)
        }
        @Suppress("UNREACHABLE_CODE") id
    }

    /** 下載完成：打開這個下載檔（由系統的下載／檔案 App 交給安裝程式）；不行時打開「下載」清單。 */
    fun openDownloaded(context: Context, id: Long) {
        val dm = context.getSystemService(DownloadManager::class.java)
        runCatching {
            context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            dm.getUriForDownloadedFile(id)?.let { uri ->
                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, dm.getMimeTypeForDownloadedFile(id))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
