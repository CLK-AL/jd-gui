/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.ui.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jd.gui.ui.FileEntry

/**
 * Main ViewModel for JD-GUI Compose application.
 * Manages UI state and business logic.
 */
class MainViewModel {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Open file picker dialog.
     */
    fun openFilePicker() {
        // Platform-specific file picker will be implemented
        _uiState.value = _uiState.value.copy(showFilePicker = true)
    }

    /**
     * Toggle search panel visibility.
     */
    fun toggleSearch() {
        _uiState.value = _uiState.value.copy(
            showSearch = !_uiState.value.showSearch
        )
    }

    /**
     * Open settings dialog.
     */
    fun openSettings() {
        _uiState.value = _uiState.value.copy(showSettings = true)
    }

    /**
     * Select a file entry from the tree.
     */
    fun selectEntry(entry: FileEntry) {
        _uiState.value = _uiState.value.copy(
            selectedEntry = entry,
            sourceCode = if (!entry.isDirectory) {
                "// Loading ${entry.name}..."
            } else {
                ""
            }
        )
        // In real implementation, this would trigger decompilation
    }

    /**
     * Load JAR/CLASS file.
     */
    fun loadFile(path: String) {
        // Platform-specific file loading
        _uiState.value = _uiState.value.copy(
            currentFilePath = path,
            fileEntries = listOf(
                FileEntry(path, path.substringAfterLast("/"), false, 0, 0)
            )
        )
    }

    /**
     * Update decompiled source code.
     */
    fun updateSourceCode(source: String, language: String = "java") {
        _uiState.value = _uiState.value.copy(
            sourceCode = source,
            currentLanguage = language
        )
    }

    /**
     * Close file picker.
     */
    fun closeFilePicker() {
        _uiState.value = _uiState.value.copy(showFilePicker = false)
    }

    /**
     * Close settings dialog.
     */
    fun closeSettings() {
        _uiState.value = _uiState.value.copy(showSettings = false)
    }

    /**
     * Toggle line numbers visibility.
     */
    fun toggleLineNumbers() {
        _uiState.value = _uiState.value.copy(
            showLineNumbers = !_uiState.value.showLineNumbers
        )
    }
}

/**
 * UI State for JD-GUI application.
 */
data class UiState(
    val currentFilePath: String = "",
    val fileEntries: List<FileEntry> = emptyList(),
    val selectedEntry: FileEntry? = null,
    val sourceCode: String = "",
    val currentLanguage: String = "java",
    val showLineNumbers: Boolean = true,
    val showFilePicker: Boolean = false,
    val showSettings: Boolean = false,
    val showSearch: Boolean = false,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
