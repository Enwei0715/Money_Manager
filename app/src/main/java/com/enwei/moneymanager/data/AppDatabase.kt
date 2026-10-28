package com.enwei.moneymanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class, CategoryEntity::class, TxnEntity::class, RecurringEntity::class,
        StockTradeEntity::class, QuoteEntity::class, FxRateEntity::class, MerchantRuleEntity::class,
        NotificationLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun categories(): CategoryDao
    abstract fun txns(): TxnDao
    abstract fun recurring(): RecurringDao
    abstract fun stocks(): StockDao
    abstract fun fx(): FxDao
    abstract fun rules(): RuleDao
    abstract fun notificationLog(): NotificationLogDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "money.db").build()
    }
}
