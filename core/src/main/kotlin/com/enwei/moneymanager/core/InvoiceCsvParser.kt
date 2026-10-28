package com.enwei.moneymanager.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class InvoiceItem(val name: String, val amountMinor: Long)

data class Invoice(
    val number: String,
    val date: LocalDate,
    val sellerName: String,
    val sellerBan: String,
    val amountMinor: Long,
    val carrierName: String,
    val items: List<InvoiceItem>,
)

/**
 * 解析財政部「電子發票整合服務平台」載具消費明細匯出的 CSV。
 * 表頭 M|載具自訂名稱|載具號碼|發票日期|商店統編|商店店名|發票號碼|總金額|發票狀態|
 * 明細 D|發票號碼|小計|品項名稱|
 */
object InvoiceCsvParser {
    private val compact = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun parse(content: String): List<Invoice> {
        data class Header(
            val number: String, val date: LocalDate, val sellerName: String, val sellerBan: String,
            val amount: Long, val carrier: String,
        )

        val headers = LinkedHashMap<String, Header>()
        val items = HashMap<String, MutableList<InvoiceItem>>()

        content.lineSequence().forEach { raw ->
            val line = raw.trim().removePrefix("﻿")
            val fields = line.split('|').map { it.trim() }
            when (fields.firstOrNull()) {
                "M" -> {
                    if (fields.size < 8) return@forEach
                    val status = fields.getOrNull(8).orEmpty()
                    if (status.contains("作廢") || status.contains("註銷")) return@forEach
                    val date = parseDate(fields[3]) ?: return@forEach
                    val amount = Money.parseMinor(fields[7]) ?: return@forEach
                    headers[fields[6]] = Header(fields[6], date, fields[5], fields[4], amount, fields[1])
                }
                "D" -> {
                    if (fields.size < 4) return@forEach
                    val amount = Money.parseMinor(fields[2]) ?: 0L
                    items.getOrPut(fields[1]) { mutableListOf() } += InvoiceItem(fields[3], amount)
                }
            }
        }
        return headers.values.map {
            Invoice(it.number, it.date, it.sellerName, it.sellerBan, it.amount, it.carrier, items[it.number].orEmpty())
        }
    }

    fun parseDate(text: String): LocalDate? {
        val t = text.trim()
        return runCatching {
            when {
                t.length == 8 && t.all(Char::isDigit) -> LocalDate.parse(t, compact)
                t.contains('/') || t.contains('-') -> {
                    val parts = t.split('/', '-').map { it.toInt() }
                    // 民國年（例如 113/10/28）
                    val year = if (parts[0] < 1911) parts[0] + 1911 else parts[0]
                    LocalDate.of(year, parts[1], parts[2])
                }
                t.length == 7 && t.all(Char::isDigit) -> // 民國 1131028
                    LocalDate.of(t.substring(0, 3).toInt() + 1911, t.substring(3, 5).toInt(), t.substring(5, 7).toInt())
                else -> null
            }
        }.getOrNull()
    }
}
