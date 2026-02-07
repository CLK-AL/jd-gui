/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package al.clk.gui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import al.clk.gui.ui.components.*
import al.clk.gui.ui.theme.JdGuiTheme
import al.clk.gui.ui.viewmodel.MainViewModel

/**
 * Main gui application composable.
 * This is the shared entry point for all Compose platforms (Android, iOS, Desktop).
 */
@Composable
fun App() {
    val viewModel = remember { MainViewModel() }

    JdGuiTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MainContent(viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("gui") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // Open file button
                    IconButton(onClick = { viewModel.openFilePicker() }) {
                        Text("📂")
                    }
                    // Search button
                    IconButton(onClick = { viewModel.toggleSearch() }) {
                        Text("🔍")
                    }
                    // Settings button
                    IconButton(onClick = { viewModel.openSettings() }) {
                        Text("⚙️")
                    }
                }
            )
        }
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // File tree panel (left side)
            FileTreePanel(
                entries = uiState.fileEntries,
                selectedEntry = uiState.selectedEntry,
                onEntryClick = viewModel::selectEntry,
                modifier = Modifier
                    .width(250.dp)
                    .fillMaxHeight()
            )

            // Divider
            Divider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
            )

            // Source code panel (right side)
            SourceCodePanel(
                sourceCode = uiState.sourceCode,
                language = uiState.currentLanguage,
                lineNumbers = uiState.showLineNumbers,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

/**
 * File tree panel showing archive structure.
 */
@Composable
fun FileTreePanel(
    entries: List<FileEntry>,
    selectedEntry: FileEntry?,
    onEntryClick: (FileEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Drop a JAR/CLASS file here\nor use 📂 to open",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(entries) { entry ->
                    FileTreeItem(
                        entry = entry,
                        isSelected = entry == selectedEntry,
                        onClick = { onEntryClick(entry) }
                    )
                }
            }
        }
    }
}

/**
 * Individual file tree item.
 */
@Composable
fun FileTreeItem(
    entry: FileEntry,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    val icon = when {
        entry.isDirectory -> "📁"
        entry.name.endsWith(".class") -> "☕"
        entry.name.endsWith(".java") -> "📄"
        entry.name.endsWith(".kt") -> "🟣"
        entry.name.endsWith(".xml") -> "📋"
        entry.name.endsWith(".json") -> "📋"
        else -> "📄"
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indentation
            Spacer(modifier = Modifier.width((entry.depth * 16).dp))

            // Icon
            Text(
                text = icon,
                modifier = Modifier.padding(end = 8.dp)
            )

            // Name
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Source code panel with syntax highlighting.
 */
@Composable
fun SourceCodePanel(
    sourceCode: String,
    language: String,
    lineNumbers: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface
    ) {
        if (sourceCode.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select a class file to view source",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                // Line numbers
                if (lineNumbers) {
                    LineNumbersColumn(
                        lineCount = sourceCode.lines().size,
                        modifier = Modifier.width(48.dp)
                    )
                }

                // Source code
                SourceCodeView(
                    sourceCode = sourceCode,
                    language = language,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Line numbers column.
 */
@Composable
fun LineNumbersColumn(
    lineCount: Int,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp)
    ) {
        items(lineCount) { lineNumber ->
            Text(
                text = "${lineNumber + 1}",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Source code view with basic syntax highlighting.
 */
@Composable
fun SourceCodeView(
    sourceCode: String,
    language: String,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(8.dp)
    ) {
        items(sourceCode.lines()) { line ->
            Text(
                text = line,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * File entry data class.
 */
data class FileEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val depth: Int = 0,
    val size: Long = 0
)
