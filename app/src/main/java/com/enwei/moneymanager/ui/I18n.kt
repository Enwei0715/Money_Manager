package com.enwei.moneymanager.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 英文介面：介面文字以繁體中文撰寫，英文模式時在顯示前查表翻譯（assets/i18n_en.tsv）。
 * 先找完整句子；找不到時逐詞替換，但只替換前後不接中文字的詞，避免把使用者自己取的名稱（帳戶、分類）翻成一半。
 */
object I18n {
    /** 0 跟隨系統、1 繁體中文、2 English */
    var english by mutableStateOf(false)
        private set

    private var exact: Map<String, String> = emptyMap()
    private var keys: List<String> = emptyList()
    private val cache = ConcurrentHashMap<String, String>()

    fun init(context: Context, language: Int) {
        if (exact.isEmpty()) runCatching {
            val map = LinkedHashMap<String, String>()
            context.assets.open("i18n_en.tsv").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val i = line.indexOf('\t')
                    if (i > 0) map[unescape(line.substring(0, i))] = unescape(line.substring(i + 1))
                }
            }
            exact = map
            keys = map.keys.sortedByDescending { it.length }
        }
        english = isEnglish(language)
        cache.clear()
    }

    fun isEnglish(language: Int): Boolean = when (language) {
        2 -> true
        1 -> false
        else -> Locale.getDefault().language == "en"
    }

    private fun unescape(s: String) = s.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\")

    private fun isCjk(c: Char) = c in '一'..'鿿'

    fun tr(s: String): String {
        if (!english || s.isEmpty() || s.none(::isCjk)) return s
        return cache.getOrPut(s) { translate(s) }
    }

    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val yearMonth = Regex("(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月")
    private val monthOnly = Regex("(?<![\\d年])(\\d{1,2})\\s*月(?![\\u4e00-\\u9fff])")
    private val yearOnly = Regex("(\\d{4})\\s*年(?![\\u4e00-\\u9fff])")
    private val everyMonthDay = Regex("每月\\s*(\\d{1,2})\\s*日")

    private fun month(n: String) = n.toIntOrNull()?.takeIf { it in 1..12 }?.let { months[it - 1] } ?: n

    private fun translate(src: String): String {
        exact[src]?.let { return it }
        // 日期格式：2026年10月 → Oct 2026、10月 → Oct、每月 5 日 → Day 5 of month
        var s = src.replace(yearMonth) { "${month(it.groupValues[2])} ${it.groupValues[1]}" }
            .replace(everyMonthDay) { "Day ${it.groupValues[1]} of month" }
            .replace(monthOnly) { month(it.groupValues[1]) }
            .replace(yearOnly) { it.groupValues[1] }
        for (k in keys) {
            var from = 0
            while (true) {
                val i = s.indexOf(k, from)
                if (i < 0) break
                val before = s.getOrNull(i - 1)
                val after = s.getOrNull(i + k.length)
                val okStart = !isCjk(k.first()) || before == null || !isCjk(before)
                val okEnd = !isCjk(k.last()) || after == null || !isCjk(after)
                if (okStart && okEnd) {
                    val v = exact.getValue(k)
                    s = s.substring(0, i) + v + s.substring(i + k.length)
                    from = i + v.length
                } else from = i + k.length
            }
        }
        // 全形標點：整句已沒有中文時才換成英文標點
        if (s.none(::isCjk)) {
            s = s.replace("，", ", ").replace("：", ": ").replace("（", " (").replace("）", ")").replace("、", ", ")
                .replace("。", ". ").replace("「", "\"").replace("」", "\"").replace("；", "; ").replace("　", " ")
                .replace("  ", " ").trim()
        }
        return s
    }
}

/**
 * App 語言（Android 13 起為「個別應用程式語言」）：連帶讓桌面上的 App 名稱在「帳理」與「Ledgerly」之間切換。
 */
fun applyAppLocale(context: Context, language: Int) {
    if (android.os.Build.VERSION.SDK_INT < 33) return
    val lm = context.getSystemService(android.app.LocaleManager::class.java) ?: return
    val desired = when (language) {
        1 -> android.os.LocaleList.forLanguageTags("zh-TW")
        2 -> android.os.LocaleList.forLanguageTags("en")
        else -> android.os.LocaleList.getEmptyLocaleList()
    }
    if (lm.applicationLocales != desired) runCatching { lm.applicationLocales = desired }
}

/** 非 Compose 的地方（通知、小工具）使用。 */
fun tr(s: String): String = I18n.tr(s)
