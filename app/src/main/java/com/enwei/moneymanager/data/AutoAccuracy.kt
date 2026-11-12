package com.enwei.moneymanager.data

import android.content.Context
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * 自動記帳準確度：只記在這支手機（不上傳、不進備份）。
 * 通知自動記下的交易，之後被你改了哪一欄（金額、分類、店名、帳戶）或被刪除，就記一次；每筆每一欄只算一次。
 * 「設定 → 通知記帳 → 準確度」用這些數字顯示每月有多少比例不用修改。
 */
object AutoAccuracy {
    enum class Field(val label: String) { AMOUNT("金額"), CATEGORY("分類"), MERCHANT("店名"), ACCOUNT("帳戶"), DELETED("刪除") }

    private fun prefs() = com.enwei.moneymanager.MoneyApp.instance.getSharedPreferences("auto_accuracy", Context.MODE_PRIVATE)

    private fun month(epochMs: Long): YearMonth = YearMonth.from(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

    private fun key(ym: YearMonth, f: Field) = "$ym|${f.name}"

    @Synchronized
    private fun mark(ym: YearMonth, f: Field, txnId: Long) {
        val p = prefs()
        val k = key(ym, f)
        val set = p.getStringSet(k, emptySet())!!
        if (txnId.toString() in set) return
        p.edit().putStringSet(k, set + txnId.toString()).apply()
    }

    /** 通知記的交易被修改：比對修改前後，記下被改的欄位 */
    fun onEdit(before: TxnEntity, after: TxnEntity) {
        if (before.source != TxnSource.NOTIFICATION || before.id == 0L) return
        runCatching {
            val ym = month(before.date)
            if (before.amount != after.amount || before.currency != after.currency) mark(ym, Field.AMOUNT, before.id)
            if (before.categoryId != null && before.categoryId != after.categoryId) mark(ym, Field.CATEGORY, before.id)
            if (!before.merchant.isNullOrBlank() && before.merchant?.trim() != after.merchant?.trim()) mark(ym, Field.MERCHANT, before.id)
            if (before.accountId != null && before.accountId != after.accountId) mark(ym, Field.ACCOUNT, before.id)
        }
    }

    /** 通知記的交易被刪除（記錯、不是交易） */
    fun onDelete(t: TxnEntity) {
        if (t.source != TxnSource.NOTIFICATION) return
        runCatching { mark(month(t.date), Field.DELETED, t.id) }
    }

    /** 復原刪除：不算記錯 */
    @Synchronized
    fun onRestore(t: TxnEntity) {
        if (t.source != TxnSource.NOTIFICATION) return
        runCatching {
            val p = prefs(); val k = key(month(t.date), Field.DELETED)
            val set = p.getStringSet(k, emptySet())!!
            if (t.id.toString() in set) p.edit().putStringSet(k, set - t.id.toString()).apply()
        }
    }

    /** 某月各欄被修正的筆數 */
    fun corrections(ym: YearMonth): Map<Field, Int> =
        Field.entries.associateWith { prefs().getStringSet(key(ym, it), emptySet())!!.size }

    /** 某月被修正或刪除的交易數（不重複） */
    fun touched(ym: YearMonth): Int =
        Field.entries.flatMap { prefs().getStringSet(key(ym, it), emptySet())!! }.toSet().size

    /** 清除統計（不影響交易） */
    fun clear() = prefs().edit().clear().apply()
}
