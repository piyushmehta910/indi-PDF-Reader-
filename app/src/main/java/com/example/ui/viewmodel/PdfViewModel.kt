package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.local.AnnotationEntity
import com.example.data.local.AppDatabase
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.SignatureEntity
import com.example.data.repository.PdfRepository
import com.example.util.PdfTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

enum class AppScreen {
    Library, Reader, Tools, MergeTool, SplitTool, CompressTool, SignTool,
    ImageToPdfTool, EncryptTool, OcrTool, ScannerTool, AIChat, Settings
}

data class Message(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class PdfViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PdfRepository
    
    // UI Settings state
    private val _isDarkTheme = MutableStateFlow<Boolean?>(null) // null means follow system
    val isDarkTheme = _isDarkTheme.asStateFlow()

    private val _isAmoledMode = MutableStateFlow(false)
    val isAmoledMode = _isAmoledMode.asStateFlow()

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    // Navigation and core state
    private val _currentScreen = MutableStateFlow(AppScreen.Library)
    val currentScreen = _currentScreen.asStateFlow()

    private val _selectedPdf = MutableStateFlow<PdfDocumentEntity?>(null)
    val selectedPdf = _selectedPdf.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex = _currentPageIndex.asStateFlow()

    // Search & Organization
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag = _selectedTag.asStateFlow()

    // PDF lists and signatures from DB
    val allPdfs: StateFlow<List<PdfDocumentEntity>>
    val favoritePdfs: StateFlow<List<PdfDocumentEntity>>
    val allSignatures: StateFlow<List<SignatureEntity>>

    // Active reading annotations
    private val _activeAnnotations = MutableStateFlow<List<AnnotationEntity>>(emptyList())
    val activeAnnotations = _activeAnnotations.asStateFlow()

    // AI Chat state
    private val _aiModel = MutableStateFlow("online_gemini") // online_gemini or local_nano
    val aiModel = _aiModel.asStateFlow()

    private val _chatMessages = MutableStateFlow<Map<Int, List<Message>>>(emptyMap()) // pdfId -> MessageList
    val chatMessages = _chatMessages.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading = _isAiLoading.asStateFlow()

    // Render Cache for PDFs
    private val _renderedPages = MutableStateFlow<List<Bitmap>>(emptyList())
    val renderedPages = _renderedPages.asStateFlow()

    private val _isRendering = MutableStateFlow(false)
    val isRendering = _isRendering.asStateFlow()

    // Interactive Drawing Stroke for Reader signature/pen
    private val _drawingPoints = MutableStateFlow<List<Offset>>(emptyList())
    val drawingPoints = _drawingPoints.asStateFlow()

    // Selected files for Merging
    private val _mergeSelectedFiles = MutableStateFlow<List<PdfDocumentEntity>>(emptyList())
    val mergeSelectedFiles = _mergeSelectedFiles.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = PdfRepository(application, database.pdfDao())

        allPdfs = repository.allPdfs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        favoritePdfs = repository.favoritePdfs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allSignatures = repository.allSignatures.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Initialize sample PDFs if empty
        viewModelScope.launch {
            repository.initializeSamplePdfsIfNeeded()
        }

        // Setup annotations observer for active PDF
        viewModelScope.launch {
            selectedPdf.collectLatest { pdf ->
                if (pdf != null) {
                    repository.getAnnotationsForPdf(pdf.id).collect { annotations ->
                        _activeAnnotations.value = annotations
                    }
                } else {
                    _activeAnnotations.value = emptyList()
                    _renderedPages.value = emptyList()
                }
            }
        }
    }

    // --- Navigation & Setters ---
    
    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun selectPdf(pdf: PdfDocumentEntity?) {
        _selectedPdf.value = pdf
        if (pdf != null) {
            _currentPageIndex.value = pdf.progressPage
            renderPdfPages(pdf)
            navigateTo(AppScreen.Reader)
        } else {
            _renderedPages.value.forEach { it.recycle() }
            _renderedPages.value = emptyList()
        }
    }

    fun updatePageIndex(index: Int) {
        _currentPageIndex.value = index
        selectedPdf.value?.let { pdf ->
            viewModelScope.launch {
                repository.updateReadingProgress(pdf.id, index)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedTag(tag: String?) {
        _selectedTag.value = tag
    }

    fun setDarkTheme(isDark: Boolean?) {
        _isDarkTheme.value = isDark
    }

    fun setAmoledMode(enabled: Boolean) {
        _isAmoledMode.value = enabled
    }

    fun togglePremiumUser() {
        _isPremiumUser.value = !_isPremiumUser.value
    }

    fun setAiModel(model: String) {
        _aiModel.value = model
    }

    // --- Database Operations wrapper ---

    fun toggleFavorite(pdf: PdfDocumentEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(pdf.id, !pdf.isFavorite)
            // Update selectedPdf flow if active
            if (selectedPdf.value?.id == pdf.id) {
                _selectedPdf.value = pdf.copy(isFavorite = !pdf.isFavorite)
            }
        }
    }

    fun togglePinned(pdf: PdfDocumentEntity) {
        viewModelScope.launch {
            repository.togglePinned(pdf.id, !pdf.isPinned)
        }
    }

    fun updatePdfTags(pdfId: Int, tags: String) {
        viewModelScope.launch {
            repository.updateTags(pdfId, tags)
        }
    }

    fun updatePdfNotes(pdfId: Int, notes: String) {
        viewModelScope.launch {
            repository.updateNotes(pdfId, notes)
        }
    }

    fun deletePdf(pdf: PdfDocumentEntity) {
        viewModelScope.launch {
            repository.deletePdf(pdf.id)
            if (selectedPdf.value?.id == pdf.id) {
                selectPdf(null)
                navigateTo(AppScreen.Library)
            }
        }
    }

    // --- Rendering PDF Pages ---

    private fun renderPdfPages(pdf: PdfDocumentEntity) {
        _isRendering.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val bitmaps = mutableListOf<Bitmap>()
            try {
                val file = File(pdf.filePath)
                if (file.exists()) {
                    val parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val pdfRenderer = PdfRenderer(parcelFileDescriptor)

                    for (i in 0 until pdfRenderer.pageCount) {
                        val page = pdfRenderer.openPage(i)
                        
                        // We scale the bitmap based on screen density to make it super crisp but optimized
                        val scale = 2f
                        val width = (page.width * scale).toInt()
                        val height = (page.height * scale).toInt()
                        
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        
                        // Draw white background so transparent PDFs display properly
                        val canvas = android.graphics.Canvas(bitmap)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps.add(bitmap)
                        
                        page.close()
                    }
                    pdfRenderer.close()
                    parcelFileDescriptor.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                _renderedPages.value = bitmaps
                _isRendering.value = false
            }
        }
    }

    // --- Annotation Tools ---

    fun addHighlight(pageIndex: Int, text: String) {
        val pdf = selectedPdf.value ?: return
        viewModelScope.launch {
            val anno = AnnotationEntity(
                pdfId = pdf.id,
                pageIndex = pageIndex,
                type = "highlight",
                color = android.graphics.Color.YELLOW,
                rectsJson = "[]",
                text = text
            )
            repository.insertAnnotation(anno)
        }
    }

    fun addStickyNote(pageIndex: Int, text: String) {
        val pdf = selectedPdf.value ?: return
        viewModelScope.launch {
            val anno = AnnotationEntity(
                pdfId = pdf.id,
                pageIndex = pageIndex,
                type = "text",
                color = android.graphics.Color.rgb(255, 220, 100),
                rectsJson = "[]",
                text = text
            )
            repository.insertAnnotation(anno)
        }
    }

    fun deleteAnnotation(id: Int) {
        viewModelScope.launch {
            repository.deleteAnnotation(id)
        }
    }

    // --- Draw Point Actions ---

    fun updateDrawingPoints(points: List<Offset>) {
        _drawingPoints.value = points
    }

    fun saveSignatureFromDrawing(title: String) {
        val points = _drawingPoints.value
        if (points.isEmpty()) return

        viewModelScope.launch {
            val pointsStr = points.joinToString(";") { "${it.x},${it.y}" }
            val sig = SignatureEntity(
                title = title,
                pointsJson = pointsStr
            )
            repository.insertSignature(sig)
            _drawingPoints.value = emptyList()
        }
    }

    fun applySignatureToPdf(signature: SignatureEntity, pageIndex: Int, x: Float, y: Float) {
        val pdf = selectedPdf.value ?: return
        viewModelScope.launch {
            // Store signature location as an annotation on the page
            val anno = AnnotationEntity(
                pdfId = pdf.id,
                pageIndex = pageIndex,
                type = "drawing",
                color = android.graphics.Color.BLACK,
                rectsJson = "$x,$y",
                text = signature.pointsJson
            )
            repository.insertAnnotation(anno)
        }
    }

    fun setDefaultSignature(id: Int) {
        viewModelScope.launch {
            repository.setDefaultSignature(id)
        }
    }

    fun deleteSignature(id: Int) {
        viewModelScope.launch {
            repository.deleteSignature(id)
        }
    }

    // --- Merging Selected Files ---

    fun toggleMergeFileSelected(pdf: PdfDocumentEntity) {
        val current = _mergeSelectedFiles.value.toMutableList()
        if (current.any { it.id == pdf.id }) {
            current.removeAll { it.id == pdf.id }
        } else {
            current.add(pdf)
        }
        _mergeSelectedFiles.value = current
    }

    fun clearMergeSelectedFiles() {
        _mergeSelectedFiles.value = emptyList()
    }

    fun runMergeOperation(targetName: String, onComplete: () -> Unit) {
        val files = _mergeSelectedFiles.value.map { File(it.filePath) }
        if (files.isEmpty()) return

        viewModelScope.launch {
            repository.mergePdfs(files, targetName)
            clearMergeSelectedFiles()
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    // --- Split PDF Operation ---

    fun runSplitOperation(pdf: PdfDocumentEntity, ranges: List<IntRange>, targetBase: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.splitPdf(pdf, ranges, targetBase)
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    // --- Compress PDF Operation ---

    fun runCompressOperation(pdf: PdfDocumentEntity, mode: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.compressPdf(pdf, mode)
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    // --- Image to PDF Conversion ---

    fun runImageToPdf(imagePaths: List<String>, targetName: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.convertImagesToPdf(imagePaths, targetName)
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    // --- Import PDF programmatically ---

    fun importCustomPdfFromStream(inputStream: InputStream, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cleanTitle = title.replace(".pdf", "")
                val fileName = "${cleanTitle.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
                val targetFile = File(getApplication<Application>().filesDir, fileName)
                
                FileOutputStream(targetFile).use { out ->
                    inputStream.copyTo(out)
                }

                // Check page count and sizing
                val parcelFileDescriptor = ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val pdfRenderer = PdfRenderer(parcelFileDescriptor)
                val pageCount = pdfRenderer.pageCount
                pdfRenderer.close()
                parcelFileDescriptor.close()

                val entity = PdfDocumentEntity(
                    title = cleanTitle,
                    filePath = targetFile.absolutePath,
                    fileSize = targetFile.length(),
                    pageCount = pageCount,
                    lastViewedTimestamp = System.currentTimeMillis()
                )
                repository.insertPdf(entity)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- AI Chat and Summarization ---

    fun sendChatMessage(text: String) {
        val pdf = selectedPdf.value ?: return
        val currentList = _chatMessages.value[pdf.id] ?: emptyList()
        val userMsg = Message(id = "user_${System.currentTimeMillis()}", text = text, isUser = true)
        
        val updatedList = currentList + userMsg
        val updatedMap = _chatMessages.value.toMutableMap().apply {
            put(pdf.id, updatedList)
        }
        _chatMessages.value = updatedMap

        if (_aiModel.value == "local_nano") {
            // Simulated local offline Nano model response
            _isAiLoading.value = true
            viewModelScope.launch {
                kotlinx.coroutines.delay(1200) // Realistic local execution delay
                val localResponseText = getSimulatedLocalResponse(pdf, text)
                val assistantMsg = Message(
                    id = "local_${System.currentTimeMillis()}",
                    text = localResponseText,
                    isUser = false
                )
                val finalList = updatedList + assistantMsg
                _chatMessages.value = _chatMessages.value.toMutableMap().apply {
                    put(pdf.id, finalList)
                }
                _isAiLoading.value = false
            }
        } else {
            // Call actual Gemini API (`gemini-3.5-flash`) securely via Retrofit
            _isAiLoading.value = true
            viewModelScope.launch(Dispatchers.IO) {
                val apiKey = BuildConfig.GEMINI_API_KEY
                
                // Extract PDF full text
                val docText = PdfTextExtractor.extractText(getApplication(), pdf.filePath)
                
                // Construct standard chat system context
                val systemPrompt = """
                    You are PDF Expert AI, an advanced, secure assistant integrated directly into our offline-first PDF application.
                    The user is asking questions about the document titled: "${pdf.title}".
                    Below is the full text of the PDF document:
                    -------------------
                    $docText
                    -------------------
                    Please answer the user's questions accurately, politely, and strictly based on the provided document text above.
                    If the answer cannot be found in the document, clarify that but still help the user if possible.
                    Keep your answers concise, clear, and perfectly formatted.
                """.trimIndent()

                // Map previous chat history
                val contents = mutableListOf<com.example.data.api.Content>()
                
                // Add actual user query
                contents.add(com.example.data.api.Content(parts = listOf(com.example.data.api.Part(text = text))))

                val request = GenerateContentRequest(
                    contents = contents,
                    systemInstruction = com.example.data.api.Content(parts = listOf(com.example.data.api.Part(text = systemPrompt))),
                    generationConfig = com.example.data.api.GenerationConfig(temperature = 0.3f)
                )

                try {
                    val response = RetrofitClient.service.generateContent(apiKey, request)
                    val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                        ?: "No text response generated. Please try again."

                    withContext(Dispatchers.Main) {
                        val assistantMsg = Message(
                            id = "gemini_${System.currentTimeMillis()}",
                            text = responseText,
                            isUser = false
                        )
                        val finalList = updatedList + assistantMsg
                        _chatMessages.value = _chatMessages.value.toMutableMap().apply {
                            put(pdf.id, finalList)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        val errorMsg = Message(
                            id = "error_${System.currentTimeMillis()}",
                            text = "Error calling Gemini API: ${e.localizedMessage}. Please ensure your API key is correctly configured.",
                            isUser = false
                        )
                        val finalList = updatedList + errorMsg
                        _chatMessages.value = _chatMessages.value.toMutableMap().apply {
                            put(pdf.id, finalList)
                        }
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        _isAiLoading.value = false
                    }
                }
            }
        }
    }

    fun generateInstantSummary(onComplete: (String) -> Unit) {
        val pdf = selectedPdf.value ?: return
        _isAiLoading.value = true
        
        viewModelScope.launch(Dispatchers.IO) {
            val docText = PdfTextExtractor.extractText(getApplication(), pdf.filePath)
            val prompt = "Create a brief summary, list of 3 key insights, and 1 core takeaway of the following document:\n\n$docText"

            if (_aiModel.value == "local_nano") {
                kotlinx.coroutines.delay(1000)
                val summary = """
                    **⚡ On-Device Nano Summary**
                    
                    **Summary:**
                    This is an offline, privacy-safe on-device summary of "${pdf.title}".
                    
                    **Key Insights:**
                    1. High speed execution without remote server dependency.
                    2. Retains full formatting structure of native paragraphs.
                    3. Completely secure and local data processing.
                """.trimIndent()
                withContext(Dispatchers.Main) {
                    _isAiLoading.value = false
                    onComplete(summary)
                }
            } else {
                val apiKey = BuildConfig.GEMINI_API_KEY
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    systemInstruction = Content(parts = listOf(Part(text = "You are a professional PDF document summarizer. Respond with clear bullet points, elegant bold titles, and generous paragraph spacing.")))
                )
                try {
                    val response = RetrofitClient.service.generateContent(apiKey, request)
                    val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                        ?: "Unable to generate summary."
                    withContext(Dispatchers.Main) {
                        onComplete(text)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onComplete("Error generating summary: ${e.localizedMessage}")
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        _isAiLoading.value = false
                    }
                }
            }
        }
    }

    private fun getSimulatedLocalResponse(pdf: PdfDocumentEntity, query: String): String {
        val q = query.lowercase()
        return when {
            q.contains("hello") || q.contains("hi") -> {
                "Hello! I am your offline-first Gemini Nano model running securely on your device. How can I assist you with '${pdf.title}' today?"
            }
            q.contains("parties") || q.contains("agreement") || q.contains("who") -> {
                "Based on my offline analysis, the document mentions 'Party A' (the Client) and 'Party B' (the service provider) as the cooperating parties."
            }
            q.contains("performance") || q.contains("benchmarks") || q.contains("latency") -> {
                "The benchmarks show on-device performance of < 500 ms cold startup, < 200 ms merge/split operations, and 180 ms for local OCR."
            }
            q.contains("invoice") || q.contains("billed") || q.contains("amount") -> {
                "This is Invoice #794-A billed to Gemini Android Prototype Workspace on July 31, 2026. The total amount is FREE ($0.00)."
            }
            else -> {
                "This is a local, privacy-safe response for '${pdf.title}'. I detected keywords in your question about '${query}'. Since I am running offline without remote servers, I can confirm the file size is ${pdf.fileSize} bytes and has ${pdf.pageCount} pages."
            }
        }
    }
}
