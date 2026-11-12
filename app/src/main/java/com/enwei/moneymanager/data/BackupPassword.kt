package com.enwei.moneymanager.data

import android.content.Context

/**
 * 備份密碼：設定後匯出與自動備份的 .lgbak 都會加密。只存在這支手機（backup_key，不進 App 備份與系統雲端備份），
 * 換手機還原時要自己輸入。
 */
object BackupPassword {
    private fun prefs() = com.enwei.moneymanager.MoneyApp.instance.getSharedPreferences("backup_key", Context.MODE_PRIVATE)

    fun get(): CharArray? = prefs().getString("pw", null)?.takeIf { it.isNotEmpty() }?.toCharArray()

    fun isSet(): Boolean = get() != null

    fun set(pw: String?) {
        prefs().edit().apply { if (pw.isNullOrEmpty()) remove("pw") else putString("pw", pw) }.apply()
    }
}
