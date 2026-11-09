package com.typedefai.cryptowl.crypto

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

fun String.hexToBytes(): ByteArray =
    chunked(2).map { it.toInt(16).toByte() }.toByteArray()
