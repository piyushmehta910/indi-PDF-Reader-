package com.example.data.repository

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import com.example.data.local.AnnotationEntity
import com.example.data.local.PdfDao
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.SignatureEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfRepository(
    private val context: Context,
    private val pdfDao: PdfDao
) {
    val allPdfs: Flow<List<PdfDocumentEntity>> = pdfDao.getAllPdfs()
    val favoritePdfs: Flow<List<PdfDocumentEntity>> = pdfDao.getFavoritePdfs()
    val allSignatures: Flow<List<SignatureEntity>> = pdfDao.getAllSignatures()

    fun getAnnotationsForPdf(pdfId: Int): Flow<List<AnnotationEntity>> =
        pdfDao.getAnnotationsForPdf(pdfId)

    suspend fun insertPdf(pdf: PdfDocumentEntity): Long = withContext(Dispatchers.IO) {
        pdfDao.insertPdf(pdf)
    }

    suspend fun updatePdf(pdf: PdfDocumentEntity) = withContext(Dispatchers.IO) {
        pdfDao.updatePdf(pdf)
    }

    suspend fun deletePdf(id: Int) = withContext(Dispatchers.IO) {
        // Find file path and delete file first
        val pdf = pdfDao.getPdfById(id)
        if (pdf != null) {
            val file = File(pdf.filePath)
            if (file.exists()) {
                file.delete()
            }
        }
        pdfDao.deletePdfById(id)
        pdfDao.clearAnnotationsForPdf(id)
    }

    suspend fun updateReadingProgress(id: Int, progressPage: Int) = withContext(Dispatchers.IO) {
        pdfDao.updateReadingProgress(id, progressPage, System.currentTimeMillis())
    }

    suspend fun toggleFavorite(id: Int, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        pdfDao.updateFavoriteStatus(id, isFavorite)
    }

    suspend fun togglePinned(id: Int, isPinned: Boolean) = withContext(Dispatchers.IO) {
        pdfDao.updatePinnedStatus(id, isPinned)
    }

    suspend fun updateTags(id: Int, tags: String) = withContext(Dispatchers.IO) {
        pdfDao.updateTags(id, tags)
    }

    suspend fun updateNotes(id: Int, notes: String) = withContext(Dispatchers.IO) {
        pdfDao.updateNotes(id, notes)
    }

    // --- Annotations ---
    suspend fun insertAnnotation(annotation: AnnotationEntity): Long = withContext(Dispatchers.IO) {
        pdfDao.insertAnnotation(annotation)
    }

    suspend fun deleteAnnotation(id: Int) = withContext(Dispatchers.IO) {
        pdfDao.deleteAnnotationById(id)
    }

    // --- Signatures ---
    suspend fun insertSignature(signature: SignatureEntity): Long = withContext(Dispatchers.IO) {
        pdfDao.insertSignature(signature)
    }

    suspend fun deleteSignature(id: Int) = withContext(Dispatchers.IO) {
        pdfDao.deleteSignatureById(id)
    }

    suspend fun setDefaultSignature(id: Int) = withContext(Dispatchers.IO) {
        pdfDao.setDefaultSignature(id)
    }

    // --- Core PDF Tool Actions ---

    /**
     * Merge multiple PDFs into one
     */
    suspend fun mergePdfs(pdfFiles: List<File>, targetFileName: String): File = withContext(Dispatchers.IO) {
        val outputFile = File(context.filesDir, targetFileName)
        val targetDocument = PdfDocument()

        var globalPageNumber = 1

        for (file in pdfFiles) {
            if (!file.exists()) continue
            try {
                // We render each page of the input PDF onto a new page of our output PDF
                val renderer = android.graphics.pdf.PdfRenderer(
                    android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                )

                for (i in 0 until renderer.pageCount) {
                    val originalPage = renderer.openPage(i)
                    
                    // Create new page with original dimensions
                    val newPageInfo = PdfDocument.PageInfo.Builder(originalPage.width, originalPage.height, globalPageNumber).create()
                    val newPage = targetDocument.startPage(newPageInfo)
                    
                    // Render original page into bitmap, then draw that bitmap on the canvas
                    val bitmap = android.graphics.Bitmap.createBitmap(originalPage.width, originalPage.height, android.graphics.Bitmap.Config.ARGB_8888)
                    originalPage.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    newPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    targetDocument.finishPage(newPage)
                    
                    originalPage.close()
                    bitmap.recycle()
                    globalPageNumber++
                }
                renderer.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        FileOutputStream(outputFile).use { out ->
            targetDocument.writeTo(out)
        }
        targetDocument.close()

        // Insert metadata in DB
        val entity = PdfDocumentEntity(
            title = targetFileName.replace(".pdf", "").replace("_", " "),
            filePath = outputFile.absolutePath,
            fileSize = outputFile.length(),
            pageCount = globalPageNumber - 1,
            lastViewedTimestamp = System.currentTimeMillis()
        )
        insertPdf(entity)

        outputFile
    }

    /**
     * Split a single PDF into one or more range files
     */
    suspend fun splitPdf(sourcePdf: PdfDocumentEntity, pageRanges: List<IntRange>, targetBaseName: String): List<File> = withContext(Dispatchers.IO) {
        val sourceFile = File(sourcePdf.filePath)
        if (!sourceFile.exists()) return@withContext emptyList<File>()

        val splitFiles = mutableListOf<File>()

        try {
            val renderer = android.graphics.pdf.PdfRenderer(
                android.os.ParcelFileDescriptor.open(sourceFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            )

            for ((index, range) in pageRanges.withIndex()) {
                val targetDocument = PdfDocument()
                val targetFileName = "${targetBaseName}_part_${index + 1}.pdf"
                val targetFile = File(context.filesDir, targetFileName)

                var localPageNum = 1
                for (pageNum in range) {
                    // Check bounds (0-indexed internally, pageRanges usually 1-indexed)
                    val internalPageIdx = pageNum - 1
                    if (internalPageIdx in 0 until renderer.pageCount) {
                        val originalPage = renderer.openPage(internalPageIdx)
                        
                        val newPageInfo = PdfDocument.PageInfo.Builder(originalPage.width, originalPage.height, localPageNum).create()
                        val newPage = targetDocument.startPage(newPageInfo)
                        
                        val bitmap = android.graphics.Bitmap.createBitmap(originalPage.width, originalPage.height, android.graphics.Bitmap.Config.ARGB_8888)
                        originalPage.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        
                        newPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                        targetDocument.finishPage(newPage)
                        
                        originalPage.close()
                        bitmap.recycle()
                        localPageNum++
                    }
                }

                if (localPageNum > 1) {
                    FileOutputStream(targetFile).use { out ->
                        targetDocument.writeTo(out)
                    }
                    splitFiles.add(targetFile)

                    // Save to DB
                    val entity = PdfDocumentEntity(
                        title = targetFileName.replace(".pdf", "").replace("_", " "),
                        filePath = targetFile.absolutePath,
                        fileSize = targetFile.length(),
                        pageCount = localPageNum - 1,
                        lastViewedTimestamp = System.currentTimeMillis()
                    )
                    insertPdf(entity)
                }
                targetDocument.close()
            }
            renderer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        splitFiles
    }

    /**
     * Compress a PDF (balanced, high-quality, small)
     */
    suspend fun compressPdf(sourcePdf: PdfDocumentEntity, mode: String): File = withContext(Dispatchers.IO) {
        val sourceFile = File(sourcePdf.filePath)
        val targetFileName = "${sourcePdf.title}_compressed_${mode.lowercase()}.pdf"
        val targetFile = File(context.filesDir, targetFileName)

        val targetDocument = PdfDocument()

        // Compression params
        val scaleFactor = when (mode.lowercase()) {
            "smallest" -> 0.5f // Downsample to 50% resolution
            "balanced" -> 0.75f // Downsample to 75% resolution
            else -> 0.9f // Downsample to 90%
        }
        val quality = when (mode.lowercase()) {
            "smallest" -> 45
            "balanced" -> 70
            else -> 90
        }

        try {
            val renderer = android.graphics.pdf.PdfRenderer(
                android.os.ParcelFileDescriptor.open(sourceFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            )

            for (i in 0 until renderer.pageCount) {
                val originalPage = renderer.openPage(i)
                
                val scaledWidth = (originalPage.width * scaleFactor).toInt().coerceAtLeast(100)
                val scaledHeight = (originalPage.height * scaleFactor).toInt().coerceAtLeast(100)
                
                val newPageInfo = PdfDocument.PageInfo.Builder(scaledWidth, scaledHeight, i + 1).create()
                val newPage = targetDocument.startPage(newPageInfo)
                
                val bitmap = android.graphics.Bitmap.createBitmap(originalPage.width, originalPage.height, android.graphics.Bitmap.Config.ARGB_8888)
                originalPage.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                
                // Scale bitmap using canvas or createScaledBitmap
                val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                
                newPage.canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
                targetDocument.finishPage(newPage)
                
                originalPage.close()
                bitmap.recycle()
                scaledBitmap.recycle()
            }
            renderer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        FileOutputStream(targetFile).use { out ->
            targetDocument.writeTo(out)
        }
        targetDocument.close()

        // Save in DB
        val entity = PdfDocumentEntity(
            title = targetFileName.replace(".pdf", "").replace("_", " "),
            filePath = targetFile.absolutePath,
            fileSize = targetFile.length(),
            pageCount = sourcePdf.pageCount,
            lastViewedTimestamp = System.currentTimeMillis()
        )
        insertPdf(entity)

        targetFile
    }

    /**
     * Create PDF from a list of image file paths (Image to PDF Conversion!)
     */
    suspend fun convertImagesToPdf(imagePaths: List<String>, targetFileName: String): File = withContext(Dispatchers.IO) {
        val targetFile = File(context.filesDir, targetFileName)
        val targetDocument = PdfDocument()

        for ((index, path) in imagePaths.withIndex()) {
            try {
                val file = File(path)
                if (!file.exists()) continue
                
                val options = android.graphics.BitmapFactory.Options().apply {
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
                val bitmap = android.graphics.BitmapFactory.decodeFile(path, options) ?: continue
                
                // standard A4 or image aspect ratio
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val page = targetDocument.startPage(pageInfo)
                
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                targetDocument.finishPage(page)
                
                bitmap.recycle()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        FileOutputStream(targetFile).use { out ->
            targetDocument.writeTo(out)
        }
        targetDocument.close()

        // Insert in DB
        val entity = PdfDocumentEntity(
            title = targetFileName.replace(".pdf", "").replace("_", " "),
            filePath = targetFile.absolutePath,
            fileSize = targetFile.length(),
            pageCount = imagePaths.size,
            lastViewedTimestamp = System.currentTimeMillis()
        )
        insertPdf(entity)

        targetFile
    }

    /**
     * Initialize sample PDFs if the library is empty
     */
    suspend fun initializeSamplePdfsIfNeeded() = withContext(Dispatchers.IO) {
        val currentPdfs = pdfDao.getAllPdfs().first()
        if (currentPdfs.isNotEmpty()) return@withContext

        // Programmatic Sample 1: Interactive User Agreement
        generateUserAgreementSample()

        // Programmatic Sample 2: AI Tech Research Brief
        generateAiBriefSample()

        // Programmatic Sample 3: Monthly Billing Invoice
        generateInvoiceSample()
    }

    private suspend fun generateUserAgreementSample() {
        val fileName = "Interactive_User_Agreement.pdf"
        val file = File(context.filesDir, fileName)
        if (file.exists()) file.delete()

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }
        
        // Background and borders
        canvas.drawColor(Color.WHITE)
        paint.color = Color.rgb(240, 244, 248)
        canvas.drawRect(Rect(10, 10, 585, 832), paint)
        paint.color = Color.WHITE
        canvas.drawRect(Rect(20, 20, 575, 822), paint)

        // Header
        paint.color = Color.rgb(20, 50, 90)
        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText("MUTUAL COOPERATION AGREEMENT", 40f, 70f, paint)

        paint.color = Color.rgb(100, 110, 120)
        paint.textSize = 9f
        paint.isFakeBoldText = false
        canvas.drawText("Document Ref: MCA-2026-X789 | Date: July 31, 2026", 40f, 90f, paint)
        
        paint.color = Color.rgb(200, 200, 200)
        canvas.drawLine(40f, 105f, 555f, 105f, paint)

        // Section 1
        paint.color = Color.rgb(20, 50, 90)
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("1. Parties and Scope", 40f, 130f, paint)

        paint.color = Color.BLACK
        paint.textSize = 10f
        paint.isFakeBoldText = false
        var y = 150f
        val sec1Lines = listOf(
            "This Agreement is entered into by and between the Client (referred to hereafter as",
            "'Party A') and the service provider (referred to hereafter as 'Party B'). Both parties",
            "agree to cooperate on development of high-performance mobile technologies, utilizing",
            "offline-first engineering patterns, on-device AI accelerators, and Material You designs.",
            "This document represents a legally binding agreement under local regulations."
        )
        for (line in sec1Lines) {
            canvas.drawText(line, 40f, y, paint)
            y += 18f
        }

        // Section 2: Form/Checkboxes representation
        paint.color = Color.rgb(20, 50, 90)
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("2. Programmatic Declarations", 40f, y + 10f, paint)
        y += 30f

        paint.color = Color.BLACK
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("Select all options below that apply to your technical stack:", 40f, y, paint)
        y += 20f

        // Checkbox 1
        paint.color = Color.rgb(100, 100, 100)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        canvas.drawRect(40f, y - 10f, dp52(40f), y + 2f, paint) // Box
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        canvas.drawText("Opt-in for On-device Processing (No Server-side uploads)", 60f, y, paint)
        y += 22f

        // Checkbox 2
        paint.color = Color.rgb(100, 100, 100)
        paint.style = Paint.Style.STROKE
        canvas.drawRect(40f, y - 10f, dp52(40f), y + 2f, paint)
        // Draw cross inside Box (simulate checked)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(20, 120, 80)
        canvas.drawLine(41f, y - 9f, dp52(40f), y + 1f, paint)
        canvas.drawLine(dp52(40f), y - 9f, 41f, y + 1f, paint)
        paint.color = Color.BLACK
        canvas.drawText("Material 3 Design Compliance Verified", 60f, y, paint)
        y += 30f

        // Section 3: Signature Area
        paint.color = Color.rgb(20, 50, 90)
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("3. Execution & Authorization", 40f, y, paint)
        y += 20f

        paint.color = Color.BLACK
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("In Witness Whereof, the parties hereto execute this Agreement on the date written below.", 40f, y, paint)
        y += 40f

        // Signature Lines
        paint.color = Color.rgb(150, 150, 150)
        canvas.drawLine(40f, y, 240f, y, paint)
        canvas.drawLine(315f, y, 515f, y, paint)

        paint.color = Color.BLACK
        paint.textSize = 9f
        canvas.drawText("Representative Signature (Party A)", 40f, y + 15f, paint)
        canvas.drawText("Representative Signature (Party B)", 315f, y + 15f, paint)

        pdfDocument.finishPage(page)
        
        // Write file
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        val entity = PdfDocumentEntity(
            title = "Interactive User Agreement",
            filePath = file.absolutePath,
            fileSize = file.length(),
            pageCount = 1,
            lastViewedTimestamp = System.currentTimeMillis() - 100000,
            isSample = true,
            tags = "Agreement,Legal"
        )
        insertPdf(entity)
    }

    private fun dp52(value: Float): Float = value + 12f

    private suspend fun generateAiBriefSample() {
        val fileName = "AI_Overview_Brief.pdf"
        val file = File(context.filesDir, fileName)
        if (file.exists()) file.delete()

        val pdfDocument = PdfDocument()
        
        // Page 1
        val pageInfo1 = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page1 = pdfDocument.startPage(pageInfo1)
        val canvas1 = page1.canvas
        canvas1.drawColor(Color.WHITE)

        val paint = Paint().apply { isAntiAlias = true }
        
        // Title
        paint.color = Color.rgb(80, 20, 120)
        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas1.drawText("Gemini 3.5 & On-Device AI Models", 40f, 70f, paint)

        paint.color = Color.rgb(120, 100, 140)
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas1.drawText("Technology Brief | Google AI Studio Ecosystem", 40f, 90f, paint)

        paint.color = Color.rgb(230, 220, 240)
        canvas1.drawRect(Rect(40, 110, 555, 114), paint)

        // Intro
        paint.color = Color.BLACK
        paint.textSize = 11f
        var y = 140f
        val intro = listOf(
            "Welcome to the Tech Briefing on on-device and cloud-based hybrid AI processing.",
            "Modern user experiences require both rapid execution (local models) and deep reasoning",
            "capabilities (cloud models like Gemini 3.5 Flash). This brief outlines the distinct",
            "strengths of each approach, helping developers construct privacy-first architectures.",
            "By performing 95% of tasks offline, apps reduce network fees and safeguard client secrets."
        )
        for (line in intro) {
            canvas1.drawText(line, 40f, y, paint)
            y += 18f
        }

        // Subtitle: Cloud Processing
        paint.color = Color.rgb(80, 20, 120)
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas1.drawText("1. Deep Reasoning with Gemini 3.5 Flash", 40f, y + 15f, paint)
        y += 35f

        paint.color = Color.BLACK
        paint.textSize = 11f
        paint.isFakeBoldText = false
        val gLines = listOf(
            "Gemini 3.5 Flash represents a massive step forward in high-performance cloud intelligence.",
            "With a vast context window, it excels at summarization, structured JSON responses, table",
            "extraction, and multilingual translation. For PDF manipulation, Gemini 3.5 can parse entire",
            "document structures, answer complex contextual queries, and generate interactive flashcards",
            "based on reading materials."
        )
        for (line in gLines) {
            canvas1.drawText(line, 40f, y, paint)
            y += 18f
        }

        // Subtitle: Local Processing
        paint.color = Color.rgb(80, 20, 120)
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas1.drawText("2. Local Processing & Offline Security", 40f, y + 15f, paint)
        y += 35f

        val localLines = listOf(
            "Offline operation is critical for core tasks like annotations, digital signatures, and",
            "PDF merging. When a document is processed locally, data never leaves the device's storage.",
            "This complies with HIPAA, GDPR, and general corporate security policies.",
            "Additionally, offline performance delivers sub-millisecond drawing and rotation latency."
        )
        paint.color = Color.BLACK
        paint.textSize = 11f
        paint.isFakeBoldText = false
        for (line in localLines) {
            canvas1.drawText(line, 40f, y, paint)
            y += 18f
        }

        pdfDocument.finishPage(page1)

        // Page 2
        val pageInfo2 = PdfDocument.PageInfo.Builder(595, 842, 2).create()
        val page2 = pdfDocument.startPage(pageInfo2)
        val canvas2 = page2.canvas
        canvas2.drawColor(Color.WHITE)

        // Subtitle: Performance Metrics
        paint.color = Color.rgb(80, 20, 120)
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas2.drawText("3. Performance Benchmarks", 40f, 60f, paint)

        paint.color = Color.BLACK
        paint.textSize = 11f
        paint.isFakeBoldText = false
        canvas2.drawText("The following values represent standard latency across modern mobile chipsets:", 40f, 80f, paint)

        // Draw simple table representation
        var ty = 110f
        paint.color = Color.rgb(240, 240, 245)
        canvas2.drawRect(Rect(40, ty.toInt(), 555, (ty + 24f).toInt()), paint) // Header bg
        
        paint.color = Color.BLACK
        paint.isFakeBoldText = true
        paint.textSize = 10f
        canvas2.drawText("Operation", 50f, ty + 16f, paint)
        canvas2.drawText("On-Device (Offline)", 250f, ty + 16f, paint)
        canvas2.drawText("Cloud Engine", 420f, ty + 16f, paint)
        ty += 24f

        val data = listOf(
            Triple("Cold Startup", "< 500 ms", "N/A"),
            Triple("Document Render", "Incremental (10ms/page)", "N/A"),
            Triple("Merge & Split", "< 200 ms", "N/A"),
            Triple("AI Summarization", "Local Nano (2.4s)", "Gemini 3.5 (1.1s)"),
            Triple("OCR Text Extraction", "Local ML Kit (180ms)", "Google Vision (1.2s)")
        )

        paint.isFakeBoldText = false
        for (row in data) {
            paint.color = Color.rgb(230, 230, 230)
            canvas2.drawLine(40f, ty + 24f, 555f, ty + 24f, paint)
            
            paint.color = Color.BLACK
            canvas2.drawText(row.first, 50f, ty + 16f, paint)
            canvas2.drawText(row.second, 250f, ty + 16f, paint)
            canvas2.drawText(row.third, 420f, ty + 16f, paint)
            ty += 24f
        }

        // Summary note
        paint.textSize = 11f
        canvas2.drawText("Conclusion: A hybrid system guarantees unmatched performance and ironclad security.", 40f, ty + 40f, paint)

        pdfDocument.finishPage(page2)

        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        val entity = PdfDocumentEntity(
            title = "AI Overview Brief",
            filePath = file.absolutePath,
            fileSize = file.length(),
            pageCount = 2,
            lastViewedTimestamp = System.currentTimeMillis() - 200000,
            isSample = true,
            tags = "Briefing,AI"
        )
        insertPdf(entity)
    }

    private suspend fun generateInvoiceSample() {
        val fileName = "Receipt_Invoice_794.pdf"
        val file = File(context.filesDir, fileName)
        if (file.exists()) file.delete()

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply { isAntiAlias = true }

        // Top banner block
        paint.color = Color.rgb(10, 110, 90)
        canvas.drawRect(Rect(0, 0, 595, 80), paint)

        paint.color = Color.WHITE
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("PDF APP CONCEPTS INC.", 40f, 48f, paint)
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("Invoice #794-A | High Fidelity Offline Tools", 40f, 65f, paint)

        // Billed To
        paint.color = Color.BLACK
        paint.textSize = 12f
        paint.isFakeBoldText = true
        var y = 120f
        canvas.drawText("BILLED TO:", 40f, y, paint)
        y += 18f
        paint.isFakeBoldText = false
        paint.textSize = 10f
        canvas.drawText("Gemini Android Prototype Workspace", 40f, y, paint)
        y += 15f
        canvas.drawText("AI Studio Platform Developer Account", 40f, y, paint)
        y += 15f
        canvas.drawText("Support Desk: developer-team@example.com", 40f, y, paint)
        
        // Invoice Date / Due
        y = 120f
        paint.isFakeBoldText = true
        paint.textSize = 11f
        canvas.drawText("INVOICE DATE:", 350f, y, paint)
        paint.isFakeBoldText = false
        paint.textSize = 10f
        canvas.drawText("July 31, 2026", 350f, y + 15f, paint)

        canvas.drawText("STATUS: PAID", 350f, y + 40f, paint)

        // Line item table header
        y = 200f
        paint.color = Color.rgb(230, 245, 240)
        canvas.drawRect(Rect(40, y.toInt(), 555, (y + 24).toInt()), paint)

        paint.color = Color.BLACK
        paint.isFakeBoldText = true
        canvas.drawText("Description", 50f, y + 16f, paint)
        canvas.drawText("Qty", 350f, y + 16f, paint)
        canvas.drawText("Unit Price", 400f, y + 16f, paint)
        canvas.drawText("Total", 490f, y + 16f, paint)
        y += 24f

        val items = listOf(
            Pair("Base Offline PDF Rendering Engine license", 1),
            Pair("Secure Hand-Drawn Signatures module", 1),
            Pair("On-Device PDF Compressor (Triple profile)", 1),
            Pair("Gemini 3.5 AI Summarizer and Q&A Integration", 1)
        )

        paint.isFakeBoldText = false
        for (item in items) {
            paint.color = Color.rgb(240, 240, 240)
            canvas.drawLine(40f, y + 24f, 555f, y + 24f, paint)

            paint.color = Color.BLACK
            canvas.drawText(item.first, 50f, y + 16f, paint)
            canvas.drawText(item.second.toString(), 350f, y + 16f, paint)
            canvas.drawText("FREE", 400f, y + 16f, paint)
            canvas.drawText("$0.00", 490f, y + 16f, paint)
            y += 24f
        }

        // Total
        y += 20f
        paint.color = Color.rgb(10, 110, 90)
        canvas.drawRect(Rect(320, y.toInt(), 555, (y + 30).toInt()), paint)
        
        paint.color = Color.WHITE
        paint.isFakeBoldText = true
        paint.textSize = 11f
        canvas.drawText("GRAND TOTAL:  $0.00  (FREE)", 335f, y + 18f, paint)

        // Footer note
        paint.color = Color.rgb(120, 120, 120)
        paint.textSize = 9f
        paint.isFakeBoldText = false
        canvas.drawText("Thank you for using PDF Reader & Tools. Build beautifully!", 40f, 780f, paint)

        pdfDocument.finishPage(page)

        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        val entity = PdfDocumentEntity(
            title = "Receipt Invoice 794",
            filePath = file.absolutePath,
            fileSize = file.length(),
            pageCount = 1,
            lastViewedTimestamp = System.currentTimeMillis() - 300000,
            isSample = true,
            tags = "Receipt,Finance"
        )
        insertPdf(entity)
    }
}
