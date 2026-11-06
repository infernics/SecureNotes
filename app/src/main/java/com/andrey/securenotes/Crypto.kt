package com.andrey.securenotes

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Шифрование AES-256-GCM.
 *
 * - Ключ выводится из пароля пользователя через PBKDF2-HMAC-SHA256
 *   (250 000 итераций, 16-байтная случайная соль).
 * - Для каждой шифровки генерируется свежий 12-байтный IV.
 * - GCM даёт не только шифрование, но и проверку целостности
 *   (тег 128 бит): подделать или «тихо» изменить данные нельзя.
 */
class Crypto private constructor(private val key: SecretKey) {

    fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_LEN).also { SECURE_RANDOM.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return iv + cipher.doFinal(plain)
    }

    fun decrypt(blob: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(TAG_BITS, blob.copyOfRange(0, IV_LEN))
        )
        return cipher.doFinal(blob, IV_LEN, blob.size - IV_LEN)
    }

    fun encrypt(text: String): ByteArray = encrypt(text.toByteArray(Charsets.UTF_8))

    fun decryptToText(blob: ByteArray): String = String(decrypt(blob), Charsets.UTF_8)

    companion object {
        private val SECURE_RANDOM = SecureRandom()

        private const val ITERATIONS = 250_000
        private const val KEY_BITS = 256
        private const val SALT_LEN = 16
        private const val IV_LEN = 12
        private const val TAG_BITS = 128

        fun newSalt(): ByteArray = ByteArray(SALT_LEN).also { SECURE_RANDOM.nextBytes(it) }

        fun fromPassphrase(passphrase: String, salt: ByteArray): Crypto {
            val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
            try {
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val keyBytes = factory.generateSecret(spec).encoded
                return Crypto(SecretKeySpec(keyBytes, "AES"))
            } finally {
                spec.clearPassword()
            }
        }
    }
}
