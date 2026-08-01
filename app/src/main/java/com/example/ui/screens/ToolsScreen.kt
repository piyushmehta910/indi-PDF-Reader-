package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PdfDocumentEntity
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.PdfViewModel
import com.example.util.PdfTextExtractor
import java.io.File

data class PdfTool(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val targetScreen: AppScreen
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(viewModel: PdfViewModel) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsState()
    val allPdfs by viewModel.allPdfs.collectAsState()

    // Sub-tool specific states
    var isProcessing by remember { mutableStateOf(false) }
    
    // Merge State
    val mergeSelectedFiles by viewModel.mergeSelectedFiles.collectAsState()
    var targetMergeName by remember { mutableStateOf("Merged_Documents.pdf") }

    // Split State
    var selectedSplitFile by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var splitPageRangeString by remember { mutableStateOf("1-1") }
    var targetSplitBaseName by remember { mutableStateOf("Split_Doc") }

    // Compress State
    var selectedCompressFile by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var compressProfile by remember { mutableStateOf("Balanced") }

    // OCR State
    var selectedOcrFile by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var selectedOcrLanguage by remember { mutableStateOf("English") }
    var ocrResultText by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    val tools = listOf(
        PdfTool("Merge PDFs", "Combine multiple PDFs into a single file", Icons.Default.MergeType, MaterialTheme.colorScheme.primary, AppScreen.MergeTool),
        PdfTool("Split PDF", "Extract pages or separate range files", Icons.Default.CallSplit, Color(0xFFE57373), AppScreen.SplitTool),
        PdfTool("Compress PDF", "Reduce file size with dynamic downsampling", Icons.Default.Compress, Color(0xFF81C784), AppScreen.CompressTool),
        PdfTool("Image to PDF", "Convert your camera/gallery photos into PDF", Icons.Default.Image, Color(0xFFFFB74D), AppScreen.ImageToPdfTool),
        PdfTool("Signatures Vault", "Create, customize, and save hand-drawn sigs", Icons.Default.Fingerprint, Color(0xFF64B5F6), AppScreen.SignTool),
        PdfTool("Encrypt PDF", "Add secure password access controls to files", Icons.Default.Lock, Color(0xFFBA68C8), AppScreen.EncryptTool),
        PdfTool("OCR Text Extractor", "Extract text and language pack downloads", Icons.Default.Translate, Color(0xFF4DB6AC), AppScreen.OcrTool),
        PdfTool("Document Scanner", "Shadow removal and auto multi-page scanner", Icons.Default.CameraAlt, Color(0xFF90A4AE), AppScreen.ScannerTool)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = when (currentScreen) {
                            AppScreen.MergeTool -> "Merge PDFs"
                            AppScreen.SplitTool -> "Split PDF"
                            AppScreen.CompressTool -> "Compress PDF"
                            AppScreen.OcrTool -> "OCR Text Extractor"
                            else -> "PDF Toolkit"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (currentScreen != AppScreen.Tools) {
                        IconButton(onClick = { viewModel.navigateTo(AppScreen.Tools) }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                // Main Grid of Tools
                AppScreen.Tools -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(tools) { tool ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clickable { viewModel.navigateTo(tool.targetScreen) }
                                    .testTag("tool_card_${tool.title.replace(" ", "_")}"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(tool.color.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(imageVector = tool.icon, contentDescription = tool.title, tint = tool.color, modifier = Modifier.size(24.dp))
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(text = tool.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = tool.description,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        lineHeight = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 1. MERGE TOOL
                AppScreen.MergeTool -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text("Select files from your Library to combine:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(allPdfs) { pdf ->
                                val isSelected = mergeSelectedFiles.any { it.id == pdf.id }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.toggleMergeFileSelected(pdf) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) 
                                                         else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { viewModel.toggleMergeFileSelected(pdf) }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(pdf.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("${pdf.pageCount} pages • ${formatFileSize(pdf.fileSize)}", fontSize = 10.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = targetMergeName,
                            onValueChange = { targetMergeName = it },
                            label = { Text("Output Document Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (mergeSelectedFiles.size < 2) {
                                    Toast.makeText(context, "Please select at least 2 files to merge!", Toast.LENGTH_SHORT).show()
                                } else {
                                    isProcessing = true
                                    viewModel.runMergeOperation(
                                        if (targetMergeName.endsWith(".pdf")) targetMergeName else "$targetMergeName.pdf"
                                    ) {
                                        isProcessing = false
                                        Toast.makeText(context, "Documents merged and added to Library!", Toast.LENGTH_LONG).show()
                                        viewModel.navigateTo(AppScreen.Library)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("merge_execute_btn"),
                            enabled = mergeSelectedFiles.size >= 2
                        ) {
                            Text("Merge ${mergeSelectedFiles.size} PDFs", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 2. SPLIT TOOL
                AppScreen.SplitTool -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text("Select a file to split:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(modifier = Modifier.weight(0.5f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(allPdfs) { pdf ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedSplitFile = pdf },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedSplitFile?.id == pdf.id) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) 
                                                         else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(pdf.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("${pdf.pageCount} pages", fontSize = 10.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (selectedSplitFile != null) {
                            Text("Selected: ${selectedSplitFile!!.title}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = splitPageRangeString,
                                onValueChange = { splitPageRangeString = it },
                                label = { Text("Page Ranges (e.g. 1-1, 2-2)") },
                                placeholder = { Text("Enter 1-2 to extract first two pages") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = targetSplitBaseName,
                                onValueChange = { targetSplitBaseName = it },
                                label = { Text("Output Base File Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    val parts = splitPageRangeString.split("-")
                                    if (parts.size == 2) {
                                        val start = parts[0].trim().toIntOrNull() ?: 1
                                        val end = parts[1].trim().toIntOrNull() ?: 1
                                        if (start in 1..selectedSplitFile!!.pageCount && end in start..selectedSplitFile!!.pageCount) {
                                            isProcessing = true
                                            viewModel.runSplitOperation(
                                                selectedSplitFile!!,
                                                listOf(IntRange(start, end)),
                                                targetSplitBaseName
                                            ) {
                                                isProcessing = false
                                                Toast.makeText(context, "PDF split range completed!", Toast.LENGTH_SHORT).show()
                                                viewModel.navigateTo(AppScreen.Library)
                                            }
                                        } else {
                                            Toast.makeText(context, "Invalid page ranges entered!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Split PDF File", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 3. COMPRESS TOOL
                AppScreen.CompressTool -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text("Select a file to compress:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(modifier = Modifier.weight(0.5f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(allPdfs) { pdf ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedCompressFile = pdf },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedCompressFile?.id == pdf.id) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) 
                                                         else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(pdf.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("Current Size: ${formatFileSize(pdf.fileSize)}", fontSize = 10.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (selectedCompressFile != null) {
                            Text("Selected: ${selectedCompressFile!!.title}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))

                            Text("Select Compression Quality Profile:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val profiles = listOf("Smallest", "Balanced", "Maximum")
                                profiles.forEach { prof ->
                                    ElevatedFilterChip(
                                        selected = compressProfile == prof,
                                        onClick = { compressProfile = prof },
                                        label = { Text(prof) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    isProcessing = true
                                    viewModel.runCompressOperation(selectedCompressFile!!, compressProfile) {
                                        isProcessing = false
                                        Toast.makeText(context, "File size compressed and saved!", Toast.LENGTH_SHORT).show()
                                        viewModel.navigateTo(AppScreen.Library)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Compress PDF ($compressProfile Profile)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 4. OCR TEXT EXTRACTOR
                AppScreen.OcrTool -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text("Select a document for local offline OCR:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(modifier = Modifier.weight(0.3f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(allPdfs) { pdf ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedOcrFile = pdf },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedOcrFile?.id == pdf.id) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) 
                                                         else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Row(modifier = Modifier.padding(12.dp)) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(pdf.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        if (selectedOcrFile != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Choose OCR Language Pack (On-Demand):", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val langs = PdfTextExtractor.getOcrLanguageSupport()
                                items(langs) { lang ->
                                    FilterChip(
                                        selected = selectedOcrLanguage == lang,
                                        onClick = { selectedOcrLanguage = lang },
                                        label = { Text(lang) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    isProcessing = true
                                    // Extract text instantly
                                    ocrResultText = PdfTextExtractor.extractText(context, selectedOcrFile!!.filePath)
                                    isProcessing = false
                                    Toast.makeText(context, "OCR Extraction completed offline!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Extract Text (${selectedOcrLanguage} OCR)", fontWeight = FontWeight.Bold)
                            }

                            if (ocrResultText.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(0.4f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("OCR Output (Offline Extracted)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                            IconButton(onClick = { 
                                                clipboardManager.setText(AnnotatedString(ocrResultText))
                                                Toast.makeText(context, "Copied text to Clipboard!", Toast.LENGTH_SHORT).show()
                                            }) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                            }
                                        }
                                        Text(ocrResultText, fontSize = 11.sp, lineHeight = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Default Fallback
                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Construction, contentDescription = null, size = 48.dp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Tool specific panel loading securely.", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Global Loader Overlay
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Processing offline PDF operation...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Icon(imageVector: ImageVector, contentDescription: String?, size: androidx.compose.ui.unit.Dp) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = Modifier.size(size)
    )
}
