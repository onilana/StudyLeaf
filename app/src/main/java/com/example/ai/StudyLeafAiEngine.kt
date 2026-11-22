package com.example.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle

/**
 * Connects StudyLeaf directly to the user's own Google Gemini account
 * with zero API keys and zero developer quota usage:
 * 1. Opens gemini.google.com inside an in-app Custom Tab (supports native Google Sign-In).
 * 2. Shares rendered document pages/files directly to the installed Google Gemini app.
 */
class StudyLeafAiEngine {

    companion object {
        const val GEMINI_WEB_URL = "https://gemini.google.com/app"
        const val GEMINI_APP_PACKAGE = "com.google.android.apps.bard"
        private const val EXTRA_CUSTOM_TABS_SESSION = "android.support.customtabs.extra.SESSION"
        private const val EXTRA_CUSTOM_TABS_TOOLBAR_COLOR = "android.support.customtabs.extra.TOOLBAR_COLOR"
        private const val EXTRA_CUSTOM_TABS_TITLE_VISIBILITY = "android.support.customtabs.extra.TITLE_VISIBILITY"
        private const val EXTRA_CUSTOM_TABS_SHARE_MENU = "android.support.customtabs.extra.SHARE_MENU_ITEM"
        private const val MAX_INLINE_DOC_CHARS = 5500
    }

    fun isGeminiAppInstalled(context: Context): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    GEMINI_APP_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(GEMINI_APP_PACKAGE, 0)
            }
            true
        }.getOrDefault(false)
    }

    fun buildSummaryPrompt(
        documentTitle: String,
        pageNumber: Int,
        extractedText: String
    ): String = buildString {
        appendLine("Summarize Page $pageNumber of \"$documentTitle\" clearly and concisely.")
        appendLine("Include: (1) a short overview, (2) bullet-point key concepts, (3) important definitions or formulas, and (4) one exam tip.")
        appendExcerptIfPresent(extractedText)
    }.trim()

    fun buildFlashcardsPrompt(
        documentTitle: String,
        pageNumber: Int,
        extractedText: String
    ): String = buildString {
        appendLine("Create 5 high-yield study flashcards (Question & Answer) and 3 key takeaways from Page $pageNumber of \"$documentTitle\".")
        appendLine("Format clearly as Q1/A1, Q2/A2, etc. so I can copy them back into StudyLeaf.")
        appendExcerptIfPresent(extractedText)
    }.trim()

    fun buildQuestionPrompt(
        documentTitle: String,
        pageNumber: Int,
        question: String,
        extractedText: String
    ): String = buildString {
        appendLine("Regarding Page $pageNumber of \"$documentTitle\":")
        appendLine(question.trim())
        appendExcerptIfPresent(extractedText)
    }.trim()

    fun buildOcrExtractPrompt(
        documentTitle: String,
        pageNumber: Int
    ): String =
        "Transcribe all readable text from Page $pageNumber of \"$documentTitle\" accurately so I can edit it. " +
            "Preserve headings, paragraphs, bullet points, and tables using clean plain text with no extra commentary."

    private fun StringBuilder.appendExcerptIfPresent(extractedText: String) {
        val cleaned = extractedText.trim()
        if (cleaned.length >= 20) {
            appendLine()
            appendLine("--- Document Text ---")
            append(cleaned.take(MAX_INLINE_DOC_CHARS))
        }
    }

    fun copyPromptToClipboard(context: Context, prompt: String) {
        if (prompt.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("StudyLeaf Gemini Prompt", prompt))
    }

    fun readClipboardText(context: Context): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
        val clip = clipboard.primaryClip ?: return ""
        if (clip.itemCount <= 0) return ""
        return clip.getItemAt(0).coerceToText(context)?.toString()?.trim().orEmpty()
    }

    /**
     * Opens gemini.google.com in an in-app Custom Tab themed with StudyLeaf's toolbar color,
     * allowing users to sign in with their own Google account natively inside the app.
     */
    fun openGeminiInApp(
        context: Context,
        prompt: String,
        toolbarColorArgb: Int
    ): Boolean {
        if (prompt.isNotBlank()) {
            copyPromptToClipboard(context, prompt)
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GEMINI_WEB_URL)).apply {
            val extras = Bundle().apply {
                putBinder(EXTRA_CUSTOM_TABS_SESSION, null)
                putInt(EXTRA_CUSTOM_TABS_TOOLBAR_COLOR, toolbarColorArgb)
                putInt(EXTRA_CUSTOM_TABS_TITLE_VISIBILITY, 1)
                putBoolean(EXTRA_CUSTOM_TABS_SHARE_MENU, true)
            }
            putExtras(extras)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /**
     * Sends a rendered page PNG or document file + study prompt straight to the user's
     * installed Google Gemini app (or system share sheet if Gemini app is not installed).
     */
    fun shareToGeminiApp(
        context: Context,
        fileUri: Uri?,
        mimeType: String,
        prompt: String,
        chooserTitle: String = "Open with Google Gemini"
    ): Boolean {
        if (prompt.isNotBlank()) {
            copyPromptToClipboard(context, prompt)
        }
        val baseIntent = Intent(Intent.ACTION_SEND).apply {
            if (fileUri != null) {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, fileUri)
                clipData = ClipData.newRawUri("StudyLeaf Page", fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
            if (prompt.isNotBlank()) {
                putExtra(Intent.EXTRA_TEXT, prompt)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Prefer sending directly into the official Google Gemini app when installed
        if (isGeminiAppInstalled(context)) {
            val directGemini = Intent(baseIntent).apply {
                setPackage(GEMINI_APP_PACKAGE)
            }
            val launchedDirectly = runCatching {
                context.startActivity(directGemini)
                true
            }.getOrDefault(false)
            if (launchedDirectly) return true
        }

        val chooser = Intent.createChooser(baseIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }
}
