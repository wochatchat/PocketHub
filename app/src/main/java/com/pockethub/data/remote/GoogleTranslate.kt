package com.pockethub.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Free Google Translate API client (no API key required).
 *
 * Uses the same `translate.googleapis.com/translate_a/single` endpoint that the
 * Google Translate web interface calls.  There is no hard rate-limit published,
 * but excessive usage may get temporarily throttled — keep requests infrequent.
 */
object GoogleTranslate {

    private const val BASE_URL = "https://translate.googleapis.com/translate_a/single"
    private const val CHUNK_SIZE = 4500 // safe limit per request

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 12_000
    /** Hard ceiling for the whole translation so the UI can never spin forever. */
    private const val OVERALL_TIMEOUT_MS = 30_000L
    /** Max concurrent chunk requests (keeps us friendly to the free endpoint). */
    private const val MAX_CONCURRENT = 6

    /**
     * Translate [text] into [targetLang] (e.g. "zh-CN", "en").
     *
     * Chunks are translated **concurrently** (capped at [MAX_CONCURRENT]) and the
     * whole operation is bounded by [OVERALL_TIMEOUT_MS]. Throws [IOException] on
     * any failure (network, timeout, bad response) so the caller can surface it
     * instead of silently showing the original text.
     */
    suspend fun translate(text: String, targetLang: String): String {
        if (text.isBlank()) return text
        return withContext(Dispatchers.IO) {
            try {
                withTimeout(OVERALL_TIMEOUT_MS) {
                    val chunks = splitIntoChunks(text)
                    if (chunks.size == 1) {
                        translateChunk(chunks[0], targetLang)
                    } else {
                        val semaphore = Semaphore(MAX_CONCURRENT)
                        coroutineScope {
                            chunks.map { chunk ->
                                async {
                                    semaphore.withPermit { translateChunk(chunk, targetLang) }
                                }
                            }.awaitAll()
                        }.joinToString("")
                    }
                }
            } catch (e: TimeoutCancellationException) {
                throw IOException("Translation timed out — check your network and retry")
            }
        }
    }

    /**
     * Simple language detection: returns "zh" if >20% of non-whitespace chars
     * are CJK, otherwise "en".  Good enough for README content.
     */
    fun detectLanguage(text: String): String {
        val chars = text.filter { !it.isWhitespace() }
        if (chars.isEmpty()) return "en"
        val cjkCount = chars.count { ch ->
            val code = ch.code
            (code in 0x4E00..0x9FFF) || (code in 0x3400..0x4DBF) ||
                (code in 0x20000..0x2A6DF) || (code in 0xF900..0xFAFF)
        }
        return if (cjkCount.toFloat() / chars.length > 0.2f) "zh" else "en"
    }

    // ── internals ──────────────────────────────────────────────

    private fun translateChunk(text: String, targetLang: String): String {
        val sl = detectLanguage(text)
        // If already in target language, skip
        if ((targetLang.startsWith("zh") && sl == "zh") ||
            (targetLang == "en" && sl == "en")
        ) {
            return text
        }

        val encoded = URLEncoder.encode(text, "UTF-8")
        val url = URL("$BASE_URL?client=gtx&sl=$sl&tl=$targetLang&dt=t&q=$encoded")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0")
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
        }
        return try {
            val code = conn.responseCode
            if (code == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parseTranslationResponse(body)
            } else {
                throw IOException("Translate service returned HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseTranslationResponse(json: String): String {
        val arr = JSONArray(json)
        val segments = arr.getJSONArray(0)
        val sb = StringBuilder()
        for (i in 0 until segments.length()) {
            val segment = segments.getJSONArray(i)
            sb.append(segment.getString(0))
        }
        return sb.toString()
    }

    /**
     * Split [text] into chunks of roughly [CHUNK_SIZE] characters,
     * breaking at paragraph boundaries (\n\n) when possible.
     */
    private fun splitIntoChunks(text: String): List<String> {
        if (text.length <= CHUNK_SIZE) return listOf(text)

        val paragraphs = text.split("\n\n")
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        for (para in paragraphs) {
            if (current.length + para.length + 2 > CHUNK_SIZE && current.isNotEmpty()) {
                chunks.add(current.toString())
                current.clear()
            }
            if (current.isNotEmpty()) current.append("\n\n")
            // If a single paragraph exceeds CHUNK_SIZE, split by lines
            if (para.length > CHUNK_SIZE) {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString())
                    current.clear()
                }
                val lines = para.split("\n")
                for (line in lines) {
                    if (current.length + line.length + 1 > CHUNK_SIZE && current.isNotEmpty()) {
                        chunks.add(current.toString())
                        current.clear()
                    }
                    if (current.isNotEmpty()) current.append("\n")
                    if (line.length > CHUNK_SIZE) {
                        // Last resort: hard-split at character boundary
                        var remaining = line
                        while (remaining.length > CHUNK_SIZE) {
                            val breakAt = remaining.lastIndexOf(' ', CHUNK_SIZE)
                                .takeIf { it > 0 } ?: CHUNK_SIZE
                            chunks.add(remaining.substring(0, breakAt))
                            remaining = remaining.substring(breakAt).trimStart()
                        }
                        current.append(remaining)
                    } else {
                        current.append(line)
                    }
                }
            } else {
                current.append(para)
            }
        }
        if (current.isNotEmpty()) chunks.add(current.toString())
        return chunks
    }
}
