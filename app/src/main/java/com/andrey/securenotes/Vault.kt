package com.andrey.securenotes

import android.content.Context
import java.io.File

/**
 * Хранилище ключевых материалов (НЕ сам ключ — только соль и проверочный блоб).
 *
 * - Соль для PBKDF2 сохраняется в файле key.salt.
 * - key.check — небольшой блоб, зашифрованный ключом при первом запуске.
 *   Если расшифровать его не удаётся, пароль неверный.
 * - Сам пароль и производный ключ НИКОГДА не сохраняются на диск.
 */
object Vault {

    private fun saltFile(context: Context) = File(context.filesDir, "key.salt")
    private fun checkFile(context: Context) = File(context.filesDir, "key.check")

    fun isSetup(context: Context): Boolean = saltFile(context).exists()

    fun create(context: Context, passphrase: String): Crypto {
        val salt = Crypto.newSalt()
        val crypto = Crypto.fromPassphrase(passphrase, salt)
        saltFile(context).writeBytes(salt)
        checkFile(context).writeBytes(crypto.encrypt("verify"))
        return crypto
    }

    fun unlock(context: Context, passphrase: String): Crypto? {
        if (!isSetup(context)) return null
        val crypto = Crypto.fromPassphrase(passphrase, saltFile(context).readBytes())
        return try {
            crypto.decryptToText(checkFile(context).readBytes())
            crypto
        } catch (e: Exception) {
            null
        }
    }
}
