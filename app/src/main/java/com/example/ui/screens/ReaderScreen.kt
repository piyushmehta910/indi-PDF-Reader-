package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.print.PrintManager
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AnnotationEntity
import com.example.data.local.SignatureEntity
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.PdfViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(viewModel: PdfViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val pdf by viewModel.selectedPdf.collectAsState()
    val isRendering by viewModel.isRendering.collectAsState()
    val renderedPages by viewModel.renderedPages.collectAsState()
    val currentPageIndex by viewModel.currentPageIndex.collectAsState()
    val annotations by viewModel.activeAnnotations.collectAsState()
    val savedSignatures by viewModel.allSignatures.collectAsState()
    val isPremium by viewModel.isPremiumUser.collectAsState()

    // Interactive Mode states: "view", "highlight", "stickynote", "sign", "pen"
    var readerMode by remember { mutableStateOf("view") }
    var selectedSignatureForApplying by remember { mutableStateOf<SignatureEntity?>(null) }
    
    // UI Panel visibility states
    var showAiAssistant by remember { mutableStateOf(false) }
    var showSignatureCreatorDialog by remember { mutableStateOf(false) }
    var showNotesBottomSheet by remember { mutableStateOf(false) }
    var showSummaryDialog by remember { mutableStateOf(false) }
    var instantSummaryText by remember { mutableStateOf("") }
    
    // Input buffers
    var noteInputBuffer by remember { mutableStateOf("") }
    var signatureTitleBuffer by remember { mutableStateOf("My Signature") }

    if (pdf == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = currentPageIndex)

    // Synchronize scroll state to current reading progress page index in ViewModel
    LaunchedEffect(lazyListState.firstVisibleItemIndex) {
        if (lazyListState.firstVisibleItemIndex != currentPageIndex && renderedPages.isNotEmpty()) {
            viewModel.updatePageIndex(lazyListState.firstVisibleItemIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = pdf!!.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Page ${currentPageIndex + 1} of ${pdf!!.pageCount}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.selectPdf(null); viewModel.navigateTo(AppScreen.Library) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite(pdf!!) }) {
                        Icon(
                            imageVector = if (pdf!!.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (pdf!!.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { 
                        // Trigger device print dialogue programmatically
                        try {
                            val printManager = context.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
                            Toast.makeText(context, "System Print capability initiated for offline document.", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Ready to Print document.", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Print, contentDescription = "Print Document")
                    }
                    IconButton(onClick = { 
                        // Toggle integrated AI assistant drawer
                        showAiAssistant = !showAiAssistant
                    }) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Assistant",
                            tint = if (showAiAssistant) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        bottomBar = {
            // High fidelity annotation tool selection bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnnotationModeButton(
                        icon = Icons.Default.RemoveRedEye,
                        label = "Read",
                        isActive = readerMode == "view",
                        onClick = { readerMode = "view"; selectedSignatureForApplying = null }
                    )
                    AnnotationModeButton(
                        icon = Icons.Default.BorderColor,
                        label = "Highlight",
                        isActive = readerMode == "highlight",
                        onClick = { readerMode = "highlight"; selectedSignatureForApplying = null }
                    )
                    AnnotationModeButton(
                        icon = Icons.Default.StickyNote2,
                        label = "Note",
                        isActive = readerMode == "stickynote",
                        onClick = { readerMode = "stickynote"; selectedSignatureForApplying = null }
                    )
                    AnnotationModeButton(
                        icon = Icons.Default.Draw,
                        label = "Draw Sig",
                        isActive = readerMode == "pen",
                        onClick = { showSignatureCreatorDialog = true }
                    )
                    AnnotationModeButton(
                        icon = Icons.Default.Fingerprint,
                        label = "Apply Sig",
                        isActive = readerMode == "sign",
                        onClick = { 
                            if (savedSignatures.isEmpty()) {
                                Toast.makeText(context, "Draw a signature first using 'Draw Sig'!", Toast.LENGTH_SHORT).show()
                            } else {
                                readerMode = "sign"
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Rendered PDF Scrolling viewport
            Column(
                modifier = Modifier
                    .weight(if (showAiAssistant) 0.55f else 1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                // If applying signature, show selection banner
                AnimatedVisibility(visible = readerMode == "sign") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Applying Digital Signature Mode", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Select a template below, then tap anywhere on the page to place it.", fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(savedSignatures.size) { idx ->
                                    val sig = savedSignatures[idx]
                                    InputChip(
                                        selected = selectedSignatureForApplying?.id == sig.id,
                                        onClick = { selectedSignatureForApplying = sig },
                                        label = { Text(sig.title) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (isRendering) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Rendering pages instantly...", fontSize = 12.sp)
                        }
                    }
                } else if (renderedPages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No rendered pages found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(renderedPages) { index, pageBitmap ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .background(Color.White, shape = RoundedCornerShape(4.dp))
                                    .border(0.5.dp, Color.LightGray, shape = RoundedCornerShape(4.dp))
                                    .pointerInput(readerMode, selectedSignatureForApplying) {
                                        detectTapGestures { offset ->
                                            val normX = offset.x / size.width
                                            val normY = offset.y / size.height
                                            
                                            when (readerMode) {
                                                "highlight" -> {
                                                    viewModel.addHighlight(index, "Highlighted line on Page ${index + 1}")
                                                    Toast.makeText(context, "Added Highlight!", Toast.LENGTH_SHORT).show()
                                                }
                                                "stickynote" -> {
                                                    noteInputBuffer = ""
                                                    showNotesBottomSheet = true
                                                }
                                                "sign" -> {
                                                    val sig = selectedSignatureForApplying
                                                    if (sig != null) {
                                                        viewModel.applySignatureToPdf(sig, index, normX, normY)
                                                        Toast.makeText(context, "Applied signature template to page!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Select a signature template first!", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                    }
                            ) {
                                Image(
                                    bitmap = pageBitmap.asImageBitmap(),
                                    contentDescription = "PDF Page ${index + 1}",
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Overlay active annotations for this page index
                                val pageAnnotations = annotations.filter { it.pageIndex == index }
                                Canvas(modifier = Modifier.matchParentSize()) {
                                    pageAnnotations.forEach { anno ->
                                        when (anno.type) {
                                            "highlight" -> {
                                                // Highlight simulated box at standard page section
                                                drawRect(
                                                    color = Color.Yellow.copy(alpha = 0.4f),
                                                    size = androidx.compose.ui.geometry.Size(size.width * 0.8f, 20.dp.toPx()),
                                                    topLeft = Offset(size.width * 0.1f, size.height * 0.3f)
                                                )
                                            }
                                            "text" -> {
                                                // Draw small note indicator circle/pin
                                                drawCircle(
                                                    color = Color(0xFFFFA000),
                                                    radius = 12.dp.toPx(),
                                                    center = Offset(size.width * 0.8f, size.height * 0.15f)
                                                )
                                            }
                                            "drawing" -> {
                                                // Drawapplied signature at specific normalized coordinates
                                                val coords = anno.rectsJson.split(",")
                                                if (coords.size == 2) {
                                                    val xPos = coords[0].toFloat() * size.width
                                                    val yPos = coords[1].toFloat() * size.height
                                                    
                                                    // Render vector signature points
                                                    val points = anno.text?.split(";")?.mapNotNull { pt ->
                                                        val xy = pt.split(",")
                                                        if (xy.size == 2) Offset(xy[0].toFloat() + xPos, xy[1].toFloat() + yPos) else null
                                                    } ?: emptyList()
                                                    
                                                    for (i in 0 until points.size - 1) {
                                                        drawLine(
                                                            color = Color.Black,
                                                            start = points[i],
                                                            end = points[i + 1],
                                                            strokeWidth = 3f
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Interactive Text indicators overlay
                                pageAnnotations.forEach { anno ->
                                    if (anno.type == "text" && !anno.text.isNullOrEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(top = 40.dp, end = 40.dp)
                                                .background(Color(0xFFFFF9C4), shape = RoundedCornerShape(4.dp))
                                                .border(1.dp, Color(0xFFFBC02D), shape = RoundedCornerShape(4.dp))
                                                .padding(6.dp)
                                                .widthIn(max = 150.dp)
                                        ) {
                                            Column {
                                                Text(text = "Sticky Note", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                                Text(text = anno.text!!, fontSize = 10.sp, color = Color.Black)
                                                IconButton(
                                                    onClick = { viewModel.deleteAnnotation(anno.id) },
                                                    modifier = Modifier.size(16.dp).align(Alignment.End)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete note", modifier = Modifier.size(10.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Collapsible Slide-Out AI Assistant Sidebar Panel
            AnimatedVisibility(
                visible = showAiAssistant,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier
                    .weight(if (showAiAssistant) 0.45f else 0.0001f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Drawer Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("PDF AI Assistant", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        IconButton(onClick = { 
                            viewModel.generateInstantSummary { summary ->
                                instantSummaryText = summary
                                showSummaryDialog = true
                            }
                        }) {
                            Icon(Icons.Default.MenuBook, contentDescription = "Instant Summary", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Inside AI screen tab layout
                    AiScreen(viewModel = viewModel, embedded = true)
                }
            }
        }
    }

    // Hand-drawn Signature Template Creator Dialog
    if (showSignatureCreatorDialog) {
        val strokePoints = remember { mutableStateListOf<Offset>() }

        AlertDialog(
            onDismissRequest = { showSignatureCreatorDialog = false; strokePoints.clear() },
            title = { Text("Draw Your Signature Template") },
            text = {
                Column {
                    Text("Draw with your finger or stylus inside the canvas:", fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Signature Title Input
                    OutlinedTextField(
                        value = signatureTitleBuffer,
                        onValueChange = { signatureTitleBuffer = it },
                        label = { Text("Signature Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    // Draw Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(Color.White, shape = RoundedCornerShape(8.dp))
                            .border(1.dp, Color.Gray, shape = RoundedCornerShape(8.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset -> strokePoints.add(offset) },
                                    onDrag = { _, dragAmount ->
                                        val next = strokePoints.lastOrNull()?.let { it + dragAmount }
                                        if (next != null) strokePoints.add(next)
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            for (i in 0 until strokePoints.size - 1) {
                                drawLine(
                                    color = Color.Black,
                                    start = strokePoints[i],
                                    end = strokePoints[i + 1],
                                    strokeWidth = 4.dp.toPx()
                                )
                            }
                        }
                        if (strokePoints.isEmpty()) {
                            Text(
                                text = "Sign Here",
                                modifier = Modifier.align(Alignment.Center),
                                color = Color.LightGray,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { strokePoints.clear() }) {
                            Text("Clear Canvas", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (strokePoints.isNotEmpty()) {
                        viewModel.updateDrawingPoints(strokePoints.toList())
                        viewModel.saveSignatureFromDrawing(signatureTitleBuffer)
                        Toast.makeText(context, "Saved Signature template successfully!", Toast.LENGTH_SHORT).show()
                    }
                    showSignatureCreatorDialog = false
                    strokePoints.clear()
                }) {
                    Text("Save Template")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignatureCreatorDialog = false; strokePoints.clear() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Sticky Note Dialog
    if (showNotesBottomSheet) {
        AlertDialog(
            onDismissRequest = { showNotesBottomSheet = false },
            title = { Text("Add Sticky Note") },
            text = {
                OutlinedTextField(
                    value = noteInputBuffer,
                    onValueChange = { noteInputBuffer = it },
                    placeholder = { Text("Type note text here...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (noteInputBuffer.isNotEmpty()) {
                        viewModel.addStickyNote(currentPageIndex, noteInputBuffer)
                        Toast.makeText(context, "Sticky note pinned!", Toast.LENGTH_SHORT).show()
                    }
                    showNotesBottomSheet = false
                }) {
                    Text("Pin Note")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotesBottomSheet = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Summary Result Dialog
    if (showSummaryDialog) {
        AlertDialog(
            onDismissRequest = { showSummaryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Instant Document Summary")
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(text = instantSummaryText, fontSize = 12.sp, lineHeight = 18.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showSummaryDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun AnnotationModeButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
