package com.enwei.moneymanager.core

import java.text.Normalizer
import java.time.LocalDateTime

/**
 * 自動分類：依序參考
 * 1. 使用者確認過的商家規則（最後一次選的分類）
 * 2. 過去的記帳紀錄（同一商家最常用的分類，含從 Money Manager 匯入的資料）
 * 3. 內建關鍵字（對應到使用者自己的分類名稱，例如「餐飲／食物／Food」都算吃的）
 * 4. 沒有商家名稱時：同一帳戶、同金額的固定支出（訂閱、月費）
 * 選到餐點類（早餐／午餐／晚餐／宵夜）的子分類時，依交易時間換成對應的那一餐。
 */
object CategorySuggester {
    data class Cat(val id: Long, val name: String, val parentId: Long?)

    /** 過去的一筆紀錄：[text] 為商家（沒有則用備註）。 */
    data class Past(val text: String?, val categoryId: Long, val at: LocalDateTime, val amountMinor: Long, val accountId: Long?)

    data class Suggestion(val categoryId: Long, val reason: String)

    fun suggest(
        merchant: String?,
        at: LocalDateTime,
        cats: List<Cat>,
        rules: Map<String, Long> = emptyMap(),
        history: List<Past> = emptyList(),
        amountMinor: Long = 0,
        accountId: Long? = null,
    ): Suggestion? {
        val byId = cats.associateBy { it.id }
        val m = merchant?.takeIf { it.isNotBlank() && !CategoryGuesser.isGenericMerchant(it) }
        if (m != null) {
            val key = canonical(m)
            // 1. 確認過的商家規則
            rules.entries.filter { it.value in byId && key.isNotEmpty() && matches(key, canonical(it.key)) }
                .maxByOrNull { it.key.length }
                ?.let { return Suggestion(meal(byId.getValue(it.value), at, cats).id, "依你之前對「${it.key}」選的分類") }
            // 2. 過去同一商家的紀錄（近期的權重較高）
            val votes = HashMap<Long, Double>()
            history.forEach { p ->
                val t = p.text?.let(::canonical)?.takeIf { it.isNotEmpty() } ?: return@forEach
                if (p.categoryId !in byId || !matches(key, t)) return@forEach
                val ageDays = java.time.Duration.between(p.at, at).toDays().coerceAtLeast(0)
                // 餐點子分類先合併到大分類計票，最後再依時間挑那一餐
                val c = byId.getValue(p.categoryId)
                val id = if (mealSlot(c.name) != null && c.parentId != null) c.parentId else c.id
                // 旅遊時記的（例如出國吃的麥當勞）只當參考：平常同一家店不會因此被分到旅遊
                val w = if (isTravel(c, byId)) 0.25 else 1.0
                votes[id] = (votes[id] ?: 0.0) + w * (1.0 + (if (ageDays < 180) 1.0 else 0.0))
            }
            votes.maxByOrNull { it.value }?.let { (id, _) ->
                byId[id]?.let { return Suggestion(meal(it, at, cats).id, "依過去「$m」的記帳紀錄") }
            }
            // 3. 內建關鍵字（英文字要是完整的字：Sketch 不算 etc；中文單字有排除詞：飯店不算飯、鍋爐不算鍋）
            val raw = Normalizer.normalize(m, Normalizer.Form.NFKC).lowercase()
            for (g in groups) {
                if (g.keywords.none { keywordHit(it, key, raw) }) continue
                val c = findCat(g.targets, cats) ?: continue
                return Suggestion(if (g.meal) meal(c, at, cats).id else c.id, "依商家名稱推測")
            }
        }
        // 4. 沒有商家：同帳戶、同金額的固定支出
        if (amountMinor > 0) {
            val same = history.filter {
                it.amountMinor == amountMinor && it.accountId == accountId && it.categoryId in byId &&
                    java.time.Duration.between(it.at, at).toDays() in 0..400
            }
            if (same.size >= 2) {
                val top = same.groupingBy { it.categoryId }.eachCount().maxByOrNull { it.value }!!
                if (top.value >= 2 && top.value * 3 >= same.size * 2) return Suggestion(top.key, "同金額的固定支出")
            }
        }
        return null
    }

    // ---------- 商家名稱比對 ----------

    /** 含這些字時，單字關鍵字（飯、麵、鍋、餃…）不算吃的 */
    private val notFood = listOf("飯店", "酒店", "旅店", "旅館", "民宿", "鍋爐", "飯盒工廠", "電鍋")

    private fun keywordHit(kw: String, key: String, raw: String): Boolean {
        val k = canonical(kw).takeIf { it.isNotEmpty() } ?: return false
        if (kw.all { it.code < 128 } && kw.any { it.isLetter() }) {
            // 英文：前後不能接英文字母
            return Regex("(?<![a-z])" + Regex.escape(kw.lowercase()) + "(?![a-z])").containsMatchIn(raw)
        }
        if (k.length == 1 && notFood.any { key.contains(it) }) return false
        return key.contains(k)
    }

    private val aliases = listOf(
        "711" to listOf("7-eleven", "7eleven", "7-11", "711", "統一超商", "seven-eleven", "seveneleven"),
        "全家" to listOf("全家", "familymart", "family mart"),
        "萊爾富" to listOf("萊爾富", "hilife"),
        "星巴克" to listOf("星巴克", "starbucks"),
        "麥當勞" to listOf("麥當勞", "mcdonald"),
        "肯德基" to listOf("肯德基", "kfc"),
        "ubereats" to listOf("uber eats", "ubereats"),
        "foodpanda" to listOf("foodpanda", "熊貓"),
        "全聯" to listOf("全聯", "pxmart"),
        "好市多" to listOf("好市多", "costco"),
        "蝦皮" to listOf("蝦皮", "shopee"),
    )

    /** 正規化：全形轉半形、小寫、去掉空白標點與「門市／分店」等字尾；常見連鎖店換成同一個名稱。 */
    fun canonical(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()
        val compact = n.replace(Regex("[\\s]"), "")
        aliases.forEach { (c, list) ->
            if (list.any { a -> val ac = a.replace(" ", ""); compact.startsWith(ac) && !(c == "全家" && compact.startsWith("全家福")) }) return c
        }
        return n.replace(Regex("[\\s\\p{Punct}，。、・·（）()【】「」]"), "")
            .replace(Regex("(股份有限公司|有限公司|門市|分店|分公司|直營店)$"), "")
    }

    private fun matches(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val (short, long) = if (a.length <= b.length) a to b else b to a
        return short.length >= 3 && long.contains(short)
    }

    // ---------- 分類名稱對應 ----------

    private class Group(val keywords: List<String>, val targets: List<String>, val meal: Boolean = false)

    private val food = listOf("餐飲", "食物", "飲食", "美食", "餐費", "伙食", "food", "飲食費")

    private val groups = listOf(
        Group(listOf("netflix", "spotify", "youtube", "disney", "kkbox", "apple.com", "icloud", "google", "chatgpt", "openai", "anthropic", "claude", "notion", "adobe", "firstory", "soundon", "patreon", "pressplay"),
            listOf("訂閱", "數位", "會員", "娛樂", "entertainment")),
        Group(listOf("咖啡", "星巴克", "路易莎", "cama", "85度c", "50嵐", "可不可", "清心", "迷客夏", "茶湯會", "五十嵐", "麻古", "鶴茶樓", "得正", "coco都可", "飲料", "手搖"),
            listOf("飲料", "咖啡", "手搖", "drinks", "drink", "coffee") + food),
        Group(listOf("飯店", "酒店", "旅館", "旅店", "民宿", "hotel", "hostel", "motel", "inn", "airbnb"), listOf("住宿", "旅館", "travel", "旅遊")),
        Group(listOf("麵包", "烘焙", "甜點", "蛋糕", "點心"), listOf("點心", "甜點", "零食", "下午茶", "snack") + food),
        Group(listOf("711", "全家", "萊爾富", "ok超商", "okmart"), listOf("便利商店", "超商") + food, meal = true),
        Group(listOf("ubereats", "foodpanda", "麥當勞", "肯德基", "摩斯", "漢堡王", "必勝客", "達美樂", "拿坡里", "八方雲集", "四海遊龍", "鬍鬚張", "爭鮮", "壽司郎", "藏壽司", "王品", "瓦城", "鼎泰豐",
            "餐廳", "餐館", "小吃", "便當", "麵", "飯", "鍋", "燒肉", "拉麵", "早餐", "早午餐", "美食", "牛排", "滷味", "鹽酥雞", "自助餐", "食堂",
            "包子", "饅頭", "水煎包", "蛋餅", "粥", "餃", "燒臘", "鐵板燒", "臭豆腐", "麻辣", "赤肉羹", "肉圓", "蚵仔煎", "壽司", "丼"),
            food, meal = true),
        Group(listOf("全聯", "家樂福", "carrefour", "大潤發", "好市多", "愛買", "美廉社", "超市", "市場", "楓康", "頂好", "jasons"),
            listOf("市場", "超市", "菜", "食材", "日用品", "生活用品", "購物")),
        Group(listOf("屈臣氏", "watsons", "康是美", "寶雅", "日藥本舖", "松本清", "大樹藥局", "杏一"), listOf("日用品", "生活用品", "美妝", "保養", "購物")),
        Group(listOf("捷運", "metro", "mrt"), listOf("捷運", "大眾運輸", "交通", "transport")),
        Group(listOf("台鐵", "臺鐵", "火車", "高鐵", "thsr"), listOf("火車", "高鐵", "台鐵", "大眾運輸", "交通", "transport")),
        Group(listOf("公車", "客運", "國光", "統聯"), listOf("公車", "客運", "大眾運輸", "交通", "transport")),
        Group(listOf("uber", "計程車", "taxi", "大車隊", "yoxi", "linego"), listOf("計程車", "taxi", "交通", "transport")),
        Group(listOf("youbike", "微笑單車"), listOf("youbike", "腳踏車", "大眾運輸", "交通", "transport")),
        Group(listOf("中油", "台塑", "全國加油", "加油", "cpc"), listOf("加油", "油錢", "油資", "汽油", "交通", "transport")),
        Group(listOf("停車", "parking", "etc", "遠通"), listOf("停車", "過路費", "交通", "transport")),
        Group(listOf("悠遊卡", "一卡通", "ipass", "easycard"), listOf("儲值", "大眾運輸", "交通", "transport")),
        Group(listOf("uniqlo", "zara", "h&m", "lativ", "nike", "adidas"), listOf("服飾", "衣服", "服裝", "治裝", "購物", "shopping")),
        Group(listOf("蝦皮", "momo", "pchome", "yahoo購物", "amazon", "淘寶", "露天", "coupang", "酷澎"), listOf("網購", "購物", "shopping")),
        Group(listOf("百貨", "sogo", "新光三越", "遠百", "微風", "誠品", "ikea", "特力屋", "無印良品"), listOf("購物", "shopping", "居家")),
        Group(listOf("steam", "playstation", "nintendo", "xbox", "遊戲"), listOf("遊戲", "game", "娛樂", "entertainment")),
        Group(listOf("威秀", "秀泰", "國賓", "電影", "影城"), listOf("電影", "娛樂", "entertainment")),
        Group(listOf("ktv", "錢櫃", "好樂迪"), listOf("唱歌", "ktv", "娛樂", "entertainment")),
        Group(listOf("中華電信", "台灣大哥大", "遠傳", "台灣之星", "亞太電信"), listOf("電信", "手機", "通訊", "電話", "網路", "居家")),
        Group(listOf("台電", "電費"), listOf("電費", "水電", "居家", "生活")),
        Group(listOf("自來水", "水費"), listOf("水費", "水電", "居家", "生活")),
        Group(listOf("瓦斯", "天然氣"), listOf("瓦斯", "水電", "居家", "生活")),
        Group(listOf("房租", "管理費"), listOf("房租", "管理費", "居住", "居家")),
        Group(listOf("醫院", "診所", "牙醫", "藥局", "藥妝", "健保"), listOf("醫療", "看病", "藥", "健康", "health")),
        Group(listOf("保險", "人壽", "產險"), listOf("保險", "insurance")),
        Group(listOf("書局", "博客來", "金石堂", "補習", "課程", "hahow", "udemy", "學費"), listOf("教育", "書", "學習", "進修", "education")),
        Group(listOf("寵物", "動物醫院"), listOf("寵物", "pet")),
    )

    private val travelWords = listOf("旅遊", "旅行", "出國", "出差", "travel", "trip")

    /** 旅遊、出差這類「情境」分類（本身或上層分類名稱有旅遊等字） */
    private fun isTravel(c: Cat, byId: Map<Long, Cat>): Boolean {
        val names = listOfNotNull(c.name, c.parentId?.let { byId[it]?.name })
        return names.any { n -> travelWords.any { n.lowercase().contains(it) } }
    }

    /**
     * 依候選名稱順序找使用者的分類。先找名稱完全相同的（所有候選都找過一輪），
     * 找不到才用「包含」比對，且不會落到旅遊、出差這類情境分類
     * （例如候選「餐飲」不能對到「旅遊餐飲」，應該用「飲食」）。
     */
    private fun findCat(targets: List<String>, cats: List<Cat>): Cat? {
        fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase().replace(Regex("\\s"), "")
        val byId = cats.associateBy { it.id }
        val wantTravel = targets.any { t -> travelWords.any { norm(t).contains(it) } }
        val usable = if (wantTravel) cats else cats.filter { !isTravel(it, byId) }
        for (t in targets) {
            val tn = norm(t)
            val exact = usable.filter { norm(it.name) == tn }
            (exact.firstOrNull { it.parentId != null } ?: exact.firstOrNull())?.let { return it }
        }
        for (t in targets) {
            val tn = norm(t)
            if (tn.length < 2) continue
            usable.firstOrNull { norm(it.name).contains(tn) }?.let { return it }
        }
        return null
    }

    // ---------- 依時間選餐點 ----------

    private enum class Meal { BREAKFAST, BRUNCH, LUNCH, TEA, DINNER, LATE }

    private fun mealSlot(name: String): Meal? {
        val n = name.lowercase()
        return when {
            "早午餐" in n || "brunch" in n -> Meal.BRUNCH
            "早餐" in n || "breakfast" in n -> Meal.BREAKFAST
            "午餐" in n || "lunch" in n -> Meal.LUNCH
            "下午茶" in n || "afternoon" in n -> Meal.TEA
            "晚餐" in n || "dinner" in n || "supper" in n -> Meal.DINNER
            "宵夜" in n || "消夜" in n || "midnight" in n -> Meal.LATE
            else -> null
        }
    }

    /** 這個時間該算哪一餐（依序找使用者有的子分類）：早午餐只在上午 9～11 點，下午不會被分成早午餐 */
    private fun slotAt(at: LocalDateTime): List<Meal> = when (at.hour) {
        in 4..8 -> listOf(Meal.BREAKFAST, Meal.BRUNCH, Meal.LUNCH)
        in 9..10 -> listOf(Meal.BRUNCH, Meal.BREAKFAST, Meal.LUNCH)
        in 11..14 -> listOf(Meal.LUNCH, Meal.BRUNCH)
        in 15..16 -> listOf(Meal.TEA, Meal.LUNCH, Meal.DINNER)
        in 17..21 -> listOf(Meal.DINNER)
        else -> listOf(Meal.LATE, Meal.DINNER)
    }

    /** [c] 是餐點子分類或底下有餐點子分類的大分類時，換成交易時間對應的那一餐（找不到就維持原分類）。 */
    private fun meal(c: Cat, at: LocalDateTime, cats: List<Cat>): Cat {
        val parentId = if (mealSlot(c.name) != null && c.parentId != null) c.parentId else c.id
        val subs = cats.filter { it.parentId == parentId }.mapNotNull { s -> mealSlot(s.name)?.let { it to s } }
        if (subs.isEmpty()) return c
        for (slot in slotAt(at)) subs.firstOrNull { it.first == slot }?.let { return it.second }
        return c
    }
}
