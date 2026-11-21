package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.data.engine.DocumentEngine
import com.example.data.engine.PdfSeedGenerator
import com.example.data.local.AnnotationEntity
import com.example.data.local.ConversionHistoryEntity
import com.example.data.local.DocumentEntity
import com.example.data.local.StudyLeafDatabase
import com.example.data.model.AnnotationTool
import com.example.data.model.ConversionToolType
import com.example.data.model.DocumentFormat
import com.example.data.model.NormalizedPoint
import com.example.data.model.PageAnnotation
import com.example.data.model.ScanEnhancementSettings
import com.example.data.model.ScanResolutionOption
import com.example.data.model.ScannerFilterMode
import com.example.data.model.decodePoints
import com.example.data.model.encodePoints
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class StudyLeafRepository(
    private val context: Context,
    private val database: StudyLeafDatabase,
    val engine: DocumentEngine
) {
    private val documentDao = database.documentDao()
    private val annotationDao = database.annotationDao()
    private val historyDao = database.conversionHistoryDao()

    val documents: Flow<List<DocumentEntity>> = documentDao.observeAllDocuments()
    val history: Flow<List<ConversionHistoryEntity>> = historyDao.observeAllHistory()

    suspend fun ensureSeeded() {
        if (documentDao.getCount() > 0) return
        val seeded = PdfSeedGenerator.generateStarterLibrary(context)
        val now = System.currentTimeMillis()
        seeded.forEachIndexed { idx, spec ->
            val id = documentDao.insert(
                DocumentEntity(
                    title = spec.title,
                    filePath = spec.file.absolutePath,
                    formatName = spec.formatName,
                    sizeBytes = spec.file.length(),
                    pageCount = spec.pageCount,
                    folderName = spec.folderName,
                    isFavorite = spec.isFavorite,
                    summarySnippet = spec.summarySnippet,
                    lastOpenedAt = now - idx * 3_600_000L,
                    createdAt = now - idx * 7_200_000L
                )
            )
            if (idx == 0) {
                annotationDao.insert(
                    AnnotationEntity(
                        documentId = id,
                        pageIndex = 0,
                        toolName = AnnotationTool.HIGHLIGHT.name,
                        colorHex = 0xFFFFD166,
                        pointsSerialized = encodePoints(
                            listOf(NormalizedPoint(0.10f, 0.245f), NormalizedPoint(0.84f, 0.245f))
                        ),
                        noteText = ""
                    )
                )
                annotationDao.insert(
                    AnnotationEntity(
                        documentId = id,
                        pageIndex = 0,
                        toolName = AnnotationTool.NOTE.name,
                        colorHex = 0xFF74C69D,
                        pointsSerialized = encodePoints(listOf(NormalizedPoint(0.78f, 0.68f))),
                        noteText = "Review ATP synthase H+ stoichiometry before Thursday quiz."
                    )
                )
            }
            if (idx == 2) {
                historyDao.insert(
                    ConversionHistoryEntity(
                        toolTypeName = ConversionToolType.SCAN_DOCUMENT.name,
                        sourceTitle = "Lecture Handout Capture (2 pages)",
                        resultTitle = spec.title,
                        resultDocumentId = id,
                        sourceFormatName = DocumentFormat.SCAN.name,
                        targetFormatName = DocumentFormat.PDF.name,
                        pageCount = spec.pageCount,
                        fileSizeBytes = spec.file.length(),
                        durationMs = 420L,
                        timestamp = now - 5_400_000L
                    )
                )
            }
        }
    }

    fun observeAnnotations(documentId: Long): Flow<List<PageAnnotation>> =
        annotationDao.observeForDocument(documentId).map { list ->
            list.map { entity ->
                PageAnnotation(
                    id = entity.id,
                    documentId = entity.documentId,
                    pageIndex = entity.pageIndex,
                    tool = runCatching { AnnotationTool.valueOf(entity.toolName) }
                        .getOrDefault(AnnotationTool.PEN),
                    colorHex = entity.colorHex,
                    points = decodePoints(entity.pointsSerialized),
                    noteText = entity.noteText,
                    createdAt = entity.createdAt
                )
            }
        }

    fun observeAnnotationCount(documentId: Long): Flow<Int> =
        annotationDao.observeCountForDocument(documentId)

    suspend fun getDocumentById(id: Long): DocumentEntity? = documentDao.getById(id)

    suspend fun markOpened(id: Long) {
        documentDao.updateLastOpened(id, System.currentTimeMillis())
    }

    suspend fun toggleFavorite(id: Long) {
        documentDao.toggleFavorite(id)
    }

    suspend fun renameDocument(id: Long, newTitle: String) {
        if (newTitle.isNotBlank()) {
            documentDao.renameDocument(id, newTitle.trim())
        }
    }

    suspend fun deleteDocument(doc: DocumentEntity) {
        annotationDao.deleteAllForDocument(doc.id)
        documentDao.deleteById(doc.id)
        runCatching { File(doc.filePath).delete() }
    }

    suspend fun addAnnotation(annotation: PageAnnotation): Long {
        return annotationDao.insert(
            AnnotationEntity(
                documentId = annotation.documentId,
                pageIndex = annotation.pageIndex,
                toolName = annotation.tool.name,
                colorHex = annotation.colorHex,
                pointsSerialized = encodePoints(annotation.points),
                noteText = annotation.noteText
            )
        )
    }

    suspend fun deleteAnnotation(id: Long) {
        annotationDao.deleteById(id)
    }

    suspend fun clearPageAnnotations(documentId: Long, pageIndex: Int) {
        annotationDao.clearPage(documentId, pageIndex)
    }

    suspend fun clearHistory() {
        historyDao.clearAll()
    }

    suspend fun importDeviceDocument(uri: Uri): DocumentEntity? {
        val imported = engine.importUriToVault(uri) ?: return null
        val (file, format, title) = imported
        val pageCount = if (format == DocumentFormat.PDF) engine.getPdfPageCount(file.absolutePath) else 1
        val now = System.currentTimeMillis()
        val entity = DocumentEntity(
            title = title,
            filePath = file.absolutePath,
            formatName = format.name,
            sizeBytes = file.length(),
            pageCount = pageCount,
            folderName = "Imported",
            summarySnippet = "Imported directly into StudyLeaf offline vault.",
            lastOpenedAt = now,
            createdAt = now
        )
        val id = documentDao.insert(entity)
        return entity.copy(id = id)
    }

    suspend fun convertImagesToPdf(
        bitmaps: List<Bitmap>,
        outputTitle: String,
        marginPt: Int,
        isScan: Boolean,
        scanResolution: ScanResolutionOption? = null
    ): DocumentEntity {
        val start = System.currentTimeMillis()
        val info = engine.imagesToPdf(bitmaps, outputTitle, marginPt)
        val duration = (System.currentTimeMillis() - start).coerceAtLeast(80L)
        val now = System.currentTimeMillis()
        val resLabel = scanResolution?.let { " • ${it.label} (${it.widthPx}×${it.heightPx})" }.orEmpty()
        val doc = DocumentEntity(
            title = outputTitle,
            filePath = info.file.absolutePath,
            formatName = if (isScan) DocumentFormat.SCAN.name else DocumentFormat.PDF.name,
            sizeBytes = info.sizeBytes,
            pageCount = info.pageCount,
            folderName = if (isScan) "Scans" else "Converted",
            summarySnippet = if (isScan) {
                "Optically scanned & enhanced (${info.pageCount} pg)$resLabel."
            } else {
                "Compiled from ${info.pageCount} local image(s) into PDF."
            },
            lastOpenedAt = now,
            createdAt = now
        )
        val id = documentDao.insert(doc)
        historyDao.insert(
            ConversionHistoryEntity(
                toolTypeName = if (isScan) ConversionToolType.SCAN_DOCUMENT.name else ConversionToolType.IMAGE_TO_PDF.name,
                sourceTitle = "${bitmaps.size} ${if (isScan) "scanned page(s)$resLabel" else "image(s)"}",
                resultTitle = outputTitle,
                resultDocumentId = id,
                sourceFormatName = if (isScan) DocumentFormat.SCAN.name else DocumentFormat.IMAGE.name,
                targetFormatName = DocumentFormat.PDF.name,
                pageCount = info.pageCount,
                fileSizeBytes = info.sizeBytes,
                durationMs = duration
            )
        )
        return doc.copy(id = id)
    }

    suspend fun convertTextToPdf(
        title: String,
        bodyText: String,
        fontSizePt: Float,
        marginPt: Int,
        sourceName: String = "Rich Study Notes"
    ): DocumentEntity {
        val start = System.currentTimeMillis()
        val info = engine.textToPdf(title, bodyText, fontSizePt, marginPt)
        val duration = (System.currentTimeMillis() - start).coerceAtLeast(80L)
        val now = System.currentTimeMillis()
        val doc = DocumentEntity(
            title = title,
            filePath = info.file.absolutePath,
            formatName = DocumentFormat.PDF.name,
            sizeBytes = info.sizeBytes,
            pageCount = info.pageCount,
            folderName = "Converted",
            summarySnippet = bodyText.trim().replace("\n", " ").take(96),
            lastOpenedAt = now,
            createdAt = now
        )
        val id = documentDao.insert(doc)
        historyDao.insert(
            ConversionHistoryEntity(
                toolTypeName = ConversionToolType.WORD_TO_PDF.name,
                sourceTitle = sourceName,
                resultTitle = title,
                resultDocumentId = id,
                sourceFormatName = DocumentFormat.WORD.name,
                targetFormatName = DocumentFormat.PDF.name,
                pageCount = info.pageCount,
                fileSizeBytes = info.sizeBytes,
                durationMs = duration
            )
        )
        return doc.copy(id = id)
    }

    suspend fun mergeDocuments(
        sources: List<DocumentEntity>,
        outputTitle: String
    ): DocumentEntity {
        val start = System.currentTimeMillis()
        val info = engine.mergePdfs(sources.map { it.filePath }, outputTitle)
        val duration = (System.currentTimeMillis() - start).coerceAtLeast(120L)
        val now = System.currentTimeMillis()
        val doc = DocumentEntity(
            title = outputTitle,
            filePath = info.file.absolutePath,
            formatName = DocumentFormat.PDF.name,
            sizeBytes = info.sizeBytes,
            pageCount = info.pageCount,
            folderName = "Converted",
            summarySnippet = "Merged from ${sources.joinToString(", ") { it.title }.take(84)}",
            lastOpenedAt = now,
            createdAt = now
        )
        val id = documentDao.insert(doc)
        historyDao.insert(
            ConversionHistoryEntity(
                toolTypeName = ConversionToolType.MERGE_PDF.name,
                sourceTitle = "${sources.size} documents merged",
                resultTitle = outputTitle,
                resultDocumentId = id,
                sourceFormatName = DocumentFormat.PDF.name,
                targetFormatName = DocumentFormat.PDF.name,
                pageCount = info.pageCount,
                fileSizeBytes = info.sizeBytes,
                durationMs = duration
            )
        )
        return doc.copy(id = id)
    }

    suspend fun reorderDocumentPages(
        source: DocumentEntity,
        orderedZeroBasedPages: List<Int>,
        outputTitle: String
    ): DocumentEntity {
        val start = System.currentTimeMillis()
        val info = engine.reorderAndExtractPdf(source.filePath, orderedZeroBasedPages, outputTitle)
        val duration = (System.currentTimeMillis() - start).coerceAtLeast(100L)
        val now = System.currentTimeMillis()
        val doc = DocumentEntity(
            title = outputTitle,
            filePath = info.file.absolutePath,
            formatName = DocumentFormat.PDF.name,
            sizeBytes = info.sizeBytes,
            pageCount = info.pageCount,
            folderName = "Converted",
            summarySnippet = "Reordered & extracted ${info.pageCount} page(s) from ${source.title}.",
            lastOpenedAt = now,
            createdAt = now
        )
        val id = documentDao.insert(doc)
        historyDao.insert(
            ConversionHistoryEntity(
                toolTypeName = ConversionToolType.SPLIT_REORDER.name,
                sourceTitle = source.title,
                resultTitle = outputTitle,
                resultDocumentId = id,
                sourceFormatName = DocumentFormat.PDF.name,
                targetFormatName = DocumentFormat.PDF.name,
                pageCount = info.pageCount,
                fileSizeBytes = info.sizeBytes,
                durationMs = duration
            )
        )
        return doc.copy(id = id)
    }

    suspend fun optimizeDocument(
        source: DocumentEntity,
        qualityScale: Float,
        outputTitle: String
    ): DocumentEntity {
        val start = System.currentTimeMillis()
        val info = engine.optimizePdf(source.filePath, qualityScale, outputTitle)
        val duration = (System.currentTimeMillis() - start).coerceAtLeast(100L)
        val now = System.currentTimeMillis()
        val doc = DocumentEntity(
            title = outputTitle,
            filePath = info.file.absolutePath,
            formatName = DocumentFormat.PDF.name,
            sizeBytes = info.sizeBytes,
            pageCount = info.pageCount,
            folderName = "Converted",
            summarySnippet = "Locally optimized copy of ${source.title}.",
            lastOpenedAt = now,
            createdAt = now
        )
        val id = documentDao.insert(doc)
        historyDao.insert(
            ConversionHistoryEntity(
                toolTypeName = ConversionToolType.OPTIMIZE_PDF.name,
                sourceTitle = source.title,
                resultTitle = outputTitle,
                resultDocumentId = id,
                sourceFormatName = DocumentFormat.PDF.name,
                targetFormatName = DocumentFormat.PDF.name,
                pageCount = info.pageCount,
                fileSizeBytes = info.sizeBytes,
                durationMs = duration
            )
        )
        return doc.copy(id = id)
    }

    suspend fun exportPdfToImages(source: DocumentEntity): List<DocumentEntity> {
        val start = System.currentTimeMillis()
        val generated = engine.pdfToPngImages(source.filePath, source.title)
        val duration = (System.currentTimeMillis() - start).coerceAtLeast(100L)
        val now = System.currentTimeMillis()
        val createdDocs = mutableListOf<DocumentEntity>()
        generated.forEachIndexed { idx, info ->
            val doc = DocumentEntity(
                title = "${source.title} — Page ${idx + 1}",
                filePath = info.file.absolutePath,
                formatName = DocumentFormat.IMAGE.name,
                sizeBytes = info.sizeBytes,
                pageCount = 1,
                folderName = "Converted",
                summarySnippet = "Exported PNG image from ${source.title}.",
                lastOpenedAt = now,
                createdAt = now
            )
            val id = documentDao.insert(doc)
            createdDocs.add(doc.copy(id = id))
        }
        if (createdDocs.isNotEmpty()) {
            historyDao.insert(
                ConversionHistoryEntity(
                    toolTypeName = ConversionToolType.PDF_TO_IMAGE.name,
                    sourceTitle = source.title,
                    resultTitle = "${createdDocs.size} PNG page(s) exported",
                    resultDocumentId = createdDocs.first().id,
                    sourceFormatName = DocumentFormat.PDF.name,
                    targetFormatName = DocumentFormat.IMAGE.name,
                    pageCount = createdDocs.size,
                    fileSizeBytes = createdDocs.sumOf { it.sizeBytes },
                    durationMs = duration
                )
            )
        }
        return createdDocs
    }

    suspend fun convertDocOrPdfToExcel(
        source: DocumentEntity,
        outputTitle: String
    ): DocumentEntity {
        val start = System.currentTimeMillis()
        val info = engine.docOrPdfToExcel(
            sourcePath = source.filePath,
            sourceTitle = source.title,
            summarySnippet = source.summarySnippet,
            pageCount = source.pageCount,
            outputTitle = outputTitle
        )
        val duration = (System.currentTimeMillis() - start).coerceAtLeast(80L)
        val now = System.currentTimeMillis()
        val doc = DocumentEntity(
            title = outputTitle,
            filePath = info.file.absolutePath,
            formatName = DocumentFormat.EXCEL.name,
            sizeBytes = info.sizeBytes,
            pageCount = 1,
            folderName = "Converted",
            summarySnippet = "Exported Excel (.xlsx) workbook from ${source.title}.",
            lastOpenedAt = now,
            createdAt = now
        )
        val id = documentDao.insert(doc)
        historyDao.insert(
            ConversionHistoryEntity(
                toolTypeName = ConversionToolType.EXCEL_CONVERTER.name,
                sourceTitle = source.title,
                resultTitle = outputTitle,
                resultDocumentId = id,
                sourceFormatName = source.formatName,
                targetFormatName = DocumentFormat.EXCEL.name,
                pageCount = 1,
                fileSizeBytes = info.sizeBytes,
                durationMs = duration
            )
        )
        return doc.copy(id = id)
    }

    suspend fun convertExcelToPdf(
        source: DocumentEntity,
        outputTitle: String
    ): DocumentEntity {
        val start = System.currentTimeMillis()
        val info = engine.excelToPdf(source.filePath, outputTitle)
        val duration = (System.currentTimeMillis() - start).coerceAtLeast(80L)
        val now = System.currentTimeMillis()
        val doc = DocumentEntity(
            title = outputTitle,
            filePath = info.file.absolutePath,
            formatName = DocumentFormat.PDF.name,
            sizeBytes = info.sizeBytes,
            pageCount = info.pageCount,
            folderName = "Converted",
            summarySnippet = "Spreadsheet table rendered to PDF from ${source.title}.",
            lastOpenedAt = now,
            createdAt = now
        )
        val id = documentDao.insert(doc)
        historyDao.insert(
            ConversionHistoryEntity(
                toolTypeName = ConversionToolType.EXCEL_CONVERTER.name,
                sourceTitle = source.title,
                resultTitle = outputTitle,
                resultDocumentId = id,
                sourceFormatName = DocumentFormat.EXCEL.name,
                targetFormatName = DocumentFormat.PDF.name,
                pageCount = info.pageCount,
                fileSizeBytes = info.sizeBytes,
                durationMs = duration
            )
        )
        return doc.copy(id = id)
    }

    suspend fun convertExcelToWord(
        source: DocumentEntity,
        outputTitle: String
    ): DocumentEntity {
        val start = System.currentTimeMillis()
        val info = engine.excelToWordDocx(source.filePath, outputTitle)
        val duration = (System.currentTimeMillis() - start).coerceAtLeast(80L)
        val now = System.currentTimeMillis()
        val doc = DocumentEntity(
            title = outputTitle,
            filePath = info.file.absolutePath,
            formatName = DocumentFormat.WORD.name,
            sizeBytes = info.sizeBytes,
            pageCount = 1,
            folderName = "Converted",
            summarySnippet = "Word (.docx) report generated from Excel sheet ${source.title}.",
            lastOpenedAt = now,
            createdAt = now
        )
        val id = documentDao.insert(doc)
        historyDao.insert(
            ConversionHistoryEntity(
                toolTypeName = ConversionToolType.EXCEL_CONVERTER.name,
                sourceTitle = source.title,
                resultTitle = outputTitle,
                resultDocumentId = id,
                sourceFormatName = DocumentFormat.EXCEL.name,
                targetFormatName = DocumentFormat.WORD.name,
                pageCount = 1,
                fileSizeBytes = info.sizeBytes,
                durationMs = duration
            )
        )
        return doc.copy(id = id)
    }

    suspend fun compressImages(
        bitmaps: List<Bitmap>,
        qualityPercent: Int,
        maxDimensionPx: Int,
        outputTitle: String
    ): List<DocumentEntity> {
        val start = System.currentTimeMillis()
        val generated = engine.compressImages(bitmaps, qualityPercent, maxDimensionPx, outputTitle)
        val duration = (System.currentTimeMillis() - start).coerceAtLeast(80L)
        val now = System.currentTimeMillis()
        val created = mutableListOf<DocumentEntity>()
        generated.forEachIndexed { idx, info ->
            val title = if (generated.size == 1) outputTitle else "$outputTitle (${idx + 1})"
            val doc = DocumentEntity(
                title = title,
                filePath = info.file.absolutePath,
                formatName = DocumentFormat.IMAGE.name,
                sizeBytes = info.sizeBytes,
                pageCount = 1,
                folderName = "Compressed",
                summarySnippet = "Compressed JPEG (${qualityPercent}% quality) saved locally.",
                lastOpenedAt = now,
                createdAt = now
            )
            val id = documentDao.insert(doc)
            created.add(doc.copy(id = id))
        }
        if (created.isNotEmpty()) {
            historyDao.insert(
                ConversionHistoryEntity(
                    toolTypeName = ConversionToolType.COMPRESS_IMAGE.name,
                    sourceTitle = "${bitmaps.size} image(s) at ${qualityPercent}% quality",
                    resultTitle = if (created.size == 1) created.first().title else "${created.size} compressed images",
                    resultDocumentId = created.first().id,
                    sourceFormatName = DocumentFormat.IMAGE.name,
                    targetFormatName = DocumentFormat.IMAGE.name,
                    pageCount = created.size,
                    fileSizeBytes = created.sumOf { it.sizeBytes },
                    durationMs = duration
                )
            )
        }
        return created
    }

    suspend fun saveEditedDocument(
        source: DocumentEntity,
        newTitle: String,
        editedText: String,
        targetFormat: DocumentFormat,
        overwriteOriginal: Boolean,
        fontSizePt: Float = 13f
    ): DocumentEntity {
        val start = System.currentTimeMillis()
        val resolvedTitle = newTitle.trim().ifBlank { source.title }
        val info = when (targetFormat) {
            DocumentFormat.WORD -> engine.saveEditedWordDocx(resolvedTitle, editedText)
            else -> engine.textToPdf(
                title = resolvedTitle,
                bodyText = editedText,
                fontSizePt = fontSizePt,
                marginPt = 42
            )
        }
        val duration = (System.currentTimeMillis() - start).coerceAtLeast(80L)
        val now = System.currentTimeMillis()
        val snippet = editedText.trim().replace(Regex("\\s+"), " ").take(96)
            .ifBlank { "Edited in StudyLeaf Document Editor." }

        val savedDoc = if (overwriteOriginal && targetFormat == source.format) {
            val oldFile = File(source.filePath)
            val updated = source.copy(
                title = resolvedTitle,
                filePath = info.file.absolutePath,
                sizeBytes = info.sizeBytes,
                pageCount = info.pageCount,
                summarySnippet = snippet,
                lastOpenedAt = now
            )
            documentDao.update(updated)
            if (oldFile.exists() && oldFile.absolutePath != info.file.absolutePath) {
                runCatching { oldFile.delete() }
            }
            updated
        } else {
            val created = DocumentEntity(
                title = resolvedTitle,
                filePath = info.file.absolutePath,
                formatName = targetFormat.name,
                sizeBytes = info.sizeBytes,
                pageCount = info.pageCount,
                folderName = if (overwriteOriginal) source.folderName else "Edited",
                isFavorite = source.isFavorite,
                summarySnippet = snippet,
                lastOpenedAt = now,
                createdAt = now
            )
            val id = documentDao.insert(created)
            created.copy(id = id)
        }

        historyDao.insert(
            ConversionHistoryEntity(
                toolTypeName = ConversionToolType.WORD_TO_PDF.name,
                sourceTitle = source.title,
                resultTitle = resolvedTitle,
                resultDocumentId = savedDoc.id,
                sourceFormatName = source.formatName,
                targetFormatName = targetFormat.name,
                pageCount = info.pageCount,
                fileSizeBytes = info.sizeBytes,
                durationMs = duration
            )
        )
        return savedDoc
    }

    suspend fun warpScanPreview(
        source: Bitmap,
        corners: List<NormalizedPoint>,
        filterMode: ScannerFilterMode,
        enhancement: ScanEnhancementSettings = ScanEnhancementSettings()
    ): Bitmap = engine.perspectiveWarpAndEnhance(source, corners, filterMode, enhancement)
}
