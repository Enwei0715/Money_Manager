package com.enwei.moneymanager.core

/**
 * 明文 http 只允許連到區域網路（家裡電腦的自架 AI 伺服器）；連到網際網路一律要 https。
 */
object NetGuard {
    /** 私有網段、本機與 .local 名稱 */
    fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase().trim('[', ']')
        if (h == "localhost" || h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".home.arpa")) return true
        if (h == "::1" || h.startsWith("fe80:") || h.startsWith("fc") || h.startsWith("fd")) return h.contains(':')
        val p = h.split('.').mapNotNull { it.toIntOrNull() }
        if (p.size != 4 || h.count { it == '.' } != 3) return false
        return p[0] == 10 || p[0] == 127 ||
            (p[0] == 172 && p[1] in 16..31) ||
            (p[0] == 192 && p[1] == 168) ||
            (p[0] == 169 && p[1] == 254) ||
            (p[0] == 100 && p[1] in 64..127) // Tailscale 等 CGNAT 網段
    }
}
