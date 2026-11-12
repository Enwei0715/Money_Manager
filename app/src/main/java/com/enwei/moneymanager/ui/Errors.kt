package com.enwei.moneymanager.ui

/**
 * 把例外換成使用者看得懂的一句話（不直接顯示英文的例外原文）。
 * 原文本身已是中文說明時直接使用。
 */
fun friendlyError(e: Throwable): String {
    val msg = e.message.orEmpty()
    val chinese = msg.any { it in '一'..'鿿' }
    return when {
        chinese -> msg
        e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.SocketTimeoutException ||
            e is javax.net.ssl.SSLException -> tr("無法連線，請確認網路後再試")
        e is SecurityException || e is java.io.FileNotFoundException -> tr("沒有存取這個檔案或資料夾的權限，請重新選擇")
        e is kotlinx.serialization.SerializationException -> tr("檔案格式不正確或已損毀")
        e is java.util.zip.ZipException || e is java.io.EOFException -> tr("檔案不完整或已損毀")
        e is OutOfMemoryError -> tr("資料量太大，記憶體不足")
        msg.contains("ENOSPC") || msg.contains("No space", ignoreCase = true) -> tr("手機儲存空間不足")
        msg.contains("HTTP 401") || msg.contains("HTTP 403") -> tr("金鑰無效或沒有權限")
        msg.contains("HTTP 429") -> tr("請求太頻繁，請稍後再試")
        else -> tr("發生錯誤，請稍後再試") + "（${e.javaClass.simpleName}）"
    }
}
