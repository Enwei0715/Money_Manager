package com.enwei.moneymanager.data

/**
 * 功能開放的唯一判斷點。目前所有功能都免費開放（一律回傳 true）。
 *
 * 規則（日後若有付費方案也必須遵守）：
 * - 記帳、統計、備份還原、匯出等「自己的資料」相關功能永遠免費，資料不會被鎖住。
 * - 投資相關的分析、選股、AI 建議不做成付費功能（避免構成收費的投資建議）。
 * - 所有付費判斷只能寫在這裡，畫面不自己判斷，避免各處規則不一致。
 */
object Features {
    enum class Feature { CLOUD_SYNC, EXTRA_THEMES, ADVANCED_REPORTS }

    @Suppress("UNUSED_PARAMETER")
    fun enabled(f: Feature): Boolean = true

    /** 贊助連結（只有 GitHub 版設定；Play 版與未設定時為 null） */
    val sponsorUrl: String? get() = com.enwei.moneymanager.BuildConfig.SPONSOR_URL.takeIf { it.isNotBlank() }
}
