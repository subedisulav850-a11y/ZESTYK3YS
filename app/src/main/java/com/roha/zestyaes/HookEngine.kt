package com.roha.zestyaes

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object HookEngine {

    var targetPackage: String = "com.dts.freefireadv"
    var targetName: String = "FF Advance"

    private val SERVER_PATHS = arrayOf(
        "/data/local/tmp/frida-server",
        "/data/local/tmp/fs",
        "/data/adb/frida-server"
    )

    fun suOk(): Boolean {
        return try {
            su("id").contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }

    fun requestRoot(): Boolean {
        val out = su("id")
        if (!out.contains("uid=0")) return false
        su("mkdir -p /data/local/tmp && chmod 777 /data/local/tmp")
        // try start any existing server
        for (p in SERVER_PATHS) {
            val exists = su("test -f $p && echo YES || echo NO")
            if (exists.contains("YES")) {
                su("pkill -f frida-server 2>/dev/null || true")
                su("chmod 755 $p")
                su("$p -D &")
                Thread.sleep(1000)
                break
            }
        }
        return true
    }

    fun run(
        onKey: (String, String, String) -> Unit,
        onStatus: (String) -> Unit
    ) {
        onStatus("Writing hook...")
        val script = File("/data/local/tmp/zesty_hook.js")
        // write via su so it lands in accessible place
        val escaped = HookScript.content.replace("'", "'\\''")
        su("cat > /data/local/tmp/zesty_hook.js << 'ZEOF'\n${HookScript.content}\nZEOF")
        su("chmod 644 /data/local/tmp/zesty_hook.js")

        // launch target if not running
        onStatus("Launching $targetName...")
        su("monkey -p $targetPackage -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true")
        Thread.sleep(1200)

        // try frida first
        val fridaBins = listOf("frida", "/data/local/tmp/frida", "/system/bin/frida")
        var used = false
        for (bin in fridaBins) {
            val has = su("command -v $bin >/dev/null 2>&1 && echo YES || echo NO")
            if (has.contains("YES")) {
                onStatus("Frida attach → $targetPackage")
                used = true
                attachFrida(bin, onKey, onStatus)
                break
            }
        }

        if (!used) {
            onStatus("No frida binary. Logcat fallback active")
            fallbackLogcat(onKey, onStatus)
        }
    }

    private fun attachFrida(
        bin: String,
        onKey: (String, String, String) -> Unit,
        onStatus: (String) -> Unit
    ) {
        val cmd = "$bin -U -n $targetPackage -l /data/local/tmp/zesty_hook.js --runtime=v8 2>&1"
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                parse(l, onKey)
                if (l.contains("AES KEY FOUND") || l.contains("AES IV FOUND") || l.contains("NATIVE")) {
                    onStatus("Key material hit")
                }
            }
            p.waitFor(40, TimeUnit.MINUTES)
        } catch (e: Exception) {
            onStatus("Attach error: ${e.message}")
        }
    }

    private fun fallbackLogcat(
        onKey: (String, String, String) -> Unit,
        onStatus: (String) -> Unit
    ) {
        su("logcat -c")
        val cmd = "logcat -v brief 2>/dev/null"
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            var line: String?
            var n = 0
            while (reader.readLine().also { line = it } != null && n < 8000) {
                n++
                val l = line ?: continue
                if (l.contains("aes", true) || l.contains("cipher", true) ||
                    l.contains("secret", true) || l.contains("encrypt", true)
                ) {
                    val m = Regex("[0-9a-fA-F]{32,64}").find(l)
                    if (m != null) {
                        onKey("HEURISTIC", m.value.lowercase(), "")
                        onStatus("Possible key from log")
                    }
                }
            }
        } catch (e: Exception) {
            onStatus("Fallback error: ${e.message}")
        }
    }

    private fun parse(line: String, onKey: (String, String, String) -> Unit) {
        when {
            line.contains("HEX :") -> {
                val hex = line.substringAfter("HEX :").trim()
                    .substringBefore("║").trim().lowercase()
                if (hex.length >= 16) {
                    val type = if (line.contains("IV")) "IV" else "KEY"
                    onKey(type, hex, "")
                }
            }
            line.contains("[NATIVE KEY]") -> {
                val hex = line.substringAfter("[NATIVE KEY]").trim().removePrefix(":").trim()
                if (hex.isNotEmpty()) onKey("NATIVE_KEY", hex, "")
            }
            line.contains("[NATIVE IV]") -> {
                val hex = line.substringAfter("[NATIVE IV]").trim().removePrefix(":").trim()
                if (hex.isNotEmpty()) onKey("NATIVE_IV", hex, "")
            }
        }
    }

    fun su(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$cmd\n")
            os.writeBytes("exit\n")
            os.flush()
            val sb = StringBuilder()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.appendLine(line)
            }
            process.waitFor(6, TimeUnit.SECONDS)
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }
}
