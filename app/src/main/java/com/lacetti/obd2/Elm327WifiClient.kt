package com.lacetti.obd2

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

class Elm327WifiClient(
    private val host: String = "192.168.0.10",
    private val port: Int = 35000
) {
    private var socket: Socket? = null
    private var writer: OutputStreamWriter? = null
    private var reader: BufferedReader? = null

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            disconnect()
            socket = Socket().apply {
                connect(InetSocketAddress(host, port), 3000)
                soTimeout = 3500
            }
            writer = OutputStreamWriter(socket!!.getOutputStream())
            reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))

            sendCommand("ATZ")
            delay(500)
            sendCommand("ATE0")
            sendCommand("ATL0")
            sendCommand("ATS0")
            sendCommand("ATSP5")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun sendCommand(cmd: String): String = withContext(Dispatchers.IO) {
        val w = writer ?: return@withContext "ERROR: Нет подключения"
        val r = reader ?: return@withContext "ERROR: Нет подключения"

        w.write("$cmd\r")
        w.flush()

        val response = StringBuilder()
        var ch: Int
        while (r.read().also { ch = it } != -1) {
            val c = ch.toChar()
            if (c == '>') break
            response.append(c)
        }
        response.toString().trim()
    }

    private suspend fun switchEcu(header: String) {
        sendCommand("ATPC")
        delay(200)
        sendCommand("ATSH $header")
    }

    suspend fun readEngineErrors(): String {
        switchEcu("81 11 F1")
        val raw = sendCommand("03")
        return parseEngineDtc(raw)
    }

    suspend fun clearEngineErrors(): String {
        switchEcu("81 11 F1")
        val raw = sendCommand("04")
        return if (raw.contains("44")) "Ошибки двигателя сброшены" else "Ответ ЭБУ: $raw"
    }

    suspend fun readAbsErrors(): String {
        switchEcu("81 28 F1")
        val raw = sendCommand("1800FF00")
        return parseAbsDtc(raw)
    }

    suspend fun clearAbsErrors(): String {
        switchEcu("81 28 F1")
        val raw = sendCommand("14FF00")
        return if (raw.contains("54")) "Ошибки ABS сброшены" else "Ответ ABS: $raw"
    }

    private fun parseEngineDtc(raw: String): String {
        val clean = cleanHex(raw)
        if (clean.contains("NODATA") || clean.contains("ERROR")) return "Нет связи с ДВС ($raw)"
        val idx = clean.indexOf("43")
        if (idx == -1 || clean.length < idx + 6) return "Ошибок ДВС нет (ответ: $clean)"

        val data = clean.substring(idx + 2)
        val codes = mutableListOf<String>()
        for (i in data.indices step 4) {
            if (i + 4 > data.length) break
            val hex = data.substring(i, i + 4)
            if (hex != "0000") codes.add(decodeDtc(hex))
        }
        return if (codes.isEmpty()) "Ошибок двигателя не найдено" else "ДВС: ${codes.joinToString(", ")}"
    }

    private fun parseAbsDtc(raw: String): String {
        val clean = cleanHex(raw)
        if (clean.contains("NODATA") || clean.contains("ERROR")) return "Нет связи с ABS ($raw)"
        val idx = clean.indexOf("58")
        if (idx == -1 || clean.length < idx + 4) return "Ответ ABS: $clean"

        val count = clean.substring(idx + 2, idx + 4).toIntOrNull(16) ?: 0
        if (count == 0) return "Ошибок ABS не найдено"

        val data = clean.substring(idx + 4)
        val codes = mutableListOf<String>()
        for (i in 0 until count) {
            val start = i * 6
            if (start + 4 > data.length) break
            val hex = data.substring(start, start + 4)
            if (hex != "0000") codes.add(decodeDtc(hex))
        }
        return if (codes.isEmpty()) "Ошибок ABS не найдено" else "ABS: ${codes.joinToString(", ")}"
    }

    private fun cleanHex(raw: String) =
        raw.replace(" ", "").replace("\r", "").replace("\n", "").uppercase()

    private fun decodeDtc(hex: String): String {
        val prefix = when (hex[0]) {
            '0' -> "P0"; '1' -> "P1"; '2' -> "P2"; '3' -> "P3"
            '4' -> "C0"; '5' -> "C1"; '6' -> "C2"; '7' -> "C3"
            '8' -> "B0"; '9' -> "B1"; 'A' -> "B2"; 'B' -> "B3"
            'C' -> "U0"; 'D' -> "U1"; 'E' -> "U2"; 'F' -> "U3"
            else -> "P0"
        }
        return prefix + hex.substring(1)
    }

    fun disconnect() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }
}
