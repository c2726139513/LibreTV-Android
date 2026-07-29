package com.vidhub.android.util

import java.security.MessageDigest

/**
 * SHA-256 工具。与服务端约定一致：sha256(PASSWORD) 的十六进制小写字符串。
 */
object Sha256 {

    fun hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
