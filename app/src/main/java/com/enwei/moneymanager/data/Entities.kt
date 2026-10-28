package com.enwei.moneymanager.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.enwei.moneymanager.core.Frequency
import com.enwei.moneymanager.core.Market
import com.enwei.moneymanager.core.StockSide
import kotlinx.serialization.Serializable

enum class AccountType(val label: String, val icon: String) {
    CASH("現金", "💵"),
    BANK("銀行帳戶", "🏦"),
    CREDIT_CARD("信用卡", "💳"),
    EWALLET("電子支付", "📱"),
    BROKERAGE("證券交割", "📈"),
    OTHER("其他", "👛"),
}

enum class TxnType(val label: String) { EXPENSE("支出"), INCOME("收入"), TRANSFER("轉帳") }

enum class TxnSource(val label: String) {
    MANUAL("手動"), NOTIFICATION("通知"), RECURRING("週期"), INVOICE("發票"), AUTOPAY("自動扣繳"), IMPORT("匯入")
}

enum class TxnStatus { CONFIRMED, PENDING }

@Serializable
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val currency: String = "TWD",
    /** 期初餘額（信用卡為負數代表已有欠款） */
    val initialBalance: Long = 0,
    val creditLimit: Long = 0,
    val cardLast4: String? = null,
    /** 信用卡結帳日 */
    val statementDay: Int? = null,
    /** 信用卡繳款日 */
    val dueDay: Int? = null,
    /** 自動扣繳的扣款帳戶 */
    val autopayFromId: Long? = null,
    /** 自動扣繳最後處理到的日期（epochDay） */
    val autopayLastRun: Long? = null,
    val includeInNetWorth: Boolean = true,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

@Serializable
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: TxnType,
    val icon: String,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

@Serializable
@Entity(
    tableName = "transactions",
    indices = [Index("date"), Index("accountId"), Index("toAccountId"), Index(value = ["externalKey"], unique = true)],
)
data class TxnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxnType,
    /** 一律為正數；方向由 type 決定 */
    val amount: Long,
    val currency: String = "TWD",
    val accountId: Long? = null,
    /** 轉帳轉入帳戶 */
    val toAccountId: Long? = null,
    /** 跨幣別轉帳時的轉入金額 */
    val toAmount: Long? = null,
    val categoryId: Long? = null,
    val merchant: String? = null,
    val note: String? = null,
    /** epoch millis */
    val date: Long,
    val source: TxnSource = TxnSource.MANUAL,
    val status: TxnStatus = TxnStatus.CONFIRMED,
    /** 去重用：發票號碼、週期規則日期等 */
    val externalKey: String? = null,
    val recurringId: Long? = null,
    val rawText: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TxnType,
    val amount: Long,
    val currency: String = "TWD",
    val accountId: Long?,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val note: String? = null,
    val frequency: Frequency = Frequency.MONTHLY,
    val interval: Int = 1,
    val dayOfMonth: Int? = null,
    /** 起始日 epochDay */
    val anchorDate: Long,
    /** 已產生到的日期 epochDay */
    val lastRunDate: Long? = null,
    val endDate: Long? = null,
    /** false 時產生的交易會先放到「待確認」 */
    val autoConfirm: Boolean = true,
    val enabled: Boolean = true,
)

@Serializable
@Entity(tableName = "stock_trades", indices = [Index("symbol"), Index(value = ["externalKey"], unique = true)])
data class StockTradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val market: Market,
    val symbol: String,
    val name: String? = null,
    val side: StockSide,
    val shares: Double,
    val price: Double,
    val fee: Double = 0.0,
    val tax: Double = 0.0,
    val cashAmount: Double = 0.0,
    val currency: String = "TWD",
    /** epochDay */
    val date: Long,
    /** 交割帳戶：買賣金額會反映在此帳戶餘額 */
    val cashAccountId: Long? = null,
    val note: String? = null,
    val source: TxnSource = TxnSource.MANUAL,
    val externalKey: String? = null,
)

@Serializable
@Entity(tableName = "quotes")
data class QuoteEntity(
    /** 例如 "TW:2330"、"US:AAPL" */
    @PrimaryKey val key: String,
    val price: Double,
    val previousClose: Double?,
    val currency: String,
    val name: String?,
    val updatedAt: Long,
)

@Serializable
@Entity(tableName = "fx_rates")
data class FxRateEntity(
    @PrimaryKey val currency: String,
    /** 1 單位基準幣可換多少此幣別 */
    val rate: Double,
    val updatedAt: Long,
)

@Serializable
@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey val keyword: String,
    val categoryId: Long,
)

@Serializable
@Entity(tableName = "notification_log", indices = [Index("postedAt")])
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val title: String?,
    val text: String?,
    val postedAt: Long,
    /** RECORDED / STOCK / DUPLICATE / UNPARSED / CANDIDATE */
    val result: String,
    val txnId: Long? = null,
)

fun quoteKey(market: Market, symbol: String) = "${market.name}:${symbol.uppercase()}"
