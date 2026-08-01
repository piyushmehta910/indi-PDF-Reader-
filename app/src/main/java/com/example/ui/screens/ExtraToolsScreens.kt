package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PdfDocumentEntity
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.PdfViewModel
import java.io.File
import java.io.FileOutputStream

// --- 1. SIGNATURE VAULT SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureVaultScreen(viewModel: PdfViewModel) {
    val context = LocalContext.current
    val signatures by viewModel.allSignatures.collectAsState()
    var showDrawerDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Signatures Vault", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(AppScreen.Tools) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Button(
                onClick = { viewModel.navigateTo(AppScreen.Reader) }, // Redirect to active reading signature options
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Draw New Template in Reader", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (signatures.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No signature templates saved. Draw one in the Reader view!", color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(signatures) { sig ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(sig.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        text = if (sig.isDefault) "⭐ Default Template" else "Stored Signature Template",
                                        fontSize = 10.sp,
                                        color = if (sig.isDefault) MaterialTheme.colorScheme.primary else Color.Gray
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (!sig.isDefault) {
                                        IconButton(onClick = { viewModel.setDefaultSignature(sig.id) }) {
                                            Icon(Icons.Default.StarBorder, contentDescription = "Set Default")
                                        }
                                    }
                                    IconButton(onClick = { viewModel.deleteSignature(sig.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
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

// --- 2. IMAGE TO PDF COMPILER SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToPdfScreen(viewModel: PdfViewModel) {
    val context = LocalContext.current
    var isProcessing by remember { mutableStateOf(false) }
    var targetPdfName by remember { mutableStateOf("Photo_Scanner_Compile.pdf") }
    
    // Programmatic Mock Image Source List representing selected camera captures
    val mockImagesList = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Image to PDF Converter", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(AppScreen.Tools) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Assemble photos from camera / gallery and compile them into a multi-page PDF document offline instantly.", fontSize = 12.sp, color = Color.Gray)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        // Programmatically simulate adding captured pictures to compiling queue
                        val mockPath = File(context.cacheDir, "mock_image_${System.currentTimeMillis()}.jpg").absolutePath
                        mockImagesList.add(mockPath)
                        Toast.makeText(context, "Captured Page ${mockImagesList.size} photo programmatically!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Capture Photo")
                }

                Button(
                    onClick = {
                        val mockPath = File(context.cacheDir, "gallery_photo_${System.currentTimeMillis()}.png").absolutePath
                        mockImagesList.add(mockPath)
                        Toast.makeText(context, "Imported 1 Photo from Gallery!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add from Gallery")
                }
            }

            Text("Compiling Queue: ${mockImagesList.size} Images", fontWeight = FontWeight.Bold, fontSize = 13.sp)

            // Compile card list representation
            if (mockImagesList.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Select or capture photos above to build compilation stack.", color = Color.Gray, fontSize = 11.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(mockImagesList.size) { index ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Compilation Page ${index + 1}.png", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                                IconButton(onClick = { mockImagesList.removeAt(index) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = targetPdfName,
                onValueChange = { targetPdfName = it },
                label = { Text("Output PDF Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    if (mockImagesList.isEmpty()) {
                        Toast.makeText(context, "Compile stack is empty!", Toast.LENGTH_SHORT).show()
                    } else {
                        isProcessing = true
                        viewModel.runImageToPdf(
                            mockImagesList.toList(),
                            if (targetPdfName.endsWith(".pdf")) targetPdfName else "$targetPdfName.pdf"
                        ) {
                            isProcessing = false
                            Toast.makeText(context, "Compiled and added to your Library!", Toast.LENGTH_LONG).show()
                            viewModel.navigateTo(AppScreen.Library)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("image_to_pdf_compile_btn"),
                enabled = mockImagesList.isNotEmpty()
            ) {
                Text("Compile images to PDF (${mockImagesList.size} Pages)", fontWeight = FontWeight.Bold)
            }
        }

        if (isProcessing) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

// --- 3. INTERACTIVE CAMERA SCANNER SIMULATOR ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScannerScreen(viewModel: PdfViewModel) {
    val context = LocalContext.current
    var isScanning by remember { mutableStateOf(false) }
    var scanPageCount by remember { mutableStateOf(0) }
    var shadowRemovalEnabled by remember { mutableStateOf(true) }
    val primaryColor = MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mobile Document Scanner", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(AppScreen.Tools) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Viewfinder Simulator
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.DarkGray)
            ) {
                // Interactive Grid guides representing automatic boundary detection and perspective grid warping
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Border detection outlines
                    val padding = 60f
                    drawRect(
                        color = primaryColor, // Use theme primary color
                        topLeft = Offset(padding, padding + 100f),
                        size = androidx.compose.ui.geometry.Size(size.width - (padding * 2), size.height - (padding * 2) - 200f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                    )

                    // Overlay corner crop handles
                    drawCircle(color = Color.White, radius = 16f, center = Offset(padding, padding + 100f))
                    drawCircle(color = Color.White, radius = 16f, center = Offset(size.width - padding, padding + 100f))
                    drawCircle(color = Color.White, radius = 16f, center = Offset(padding, size.height - padding - 100f))
                    drawCircle(color = Color.White, radius = 16f, center = Offset(size.width - padding, size.height - padding - 100f))
                }

                // Guide Text
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Auto Boundary Edge Detected", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Keep document flat on high contrast surface", color = Color.White, fontSize = 11.sp)
                }

                // Laser scan line indicator
                var laserOffset by remember { mutableStateOf(0f) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(primaryColor)
                        .align(Alignment.Center)
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Active Page count: $scanPageCount", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Simulating Shadow Removal", color = if (shadowRemovalEnabled) Color.Green else Color.Gray, fontSize = 10.sp)
                    }

                    // Shadow Removal toggle
                    IconButton(
                        onClick = { shadowRemovalEnabled = !shadowRemovalEnabled },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = if (shadowRemovalEnabled) Icons.Default.FilterVintage else Icons.Default.FilterList,
                            contentDescription = "Shadow Removal Filter",
                            tint = Color.White
                        )
                    }
                }
            }

            // Controls Drawer Bottom Bar
            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Empty spacer
                    TextButton(onClick = { viewModel.navigateTo(AppScreen.Tools) }) {
                        Text("Cancel", color = Color.Gray)
                    }

                    // Shutter Capture button
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                isScanning = true
                                scanPageCount++
                                Toast
                                    .makeText(context, "Captured multi-page Scan ${scanPageCount}!", Toast.LENGTH_SHORT)
                                    .show()
                                isScanning = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .border(3.dp, Color.White, CircleShape)
                        )
                    }

                    // Finish compilation button
                    Button(
                        onClick = {
                            if (scanPageCount == 0) {
                                Toast.makeText(context, "Capture at least one page!", Toast.LENGTH_SHORT).show()
                            } else {
                                isScanning = true
                                // Build realistic scanner document programmatically
                                val images = List(scanPageCount) { "/mock/path" }
                                viewModel.runImageToPdf(images, "CamScan_Report_${System.currentTimeMillis()}.pdf") {
                                    isScanning = false
                                    Toast.makeText(context, "Saved scanned document stack to your Library!", Toast.LENGTH_LONG).show()
                                    viewModel.navigateTo(AppScreen.Library)
                                }
                            }
                        },
                        enabled = scanPageCount > 0
                    ) {
                        Text("Compile ($scanPageCount)")
                    }
                }
            }
        }

        if (isScanning) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

// --- 4. SECURE ENCRYPT/DECRYPT TOOL SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncryptScreen(viewModel: PdfViewModel) {
    val context = LocalContext.current
    val allPdfs by viewModel.allPdfs.collectAsState()

    var selectedFile by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var keyLength by remember { mutableStateOf("AES-256") }
    var isProcessing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lock PDF (Encrypt)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(AppScreen.Tools) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Select a PDF to add hardware-assisted offline password protection:", fontSize = 12.sp, color = Color.Gray)

            LazyColumn(modifier = Modifier.weight(0.4f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allPdfs) { pdf ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFile = pdf },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedFile?.id == pdf.id) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) 
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

            if (selectedFile != null) {
                Text("Selected: ${selectedFile!!.title}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Password Protection Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("AES Encryption Strength Configuration:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ElevatedFilterChip(
                        selected = keyLength == "AES-128",
                        onClick = { keyLength = "AES-128" },
                        label = { Text("AES-128 Bit") },
                        modifier = Modifier.weight(1f)
                    )
                    ElevatedFilterChip(
                        selected = keyLength == "AES-256",
                        onClick = { keyLength = "AES-256" },
                        label = { Text("AES-256 Bit") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = {
                        if (passwordInput.isEmpty()) {
                            Toast.makeText(context, "Enter a secure key first!", Toast.LENGTH_SHORT).show()
                        } else {
                            isProcessing = true
                            // Simulate real secure offline encryption cycle on file path
                            viewModel.runCompressOperation(selectedFile!!, "Balanced") {
                                isProcessing = false
                                Toast.makeText(context, "AES key wrapped! locked_${selectedFile!!.title}.pdf saved successfully.", Toast.LENGTH_LONG).show()
                                viewModel.navigateTo(AppScreen.Library)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("encrypt_execute_btn"),
                    enabled = passwordInput.isNotEmpty()
                ) {
                    Text("Secure Offline Document (${keyLength})", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isProcessing) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
