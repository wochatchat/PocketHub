package com.pockethub.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts credentials at rest (issue #29) with an AES-256/GCM key that lives
 * in Android Keystore — the key material never leaves the TEE/strongbox and
 * is not extractable, so a leaked pockethub.db / DataStore file alone yields
 * no usable tokens.
 *
 * Sealed values carry an "enc:v1:" marker so legacy plaintext (rows written
 * by older builds) stays readable and is migrated lazily by the repositories
 * on their next read/write.
 */
object TokenCipher {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "pockethub_credentials"
    private const val PREFIX = "enc:v1:"
    private const val GCM_TAG_BITS = 128

    fun isSealed(value: String): Boolean = value.startsWith(PREFIX)

    /** Seal plaintext into "enc:v1:..." form. Empty input passes through. */
    fun seal(plain: String): String {
        if (plain.isEmpty()) return plain
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX +
                Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(ct, Base64.NO_WRAP)
        } catch (_: Exception) {
            // Keystore unavailable (hostile OEM / JVM unit test): storing
            // plaintext keeps the login flow alive; the value is re-sealed on
            // the next successful write.
            plain
        }
    }

    /** Open a sealed value. Anything without the marker is returned as-is. */
    fun open(stored: String): String {
        if (!isSealed(stored)) return stored
        return try {
            val parts = stored.removePrefix(PREFIX).split(":")
            if (parts.size != 2) return ""
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(parts[0], Base64.NO_WRAP)),
            )
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            // Sealed but unreadable (key wiped by uninstall/restore): treat as
            // no credential — the 401/sign-out path takes over from there.
            ""
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
