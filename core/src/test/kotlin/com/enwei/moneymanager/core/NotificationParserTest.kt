package com.enwei.moneymanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationParserTest {
    @Test fun creditCardSpend() {
        val r = NotificationParser.parse("國泰世華信用卡", "您的信用卡末四碼1234於10/28 12:30在全聯福利中心消費NT$1,280，提醒您")!!
        assertEquals(ParsedType.EXPENSE, r.type)
        assertEquals(128000L, r.amountMinor)
        assertEquals("TWD", r.currency)
        assertEquals("1234", r.cardLast4)
        assertEquals("全聯福利中心", r.merchant)
    }

    @Test fun amountInYuan() {
        val r = NotificationParser.parse("刷卡消費通知", "您以卡號末四碼 5678 消費 新臺幣 350 元")!!
        assertEquals(35000L, r.amountMinor)
        assertEquals("5678", r.cardLast4)
    }

    @Test fun linePay() {
        val r = NotificationParser.parse("LINE Pay", "您已於 7-ELEVEN 付款 NT$85")!!
        assertEquals(8500L, r.amountMinor)
        assertEquals("7-ELEVEN", r.merchant)
    }

    @Test fun foreignCurrency() {
        val r = NotificationParser.parse("消費通知", "末四碼9999於NETFLIX.COM消費USD 15.49")!!
        assertEquals("USD", r.currency)
        assertEquals(1549L, r.amountMinor)
        assertEquals("NETFLIX.COM", r.merchant)
    }

    @Test fun incomeTransfer() {
        val r = NotificationParser.parse("入帳通知", "您的帳戶已轉入 NT$30,000，帳戶餘額 NT$85,000")!!
        assertEquals(ParsedType.INCOME, r.type)
        assertEquals(3000000L, r.amountMinor)
    }

    @Test fun skipsBalanceAmount() {
        val r = NotificationParser.parse("扣款通知", "可用餘額 NT$5,000，本次扣款 NT$499")!!
        assertEquals(49900L, r.amountMinor)
    }

    @Test fun refundIsIncome() {
        val r = NotificationParser.parse("退款通知", "您於蝦皮購物的消費已退款 NT$299")!!
        assertEquals(ParsedType.INCOME, r.type)
    }

    @Test fun ignoresOtp() {
        assertNull(NotificationParser.parse("簡訊", "您的交易驗證碼為 123456，消費金額 NT$1,000"))
    }

    @Test fun ignoresNonTransactions() {
        assertNull(NotificationParser.parse("天氣", "今日氣溫 25 度"))
    }

    @Test fun stockFillBuy() {
        val r = NotificationParser.parseStockFill("成交回報", "買進 2330 台積電 2張 成交價 1,050")!!
        assertEquals(StockSide.BUY, r.side)
        assertEquals("2330", r.symbol)
        assertEquals("台積電", r.name)
        assertEquals(2000.0, r.shares, 0.0)
        assertEquals(1050.0, r.price, 0.0)
    }

    @Test fun stockFillSellOddLot() {
        val r = NotificationParser.parseStockFill("委託成交", "【成交】現股賣出 0050 元大台灣50 150股 @185.5")!!
        assertEquals(StockSide.SELL, r.side)
        assertEquals("0050", r.symbol)
        assertEquals(150.0, r.shares, 0.0)
        assertEquals(185.5, r.price, 0.0)
    }

    @Test fun stockNotFilled() {
        assertNull(NotificationParser.parseStockFill("委託回報", "買進 2330 1張 未成交"))
    }
}
