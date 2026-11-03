package org.phalanx.android.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * A short code kept only as a slow salted hash (PBKDF2-HMAC-SHA256), so a copy of the file does
 * not give it away by trying every code quickly. The stored form is the salt followed by the hash.
 */
internal object CodeHash {
    /** Digits only, as the lock screen's keypad types them, and long enough not to be guessed in a few tries. */
    fun isValid(code: String): Boolean = code.length in MIN_LENGTH..MAX_LENGTH && code.all { it in '0'..'9' }

    fun create(code: String): ByteArray {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        return salt + derive(code, salt)
    }

    /** Compares in constant time, so how long a wrong code takes says nothing about the right one. */
    fun matches(code: String, stored: ByteArray): Boolean {
        if (stored.size != SALT_BYTES + HASH_BYTES) return false
        val salt = stored.copyOfRange(0, SALT_BYTES)
        return MessageDigest.isEqual(derive(code, salt), stored.copyOfRange(SALT_BYTES, stored.size))
    }

    private fun derive(code: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(code.toCharArray(), salt, ITERATIONS, HASH_BYTES * Byte.SIZE_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 16
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val HASH_BYTES = 32
}
