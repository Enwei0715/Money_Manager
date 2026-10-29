package com.enwei.moneymanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/** 以合成銀行通知格式測試，不保存使用者的原始通知資料。 */
class RealNotificationTest {
    private val now = LocalDateTime.of(2026, 1, 28, 14, 30)
    private fun p(title: String?, text: String) = NotificationParser.parse(title, text, now)

    @Test fun cathayCardSpend() {
        val r = p("刷卡通知", "金額NT\$229元 卡號末四碼1234於 2026/01/26 16:22 在合成商店 刷卡。立即以點數即時折抵，最高可折抵消費金額30%，點擊查看消費明細")!!
        assertEquals(ParsedType.EXPENSE, r.type)
        assertEquals(ParsedKind.NORMAL, r.kind)
        assertEquals(22900L, r.amountMinor)
        assertEquals("1234", r.cardLast4)
        assertEquals("合成商店", r.merchant)
        assertEquals(LocalDateTime.of(2026, 1, 26, 16, 22), r.occurredAt)
    }

    @Test fun cathayForeignMerchant() {
        val r = p("刷卡通知", "金額NT\$687元 卡號末四碼1234於 2026/01/23 17:23 在合成海外商店 刷卡。")!!
        assertEquals("合成海外商店", r.merchant)
    }

    @Test fun sinopacLinePay() {
        val r = p(null, "永豐貴賓您好，末四碼1234感謝01/28 13:24刷卡台幣27元，商店名稱:LINE Pay，實際商店名稱請以信用卡帳單列示為準。謹慎理財信用至上循環利率5~15%")!!
        assertEquals(2700L, r.amountMinor)
        assertEquals("1234", r.cardLast4)
        assertEquals("LINE Pay", r.merchant)
        assertEquals(LocalDateTime.of(2026, 1, 28, 13, 24), r.occurredAt)
        assertEquals(true, CategoryGuesser.isGenericMerchant(r.merchant))
    }

    @Test fun sinopacRocDate() {
        val r = p(null, "永豐信用卡末四碼1234刷卡通知1150106_21:44金額台幣40元，商店名稱:數位服務，實際商店名稱請以信用卡帳單列示為準")!!
        assertEquals(4000L, r.amountMinor)
        assertEquals(LocalDateTime.of(2026, 1, 6, 21, 44), r.occurredAt)
    }

    @Test fun cathayEPaymentTopUp() {
        val r = p("帳戶出帳通知", "您於2026/01/20 12:31:57電支交易,金額為NT\$100元")!!
        assertEquals(ParsedKind.TOPUP_OUT, r.kind)
        assertEquals(10000L, r.amountMinor)
        assertEquals(LocalDateTime.of(2026, 1, 20, 12, 31, 57), r.occurredAt)
    }

    @Test fun cathayAccountTail() {
        val r = p("帳戶出帳通知", "【帳戶出帳通知】自動化通路交易 帳號：末五碼11111 交易時間：2026/01/20 12:31:57 摘要：電支交易 金額：NT\$100")!!
        assertEquals("1111", r.cardLast4)
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
        val r = p("帳戶出帳通知", "您於2026/01/25 20:52:48跨行提款,金額為NT\$1,000元")!!
        assertEquals(ParsedKind.WITHDRAWAL, r.kind)
        assertEquals(100000L, r.amountMinor)
    }

    @Test fun transfers() {
        assertEquals(ParsedKind.TRANSFER_IN, p("帳戶入帳通知", "您於2026/01/28 05:06:52跨行轉入,金額為NT\$12,000元")!!.kind)
        assertEquals(ParsedKind.TRANSFER_OUT, p("帳戶出帳通知", "您於2026/01/20 09:30:30電子轉出,金額為NT\$25,200元")!!.kind)
    }

    @Test fun nonTransactions() {
        assertNull(p(null, "銀行通知：您於2026/01/29 01:28:59完成信用卡片資訊查詢，若有疑問請聯繫客服，謝謝您。"))
        assertNull(p(null, "【裝置綁定】您於2026/01/28 19:57:57新增新裝置綁定，請確認是否本人操作"))
    }
}

class CardPaymentTest {
    @Test fun autopay() {
        val r = NotificationParser.parse("帳戶出帳通知", "您於2026/01/20 08:01:02信用卡款自動扣繳,金額為NT\$6,146元")!!
        assertEquals(ParsedKind.CARD_PAYMENT, r.kind)
        assertEquals(614600L, r.amountMinor)
    }
}

class LineWalletTest {
    @Test fun englishPurchase() {
        val r = NotificationParser.parse("LINE錢包", "LINE Pay Purchase NT$ 25\nPayment complete.\nMerchant: 合成商店")!!
        assertEquals(ParsedType.EXPENSE, r.type)
        assertEquals(ParsedKind.NORMAL, r.kind)
        assertEquals(2500L, r.amountMinor)
        assertEquals("TWD", r.currency)
        assertEquals("合成商店", r.merchant)
    }

    @Test fun truncatedMerchant() {
        val r = NotificationParser.parse("LINE錢包", "LINE Pay Purchase NT$ 25\nPayment complete.\nMerchan")!!
        assertEquals(2500L, r.amountMinor)
    }

    @Test fun englishRefund() {
        assertEquals(ParsedType.INCOME, NotificationParser.parse("LINE錢包", "LINE Pay Refund NT$ 120\nRefund complete.")!!.type)
    }

    @Test fun failed() {
        assertNull(NotificationParser.parse("LINE錢包", "LINE Pay Purchase NT$ 25\nPayment failed."))
    }
}
