package com.example.ai

import android.graphics.Bitmap
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
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

class StudyLeafAiEngine {

    private val studyDeckSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Concise study deck title based on the document"),
            "overview" to Schema.string(description = "2-sentence overview of the page content"),
            "keyTakeaways" to Schema.array(
                items = Schema.string(),
                description = "3 to 5 bullet-ready key concepts from the document"
            ),
            "flashcards" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "question" to Schema.string(description = "Clear study question"),
                        "answer" to Schema.string(description = "Concise accurate answer"),
                        "difficulty" to Schema.enumeration(
                            values = listOf("Core", "Intermediate", "Exam Focus"),
                            description = "Difficulty tier"
                        )
                    )
                ),
                description = "4 to 6 high-yield study flashcards"
            )
        )
    )

    // Lazily initialized per Firebase AI Logic best practices (never evaluated at construction)
    private val generalModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    private val structuredDeckModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = studyDeckSchema
            }
        )
    }

    fun summarizePageStream(
        pageBitmap: Bitmap?,
        documentTitle: String,
        pageNumber: Int
    ): Flow<String> {
        val safeBitmap = pageBitmap?.let(::downscaleForPayload)
        val prompt = content {
            if (safeBitmap != null) {
                image(safeBitmap)
            }
            text(
                "You are StudyLeaf, an academic study assistant. Summarize Page $pageNumber of \"$documentTitle\" " +
                    "clearly and concisely. Organize your response with a short overview followed by key points, " +
                    "important definitions or equations, and one exam preparation tip. Keep plain, clean text formatting."
            )
        }
        return generalModel.generateContentStream(prompt).map { chunk ->
            chunk.text.orEmpty()
        }
    }

    fun askDocumentStream(
        pageBitmap: Bitmap?,
        documentTitle: String,
        pageNumber: Int,
        priorMessages: List<AiChatMessage>,
        question: String
    ): Flow<String> {
        val safeBitmap = pageBitmap?.let(::downscaleForPayload)
        val conversationContext = priorMessages
            .takeLast(6)
            .joinToString("\n") { msg ->
                val speaker = if (msg.isUser) "Student" else "Tutor"
                "$speaker: ${msg.text}"
            }

        val prompt = content {
            if (safeBitmap != null) {
                image(safeBitmap)
            }
            val prefix = buildString {
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
            text(prefix)
        }
        return generalModel.generateContentStream(prompt).map { chunk ->
            chunk.text.orEmpty()
        }
    }

    suspend fun generateStudyDeck(
        pageBitmap: Bitmap?,
        documentTitle: String,
        pageNumber: Int
    ): StructuredStudyDeck {
        val safeBitmap = pageBitmap?.let(::downscaleForPayload)
        val prompt = content {
            if (safeBitmap != null) {
                image(safeBitmap)
            }
            text(
                "Create a high-yield study deck with key takeaways and flashcards based on " +
                    "Page $pageNumber of \"$documentTitle\"."
            )
        }
        val response = structuredDeckModel.generateContent(prompt)
        val rawJson = response.text
        if (rawJson.isNullOrBlank()) {
            throw IllegalStateException("Gemini returned an empty study deck response.")
        }

        // Direct JSON parsing guaranteed by responseMimeType = application/json + responseSchema
        val root = JSONObject(rawJson)
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

        return StructuredStudyDeck(
            title = title,
            overview = overview,
            keyTakeaways = takeaways,
            flashcards = cards
        )
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
