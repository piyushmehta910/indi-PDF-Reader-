package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.PdfViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: PdfViewModel by viewModels()
            
            // Gather reactive preferences
            val isDarkPref by viewModel.isDarkTheme.collectAsState()
            val isAmoled by viewModel.isAmoledMode.collectAsState()
            
            val systemDark = isSystemInDarkTheme()
            val activeDarkTheme = isDarkPref ?: systemDark

            MyApplicationTheme(
                darkTheme = activeDarkTheme,
                isAmoled = isAmoled
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigationContainer(viewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigationContainer(viewModel: PdfViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val selectedPdf by viewModel.selectedPdf.collectAsState()

    // Handle system back gestures based on navigation context
    BackHandler(enabled = currentScreen != AppScreen.Library) {
        when (currentScreen) {
            AppScreen.Reader -> {
                viewModel.selectPdf(null)
                viewModel.navigateTo(AppScreen.Library)
            }
            AppScreen.MergeTool, AppScreen.SplitTool, AppScreen.CompressTool,
            AppScreen.ImageToPdfTool, AppScreen.SignTool, AppScreen.EncryptTool,
            AppScreen.OcrTool, AppScreen.ScannerTool -> {
                viewModel.navigateTo(AppScreen.Tools)
            }
            else -> {
                viewModel.navigateTo(AppScreen.Library)
            }
        }
    }

    Scaffold(
        bottomBar = {
            // Only show the main bottom navigation bar on standard tab destinations
            val isMainTab = currentScreen == AppScreen.Library || 
                            currentScreen == AppScreen.Tools || 
                            currentScreen == AppScreen.AIChat || 
                            currentScreen == AppScreen.Settings

            if (isMainTab) {
                NavigationBar(modifier = Modifier.testTag("bottom_nav")) {
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.Library,
                        onClick = { viewModel.navigateTo(AppScreen.Library) },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == AppScreen.Library) Icons.Filled.MenuBook else Icons.Outlined.MenuBook,
                                contentDescription = "Library"
                            )
                        },
                        label = { Text("Library", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_library")
                    )
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.Tools || currentScreen == AppScreen.MergeTool || currentScreen == AppScreen.SplitTool || currentScreen == AppScreen.CompressTool || currentScreen == AppScreen.OcrTool,
                        onClick = { viewModel.navigateTo(AppScreen.Tools) },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == AppScreen.Tools) Icons.Filled.Build else Icons.Outlined.Build,
                                contentDescription = "Tools"
                            )
                        },
                        label = { Text("Tools", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_tools")
                    )
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.AIChat,
                        onClick = { viewModel.navigateTo(AppScreen.AIChat) },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == AppScreen.AIChat) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                                contentDescription = "AI Hub"
                            )
                        },
                        label = { Text("AI Chat", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_ai_chat")
                    )
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.Settings,
                        onClick = { viewModel.navigateTo(AppScreen.Settings) },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == AppScreen.Settings) Icons.Filled.Settings else Icons.Outlined.Settings,
                                contentDescription = "Settings"
                            )
                        },
                        label = { Text("Settings", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_settings")
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentScreen) {
                AppScreen.Library -> DashboardScreen(viewModel = viewModel)
                AppScreen.Reader -> ReaderScreen(viewModel = viewModel)
                
                // Toolkit routing
                AppScreen.Tools, AppScreen.MergeTool, AppScreen.SplitTool, 
                AppScreen.CompressTool, AppScreen.OcrTool -> ToolsScreen(viewModel = viewModel)
                
                AppScreen.SignTool -> SignatureVaultScreen(viewModel = viewModel)
                AppScreen.ImageToPdfTool -> ImageToPdfScreen(viewModel = viewModel)
                AppScreen.ScannerTool -> CameraScannerScreen(viewModel = viewModel)
                AppScreen.EncryptTool -> EncryptScreen(viewModel = viewModel)
                
                AppScreen.AIChat -> AiScreen(viewModel = viewModel)
                AppScreen.Settings -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
