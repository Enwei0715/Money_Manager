package com.enwei.moneymanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/** 以實際收到的銀行通知格式測試。 */
class RealNotificationTest {
    private val now = LocalDateTime.of(2026, 10, 28, 14, 30)
    private fun p(title: String?, text: String) = NotificationParser.parse(title, text, now)

    @Test fun cathayCardSpend() {
        val r = p("刷卡通知", "金額NT\$229元 卡號末四碼2917於 2026/10/26 16:22 在全聯福利中心 - 中正南海 刷卡。立即以點數即時折抵，最高可折抵消費金額30%，點擊查看消費明細")!!
        assertEquals(ParsedType.EXPENSE, r.type)
        assertEquals(ParsedKind.NORMAL, r.kind)
        assertEquals(22900L, r.amountMinor)
        assertEquals("2917", r.cardLast4)
        assertEquals("全聯福利中心 - 中正南海", r.merchant)
        assertEquals(LocalDateTime.of(2026, 10, 26, 16, 22), r.occurredAt)
    }

    @Test fun cathayForeignMerchant() {
        val r = p("刷卡通知", "金額NT\$687元 卡號末四碼2917於 2026/10/23 17:23 在ANTHROPIC* CLAUDE SUB 刷卡。")!!
        assertEquals("ANTHROPIC* CLAUDE SUB", r.merchant)
    }

    @Test fun sinopacLinePay() {
        val r = p(null, "永豐貴賓您好，末四碼5908感謝10/28 13:24刷卡台幣27元，商店名稱:LINE Pay，實際商店名稱請以信用卡帳單列示為準。謹慎理財信用至上循環利率5~15%")!!
        assertEquals(2700L, r.amountMinor)
        assertEquals("5908", r.cardLast4)
        assertEquals("LINE Pay", r.merchant)
        assertEquals(LocalDateTime.of(2026, 10, 28, 13, 24), r.occurredAt)
        assertEquals(true, CategoryGuesser.isGenericMerchant(r.merchant))
    }

    @Test fun sinopacRocDate() {
        val r = p(null, "永豐信用卡末四碼5908刷卡通知1151006_21:44金額台幣40元，商店名稱:GOOGLE*WALLET TEMP，實際商店名稱請以信用卡帳單列示為準")!!
        assertEquals(4000L, r.amountMinor)
        assertEquals(LocalDateTime.of(2026, 10, 6, 21, 44), r.occurredAt)
    }

    @Test fun cathayEPaymentTopUp() {
        val r = p("帳戶出帳通知", "您於2026/10/20 12:31:57電支交易,金額為NT\$100元")!!
        assertEquals(ParsedKind.TOPUP_OUT, r.kind)
        assertEquals(10000L, r.amountMinor)
        assertEquals(LocalDateTime.of(2026, 10, 20, 12, 31, 57), r.occurredAt)
    }

    @Test fun cathayAccountTail() {
        val r = p("帳戶出帳通知", "【帳戶出帳通知】自動化通路交易 帳號：末五碼14773 交易時間：2026/10/20 12:31:57 摘要：電支交易 金額：NT\$100")!!
        assertEquals("4773", r.cardLast4)
        assertEquals(ParsedKind.TOPUP_OUT, r.kind)
    }

    @Test fun jkoAutoTopUp() {
        val r = p("系統通知", "因街口帳戶餘額低於100元，已自動儲值100元至您的街口帳戶。")!!
        assertEquals(ParsedKind.TOPUP_IN, r.kind)
        assertEquals(ParsedType.INCOME, r.type)
        assertEquals(10000L, r.amountMinor)
        assertEquals("街口", r.walletHint)
    }

    @Test fun withdrawal() {
        val r = p("帳戶出帳通知", "您於2026/10/25 20:52:48跨行提款,金額為NT\$1,000元")!!
        assertEquals(ParsedKind.WITHDRAWAL, r.kind)
        assertEquals(100000L, r.amountMinor)
    }

    @Test fun transfers() {
        assertEquals(ParsedKind.TRANSFER_IN, p("帳戶入帳通知", "您於2026/10/28 05:06:52跨行轉入,金額為NT\$12,000元")!!.kind)
        assertEquals(ParsedKind.TRANSFER_OUT, p("帳戶出帳通知", "您於2026/10/20 09:30:30電子轉出,金額為NT\$25,200元")!!.kind)
    }

    @Test fun nonTransactions() {
        assertNull(p(null, "永豐貴賓您好，您於2026/09/29 01:28:59完成「DAWHO現金回饋信用卡5908」卡片資訊查詢，若有疑問請聯繫客服02-25287776，謝謝您。"))
        assertNull(p(null, "【裝置綁定】您好，您於2026/09/28 19:57:57新增ASUS AI2302綁定，請您確認是否在此"))
    }
}

class CardPaymentTest {
    @Test fun autopay() {
        val r = NotificationParser.parse("帳戶出帳通知", "您於2026/10/20 08:01:02信用卡款自動扣繳,金額為NT\$6,146元")!!
        assertEquals(ParsedKind.CARD_PAYMENT, r.kind)
        assertEquals(614600L, r.amountMinor)
    }
}
