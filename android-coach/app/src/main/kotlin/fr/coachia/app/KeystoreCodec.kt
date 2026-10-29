package fr.coachia.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import fr.coachia.core.Codec
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Chiffre les données de l'appli (clé API, jetons Strava, santé) avec une clé AES du Keystore Android. */
class KeystoreCodec(private val alias: String = "coachia-data") : Codec {
    private val key: SecretKey by lazy {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
            }
            .generateKey()
    }

    override fun encode(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val enc = cipher.doFinal(plain)
        return ByteBuffer.allocate(1 + iv.size + enc.size).put(iv.size.toByte()).put(iv).put(enc).array()
    }

    override fun decode(stored: ByteArray): ByteArray {
        val buf = ByteBuffer.wrap(stored)
        val iv = ByteArray(buf.get().toInt()).also { buf.get(it) }
        val enc = ByteArray(buf.remaining()).also { buf.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(enc)
    }
}
