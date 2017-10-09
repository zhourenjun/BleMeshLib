package com.telink.util

import java.io.UnsupportedEncodingException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException

import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException
import javax.crypto.spec.SecretKeySpec

object AES {
    var Security = true

    init {
        System.loadLibrary("TelinkCrypto")
    }

    @Throws(NoSuchAlgorithmException::class, NoSuchPaddingException::class, UnsupportedEncodingException::class, InvalidKeyException::class, IllegalBlockSizeException::class, BadPaddingException::class, NoSuchProviderException::class)
    fun encrypt(key: ByteArray?, content: ByteArray?): ByteArray? {
        var key = key
        var content = content
        if (!Security) return content
        key = Arrays.reverse(key)
        content = Arrays.reverse(content)
        val secretKeySpec = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
        return cipher.doFinal(content)
    }

    @Throws(IllegalBlockSizeException::class, BadPaddingException::class, NoSuchAlgorithmException::class, NoSuchPaddingException::class, InvalidKeyException::class, NoSuchProviderException::class)
    fun decrypt(key: ByteArray, content: ByteArray): ByteArray {

        if (!Security) return content
        val secretKeySpec = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec)
        return cipher.doFinal(content)
    }

    fun encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray) = if (!Security) plaintext else encryptCmd(plaintext, nonce, key)

    fun decrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray) = if (!Security) plaintext else decryptCmd(plaintext, nonce, key)

    private external fun encryptCmd(packet: ByteArray, iv: ByteArray, sk: ByteArray): ByteArray

    private external fun decryptCmd(packet: ByteArray, iv: ByteArray, sk: ByteArray): ByteArray
}