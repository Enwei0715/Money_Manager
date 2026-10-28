package com.enwei.moneymanager.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import com.enwei.moneymanager.core.FxConverter
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.core.NotificationParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class MmImportSummary(
    val accounts: Int,
    val categories: Int,
    val transactions: Int,
    val skipped: Int,
    val memos: Int,
    val recurring: Int,
)

/**
 * Money Manager（Realbyte）備份檔 .mmbak 的匯入與匯出。
 * .mmbak 是 SQLite 資料庫：ASSETS 帳戶、ASSETGROUP 帳戶群組、ZCATEGORY 分類（含子分類）、
 * INOUTCOME 交易（DO_TYPE 0 收入、1 支出、3 轉出、4 轉入、7／8 餘額調整增／減）、MEMO 備忘。
 */
object MmbakIO {

    private fun Cursor.str(col: String): String? = getColumnIndex(col).takeIf { it >= 0 }?.let { if (isNull(it)) null else getString(it) }
    private fun Cursor.num(col: String): Double? = str(col)?.toDoubleOrNull()

    private fun <T> SQLiteDatabase.rows(sql: String, map: (Cursor) -> T): List<T> =
        rawQuery(sql, null).use { c -> buildList { while (c.moveToNext()) add(map(c)) } }

    private fun SQLiteDatabase.hasTable(name: String) =
        rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name)).use { it.moveToFirst() }

    /** 帳戶群組類型 → 記帳本帳戶類型 */
    private fun accountType(groupType: Int?, name: String): AccountType {
        val looksWallet = NotificationParser.walletAliases.values.flatten().any { name.contains(it, ignoreCase = true) }
        return when (groupType) {
            11 -> AccountType.CASH
            1, 3, 4 -> AccountType.BANK
            2 -> AccountType.CREDIT_CARD
            6 -> AccountType.EWALLET
            8 -> AccountType.BROKERAGE
            else -> if (looksWallet) AccountType.EWALLET else AccountType.OTHER
        }
    }

    private fun groupTypeOf(t: AccountType) = when (t) {
        AccountType.CASH -> 11
        AccountType.BANK -> 1
        AccountType.CREDIT_CARD -> 2
        AccountType.EWALLET -> 6
        AccountType.BROKERAGE -> 8
        AccountType.OTHER -> 7
    }

    private fun isoOf(currencyUid: String?, fallback: String): String =
        currencyUid?.substringAfter('_', "")?.takeIf { it.length == 3 }?.uppercase() ?: fallback

    /**
     * 匯入。replace = true 會先清空記帳本現有的帳戶、分類、交易；
     * 否則合併：同名同類型的帳戶與分類會沿用，已匯入過的交易（以原始 uid 判斷）不會重複。
     */
    suspend fun import(context: Context, repo: Repository, file: File, replace: Boolean): MmImportSummary = withContext(Dispatchers.IO) {
        val src = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        src.use { mm ->
            require(mm.hasTable("INOUTCOME") && mm.hasTable("ASSETS")) { "這不是 Money Manager 的備份檔" }
            val base = repo.settings.value.baseCurrency
            val db = repo.db

            val groups = mm.rows("SELECT uid, TYPE FROM ASSETGROUP") { it.str("uid") to (it.str("TYPE")?.toIntOrNull()) }.toMap()
            data class MmAsset(
                val uid: String, val name: String, val group: Int?, val statement: Int?, val due: Int?, val hidden: Boolean,
                val desc: String?, val currency: String, val payFrom: String?, val order: Int,
            )
            val assets = mm.rows("SELECT * FROM ASSETS") { c ->
                MmAsset(
                    uid = c.str("uid").orEmpty(), name = c.str("NIC_NAME").orEmpty().trim().ifEmpty { "未命名帳戶" },
                    group = groups[c.str("groupUid")], statement = c.str("CARD_DAY_FIN")?.toIntOrNull(), due = c.str("CARD_DAY_PAY")?.toIntOrNull(),
                    hidden = c.str("ZDATA") == "1", desc = c.str("ZDATA1")?.ifBlank { null },
                    currency = isoOf(c.str("currencyUid"), base), payFrom = c.str("cardAssetUid")?.ifBlank { null },
                    order = c.str("ORDERSEQ")?.toIntOrNull() ?: 0,
                )
            }.filter { it.uid.isNotEmpty() }

            data class MmCat(val uid: String, val name: String, val kind: TxnType, val parent: String?, val deleted: Boolean, val order: Int)
            val cats = mm.rows("SELECT * FROM ZCATEGORY") { c ->
                MmCat(
                    uid = c.str("uid").orEmpty(), name = c.str("NAME").orEmpty().trim(),
                    kind = if (c.str("TYPE") == "0") TxnType.INCOME else TxnType.EXPENSE,
                    parent = c.str("pUid")?.takeIf { it.isNotBlank() && it != "0" && c.str("STATUS") == "2" },
                    deleted = c.str("C_IS_DEL") == "1", order = c.str("ORDERSEQ")?.toIntOrNull() ?: 0,
                )
            }.filter { it.uid.isNotEmpty() && it.name.isNotEmpty() }

            data class MmTx(
                val uid: String, val asset: String?, val toAsset: String?, val cat: String?, val content: String?, val millis: Long,
                val type: String?, val amount: Double, val inAmount: Double?, val pair: String?, val memo: String?, val currency: String,
            )
            val txs = mm.rows("SELECT * FROM INOUTCOME WHERE IFNULL(IS_DEL,0) = 0") { c ->
                MmTx(
                    uid = c.str("uid").orEmpty(), asset = c.str("assetUid")?.ifBlank { null }, toAsset = c.str("toAssetUid")?.ifBlank { null },
                    cat = c.str("ctgUid")?.ifBlank { null }, content = c.str("ZCONTENT")?.ifBlank { null },
                    millis = c.str("ZDATE")?.toLongOrNull() ?: 0L, type = c.str("DO_TYPE"),
                    amount = c.num("ZMONEY") ?: 0.0, inAmount = c.num("IN_ZMONEY"), pair = c.str("txUidTrans")?.ifBlank { null },
                    memo = c.str("ZDATA")?.ifBlank { null }, currency = isoOf(c.str("currencyUid"), base),
                )
            }.filter { it.uid.isNotEmpty() && it.millis > 0 }

            val memos = if (mm.hasTable("MEMO")) mm.rows("SELECT * FROM MEMO WHERE IFNULL(isDel,0) = 0") { c ->
                val title = c.str("title").orEmpty().trim()
                val content = c.str("content").orEmpty().trim()
                val date = c.str("memoDate")?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: java.time.LocalDate.now()
                Memo(0, date.toMillis(), listOf(title, content).filter { it.isNotEmpty() }.joinToString("\n"))
            }.filter { it.text.isNotEmpty() } else emptyList()

            data class MmRepeat(val type: String?, val repeatType: Int?, val next: Long?, val asset: String?, val toAsset: String?, val cat: String?,
                                val amount: Double, val memo: String?, val payee: String?, val currency: String)
            val repeats = if (mm.hasTable("REPEATTRANSACTION")) mm.rows("SELECT * FROM REPEATTRANSACTION WHERE IFNULL(IS_DEL,0) = 0") { c ->
                MmRepeat(c.str("DO_TYPE"), c.str("REPEAT_TYPE")?.toIntOrNull(), c.str("NEXT_DATE")?.toLongOrNull(), c.str("assetUid")?.ifBlank { null },
                    c.str("toAssetUid")?.ifBlank { null }, c.str("ctgUid")?.ifBlank { null }, c.num("AMOUNT_SUB") ?: 0.0,
                    c.str("MEMO")?.ifBlank { null }, c.str("PAYEE")?.ifBlank { null }, isoOf(c.str("currencyUid"), base))
            } else emptyList()

            var created = 0
            var skipped = 0
            var catCount = 0
            var accCount = 0
            db.withTransaction {
                if (replace) {
                    db.txns().clear(); db.accounts().clear(); db.categories().clear(); db.recurring().clear(); db.rules().clear()
                    db.stocks().detachAllAccounts()
                }
                // 帳戶
                val existingAcc = db.accounts().all()
                val accMap = HashMap<String, Long>()
                fun acc(u: String?): Long? = u?.let { accMap[it] }
                assets.sortedBy { it.order }.forEach { a ->
                    val type = accountType(a.group, a.name)
                    val found = existingAcc.firstOrNull { it.name.trim() == a.name && it.type == type }
                    accMap[a.uid] = found?.id ?: run {
                        accCount++
                        db.accounts().upsert(
                            AccountEntity(
                                name = a.name, type = type, currency = a.currency, archived = a.hidden, description = a.desc,
                                statementDay = if (type == AccountType.CREDIT_CARD) a.statement?.takeIf { it in 1..31 } else null,
                                dueDay = if (type == AccountType.CREDIT_CARD) a.due?.takeIf { it in 1..31 } else null,
                                sortOrder = existingAcc.size + a.order,
                            ),
                        )
                    }
                }
                // 信用卡的繳款帳戶（第二輪，帳戶都建立後才能對應）
                assets.filter { it.payFrom != null }.forEach { a ->
                    val id = accMap[a.uid] ?: return@forEach
                    val from = acc(a.payFrom) ?: return@forEach
                    db.accounts().get(id)?.takeIf { it.type == AccountType.CREDIT_CARD && it.autopayFromId == null }?.let {
                        db.accounts().upsert(it.copy(autopayFromId = from))
                    }
                }

                // 分類：先大類再子分類
                val existingCats = db.categories().all()
                val catMap = HashMap<String, Long>()
                fun cat(u: String?): Long? = u?.let { catMap[it] }
                suspend fun findOrCreate(c: MmCat, parentId: Long?): Long =
                    existingCats.firstOrNull { it.name == c.name && it.kind == c.kind && it.parentId == parentId }?.id
                        ?: db.categories().upsert(
                            CategoryEntity(name = c.name, kind = c.kind, icon = "", sortOrder = c.order,
                                archived = c.deleted, parentId = parentId),
                        ).also { catCount++ }
                cats.filter { it.parent == null }.forEach { catMap[it.uid] = findOrCreate(it, null) }
                cats.filter { it.parent != null }.forEach { c -> catMap[c.uid] = findOrCreate(c, cat(c.parent)) }

                // 交易
                val transferIn = txs.filter { it.type == "4" && it.pair != null }.associateBy { it.pair }
                val assetCurrency = assets.associate { it.uid to it.currency }
                txs.forEach { t ->
                    val key = "MM:${t.uid}"
                    // ZMONEY 是帳戶幣別的金額；外幣交易的原幣金額在 IN_ZMONEY，寫進說明保留
                    val amount = Money.toMinor(kotlin.math.abs(t.amount))
                    val accCur = t.asset?.let { assetCurrency[it] } ?: base
                    val foreign = if (t.currency != accCur && t.inAmount != null) "原幣 ${t.currency} ${Money.fromMinor(Money.toMinor(t.inAmount)).stripTrailingZeros().toPlainString()}" else null
                    val adjust = t.cat == "-4"
                    val base0 = TxnEntity(
                        type = TxnType.EXPENSE, amount = amount, currency = accCur, accountId = acc(t.asset),
                        merchant = t.content ?: if (adjust) "餘額調整" else null,
                        note = listOfNotNull(t.memo, foreign).joinToString("　").ifEmpty { null },
                        date = t.millis, source = TxnSource.IMPORT, externalKey = key,
                    )
                    val entity = when (t.type) {
                        "0" -> base0.copy(type = TxnType.INCOME, categoryId = cat(t.cat))
                        "1" -> base0.copy(type = TxnType.EXPENSE, categoryId = cat(t.cat))
                        "3" -> {
                            val inRow = t.pair?.let { transferIn[it] }
                            val toAcc = acc(t.toAsset) ?: acc(inRow?.asset)
                            val toAmount = inRow?.amount?.let { Money.toMinor(kotlin.math.abs(it)) }?.takeIf { it != amount }
                            base0.copy(type = TxnType.TRANSFER, toAccountId = toAcc, toAmount = toAmount)
                        }
                        "7" -> base0.copy(type = TxnType.INCOME, merchant = t.content ?: "餘額調整", note = t.memo ?: "Money Manager 餘額調整")
                        "8" -> base0.copy(type = TxnType.EXPENSE, merchant = t.content ?: "餘額調整", note = t.memo ?: "Money Manager 餘額調整")
                        else -> null
                    }
                    if (entity == null) return@forEach
                    if (db.txns().insert(entity) > 0) created++ else skipped++
                }

                // 週期：僅確定的「每月」直接啟用，其餘匯入為停用待確認
                val existingRules = db.recurring().all()
                repeats.forEach { r ->
                    val accId = acc(r.asset) ?: return@forEach
                    val next = r.next?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() } ?: return@forEach
                    val type = when (r.type) { "0" -> TxnType.INCOME; "3" -> TxnType.TRANSFER; else -> TxnType.EXPENSE }
                    val monthly = r.repeatType == 1
                    val name = r.payee ?: r.memo ?: cat(r.cat)?.let { id -> db.categories().all().firstOrNull { it.id == id }?.name } ?: "週期交易"
                    val amountMinor = Money.toMinor(kotlin.math.abs(r.amount))
                    // 合併匯入時，同名、同金額、同帳戶的週期設定視為已存在
                    if (existingRules.any { it.name == name && it.amount == amountMinor && it.accountId == accId }) return@forEach
                    db.recurring().upsert(
                        RecurringEntity(
                            name = name,
                            type = type, amount = amountMinor, currency = r.asset?.let { assetCurrency[it] } ?: r.currency, accountId = accId,
                            toAccountId = if (type == TxnType.TRANSFER) acc(r.toAsset) else null,
                            categoryId = if (type == TxnType.TRANSFER) null else cat(r.cat),
                            frequency = com.enwei.moneymanager.core.Frequency.MONTHLY, dayOfMonth = next.dayOfMonth,
                            anchorDate = next.toEpochDay(), lastRunDate = next.minusDays(1).toEpochDay(), enabled = monthly,
                            note = if (monthly) r.memo else "自 Money Manager 匯入，頻率代碼 ${r.repeatType}，請確認後啟用",
                        ),
                    )
                }
            }
            val accountsNow = db.accounts().all()
            val ids = accountsNow.map { it.id }.toSet()
            repo.settings.update { s ->
                val existing = s.memos.map { it.text }.toSet()
                s.copy(
                    memos = s.memos + memos.filter { it.text !in existing }.mapIndexed { i, m -> m.copy(id = System.currentTimeMillis() + i) },
                    // 取代匯入後舊帳戶已不存在：預設帳戶改為現金，移除失效的對應
                    defaultAccountId = s.defaultAccountId?.takeIf { it in ids }
                        ?: accountsNow.firstOrNull { it.type == AccountType.CASH && !it.archived }?.id ?: accountsNow.firstOrNull()?.id,
                    notifAccountMap = s.notifAccountMap.filterValues { it in ids },
                    cashCounts = s.cashCounts.filterKeys { it in ids },
                )
            }
            MmImportSummary(accCount, catCount, created, skipped, memos.size, repeats.size)
        }
    }

    /** 匯出成 Money Manager 的 .mmbak 格式（同樣的資料表結構）。 */
    suspend fun export(context: Context, repo: Repository, out: File) = withContext(Dispatchers.IO) {
        out.delete()
        val schema = context.assets.open("mmbak_schema.sql").bufferedReader().readText()
        val userVersion = Regex("user_version=(\\d+)").find(schema)?.groupValues?.get(1)?.toInt() ?: 19
        val db = repo.db
        val accounts = db.accounts().all()
        val categories = db.categories().all()
        val txns = db.txns().all().filter { it.status == TxnStatus.CONFIRMED }
        val fx: FxConverter = repo.fx.first()
        val base = repo.settings.value.baseCurrency
        val now = System.currentTimeMillis()

        val mm = SQLiteDatabase.openDatabase(out.path, null, SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        mm.use { w ->
            w.beginTransaction()
            try {
                schema.lineSequence().filter { !it.startsWith("--") }.joinToString("\n").split(";\n")
                    .map { it.trim() }.filter { it.isNotEmpty() }.forEach { w.execSQL(it) }
                w.execSQL("INSERT INTO android_metadata VALUES ('en_US')")
                listOf("Accounts" to 1, "Card" to 2, "Debit Card" to 3, "Savings" to 4, "Loan" to 5, "Top-Up/Prepaid" to 6, "Others" to 7,
                    "Investments" to 8, "Overdrafts" to 9, "Insurance" to 10, "Cash" to 11).forEachIndexed { i, (name, type) ->
                    w.execSQL("INSERT INTO ASSETGROUP (uid, IS_DEL, USETIME, ACC_GROUP_NAME, TYPE, ORDERSEQ) VALUES (?,0,?,?,?,?)",
                        arrayOf<Any>(type.toString(), now, name, type, if (type == 11) 1 else i + 2))
                }
                val usedCurrencies = (listOf(base) + accounts.map { it.currency } + txns.map { it.currency }).distinct()
                usedCurrencies.forEachIndexed { i, iso ->
                    val rate = if (iso == base) 1.0 else 1_000_000.0 / fx.convert(1_000_000, base, iso).coerceAtLeast(1)
                    w.execSQL(
                        "INSERT INTO CURRENCY (uid, NAME, ISO, MAIN_ISO, IS_DEL, ORDER_SEQ, RATE, SYMBOL, INSERT_TYPE, SYMBOL_POSITION, IS_MAIN_CURRENCY, IS_SHOW, MODIFY_DATE, DECIMAL_POINT) " +
                            "VALUES (?,?,?,?,0,?,?,?,'I','P',?,1,?,2)",
                        arrayOf<Any>("${base}_$iso", iso, iso, base, 100 + i, rate, Money.symbolOf(iso).trim(), if (iso == base) 1 else 0, now),
                    )
                }
                val accUid = accounts.associate { it.id to "mmapp-acc-${it.id}" }
                accounts.forEach { a ->
                    w.execSQL(
                        "INSERT INTO ASSETS (CARD_DAY_FIN, CARD_DAY_PAY, NIC_NAME, ORDERSEQ, ZDATA, ZDATA1, ZDATA2, IS_TRANS_EXPENSE, IS_CARD_AUTO_PAY, A_UTIME, uid, currencyUid, cardAssetUid, groupUid) " +
                            "VALUES (?,?,?,?,?,?,'0',0,0,?,?,?,?,?)",
                        arrayOf<Any>(
                            (a.statementDay ?: 1).toString(), (a.dueDay ?: 1).toString(), a.name, a.sortOrder, if (a.archived) "1" else "0",
                            a.description.orEmpty(), now, accUid.getValue(a.id), "${base}_${a.currency}",
                            a.autopayFromId?.let { accUid[it] }.orEmpty(), groupTypeOf(a.type).toString(),
                        ),
                    )
                }
                val catUid = categories.associate { it.id to "mmapp-ctg-${it.id}" }
                categories.forEach { c ->
                    w.execSQL(
                        "INSERT INTO ZCATEGORY (C_IS_DEL, C_UTIME, uid, NAME, ORDERSEQ, TYPE, STATUS, pUid) VALUES (?,?,?,?,?,?,?,?)",
                        arrayOf<Any>(if (c.archived) 1 else 0, now, catUid.getValue(c.id), c.name, c.sortOrder,
                            if (c.kind == TxnType.INCOME) 0 else 1, if (c.parentId != null) 2 else 0, c.parentId?.let { catUid[it] } ?: "0"),
                    )
                }
                fun insertTx(uid: String, asset: String?, toAsset: String?, ctg: String?, t: TxnEntity, doType: Int, amount: Long, pair: String?) {
                    val date = Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate()
                    w.execSQL(
                        "INSERT INTO INOUTCOME (uid, assetUid, CARDDIVIDMONTH, ctgUid, toAssetUid, ZCONTENT, ZDATE, WDATE, DO_TYPE, ZMONEY, txUidTrans, ZDATA, IN_ZMONEY, MARK, IS_DEL, UTIME, currencyUid, AMOUNT_ACCOUNT) " +
                            "VALUES (?,?,'0',?,?,?,?,?,?,?,?,?,?,0,0,?,?,?)",
                        arrayOf<Any>(
                            uid, asset.orEmpty(), ctg.orEmpty(), toAsset.orEmpty(), t.merchant.orEmpty(), t.date.toString(), date.toString(),
                            doType.toString(), Money.fromMinor(amount).toPlainString(), pair.orEmpty(), t.note.orEmpty(),
                            Money.fromMinor(amount).toPlainString(), now, "${base}_${t.currency}", Money.toDouble(amount),
                        ),
                    )
                }
                txns.forEach { t ->
                    val uid = t.externalKey?.removePrefix("MM:")?.takeIf { t.externalKey.startsWith("MM:") } ?: UUID.randomUUID().toString()
                    val asset = t.accountId?.let { accUid[it] }
                    val ctg = t.categoryId?.let { catUid[it] }
                    when (t.type) {
                        TxnType.INCOME -> insertTx(uid, asset, null, ctg, t, 0, t.amount, null)
                        TxnType.EXPENSE -> insertTx(uid, asset, null, ctg, t, 1, t.amount, null)
                        TxnType.TRANSFER -> {
                            val to = t.toAccountId?.let { accUid[it] }
                            val pair = UUID.randomUUID().toString()
                            insertTx(uid, asset, to, null, t, 3, t.amount, pair)
                            insertTx(UUID.randomUUID().toString(), to, asset, null, t, 4, t.toAmount ?: t.amount, pair)
                        }
                    }
                }
                repo.settings.value.memos.forEach { m ->
                    val d = Instant.ofEpochMilli(m.date).atZone(ZoneId.systemDefault()).toLocalDate()
                    val lines = m.text.lines()
                    w.execSQL(
                        "INSERT INTO MEMO (uid, title, content, memoTime, memoDate, pinned, orderSeq, isDel, utime) VALUES (?,?,?,?,?,0,0,0,?)",
                        arrayOf<Any>(UUID.randomUUID().toString(), lines.first(), lines.drop(1).joinToString("\n"), m.date.toDouble(), d.toString(), now.toDouble()),
                    )
                }
                w.setTransactionSuccessful()
            } finally {
                w.endTransaction()
            }
            w.version = userVersion
        }
    }
}
