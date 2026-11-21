package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class StudyFlashcard(
    val question: String,
    val answer: String,
    val difficulty: String
)

data class StructuredStudyDeck(
    val title: String,
    val overview: String,
    val keyTakeaways: List<String>,
    val flashcards: List<StudyFlashcard>
)

data class AiChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val isStreaming: Boolean = false
)

class MissingGeminiApiKeyException :
    IllegalStateException("Add your own Gemini API key to use AI features.")

class StudyLeafAiEngine {

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        val AVAILABLE_MODELS = listOf(
            "gemini-2.5-flash" to "Gemini 2.5 Flash (Recommended)",
            "gemini-2.5-flash-lite" to "Gemini 2.5 Flash-Lite (Fast)",
            "gemini-2.5-pro" to "Gemini 2.5 Pro (Deep)"
        )
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 55_000
    }

    fun summarizePageStream(
        apiKey: String,
        modelName: String = DEFAULT_MODEL,
        pageBitmap: Bitmap?,
        documentTitle: String,
        pageNumber: Int
    ): Flow<String> {
        val cleanedKey = requireValidKey(apiKey)
        val safeBitmap = pageBitmap?.let(::downscaleForPayload)
        val payload = buildGenerateContentRequest(
            bitmaps = listOfNotNull(safeBitmap),
            promptText = "You are StudyLeaf, an academic study assistant. Summarize Page $pageNumber of \"$documentTitle\" " +
                "clearly and concisely. Organize your response with a short overview followed by key points, " +
                "important definitions or equations, and one exam preparation tip. Keep plain, clean text formatting."
        )
        return streamGenerateContent(cleanedKey, modelName, payload)
    }

    fun askDocumentStream(
        apiKey: String,
        modelName: String = DEFAULT_MODEL,
        pageBitmap: Bitmap?,
        documentTitle: String,
        pageNumber: Int,
        priorMessages: List<AiChatMessage>,
        question: String
    ): Flow<String> {
        val cleanedKey = requireValidKey(apiKey)
        val safeBitmap = pageBitmap?.let(::downscaleForPayload)
        val conversationContext = priorMessages
            .takeLast(6)
            .joinToString("\n") { msg ->
                val speaker = if (msg.isUser) "Student" else "Tutor"
                "$speaker: ${msg.text}"
            }

        val promptText = buildString {
            append("You are StudyLeaf, a calm and precise academic tutor helping with ")
            append("Page $pageNumber of \"$documentTitle\". ")
            append("Ground your answer in the attached document page whenever possible. ")
            if (conversationContext.isNotBlank()) {
                append("\nRecent conversation:\n")
                append(conversationContext)
                append("\n")
            }
            append("\nStudent question: ")
            append(question)
        }
        val payload = buildGenerateContentRequest(
            bitmaps = listOfNotNull(safeBitmap),
            promptText = promptText
        )
        return streamGenerateContent(cleanedKey, modelName, payload)
    }

    suspend fun generateStudyDeck(
        apiKey: String,
        modelName: String = DEFAULT_MODEL,
        pageBitmap: Bitmap?,
        documentTitle: String,
        pageNumber: Int
    ): StructuredStudyDeck = withContext(Dispatchers.IO) {
        val cleanedKey = requireValidKey(apiKey)
        val safeBitmap = pageBitmap?.let(::downscaleForPayload)
        val payload = buildGenerateContentRequest(
            bitmaps = listOfNotNull(safeBitmap),
            promptText = "Create a high-yield study deck with key takeaways and flashcards based on " +
                "Page $pageNumber of \"$documentTitle\".",
            responseSchema = buildStudyDeckSchemaJson()
        )
        val rawJson = executeGenerateContent(cleanedKey, modelName, payload)
        if (rawJson.isBlank()) {
            throw IllegalStateException("Gemini returned an empty study deck response.")
        }

        val normalizedJson = rawJson
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val root = JSONObject(normalizedJson)
        val title = root.optString("title", "$documentTitle Study Deck")
        val overview = root.optString("overview", "")

        val takeawaysArray = root.optJSONArray("keyTakeaways")
        val takeaways = buildList {
            if (takeawaysArray != null) {
                for (i in 0 until takeawaysArray.length()) {
                    val item = takeawaysArray.optString(i)
                    if (item.isNotBlank()) add(item)
                }
            }
        }

        val cardsArray = root.optJSONArray("flashcards")
        val cards = buildList {
            if (cardsArray != null) {
                for (i in 0 until cardsArray.length()) {
                    val obj = cardsArray.optJSONObject(i) ?: continue
                    val q = obj.optString("question")
                    val a = obj.optString("answer")
                    val d = obj.optString("difficulty", "Core")
                    if (q.isNotBlank() && a.isNotBlank()) {
                        add(StudyFlashcard(question = q, answer = a, difficulty = d))
                    }
                }
            }
        }

        StructuredStudyDeck(
            title = title,
            overview = overview,
            keyTakeaways = takeaways,
            flashcards = cards
        )
    }

    suspend fun extractEditableTextFromPages(
        apiKey: String,
        modelName: String = DEFAULT_MODEL,
        pageBitmaps: List<Bitmap>,
        documentTitle: String
    ): String = withContext(Dispatchers.IO) {
        val cleanedKey = requireValidKey(apiKey)
        val safeBitmaps = pageBitmaps.take(6).map { downscaleForPayload(it, maxDim = 1280) }
        val payload = buildGenerateContentRequest(
            bitmaps = safeBitmaps,
            promptText = "Transcribe all readable text from these page images of \"$documentTitle\" " +
                "accurately so the user can edit the document. Preserve headings, paragraphs, " +
                "bullet lists, numbered items, and table rows using clean plain text. " +
                "Separate pages with a blank line. Do not add commentary or markdown code fences."
        )
        executeGenerateContent(cleanedKey, modelName, payload).trim()
    }

    suspend fun verifyApiKey(
        apiKey: String,
        modelName: String = DEFAULT_MODEL
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanedKey = requireValidKey(apiKey)
            val payload = buildGenerateContentRequest(
                bitmaps = emptyList(),
                promptText = "Reply with OK"
            )
            val response = executeGenerateContent(cleanedKey, modelName, payload)
            if (response.isBlank()) {
                throw IllegalStateException("Key connected, but Gemini returned an empty reply.")
            }
            "API key verified with $modelName"
        }
    }

    private fun requireValidKey(apiKey: String): String {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) throw MissingGeminiApiKeyException()
        return trimmed
    }

    private fun buildGenerateContentRequest(
        bitmaps: List<Bitmap>,
        promptText: String,
        responseSchema: JSONObject? = null
    ): JSONObject {
        val parts = JSONArray()
        bitmaps.forEach { bmp ->
            val inlineData = JSONObject()
                .put("mime_type", "image/jpeg")
                .put("data", encodeBitmapToBase64Jpeg(bmp))
            parts.put(JSONObject().put("inline_data", inlineData))
        }
        parts.put(JSONObject().put("text", promptText))

        val contentObj = JSONObject()
            .put("role", "user")
            .put("parts", parts)

        val root = JSONObject()
            .put("contents", JSONArray().put(contentObj))

        if (responseSchema != null) {
            root.put(
                "generationConfig",
                JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", responseSchema)
            )
        }
        return root
    }

    private fun buildStudyDeckSchemaJson(): JSONObject {
        val flashcardProperties = JSONObject()
            .put(
                "question",
                JSONObject()
                    .put("type", "STRING")
                    .put("description", "Clear study question")
            )
            .put(
                "answer",
                JSONObject()
                    .put("type", "STRING")
                    .put("description", "Concise accurate answer")
            )
            .put(
                "difficulty",
                JSONObject()
                    .put("type", "STRING")
                    .put("enum", JSONArray(listOf("Core", "Intermediate", "Exam Focus")))
                    .put("description", "Difficulty tier")
            )

        val flashcardItem = JSONObject()
            .put("type", "OBJECT")
            .put("properties", flashcardProperties)
            .put("required", JSONArray(listOf("question", "answer", "difficulty")))

        val rootProperties = JSONObject()
            .put(
                "title",
                JSONObject()
                    .put("type", "STRING")
                    .put("description", "Concise study deck title based on the document")
            )
            .put(
                "overview",
                JSONObject()
                    .put("type", "STRING")
                    .put("description", "2-sentence overview of the page content")
            )
            .put(
                "keyTakeaways",
                JSONObject()
                    .put("type", "ARRAY")
                    .put("items", JSONObject().put("type", "STRING"))
                    .put("description", "3 to 5 bullet-ready key concepts from the document")
            )
            .put(
                "flashcards",
                JSONObject()
                    .put("type", "ARRAY")
                    .put("items", flashcardItem)
                    .put("description", "4 to 6 high-yield study flashcards")
            )

        return JSONObject()
            .put("type", "OBJECT")
            .put("properties", rootProperties)
            .put("required", JSONArray(listOf("title", "overview", "keyTakeaways", "flashcards")))
    }

    private fun streamGenerateContent(
        apiKey: String,
        modelName: String,
        payload: JSONObject
    ): Flow<String> = flow {
        val resolvedModel = modelName.trim().ifBlank { DEFAULT_MODEL }
        val endpoint = "$BASE_URL/$resolvedModel:streamGenerateContent?alt=sse"
        val connection = openGeminiConnection(endpoint, apiKey)
        try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                val errorBody = readStreamText(connection.errorStream)
                throw IllegalStateException(parseFriendlyApiError(code, errorBody))
            }

            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                val dataBuffer = StringBuilder()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    when {
                        line.startsWith("data:") -> {
                            dataBuffer.append(line.removePrefix("data:").trim())
                        }
                        line.isBlank() -> {
                            if (dataBuffer.isNotEmpty()) {
                                val textChunk = extractTextFromCandidateJson(dataBuffer.toString())
                                dataBuffer.setLength(0)
                                if (textChunk.isNotEmpty()) {
                                    emit(textChunk)
                                }
                            }
                        }
                    }
                }
                if (dataBuffer.isNotEmpty()) {
                    val textChunk = extractTextFromCandidateJson(dataBuffer.toString())
                    if (textChunk.isNotEmpty()) {
                        emit(textChunk)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private fun executeGenerateContent(
        apiKey: String,
        modelName: String,
        payload: JSONObject
    ): String {
        val resolvedModel = modelName.trim().ifBlank { DEFAULT_MODEL }
        val endpoint = "$BASE_URL/$resolvedModel:generateContent"
        val connection = openGeminiConnection(endpoint, apiKey)
        try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                val errorBody = readStreamText(connection.errorStream)
                throw IllegalStateException(parseFriendlyApiError(code, errorBody))
            }

            val body = readStreamText(connection.inputStream)
            return extractTextFromCandidateJson(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun openGeminiConnection(endpoint: String, apiKey: String): HttpURLConnection {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection)
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.doInput = true
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        // Send user's API key in header rather than URL query params
        connection.setRequestProperty("x-goog-api-key", apiKey)
        return connection
    }

    private fun readStreamText(stream: java.io.InputStream?): String {
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun extractTextFromCandidateJson(rawJson: String): String {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty() || trimmed == "[DONE]") return ""
        return runCatching {
            val root = JSONObject(trimmed)
            val candidates = root.optJSONArray("candidates") ?: return@runCatching ""
            val sb = StringBuilder()
            for (i in 0 until candidates.length()) {
                val candidate = candidates.optJSONObject(i) ?: continue
                val content = candidate.optJSONObject("content") ?: continue
                val parts = content.optJSONArray("parts") ?: continue
                for (j in 0 until parts.length()) {
                    val part = parts.optJSONObject(j) ?: continue
                    val text = part.optString("text")
                    if (text.isNotEmpty()) sb.append(text)
                }
            }
            sb.toString()
        }.getOrDefault("")
    }

    private fun parseFriendlyApiError(httpCode: Int, errorBody: String): String {
        val serverMsg = runCatching {
            JSONObject(errorBody).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")

        return when {
            httpCode == 400 && serverMsg.contains("API key", ignoreCase = true) ->
                "Invalid Gemini API key. Open Key Settings to check or replace your key."
            httpCode == 401 || httpCode == 403 ->
                "Your Gemini API key was rejected (HTTP $httpCode). Verify your key permissions in Google AI Studio."
            httpCode == 429 ->
                "Your Gemini API key has reached its rate or quota limit (HTTP 429). Try again shortly or switch model."
            serverMsg.isNotBlank() ->
                serverMsg
            else ->
                "Gemini request failed (HTTP $httpCode). Check your connection and API key."
        }
    }

    private fun encodeBitmapToBase64Jpeg(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun downscaleForPayload(bitmap: Bitmap, maxDim: Int = 1100): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDim) return bitmap
        val scale = min(1f, maxDim.toFloat() / largest.toFloat())
        return Bitmap.createScaledBitmap(
            bitmap,
            max(1, (bitmap.width * scale).roundToInt()),
            max(1, (bitmap.height * scale).roundToInt()),
            true
        )
    }
}
