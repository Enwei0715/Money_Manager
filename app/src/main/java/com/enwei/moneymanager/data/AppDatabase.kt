package com.enwei.moneymanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class, CategoryEntity::class, TxnEntity::class, RecurringEntity::class,
        StockTradeEntity::class, QuoteEntity::class, FxRateEntity::class, MerchantRuleEntity::class,
        NotificationLogEntity::class,
    ],
    version = 2,
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
            Room.databaseBuilder(context, AppDatabase::class.java, "money.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN description TEXT")
            }
        }
    }
}
