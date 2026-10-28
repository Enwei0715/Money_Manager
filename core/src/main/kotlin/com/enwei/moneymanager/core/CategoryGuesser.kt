package com.enwei.moneymanager.core

/** 依商家名稱推測分類。使用者確認過的商家規則優先於內建關鍵字。 */
object CategoryGuesser {
    val builtIn: Map<String, List<String>> = linkedMapOf(
        "餐飲" to listOf("餐", "食", "飯", "麵", "咖啡", "星巴克", "STARBUCKS", "路易莎", "麥當勞", "肯德基", "摩斯", "漢堡", "早餐",
            "Uber Eats", "UBER EATS", "foodpanda", "FOODPANDA", "飲", "茶", "50嵐", "可不可", "清心", "鍋", "壽司", "拉麵", "便當", "小吃", "烘焙", "麵包"),
        "日用品" to listOf("全聯", "家樂福", "大潤發", "好市多", "COSTCO", "屈臣氏", "康是美", "寶雅", "IKEA", "愛買", "美廉社", "日藥本舖"),
        "便利商店" to listOf("7-ELEVEN", "7-11", "統一超商", "全家", "FamilyMart", "萊爾富", "OK超商", "OKmart"),
        "交通" to listOf("捷運", "台鐵", "臺鐵", "高鐵", "Uber", "UBER", "計程車", "台灣大車隊", "加油", "中油", "台塑", "停車", "悠遊卡", "一卡通", "iPASS", "ETC", "遠通", "公車", "客運", "YouBike"),
        "購物" to listOf("蝦皮", "Shopee", "momo", "MOMO", "PChome", "博客來", "Amazon", "AMAZON", "淘寶", "Yahoo購物", "uniqlo", "UNIQLO", "NET", "誠品", "百貨", "SOGO", "新光三越", "遠東"),
        "娛樂" to listOf("Netflix", "NETFLIX", "Spotify", "SPOTIFY", "YouTube", "Disney", "KKBOX", "Steam", "STEAM", "PlayStation", "Nintendo", "電影", "威秀", "秀泰", "KTV", "錢櫃", "好樂迪"),
        "電信網路" to listOf("中華電信", "台灣大哥大", "遠傳", "台灣之星", "亞太電信", "Apple.com", "APPLE.COM", "Google", "GOOGLE", "iCloud"),
        "居家" to listOf("台電", "電費", "水費", "自來水", "瓦斯", "天然氣", "管理費", "房租"),
        "醫療" to listOf("醫院", "診所", "藥局", "牙醫", "健保"),
        "保險" to listOf("保險", "人壽", "產險"),
        "教育" to listOf("書局", "補習", "課程", "Hahow", "Udemy", "學費"),
    )

    fun guess(merchant: String?, userRules: Map<String, String> = emptyMap()): String? {
        if (merchant.isNullOrBlank()) return null
        userRules.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { merchant.contains(it.key, ignoreCase = true) }
            ?.let { return it.value }
        return builtIn.entries.firstOrNull { (_, keys) -> keys.any { merchant.contains(it, ignoreCase = true) } }?.key
    }
}
