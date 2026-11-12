package com.enwei.moneymanager.core

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 備份檔加密：密碼經 PBKDF2（12 萬次）衍生金鑰，AES-256-GCM 加密（含完整性檢查，密碼錯或檔案被改都會失敗）。
 * 檔案格式：「LGBE1」＋16 位元組鹽＋12 位元組 IV＋密文。
 */
object BackupCrypto {
    private val MAGIC = "LGBE1".toByteArray()
    private const val ITERATIONS = 120_000

    fun isEncrypted(head: ByteArray): Boolean = head.size >= MAGIC.size && MAGIC.indices.all { head[it] == MAGIC[it] }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }

    /** 包一層加密輸出：寫入的內容會被加密；關閉時寫入驗證碼 */
    fun encrypt(out: OutputStream, password: CharArray): OutputStream {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        out.write(MAGIC); out.write(salt); out.write(iv)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv)) }
        return CipherOutputStream(out, c)
    }

    /** 讀取加密備份：[input] 從檔頭開始；密碼錯誤時讀取會丟出例外 */
    fun decrypt(input: InputStream, password: CharArray): InputStream {
        val magic = input.readNBytesCompat(MAGIC.size)
        require(isEncrypted(magic)) { "不是加密的備份檔" }
        val salt = input.readNBytesCompat(16)
        val iv = input.readNBytesCompat(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv)) }
        return CipherInputStream(input, c)
    }

    private fun InputStream.readNBytesCompat(n: Int): ByteArray {
        val b = ByteArray(n); var off = 0
        while (off < n) { val r = read(b, off, n - off); if (r < 0) break; off += r }
        require(off == n) { "檔案不完整" }
        return b
    }
}
