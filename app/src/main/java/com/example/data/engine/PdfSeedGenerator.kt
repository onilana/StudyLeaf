package com.example.data.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfSeedGenerator {

    data class SeededDocSpec(
        val title: String,
        val file: File,
        val formatName: String,
        val pageCount: Int,
        val folderName: String,
        val isFavorite: Boolean,
        val summarySnippet: String
    )

    suspend fun generateStarterLibrary(context: Context): List<SeededDocSpec> =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }
            val results = mutableListOf<SeededDocSpec>()

            val botanyFile = File(dir, "Botanical_Cell_Biology_Notes.pdf")
            createAcademicPdf(
                file = botanyFile,
                badge = "BIOLOGY 204  •  LECTURE SERIES",
                title = "Botanical Cell Biology & Chloroplast Structure",
                accentHex = "#012D1D",
                pages = listOf(
                    PageSpec(
                        heading = "1. Thylakoid Membrane Architecture",
                        paragraphs = listOf(
                            "Chloroplasts are double-membrane organelles responsible for photosynthetic energy transduction in terrestrial flora. Within the aqueous stroma lies a continuous lamellar network of flattened sacs called thylakoids.",
                            "Stacked regions known as grana concentrate Photosystem II complexes, whereas unstacked stroma lamellae preferentially house Photosystem I and ATP synthase rotors to prevent steric crowding."
                        ),
                        calloutTitle = "Key Examination Note",
                        calloutBody = "Proton-motive force across the thylakoid lumen drives CF0-CF1 ATP synthase at a stoichiometry of roughly 4.67 H+ per ATP synthesized.",
                        diagramStyle = DiagramStyle.CELL
                    ),
                    PageSpec(
                        heading = "2. Carbon Fixation & Calvin-Benson Cycle",
                        paragraphs = listOf(
                            "Ribulose-1,5-bisphosphate carboxylase/oxygenase (RuBisCO) catalyzes the primary entry of atmospheric CO2 into organic carbon skeletons, producing two molecules of 3-phosphoglycerate.",
                            "Regeneration of RuBP consumes ATP and NADPH derived from the light-dependent reactions, balancing cellular redox poise under fluctuating canopy irradiance."
                        ),
                        calloutTitle = "Stoichiometric Balance",
                        calloutBody = "3 CO2 + 9 ATP + 6 NADPH + 5 H2O → G3P + 9 ADP + 8 Pi + 6 NADP+",
                        diagramStyle = DiagramStyle.CYCLE
                    ),
                    PageSpec(
                        heading = "3. Stomatal Conductance & Water Potential",
                        paragraphs = listOf(
                            "Guard cell turgor is modulated by blue-light activated H+-ATPases that hyperpolarize the plasma membrane, triggering inward K+ influx and osmotic swelling.",
                            "Under drought stress, abscisic acid (ABA) activates anion efflux channels, rapidly closing stomatal pores to preserve leaf hydraulic conductance."
                        ),
                        calloutTitle = "Lab Observation Checklist",
                        calloutBody = "Measure epidermal peel aperture across 0, 50, and 200 µmol·m⁻²·s⁻¹ irradiance intervals using calibrated ocular micrometers.",
                        diagramStyle = DiagramStyle.GRAPH
                    )
                )
            )
            results.add(
                SeededDocSpec(
                    title = "Botanical Cell Biology Notes",
                    file = botanyFile,
                    formatName = "PDF",
                    pageCount = 3,
                    folderName = "Lectures",
                    isFavorite = true,
                    summarySnippet = "Chloroplast thylakoid architecture, Calvin cycle stoichiometry, and stomatal conductance."
                )
            )

            val chemFile = File(dir, "Organic_Chemistry_Lab_Manual.pdf")
            createAcademicPdf(
                file = chemFile,
                badge = "CHEM 312  •  SYNTHESIS MANUAL",
                title = "Organic Chemistry Lab Manual: Esterification",
                accentHex = "#D9383A",
                pages = listOf(
                    PageSpec(
                        heading = "Experiment 04: Fischer Esterification",
                        paragraphs = listOf(
                            "Carboxylic acids react reversibly with primary alcohols in the presence of catalytic sulfuric acid to afford esters and water. Driving equilibrium toward product requires either excess alcohol or azeotropic water removal.",
                            "Assemble a 100 mL round-bottom flask fitted with a water-cooled reflux condenser. Maintain gentle boiling for 60 minutes while monitoring vapor ring height."
                        ),
                        calloutTitle = "Safety & Isolation Protocol",
                        calloutBody = "Quench cooled mixture into chilled saturated NaHCO3 slowly to neutralize residual acid. Dry organic layer over anhydrous MgSO4.",
                        diagramStyle = DiagramStyle.GRAPH
                    ),
                    PageSpec(
                        heading = "Infrared Spectroscopy Verification",
                        paragraphs = listOf(
                            "Confirm conversion by inspecting the sharp C=O stretching absorption near 1740 cm⁻¹ alongside the disappearance of the broad carboxylic acid O–H envelope (2500–3300 cm⁻¹).",
                            "Calculate percent yield from the limiting reagent and compare boiling point against literature constants."
                        ),
                        calloutTitle = "Expected Peaks",
                        calloutBody = "C=O Ester: 1735–1750 cm⁻¹ (strong)  |  C–O Stretch: 1180–1240 cm⁻¹",
                        diagramStyle = DiagramStyle.CYCLE
                    )
                )
            )
            results.add(
                SeededDocSpec(
                    title = "Organic Chemistry Lab Manual",
                    file = chemFile,
                    formatName = "PDF",
                    pageCount = 2,
                    folderName = "Labs",
                    isFavorite = true,
                    summarySnippet = "Fischer esterification reflux procedure, neutralization protocol, and IR verification."
                )
            )

            val mathFile = File(dir, "Linear_Algebra_Problem_Set_4.pdf")
            createAcademicPdf(
                file = mathFile,
                badge = "MATH 240  •  SCANNED HANDOUT",
                title = "Linear Algebra: Eigenvalues & Spectral Theorem",
                accentHex = "#0E8388",
                pages = listOf(
                    PageSpec(
                        heading = "Problem Set 4 — Orthogonal Diagonalization",
                        paragraphs = listOf(
                            "Theorem (Real Spectral Theorem): Every real symmetric n × n matrix A admits an orthonormal basis of eigenvectors in R^n, so that A = Q Λ Qᵀ where Q is orthogonal and Λ is diagonal.",
                            "Exercise 1: Compute the characteristic polynomial det(A − λI) for the 3 × 3 tridiagonal symmetric matrix and verify orthogonality of distinct eigenspaces."
                        ),
                        calloutTitle = "Submission Reminder",
                        calloutBody = "Show all Gram-Schmidt normalization steps explicitly for repeated eigenvalues.",
                        diagramStyle = DiagramStyle.GRAPH
                    ),
                    PageSpec(
                        heading = "Singular Value Decomposition Preview",
                        paragraphs = listOf(
                            "For any real m × n matrix M, the factorization M = U Σ Vᵀ generalizes eigendecomposition to rectangular linear maps, with singular values σᵢ = √λᵢ(MᵀM).",
                            "Low-rank truncated SVD provides the optimal Frobenius-norm approximation by the Eckart-Young-Mirsky theorem."
                        ),
                        calloutTitle = "Geometric Interpretation",
                        calloutBody = "Every linear map is a rotation (Vᵀ), axis-aligned scaling (Σ), followed by a second rotation (U).",
                        diagramStyle = DiagramStyle.CELL
                    )
                )
            )
            results.add(
                SeededDocSpec(
                    title = "Linear Algebra Problem Set 4",
                    file = mathFile,
                    formatName = "SCAN",
                    pageCount = 2,
                    folderName = "Scans",
                    isFavorite = false,
                    summarySnippet = "Real spectral theorem, orthogonal diagonalization exercises, and SVD preview."
                )
            )

            val notesFile = File(dir, "Field_Research_Summary.txt")
            notesFile.writeText(
                """
                FIELD ECOLOGY RESEARCH SUMMARY — CASCADE WATERSHED

                Date: Autumn Quarter Survey
                Location: Upper Riparian Transect B4

                1. Canopy Cover & Understory Microclimate
                Old-growth Douglas-fir and western hemlock stands maintained an average midday soil temperature 4.2 °C cooler than adjacent clearings. Bryophyte mat density correlated strongly with ambient relative humidity (r = 0.86).

                2. Sampling Methodology
                Twelve 10m x 10m quadrats were surveyed along an elevation gradient from 420m to 780m. Leaf area index (LAI) was captured optically at dawn to minimize direct solar glare.

                3. Next Steps for Final Report
                - Export this document to PDF via StudyLeaf Word & Text tool.
                - Merge with scanned topographic quadrat maps.
                - Annotate outlier soil moisture points before Friday seminar.
                """.trimIndent()
            )
            results.add(
                SeededDocSpec(
                    title = "Field Research Summary",
                    file = notesFile,
                    formatName = "WORD",
                    pageCount = 1,
                    folderName = "Lectures",
                    isFavorite = false,
                    summarySnippet = "Cascade watershed riparian survey notes ready for one-tap PDF typesetting."
                )
            )

            val plateFile = File(dir, "Microscope_Slide_Plate_07.png")
            createBotanicalPlateImage(plateFile)
            results.add(
                SeededDocSpec(
                    title = "Microscope Slide Plate 07",
                    file = plateFile,
                    formatName = "IMAGE",
                    pageCount = 1,
                    folderName = "Labs",
                    isFavorite = false,
                    summarySnippet = "High-contrast vascular bundle cross-section plate ready for Image-to-PDF conversion."
                )
            )

            val excelFile = File(dir, "Lab_Data_Measurements.xlsx")
            ExcelAndImageEngine.writeXlsxWorkbook(
                sheetName = "Botany Lab",
                rows = listOf(
                    listOf("Trial", "Irradiance (umol)", "Aperture (um)", "Temp (C)"),
                    listOf("Sample A", "0", "2.4", "21.2"),
                    listOf("Sample B", "50", "5.8", "21.6"),
                    listOf("Sample C", "120", "8.9", "22.1"),
                    listOf("Sample D", "200", "11.4", "22.5"),
                    listOf("Sample E", "300", "12.1", "23.0")
                ),
                destFile = excelFile
            )
            results.add(
                SeededDocSpec(
                    title = "Lab Data Measurements",
                    file = excelFile,
                    formatName = "EXCEL",
                    pageCount = 1,
                    folderName = "Labs",
                    isFavorite = false,
                    summarySnippet = "Stomatal irradiance trial spreadsheet ready for Excel-to-PDF or Excel-to-Word export."
                )
            )

            results
        }

    fun generateSampleSkewedDeskBitmap(): Bitmap {
        val w = 960
        val h = 1280
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Warm wooden desk background
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                Color.parseColor("#2B302D"),
                Color.parseColor("#181C1A"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        // Subtle desk grain lines
        val grainPaint = Paint().apply {
            color = Color.parseColor("#22FFFFFF")
            strokeWidth = 2f
        }
        for (i in 0..24) {
            val y = i * 56f
            canvas.drawLine(0f, y, w.toFloat(), y + 18f, grainPaint)
        }

        // Skewed paper quadrilateral simulating an angled desk photograph
        val paperPath = Path().apply {
            moveTo(w * 0.14f, h * 0.14f)
            lineTo(w * 0.88f, h * 0.11f)
            lineTo(w * 0.92f, h * 0.88f)
            lineTo(w * 0.10f, h * 0.85f)
            close()
        }
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#55000000")
        }
        canvas.save()
        canvas.translate(10f, 14f)
        canvas.drawPath(paperPath, shadowPaint)
        canvas.restore()

        val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F7F6F0")
        }
        canvas.drawPath(paperPath, paperPaint)

        // Draw realistic study notes onto the skewed sheet
        canvas.save()
        canvas.rotate(-1.8f, w * 0.5f, h * 0.5f)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#012D1D")
            textSize = 36f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#006C48")
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#242926")
            textSize = 24f
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#D5DDD7")
            strokeWidth = 2f
        }

        canvas.drawText("SEMINAR HANDOUT  •  WEEK 09", w * 0.19f, h * 0.21f, subPaint)
        canvas.drawText("Plant Physiology: Phloem Transport", w * 0.19f, h * 0.255f, headerPaint)
        canvas.drawLine(w * 0.19f, h * 0.275f, w * 0.83f, h * 0.275f, linePaint)

        val notes = listOf(
            "1. Pressure-Flow Hypothesis (Münch Model):",
            "   Sucrose is actively loaded into sieve-tube elements",
            "   at photosynthetic source leaves via H+/sucrose",
            "   symporters, lowering osmotic potential.",
            "",
            "2. Bulk Hydrostatic Gradient:",
            "   Water enters from adjacent xylem vessels, generating",
            "   positive turgor pressure that drives sap toward",
            "   metabolic sinks (roots, meristems, developing fruit).",
            "",
            "3. Key Equations to Remember:",
            "   Ψw = Ψs + Ψp    |    Jv = Lp · ΔP"
        )
        var y = h * 0.33f
        for (line in notes) {
            canvas.drawText(line, w * 0.19f, y, bodyPaint)
            y += 40f
        }

        // Botanical highlight box on paper
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E3F5EB")
        }
        canvas.drawRoundRect(
            RectF(w * 0.19f, h * 0.72f, w * 0.82f, h * 0.81f),
            16f, 16f, boxPaint
        )
        canvas.drawText("Exam Focus: Compare apoplastic vs symplastic loading", w * 0.22f, h * 0.775f, subPaint)
        canvas.restore()

        return bmp
    }

    private enum class DiagramStyle { CELL, CYCLE, GRAPH }

    private data class PageSpec(
        val heading: String,
        val paragraphs: List<String>,
        val calloutTitle: String,
        val calloutBody: String,
        val diagramStyle: DiagramStyle
    )

    private fun createAcademicPdf(
        file: File,
        badge: String,
        title: String,
        accentHex: String,
        pages: List<PageSpec>
    ) {
        val pdf = PdfDocument()
        val w = 595
        val h = 842
        val margin = 46f
        val accent = Color.parseColor(accentHex)

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1C1E")
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#012D1D")
            textSize = 15.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2A2F2C")
            textSize = 12.2f
        }
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#D8E0DA")
            strokeWidth = 1f
        }

        try {
            pages.forEachIndexed { idx, spec ->
                val pageInfo = PdfDocument.PageInfo.Builder(w, h, idx + 1).create()
                val page = pdf.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                // Top subtle botanical accent bar
                val barPaint = Paint().apply { color = accent }
                canvas.drawRect(0f, 0f, w.toFloat(), 6f, barPaint)

                var y = 48f
                canvas.drawText(badge, margin, y, badgePaint)
                y += 26f
                if (idx == 0) {
                    y = drawWrappedText(canvas, title, margin, y, w - margin * 2, titlePaint, 28f)
                    y += 8f
                    canvas.drawLine(margin, y, w - margin, y, dividerPaint)
                    y += 26f
                }

                canvas.drawText(spec.heading, margin, y, headingPaint)
                y += 24f

                for (para in spec.paragraphs) {
                    y = drawWrappedText(canvas, para, margin, y, w - margin * 2, bodyPaint, 19f)
                    y += 12f
                }

                // Visual study diagram box
                val diagramRect = RectF(margin, y + 6f, w - margin, y + 178f)
                drawStudyDiagram(canvas, diagramRect, spec.diagramStyle, accent)
                y = diagramRect.bottom + 24f

                // Callout container
                val calloutRect = RectF(margin, y, w - margin, y + 88f)
                val calloutBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#F1F5F2")
                }
                val calloutStripe = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
                canvas.drawRoundRect(calloutRect, 12f, 12f, calloutBg)
                canvas.drawRoundRect(
                    RectF(calloutRect.left, calloutRect.top, calloutRect.left + 5f, calloutRect.bottom),
                    4f, 4f, calloutStripe
                )
                canvas.drawText(spec.calloutTitle, calloutRect.left + 18f, calloutRect.top + 24f, badgePaint)
                drawWrappedText(
                    canvas,
                    spec.calloutBody,
                    calloutRect.left + 18f,
                    calloutRect.top + 46f,
                    calloutRect.width() - 32f,
                    bodyPaint,
                    17f
                )

                // Footer
                canvas.drawLine(margin, h - 46f, w - margin, h - 46f, dividerPaint)
                val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#717973")
                    textSize = 10f
                }
                canvas.drawText("StudyLeaf Offline Library", margin, h - 26f, footerPaint)
                canvas.drawText("Page ${idx + 1} of ${pages.size}", w - margin - 62f, h - 26f, footerPaint)

                pdf.finishPage(page)
            }
            FileOutputStream(file).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }

    private fun drawStudyDiagram(canvas: Canvas, rect: RectF, style: DiagramStyle, accent: Int) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F7FAF8") }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D1DDD6")
            this.style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        canvas.drawRoundRect(rect, 14f, 14f, bgPaint)
        canvas.drawRoundRect(rect, 14f, 14f, borderPaint)

        val primaryFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#92F7C3") }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            this.style = Paint.Style.STROKE
            strokeWidth = 2.2f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#414844")
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        when (style) {
            DiagramStyle.CELL -> {
                val outer = RectF(rect.left + 50f, rect.top + 28f, rect.right - 50f, rect.bottom - 30f)
                canvas.drawOval(outer, primaryFill)
                canvas.drawOval(outer, stroke)
                val stackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B4332") }
                for (col in 0..2) {
                    val cx = rect.left + 135f + col * 115f
                    for (row in 0..3) {
                        val cy = rect.top + 54f + row * 16f
                        canvas.drawRoundRect(RectF(cx - 34f, cy, cx + 34f, cy + 10f), 5f, 5f, stackPaint)
                    }
                }
                canvas.drawText("FIG. A — THYLAKOID GRANA & STROMA LAMELLAE", rect.left + 20f, rect.bottom - 10f, labelPaint)
            }
            DiagramStyle.CYCLE -> {
                val cx = rect.centerX()
                val cy = rect.centerY() - 6f
                canvas.drawCircle(cx, cy, 48f, stroke)
                val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
                canvas.drawCircle(cx, cy - 48f, 10f, nodePaint)
                canvas.drawCircle(cx + 42f, cy + 24f, 10f, nodePaint)
                canvas.drawCircle(cx - 42f, cy + 24f, 10f, nodePaint)
                canvas.drawText("Carbon Fixation", cx - 36f, cy - 62f, labelPaint)
                canvas.drawText("Reduction", cx + 56f, cy + 28f, labelPaint)
                canvas.drawText("Regeneration", cx - 118f, cy + 28f, labelPaint)
            }
            DiagramStyle.GRAPH -> {
                val left = rect.left + 44f
                val bottom = rect.bottom - 34f
                val right = rect.right - 36f
                val top = rect.top + 24f
                canvas.drawLine(left, bottom, right, bottom, stroke)
                canvas.drawLine(left, bottom, left, top, stroke)
                val curve = Path().apply {
                    moveTo(left + 12f, bottom - 12f)
                    cubicTo(left + 90f, bottom - 95f, right - 140f, top + 12f, right - 16f, top + 24f)
                }
                canvas.drawPath(curve, stroke)
                canvas.drawText("FIG. B — EMPIRICAL RESPONSE CURVE", left + 8f, rect.bottom - 12f, labelPaint)
            }
        }
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float
    ): Float {
        var y = startY
        var line = ""
        for (word in text.split(Regex("\\s+"))) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(test) <= maxWidth) {
                line = test
            } else {
                canvas.drawText(line, x, y, paint)
                y += lineHeight
                line = word
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line, x, y, paint)
            y += lineHeight
        }
        return y
    }

    private fun createBotanicalPlateImage(file: File) {
        val w = 900
        val h = 1120
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#F5F8F6"))

        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#012D1D")
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRoundRect(RectF(42f, 42f, w - 42f, h - 42f), 28f, 28f, framePaint)

        // Botanical specimen cross-section illustration
        val cx = w / 2f
        val cy = h * 0.45f
        val outerRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C1ECD4") }
        val innerRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E7ECE8") }
        val xylemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#006C48") }
        val phloemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E58E26") }

        canvas.drawCircle(cx, cy, 260f, outerRing)
        canvas.drawCircle(cx, cy, 210f, innerRing)
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0)
            val bx = cx + (155f * kotlin.math.cos(angle)).toFloat()
            val by = cy + (155f * kotlin.math.sin(angle)).toFloat()
            canvas.drawCircle(bx, by, 34f, xylemPaint)
            val px = cx + (192f * kotlin.math.cos(angle)).toFloat()
            val py = cy + (192f * kotlin.math.sin(angle)).toFloat()
            canvas.drawCircle(px, py, 16f, phloemPaint)
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#012D1D")
            textSize = 36f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#414844")
            textSize = 22f
        }
        canvas.drawText("PLATE 07 — DICOT STEM VASCULAR RING", 90f, h * 0.80f, titlePaint)
        canvas.drawText("40x Brightfield  •  Xylem (Emerald) & Phloem (Amber)", 90f, h * 0.85f, subPaint)

        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
        bmp.recycle()
    }
}
