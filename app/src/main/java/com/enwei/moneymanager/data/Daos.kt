package com.enwei.moneymanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY archived, sortOrder, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts")
    suspend fun all(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): AccountEntity?

    @Upsert
    suspend fun upsert(a: AccountEntity): Long

    @Delete
    suspend fun delete(a: AccountEntity)

    @Query("UPDATE accounts SET autopayFromId = NULL WHERE autopayFromId = :id")
    suspend fun detachAutopay(id: Long)

    @Query("UPDATE accounts SET linkedAccountId = NULL WHERE linkedAccountId = :id")
    suspend fun detachLinked(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<AccountEntity>)

    @Query("DELETE FROM accounts")
    suspend fun clear()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY kind, sortOrder, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(c: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<CategoryEntity>)

    @Query("DELETE FROM categories")
    suspend fun clear()

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE transactions SET categoryId = :to WHERE categoryId = :from")
    suspend fun reassignTxns(from: Long, to: Long?)

    @Query("UPDATE recurring SET categoryId = :to WHERE categoryId = :from")
    suspend fun reassignRecurring(from: Long, to: Long?)

    @Query("DELETE FROM merchant_rules WHERE categoryId = :id")
    suspend fun deleteRules(id: Long)
}

@Dao
interface TxnDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<TxnEntity>>

    @Query("SELECT * FROM transactions WHERE date >= :from AND date < :to ORDER BY date DESC, id DESC")
    fun observeRange(from: Long, to: Long): Flow<List<TxnEntity>>

    @Query("SELECT * FROM transactions WHERE status = 'PENDING' ORDER BY date DESC")
    fun observePending(): Flow<List<TxnEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): TxnEntity?

    @Query("SELECT * FROM transactions")
    suspend fun all(): List<TxnEntity>

    @Query("SELECT * FROM transactions WHERE externalKey = :key LIMIT 1")
    suspend fun byExternalKey(key: String): TxnEntity?

    @Query("SELECT * FROM transactions WHERE type = :type AND amount = :amount AND date BETWEEN :from AND :to")
    suspend fun findSimilar(type: TxnType, amount: Long, from: Long, to: Long): List<TxnEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(t: TxnEntity): Long

    @Upsert
    suspend fun upsert(t: TxnEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<TxnEntity>)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE transactions SET accountId = NULL WHERE accountId = :accountId")
    suspend fun detachAccount(accountId: Long)

    @Query("UPDATE transactions SET toAccountId = NULL WHERE toAccountId = :accountId")
    suspend fun detachToAccount(accountId: Long)

    @Query("DELETE FROM transactions")
    suspend fun clear()
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring ORDER BY enabled DESC, id")
    fun observeAll(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurring WHERE enabled = 1")
    suspend fun enabled(): List<RecurringEntity>

    @Query("SELECT * FROM recurring")
    suspend fun all(): List<RecurringEntity>

    @Query("SELECT * FROM recurring WHERE id = :id")
    suspend fun get(id: Long): RecurringEntity?

    @Upsert
    suspend fun upsert(r: RecurringEntity): Long

    @Query("DELETE FROM recurring WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE recurring SET enabled = 0 WHERE accountId = :id OR toAccountId = :id")
    suspend fun detachAccount(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<RecurringEntity>)

    @Query("DELETE FROM recurring")
    suspend fun clear()
}

@Dao
interface StockDao {
    @Query("SELECT * FROM stock_trades ORDER BY date DESC, id DESC")
    fun observeTrades(): Flow<List<StockTradeEntity>>

    @Query("SELECT * FROM stock_trades")
    suspend fun allTrades(): List<StockTradeEntity>

    @Query("SELECT * FROM stock_trades WHERE id = :id")
    suspend fun getTrade(id: Long): StockTradeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrade(t: StockTradeEntity): Long

    @Upsert
    suspend fun upsertTrade(t: StockTradeEntity): Long

    @Query("DELETE FROM stock_trades WHERE id = :id")
    suspend fun deleteTrade(id: Long)

    @Query("DELETE FROM stock_trades WHERE market = :market AND UPPER(symbol) = :symbol")
    suspend fun deleteSymbol(market: com.enwei.moneymanager.core.Market, symbol: String)

    @Query("UPDATE stock_trades SET cashAccountId = NULL WHERE cashAccountId = :id")
    suspend fun detachCash(id: Long)

    @Query("UPDATE stock_trades SET brokerAccountId = NULL WHERE brokerAccountId = :id")
    suspend fun detachBroker(id: Long)

    @Query("UPDATE stock_trades SET cashAccountId = NULL, brokerAccountId = NULL")
    suspend fun detachAllAccounts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrades(list: List<StockTradeEntity>)

    @Query("DELETE FROM stock_trades")
    suspend fun clearTrades()

    @Query("SELECT * FROM quotes")
    fun observeQuotes(): Flow<List<QuoteEntity>>

    @Upsert
    suspend fun upsertQuotes(list: List<QuoteEntity>)
}

@Dao
interface FxDao {
    @Query("SELECT * FROM fx_rates ORDER BY currency")
    fun observeAll(): Flow<List<FxRateEntity>>

    @Query("SELECT * FROM fx_rates")
    suspend fun all(): List<FxRateEntity>

    @Query("DELETE FROM fx_rates")
    suspend fun clear()

    @Upsert
    suspend fun upsertAll(list: List<FxRateEntity>)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM merchant_rules")
    fun observeAll(): Flow<List<MerchantRuleEntity>>

    @Query("SELECT * FROM merchant_rules")
    suspend fun all(): List<MerchantRuleEntity>

    @Upsert
    suspend fun upsert(r: MerchantRuleEntity)

    @Query("DELETE FROM merchant_rules WHERE keyword = :keyword")
    suspend fun delete(keyword: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<MerchantRuleEntity>)

    @Query("DELETE FROM merchant_rules")
    suspend fun clear()
}

@Dao
interface NotificationLogDao {
    @Query("SELECT * FROM notification_log ORDER BY postedAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<NotificationLogEntity>>

    @Query("SELECT * FROM notification_log")
    suspend fun all(): List<NotificationLogEntity>

    @Query("SELECT * FROM notification_log WHERE txnId = :txnId ORDER BY postedAt DESC LIMIT 1")
    suspend fun byTxn(txnId: Long): NotificationLogEntity?

    @Query("UPDATE notification_log SET result = :result WHERE txnId = :txnId")
    suspend fun setResultForTxn(txnId: Long, result: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<NotificationLogEntity>)

    @Query("DELETE FROM notification_log")
    suspend fun clear()

    @Insert
    suspend fun insert(e: NotificationLogEntity): Long

    @Query("SELECT COUNT(*) FROM notification_log WHERE packageName = :pkg AND title IS :title AND text IS :text AND postedAt > :since")
    suspend fun countSame(pkg: String, title: String?, text: String?, since: Long): Int

    @Query("DELETE FROM notification_log WHERE postedAt < :before")
    suspend fun prune(before: Long)

    @Query("UPDATE notification_log SET result = :result WHERE id = :id")
    suspend fun setResult(id: Long, result: String)
}
