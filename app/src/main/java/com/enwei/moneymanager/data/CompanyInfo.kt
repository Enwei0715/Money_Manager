package com.enwei.moneymanager.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 公司說明（AI 依產業、近期新聞與月營收整理）：存在手機裡，附整理時間與依據；超過 [STALE_DAYS] 天自動重新整理。
 */
object CompanyInfo {
    @Serializable
    data class Entry(val text: String, val recent: String = "", val at: Long = 0, val basis: String = "")

    const val STALE_DAYS = 14
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = MapSerializer(String.serializer(), Entry.serializer())

    private fun prefs(c: Context) = c.getSharedPreferences("company_info", Context.MODE_PRIVATE)

    fun get(c: Context, symbol: String): Entry? = runCatching {
        json.decodeFromString(ser, prefs(c).getString("all", "{}")!!)[symbol]
    }.getOrNull()

    fun put(c: Context, symbol: String, e: Entry) {
        val all = runCatching { json.decodeFromString(ser, prefs(c).getString("all", "{}")!!) }.getOrDefault(emptyMap())
        prefs(c).edit().putString("all", json.encodeToString(ser, all + (symbol to e))).apply()
    }

    fun stale(e: Entry?) = e == null || e.at == 0L || System.currentTimeMillis() - e.at > STALE_DAYS * 86_400_000L

    /** 蒐集資料並請 AI 整理；回傳新的說明（AI 沒有回覆時為 null）。 */
    suspend fun refresh(c: Context, symbol: String, name: String, news: List<NewsItem>): Entry? {
        val info = runCatching { com.enwei.moneymanager.net.TwData.stockList(c) }.getOrDefault(emptyList()).firstOrNull { it.code == symbol }
        val rev = runCatching { com.enwei.moneymanager.net.TwData.monthRevenue(symbol, 13) }.getOrDefault(emptyList())
        val basis = ArrayList<String>()
        val data = buildString {
            info?.industry?.takeIf { it.isNotBlank() }?.let { appendLine("產業類別：$it"); basis += "產業類別" }
            rev.lastOrNull()?.let { m ->
                appendLine("最新月營收：${m.year}/${m.month} ${"%.1f".format(m.revenue / 1e8)} 億" +
                    (m.yoy?.let { "，年增 ${"%+.1f".format(it)}%" } ?: "") + (m.mom?.let { "，月增 ${"%+.1f".format(it)}%" } ?: ""))
                basis += "月營收"
            }
            val recent = news.take(10)
            if (recent.isNotEmpty()) {
                appendLine("近期新聞：")
                recent.forEach { n -> appendLine("- " + n.title.take(60) + (n.note.takeIf { it.isNotBlank() }?.let { "（$it）" } ?: "")) }
                basis += "近 ${recent.size} 則新聞"
            }
        }
        val (d, r) = com.enwei.moneymanager.net.GroqAi.describeCompany(symbol, name, data) ?: return null
        val e = Entry(d, r, System.currentTimeMillis(), basis.joinToString("・").ifEmpty { "公司名稱（沒有其他資料）" })
        put(c, symbol, e)
        // 對話與報告也會用到公司說明
        com.enwei.moneymanager.MoneyApp.news.update { it.copy(business = it.business + (symbol to d)) }
        return e
    }
}
