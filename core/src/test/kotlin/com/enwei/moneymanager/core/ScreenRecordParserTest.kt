package com.enwei.moneymanager.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ScreenRecordParserTest {
    private val today = LocalDate.of(2026, 10, 28)

    @Test fun jkoDateBeforeAmount() {
        val r = ScreenRecordParser.parse(
            listOf("交易紀錄", "日期", "交易類型", "2026/04/28~2026/10/28",
                "國泰世華銀行", "儲值", "2026/10/22 15:25", "+100",
                "合成商店", "支付", "2026/10/22 15:25", "-30",
                "合成餐飲", "支付", "2026/10/22 12:33", "-109"),
            today,
        )
        assertEquals(3, r.size)
        assertEquals(true, r[0].isTopUp)
        assertEquals("國泰世華銀行", r[0].merchant)
        assertEquals("合成商店", r[1].merchant)
        assertEquals(3000L, r[1].amountMinor)
        assertEquals(false, r[1].isIncome)
        assertEquals(LocalDateTime.of(2026, 10, 22, 12, 33), r[2].time)
    }

    @Test fun jkoAmountBeforeDate() {
        val r = ScreenRecordParser.parse(
            listOf("合成銀行", "+100", "儲值", "2026/10/22 15:25", "合成餐飲", "-88", "支付", "2026/10/20 12:31"),
            today,
        )
        assertEquals(LocalDateTime.of(2026, 10, 22, 15, 25), r[0].time)
        assertEquals("合成餐飲", r[1].merchant)
        assertEquals(LocalDateTime.of(2026, 10, 20, 12, 31), r[1].time)
    }

    @Test fun easyWallet() {
        val r = ScreenRecordParser.parse(
            listOf("悠遊付錢包餘額 (NT)", "$88", "全部", "支出", "收入", "大眾運輸", "2026 年 10 月",
                "19日", "臺北大眾捷運股份有限公司", "嗶乘車", "- $20",
                "19日", "臺北大眾捷運股份有限公司", "嗶乘車", "$0",
                "19日", "彰化汽車客運股份有限公司", "嗶乘車", "- $15",
                "16日", "臺北大眾捷運股份有限公司", "嗶乘車", "- $20"),
            today,
        )
        assertEquals(3, r.size)
        assertEquals("彰化汽車客運股份有限公司", r[1].merchant)
        assertEquals(1500L, r[1].amountMinor)
        assertEquals(LocalDate.of(2026, 10, 19), r[1].time.toLocalDate())
        assertEquals(true, r[1].dateOnly)
        assertEquals(LocalDate.of(2026, 10, 16), r[2].time.toLocalDate())
        assertEquals("嗶乘車", r[2].typeLabel)
    }

    @Test fun debugSimulationSampleParsesAllRecordTypes() {
        val day = today
        val records = ScreenRecordParser.parse(
            listOf(
                day.toString(), "合成商店", "支付", "- NT$125",
                day.toString(), "合成退款", "退款", "+ NT$50",
                day.toString(), "合成儲值", "儲值", "+ NT$200",
            ),
            day,
        )
        assertEquals(3, records.size)
        assertEquals(false, records[0].isIncome)
        assertEquals(true, records[1].isIncome)
        assertEquals(true, records[2].isTopUp)
    }
}
