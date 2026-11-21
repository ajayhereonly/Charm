package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.python.PythonLibraryCatalog
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.PythonAmber
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TerminalScreen(
    uiState: StudioUiState,
    viewModel: StudioViewModel,
    onInsertLibraryToIde: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }
    var historyCursor by remember { mutableIntStateOf(-1) }
    var showLibsDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.terminalEntries.size) {
        if (uiState.terminalEntries.isNotEmpty()) {
            listState.animateScrollToItem(uiState.terminalEntries.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(10.dp)
    ) {
        // Header strip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Surface(
                color = ObsidianSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = "Python 3.12 REPL & Shell",
                        style = MaterialTheme.typography.titleSmall,
                        color = ElectricCyan
                    )
                    Text(
                        text = "${PythonLibraryCatalog.libraries.size} libs active",
                        style = MaterialTheme.typography.labelSmall,
                        color = SuccessEmerald
                    )
                }
            }

            OutlinedButton(
                onClick = { showLibsDialog = true },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                border = BorderStroke(1.dp, PythonAmber.copy(alpha = 0.65f)),
                modifier = Modifier
                    .height(40.dp)
                    .testTag("terminal_libraries_button")
            ) {
                Icon(Icons.Filled.LibraryBooks, contentDescription = "Python Libraries", tint = PythonAmber, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Libraries", color = PythonAmber, style = MaterialTheme.typography.labelLarge)
            }

            IconButton(
                onClick = { viewModel.runTerminalCommand("clear") },
                modifier = Modifier
                    .size(40.dp)
                    .background(ObsidianSurface, RoundedCornerShape(10.dp))
            ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Clear terminal", tint = TextSecondary)
            }
        }

        // Quick shell & REPL command chips
        val quickCmds = listOf(
            "pip list",
            "ls",
            "import numpy as np; np.linspace(0, 1, 5)",
            "import math; math.factorial(10)",
            "help",
            "reset"
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp)
        ) {
            quickCmds.forEach { cmd ->
                Surface(
                    color = ObsidianSurfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.clickable { viewModel.runTerminalCommand(cmd) }
                ) {
                    Text(
                        text = cmd,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Terminal Scroll Output
        Surface(
            color = Color(0xFF070B15),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("terminal_output_list")
            ) {
                items(uiState.terminalEntries, key = { it.id }) { entry ->
                    val color = when (entry.kind) {
                        TerminalLineKind.COMMAND -> ElectricCyan
                        TerminalLineKind.STDOUT -> TextPrimary
                        TerminalLineKind.RESULT -> SuccessEmerald
                        TerminalLineKind.STDERR -> ErrorCoral
                        TerminalLineKind.SYSTEM -> PythonAmber
                    }
                    Text(
                        text = entry.text,
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            color = color
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Interactive Input Prompt Bar
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = ">>>",
                    style = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ElectricCyan
                    ),
                    modifier = Modifier.padding(start = 6.dp)
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = {
                        Text(
                            "Enter Python or shell command...",
                            style = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, color = TextSecondary)
                        )
                    },
                    textStyle = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.sp,
                        color = TextPrimary
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (input.isNotBlank()) {
                                viewModel.runTerminalCommand(input)
                                input = ""
                                historyCursor = -1
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("terminal_input_field")
                )

                if (uiState.terminalHistory.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            val next = if (historyCursor < 0) uiState.terminalHistory.lastIndex
                            else (historyCursor - 1).coerceAtLeast(0)
                            historyCursor = next
                            input = uiState.terminalHistory[next]
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.History, contentDescription = "Previous command", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                IconButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            viewModel.runTerminalCommand(input)
                            input = ""
                            historyCursor = -1
                        }
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("terminal_send_button")
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Execute command", tint = ElectricCyan)
                }
            }
        }
    }

    if (showLibsDialog) {
        LibraryCatalogDialog(
            onDismiss = { showLibsDialog = false },
            onInsertImport = { snippet ->
                showLibsDialog = false
                onInsertLibraryToIde(snippet)
            }
        )
    }
}

@Composable
private fun LibraryCatalogDialog(
    onDismiss: () -> Unit,
    onInsertImport: (String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(search) {
        if (search.isBlank()) PythonLibraryCatalog.libraries
        else PythonLibraryCatalog.libraries.filter {
            it.name.contains(search, true) || it.category.contains(search, true) || it.description.contains(search, true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Column {
                Text("Bundled Python Libraries", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${PythonLibraryCatalog.libraries.size} ready-to-import stdlib, scientific & 3D modules",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.height(420.dp)
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search numpy, py3d, pandas, scipy...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filtered, key = { it.name }) { lib ->
                        Surface(
                            color = ObsidianSurfaceElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${lib.name}  v${lib.version}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = ElectricCyan
                                    )
                                    Text(
                                        text = lib.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PythonAmber
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = lib.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = lib.sampleImport,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SuccessEmerald
                                    )
                                    Button(
                                        onClick = { onInsertImport(lib.sampleImport) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ElectricCyan.copy(alpha = 0.18f),
                                            contentColor = ElectricCyan
                                        ),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Use in IDE", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
