package com.anitech.growdaily.database

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

object SecurityUtils {
    private const val KEY_ALIAS = "growdaily_db_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val PREFS_NAME = "security_prefs"
    private const val ENCRYPTED_PASSPHRASE_KEY = "encrypted_passphrase"
    private const val IV_KEY = "passphrase_iv"

    fun getDatabasePassphrase(context: Context): ByteArray {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedPassphraseBase64 = prefs.getString(ENCRYPTED_PASSPHRASE_KEY, null)
        val ivBase64 = prefs.getString(IV_KEY, null)

        return if (encryptedPassphraseBase64 != null && ivBase64 != null) {
            try {
                val encryptedPassphrase = Base64.decode(encryptedPassphraseBase64, Base64.DEFAULT)
                val iv = Base64.decode(ivBase64, Base64.DEFAULT)
                decrypt(encryptedPassphrase, iv, keyStore)
            } catch (e: Exception) {
                generateAndSavePassphrase(prefs, keyStore)
            }
        } else {
            generateAndSavePassphrase(prefs, keyStore)
        }
    }

    private fun generateAndSavePassphrase(prefs: android.content.SharedPreferences, keyStore: KeyStore): ByteArray {
        val passphrase = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val (encrypted, iv) = encrypt(passphrase, keyStore)
        
        prefs.edit()
            .putString(ENCRYPTED_PASSPHRASE_KEY, Base64.encodeToString(encrypted, Base64.DEFAULT))
            .putString(IV_KEY, Base64.encodeToString(iv, Base64.DEFAULT))
            .apply()
        return passphrase
    }

    private fun encrypt(data: ByteArray, keyStore: KeyStore): Pair<ByteArray, ByteArray> {
        val secretKey = (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return Pair(cipher.doFinal(data), cipher.iv)
    }

    private fun decrypt(data: ByteArray, iv: ByteArray, keyStore: KeyStore): ByteArray {
        val secretKey = (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(data)
    }
}
