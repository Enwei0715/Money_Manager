package com.enwei.moneymanager.data

import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 帳理專屬完整備份（.lgbak）：gzip 壓縮的 JSON，包含所有資料表與所有設定。
 * 不含：GitHub Token（憑證）、股價與匯率快取（開啟後會自動重新抓取）。
 */
@Serializable
data class FullBackup(
    val format: String = FORMAT,
    val version: Int = 1,
    val appVersion: String,
    val dbVersion: Int,
    val exportedAt: Long,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TxnEntity>,
    val recurring: List<RecurringEntity>,
    val trades: List<StockTradeEntity>,
    val rules: List<MerchantRuleEntity>,
    val notificationLog: List<NotificationLogEntity>,
    /** 所有設定（SharedPreferences 原樣） */
    val settings: Map<String, JsonElement>,
) {
    companion object {
        const val FORMAT = "ledgerly-backup"
        const val EXTENSION = "lgbak"
    }
}

object FullBackupIO {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(repo: Repository, appVersion: String, out: OutputStream) {
        val db = repo.db
        val b = FullBackup(
            appVersion = appVersion,
            dbVersion = db.openHelper.readableDatabase.version,
            exportedAt = System.currentTimeMillis(),
            accounts = db.accounts().all(),
            categories = db.categories().all(),
            transactions = db.txns().all(),
            recurring = db.recurring().all(),
            trades = db.stocks().allTrades(),
            rules = db.rules().all(),
            notificationLog = db.notificationLog().all(),
            settings = repo.settings.exportRaw(),
        )
        GZIPOutputStream(out).bufferedWriter().use { it.write(json.encodeToString(FullBackup.serializer(), b)) }
    }

    /** 讀取並驗證；不是帳理備份檔時丟出例外。 */
    fun read(input: InputStream): FullBackup {
        val text = GZIPInputStream(input).bufferedReader().use { it.readText() }
        val b = json.decodeFromString(FullBackup.serializer(), text)
        require(b.format == FullBackup.FORMAT) { "不是帳理備份檔" }
        return b
    }

    /** 以備份完整取代目前所有資料與設定。 */
    suspend fun restore(repo: Repository, b: FullBackup) {
        val db = repo.db
        db.withTransaction {
            db.txns().clear(); db.accounts().clear(); db.categories().clear()
            db.recurring().clear(); db.stocks().clearTrades(); db.rules().clear(); db.notificationLog().clear()
            db.accounts().insertAll(b.accounts)
            db.categories().insertAll(b.categories)
            db.txns().insertAll(b.transactions)
            db.recurring().insertAll(b.recurring)
            db.stocks().insertTrades(b.trades)
            db.rules().insertAll(b.rules)
            db.notificationLog().insertAll(b.notificationLog)
        }
        repo.settings.importRaw(b.settings)
    }
}
