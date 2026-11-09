package com.enwei.moneymanager.net

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Shizuku：不用電腦，用手機自己的「無線偵錯」取得 adb 權限，一鍵開啟「讀取敏感通知」（Android 15 起銀行通知可能被隱藏）。
 * 使用者要先安裝並啟動 Shizuku App。
 */
object ShizukuHelper {
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"
    private const val CMD = "appops set com.enwei.moneymanager RECEIVE_SENSITIVE_NOTIFICATIONS allow"

    /** Shizuku 有在執行（已安裝並啟動） */
    val running: Boolean get() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    /** 帳理已取得 Shizuku 授權 */
    val granted: Boolean get() = running && runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)

    /** 請使用者在 Shizuku 跳出的視窗允許帳理 */
    suspend fun requestPermission(): Boolean = suspendCancellableCoroutine { c ->
        if (granted) { c.resumeWith(Result.success(true)); return@suspendCancellableCoroutine }
        val code = 4217
        val l = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != code) return
                Shizuku.removeRequestPermissionResultListener(this)
                if (c.isActive) c.resumeWith(Result.success(grantResult == PackageManager.PERMISSION_GRANTED))
            }
        }
        runCatching {
            Shizuku.addRequestPermissionResultListener(l)
            Shizuku.requestPermission(code)
        }.onFailure { if (c.isActive) c.resumeWith(Result.success(false)) }
        c.invokeOnCancellation { runCatching { Shizuku.removeRequestPermissionResultListener(l) } }
    }

    /** 透過 Shizuku 執行開啟敏感通知的指令；成功回傳 null，失敗回傳原因 */
    suspend fun allowSensitiveNotifications(): String? = withContext(Dispatchers.IO) {
        if (!running) return@withContext "Shizuku 沒有在執行"
        if (!granted) return@withContext "還沒允許帳理使用 Shizuku"
        runCatching {
            // Shizuku 13 的 newProcess 不是公開方法，用反射呼叫（官方工具與許多 App 都這樣用）
            val m = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            m.isAccessible = true
            val p = m.invoke(null, arrayOf("sh", "-c", CMD), null, null) as Process
            val err = p.errorStream.bufferedReader().readText()
            val code = p.waitFor()
            if (code == 0) null else "指令失敗（$code）：${err.take(120)}"
        }.getOrElse { it.message ?: it.javaClass.simpleName }
    }
}
