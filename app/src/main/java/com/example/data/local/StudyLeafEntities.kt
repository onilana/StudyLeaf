package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.DocumentFormat

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val filePath: String,
    val formatName: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val folderName: String = "Lectures",
    val isFavorite: Boolean = false,
    val summarySnippet: String = "",
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val format: DocumentFormat
        get() = runCatching { DocumentFormat.valueOf(formatName) }.getOrDefault(DocumentFormat.PDF)
}

@Entity(tableName = "annotations")
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val documentId: Long,
    val pageIndex: Int,
    val toolName: String,
    val colorHex: Long,
    val pointsSerialized: String,
    val noteText: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "conversion_history")
data class ConversionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val toolTypeName: String,
    val sourceTitle: String,
    val resultTitle: String,
    val resultDocumentId: Long?,
    val sourceFormatName: String,
    val targetFormatName: String,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)
