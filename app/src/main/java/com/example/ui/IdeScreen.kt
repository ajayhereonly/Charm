package com.example.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.firebase.PythonScript
import com.example.firebase.ScriptCategory
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.PythonAmber
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.SyntaxViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun IdeScreen(
    uiState: StudioUiState,
    scriptsState: WorkspaceLoadState<List<PythonScript>>,
    viewModel: StudioViewModel,
    onJumpTo3D: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showAiDialog by remember { mutableStateOf(false) }
    var showOutputPane by remember { mutableStateOf(true) }

    // Zero-permission SAF import for any .py file
    val importPyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                var displayName = "imported.py"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && cursor.moveToFirst()) {
                        displayName = cursor.getString(nameIdx) ?: displayName
                    }
                }
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (content != null) {
                    viewModel.importExternalPythonFile(displayName, content)
                }
            } catch (e: Exception) {
                viewModel.showMessage("Could not import file: ${e.localizedMessage}")
            }
        }
    }

    // Zero-permission SAF export for current .py file
    val exportPyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-python")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(uiState.editorCode.toByteArray())
                }
                viewModel.showMessage("Exported ${uiState.editorFilename}")
            } catch (e: Exception) {
                viewModel.showMessage("Export failed: ${e.localizedMessage}")
            }
        }
    }

    val scripts = (scriptsState as? WorkspaceLoadState.Success)?.data.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // Workspace File Tabs Bar
        Surface(
            color = ObsidianSurface,
            border = BorderStroke(0.5.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(scripts, key = { it.id }) { script ->
                        val selected = script.id == uiState.activeScriptId
                        Surface(
                            color = if (selected) ObsidianSurfaceElevated else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (selected) ElectricCyan.copy(alpha = 0.7f) else ObsidianBorder
                            ),
                            modifier = Modifier
                                .clickable { viewModel.openScript(script) }
                                .testTag("script_tab_${script.filename}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = script.filename,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) ElectricCyan else TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (selected && uiState.isDirty) {
                                    Text(
                                        text = " •",
                                        color = PythonAmber,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }

                IconButton(
                    onClick = { showNewFileDialog = true },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("ide_new_file_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "New Python file", tint = ElectricCyan)
                }
                IconButton(
                    onClick = { importPyLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("ide_import_py_button")
                ) {
                    Icon(Icons.Filled.FileOpen, contentDescription = "Import .py file", tint = TextSecondary)
                }
                IconButton(
                    onClick = { exportPyLauncher.launch(uiState.editorFilename) },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("ide_export_py_button")
                ) {
                    Icon(Icons.Filled.Download, contentDescription = "Export .py file", tint = TextSecondary)
                }
            }
        }

        // Primary IDE Action Toolbar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurfaceElevated.copy(alpha = 0.65f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Button(
                onClick = {
                    showOutputPane = true
                    viewModel.runCurrentScript()
                },
                enabled = !uiState.isRunningPython,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = Color(0xFF001C24)
                ),
                modifier = Modifier
                    .height(40.dp)
                    .testTag("ide_run_button")
            ) {
                if (uiState.isRunningPython) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF001C24)
                    )
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Run Python", modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("Run", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }

            OutlinedButton(
                onClick = { viewModel.saveCurrentScript() },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                border = BorderStroke(1.dp, if (uiState.isDirty) PythonAmber else ObsidianBorder),
                modifier = Modifier
                    .height(40.dp)
                    .testTag("ide_save_button")
            ) {
                Icon(
                    Icons.Filled.Save,
                    contentDescription = "Save to cloud",
                    tint = if (uiState.isDirty) PythonAmber else TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    "Save",
                    color = if (uiState.isDirty) PythonAmber else TextPrimary,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            OutlinedButton(
                onClick = { showAiDialog = true },
                enabled = !uiState.isAiBusy,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                border = BorderStroke(1.dp, SyntaxViolet.copy(alpha = 0.7f)),
                modifier = Modifier
                    .height(40.dp)
                    .testTag("ide_ai_assist_button")
            ) {
                if (uiState.isAiBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = SyntaxViolet)
                } else {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = "AI Code Assist", tint = SyntaxViolet, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(5.dp))
                Text("AI Code", color = SyntaxViolet, style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.weight(1f))

            if (uiState.lastExecution?.scene3D != null) {
                Surface(
                    color = PythonAmber.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, PythonAmber.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .clickable { onJumpTo3D() }
                        .testTag("ide_open_3d_scene_badge")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Icon(Icons.Filled.ViewInAr, contentDescription = "View 3D Model", tint = PythonAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("View 3D", color = PythonAmber, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            val activeScript = scripts.firstOrNull { it.id == uiState.activeScriptId }
            if (activeScript != null && scripts.size > 1) {
                IconButton(
                    onClick = { viewModel.deleteScript(activeScript) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete file", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Quick Python Symbol & Library Bar
        val quickSnippets = listOf(
            "def " to "def fn():\n    ",
            "for " to "for i in range(10):\n    ",
            "import py3d" to "import py3d\nscene = py3d.Scene()\n",
            "import np" to "import numpy as np\n",
            "import plt" to "import matplotlib.pyplot as plt\n",
            "print()" to "print()",
            "    " to "    "
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            quickSnippets.forEach { (label, code) ->
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.clickable { viewModel.insertSnippet(code) }
                ) {
                    Text(
                        text = if (label == "    ") "TAB" else label,
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Syntax-Highlighted Code Editor with Line Number Gutter
        val editorScroll = rememberScrollState()
        val lineCount = remember(uiState.editorCode) { uiState.editorCode.count { it == '\n' } + 1 }

        Surface(
            color = Color(0xFF080C18),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(editorScroll)
                    .padding(vertical = 10.dp)
            ) {
                // Line numbers
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .background(Color(0xFF0B1020))
                        .padding(start = 8.dp, end = 10.dp)
                ) {
                    for (n in 1..lineCount) {
                        Text(
                            text = n.toString(),
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = Color(0xFF475569)
                            )
                        )
                    }
                }

                BasicTextField(
                    value = uiState.editorCode,
                    onValueChange = { viewModel.updateEditorCode(it) },
                    textStyle = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = TextPrimary
                    ),
                    cursorBrush = SolidColor(ElectricCyan),
                    visualTransformation = PythonSyntaxHighlighter,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                        .testTag("python_code_editor")
                )
            }
        }

        // Execution Output & Matplotlib Plot Drawer
        val exec = uiState.lastExecution
        if (exec != null && showOutputPane) {
            Surface(
                color = ObsidianSurface,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                border = BorderStroke(1.dp, if (exec.success) ObsidianBorder else ErrorCoral.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 270.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (exec.success) "OUTPUT • ${exec.elapsedMs}ms" else "TRACEBACK ERROR • ${exec.elapsedMs}ms",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (exec.success) SuccessEmerald else ErrorCoral
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (!exec.success) {
                            TextButton(
                                onClick = {
                                    viewModel.requestAiCodeAssist("Fix the Python error in this script and keep its intended behavior.")
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("ide_ai_fix_error_button")
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = SyntaxViolet, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("AI Fix", color = SyntaxViolet, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(
                            onClick = { showOutputPane = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Hide output", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (exec.stdout.isNotBlank()) {
                            Text(
                                text = exec.stdout.trimEnd(),
                                style = TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = TextPrimary
                                ),
                                modifier = Modifier.testTag("ide_stdout_text")
                            )
                        }
                        if (exec.stderr.isNotBlank()) {
                            Text(
                                text = exec.stderr.trimEnd(),
                                style = TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = ErrorCoral
                                ),
                                modifier = Modifier.testTag("ide_stderr_text")
                            )
                        }
                        exec.plots.forEach { plot ->
                            PlotCard(spec = plot)
                        }
                        if (exec.stdout.isBlank() && exec.stderr.isBlank() && exec.plots.isEmpty()) {
                            Text(
                                text = "Script completed with no console output.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewFileDialog) {
        NewPythonFileDialog(
            onDismiss = { showNewFileDialog = false },
            onCreate = { name, cat ->
                viewModel.createNewScript(name, cat)
                showNewFileDialog = false
            }
        )
    }

    if (showAiDialog) {
        AiCodePromptDialog(
            title = "Gemini Python Assistant",
            hint = "e.g., Write a fractal tree generator, solve a matrix system with numpy, or build a 3D rocket using py3d",
            onDismiss = { showAiDialog = false },
            onGenerate = { prompt ->
                showAiDialog = false
                viewModel.requestAiCodeAssist(prompt, for3D = prompt.contains("3d", true))
            }
        )
    }
}

@Composable
private fun NewPythonFileDialog(
    onDismiss: () -> Unit,
    onCreate: (String, ScriptCategory) -> Unit
) {
    var name by remember { mutableStateOf("script.py") }
    var category by remember { mutableStateOf(ScriptCategory.PYTHON) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text("New Python File", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Filename") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Template", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    ScriptCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                selectedLabelColor = ElectricCyan
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name, category) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF001C24))
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AiCodePromptDialog(
    title: String,
    hint: String,
    onDismiss: () -> Unit,
    onGenerate: (String) -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = SyntaxViolet)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                placeholder = { Text(hint, style = MaterialTheme.typography.bodySmall) },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_prompt_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { if (prompt.isNotBlank()) onGenerate(prompt.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = SyntaxViolet, contentColor = ObsidianBg),
                modifier = Modifier.testTag("ai_prompt_submit")
            ) {
                Text("Generate", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
