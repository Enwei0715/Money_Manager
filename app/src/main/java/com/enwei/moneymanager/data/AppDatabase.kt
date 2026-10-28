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
    version = 5,
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN description TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN parentId INTEGER")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN autopayDay INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN linkedAccountId INTEGER")
                db.execSQL("ALTER TABLE accounts ADD COLUMN feeDiscount REAL")
                db.execSQL("ALTER TABLE stock_trades ADD COLUMN brokerAccountId INTEGER")
                // 舊資料：交割帳戶若是證券戶，改記為所屬證券戶
                db.execSQL(
                    "UPDATE stock_trades SET brokerAccountId = cashAccountId WHERE cashAccountId IN " +
                        "(SELECT id FROM accounts WHERE type = 'BROKERAGE')",
                )
            }
        }
    }
}
