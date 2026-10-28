package com.enwei.moneymanager.core

object VersionUtil {
    /** 從 Release tag（例如 "v1.0.23" 或 "build-23"）取出最後一段數字作為 versionCode。 */
    fun codeFromTag(tag: String): Int? = Regex("(\\d+)$").find(tag.trim())?.groupValues?.get(1)?.toIntOrNull()
}
