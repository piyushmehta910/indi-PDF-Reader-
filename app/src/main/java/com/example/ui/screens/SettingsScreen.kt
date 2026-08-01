package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.PdfViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: PdfViewModel) {
    val context = LocalContext.current
    val darkThemePref by viewModel.isDarkTheme.collectAsState()
    val isAmoled by viewModel.isAmoledMode.collectAsState()
    val isPremium by viewModel.isPremiumUser.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Settings", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Premium Subscription Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isPremium) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isPremium) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color(0xFFFFD54F).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPremium) Icons.Default.VerifiedUser else Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = if (isPremium) MaterialTheme.colorScheme.primary else Color(0xFFFFB300),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isPremium) "PDF Expert Pro Active" else "Upgrade to PDF Expert Pro",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (isPremium) "All premium local & cloud tools unlocked" else "Unlock OCR, translation, advanced signing, and unlimited merges",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }

                    Button(
                        onClick = { viewModel.togglePremiumUser() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPremium) MaterialTheme.colorScheme.primary else Color(0xFFFFB300),
                            contentColor = if (isPremium) MaterialTheme.colorScheme.onPrimary else Color.Black
                        )
                    ) {
                        Text(if (isPremium) "Downgrade" else "Join Pro", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }

            Text("Theme & Customization", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            // Theme selection list items
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Column {
                    SettingsRowWithRadio(
                        title = "Follow System Theme",
                        selected = darkThemePref == null,
                        onClick = { viewModel.setDarkTheme(null) }
                    )
                    HorizontalDivider()
                    SettingsRowWithRadio(
                        title = "Force Light Theme",
                        selected = darkThemePref == false,
                        onClick = { viewModel.setDarkTheme(false) }
                    )
                    HorizontalDivider()
                    SettingsRowWithRadio(
                        title = "Force Dark Theme",
                        selected = darkThemePref == true,
                        onClick = { viewModel.setDarkTheme(true) }
                    )
                    
                    // Only show AMOLED switch if dark theme active
                    if (darkThemePref == true) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("AMOLED Pure Black Mode", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Saves battery on OLED screens", fontSize = 10.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = isAmoled,
                                onCheckedChange = { viewModel.setAmoledMode(it) }
                            )
                        }
                    }
                }
            }

            Text("Modular Packages Status", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Column {
                    SettingsStatusRow(
                        title = "Base Offline Engine",
                        status = "INSTALLED (7.4 MB)",
                        subtitle = "Native renderers and split/merge utilities",
                        isInstalled = true
                    )
                    HorizontalDivider()
                    SettingsStatusRow(
                        title = "English & Spanish OCR Language Packs",
                        status = "INSTALLED (2.1 MB)",
                        subtitle = "Required for on-device document search and copy",
                        isInstalled = true
                    )
                    HorizontalDivider()
                    SettingsStatusRow(
                        title = "Other Languages OCR Pack",
                        status = "AVAILABLE FOR DOWNLOAD (4.2 MB)",
                        subtitle = "Hindi, French, German, Japanese, Chinese packs",
                        isInstalled = false
                    )
                    HorizontalDivider()
                    SettingsStatusRow(
                        title = "Local Gemini Nano Model",
                        status = "AVAILABLE FOR DOWNLOAD (112 MB)",
                        subtitle = "Provides offline AI summaries and contextual Q&A",
                        isInstalled = false
                    )
                }
            }

            Text("About PDF Expert", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Version: 1.0.0 (Base Stack)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Developer: Piyush Kumar | developer-team@example.com", fontSize = 11.sp, color = Color.Gray)
                    Text("Core Engine: Android Native PdfRenderer & PdfDocument APIs.", fontSize = 11.sp, color = Color.Gray)
                    Text("All documents are processed and saved locally inside your private app directory to maintain total privacy.", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun SettingsRowWithRadio(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
fun SettingsStatusRow(
    title: String,
    status: String,
    subtitle: String,
    isInstalled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, fontSize = 10.sp, color = Color.Gray, lineHeight = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = status,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isInstalled) MaterialTheme.colorScheme.primary else Color(0xFFFFB300)
            )
        }
        if (!isInstalled) {
            IconButton(onClick = { /* Simulated download */ }) {
                Icon(Icons.Default.Download, contentDescription = "Download Package", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
