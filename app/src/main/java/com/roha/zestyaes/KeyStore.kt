package com.roha.zestyaes

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KeyStore {
    private val file = File("/sdcard/Download/zesty_keys.txt")

    fun append(type: String, hex: String, str: String) {
        try {
            file.parentFile?.mkdirs()
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            file.appendText("[$ts] $type\n  HEX: $hex\n  STR: $str\n\n")
        } catch (_: Exception) { }
    }

    fun readAll(): String {
        return try {
            if (file.exists()) file.readText() else ""
        } catch (_: Exception) {
            ""
        }
    }

    fun clear() {
        try {
            if (file.exists()) file.writeText("")
        } catch (_: Exception) { }
    }
}
