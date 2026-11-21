package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.firebase.AutomatedAppRecord
import com.example.firebase.InputsSpecCodec
import com.example.firebase.TriggerMode
import com.example.graphics3d.Gl3DViewport
import com.example.graphics3d.MeshBuilder
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
import kotlinx.coroutines.delay

@Composable
fun AutomatorScreen(
    uiState: StudioUiState,
    appsState: WorkspaceLoadState<List<AutomatedAppRecord>>,
    viewModel: StudioViewModel,
    onOpenCodeInIde: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeApp = uiState.runningApp
    if (activeApp != null) {
        AutomatedAppRunnerScreen(
            app = activeApp,
            uiState = uiState,
            viewModel = viewModel,
            onClose = { viewModel.closeAutomatedApp() },
            onInspectCode = {
                viewModel.openAutomatedAppInIde(activeApp)
                onOpenCodeInIde()
            },
            modifier = modifier
        )
        return
    }

    var ideaPrompt by remember { mutableStateOf("") }
    val apps = (appsState as? WorkspaceLoadState.Success)?.data.orEmpty()

    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // AI Automated App Generator Hero Card
        item {
            Surface(
                color = ObsidianSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, SyntaxViolet.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    SyntaxViolet.copy(alpha = 0.12f),
                                    ElectricCyan.copy(alpha = 0.06f)
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = SyntaxViolet, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "AI Automated App Builder",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = "Describe any app — Gemini builds live UI controls, triggers, Python logic, charts & 3D views",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    OutlinedTextField(
                        value = ideaPrompt,
                        onValueChange = { ideaPrompt = it },
                        placeholder = {
                            Text("e.g., Automated Projectile Physics Simulator with 3D trajectory & sliders")
                        },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("automator_prompt_input")
                    )

                    // Quick inspiration chips
                    val ideas = listOf(
                        "3D Solar Orbit Simulator",
                        "Loan & Mortgage Auto Calculator",
                        "Live Signal Filter & Chart"
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        ideas.forEach { idea ->
                            Surface(
                                color = ObsidianSurfaceElevated,
                                shape = RoundedCornerShape(100.dp),
                                border = BorderStroke(1.dp, ObsidianBorder),
                                modifier = Modifier.clickable { ideaPrompt = idea }
                            ) {
                                Text(
                                    text = idea,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (ideaPrompt.isNotBlank()) {
                                val req = ideaPrompt.trim()
                                ideaPrompt = ""
                                viewModel.generateAutomatedAppWithAi(req) { created ->
                                    viewModel.launchAutomatedApp(created)
                                }
                            }
                        },
                        enabled = !uiState.isAiBusy && ideaPrompt.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SyntaxViolet,
                            contentColor = ObsidianBg
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("automator_generate_button")
                    ) {
                        if (uiState.isAiBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ObsidianBg)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(uiState.aiStatusMessage ?: "Building Automated App...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Build & Launch Automated App", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Your Automated Apps (${apps.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Synced via Firestore",
                    style = MaterialTheme.typography.labelSmall,
                    color = SuccessEmerald
                )
            }
        }

        when (appsState) {
            is WorkspaceLoadState.Loading -> {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    ) {
                        CircularProgressIndicator(color = ElectricCyan)
                    }
                }
            }
            is WorkspaceLoadState.Error -> {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = appsState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
            is WorkspaceLoadState.Success -> {
                items(apps, key = { it.id }) { app ->
                    AutomatedAppCard(
                        app = app,
                        onLaunch = { viewModel.launchAutomatedApp(app) },
                        onInspectCode = {
                            viewModel.openAutomatedAppInIde(app)
                            onOpenCodeInIde()
                        },
                        onDelete = { viewModel.deleteAutomatedApp(app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AutomatedAppCard(
    app: AutomatedAppRecord,
    onLaunch: () -> Unit,
    onInspectCode: () -> Unit,
    onDelete: () -> Unit
) {
    val rgba = MeshBuilder.parseColor(app.accentHex)
    val accent = Color(rgba[0], rgba[1], rgba[2], 1f)
    val trigger = app.resolvedTriggerMode()

    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLaunch() }
            .testTag("automated_app_card_${app.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = accent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = iconForKey(app.iconKey),
                            contentDescription = app.name,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = app.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete app", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = ObsidianSurfaceElevated,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (trigger == TriggerMode.INTERVAL) "Auto every ${app.intervalSeconds.coerceAtLeast(1)}s" else trigger.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (app.has3dViewport) {
                    Surface(
                        color = PythonAmber.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "GPU 3D",
                            style = MaterialTheme.typography.labelSmall,
                            color = PythonAmber,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onLaunch,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = ObsidianBg),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("launch_app_${app.id}")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Launch App", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onInspectCode,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Filled.Code, contentDescription = "View Python Code", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Code", color = TextPrimary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun AutomatedAppRunnerScreen(
    app: AutomatedAppRecord,
    uiState: StudioUiState,
    viewModel: StudioViewModel,
    onClose: () -> Unit,
    onInspectCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onClose() }

    val controls = remember(app.id, app.inputsSpec) { InputsSpecCodec.parse(app.inputsSpec) }
    val values = remember(app.id) {
        mutableStateMapOf<String, String>().apply {
            controls.forEach { put(it.key, it.defaultValue) }
        }
    }
    val trigger = app.resolvedTriggerMode()
    var autoRunning by remember(app.id) { mutableStateOf(trigger == TriggerMode.INTERVAL) }
    var tick by remember(app.id) { mutableIntStateOf(1) }

    // Initial run on launch
    LaunchedEffect(app.id) {
        viewModel.executeAutomatedApp(app, values.toMap(), tick)
    }

    // Interval scheduler for INTERVAL automated apps
    LaunchedEffect(app.id, autoRunning) {
        if (trigger == TriggerMode.INTERVAL && autoRunning) {
            val intervalMs = (app.intervalSeconds.coerceAtLeast(1) * 1000L).coerceIn(1000L, 60_000L)
            while (true) {
                delay(intervalMs)
                tick += 1
                viewModel.executeAutomatedApp(app, values.toMap(), tick)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // Runner Top Bar
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
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("runner_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to apps", tint = TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        text = if (trigger == TriggerMode.INTERVAL) "Auto-tick #$tick (${if (autoRunning) "Active" else "Paused"})" else trigger.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }
                if (trigger == TriggerMode.INTERVAL) {
                    IconButton(onClick = { autoRunning = !autoRunning }) {
                        Icon(
                            imageVector = if (autoRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (autoRunning) "Pause automation" else "Resume automation",
                            tint = PythonAmber
                        )
                    }
                }
                IconButton(
                    onClick = {
                        tick += 1
                        viewModel.executeAutomatedApp(app, values.toMap(), tick)
                    },
                    modifier = Modifier.testTag("runner_manual_run")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Run now", tint = ElectricCyan)
                }
                IconButton(onClick = onInspectCode) {
                    Icon(Icons.Filled.Code, contentDescription = "Open Python code in IDE", tint = TextSecondary)
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            // Optional Live GPU 3D Viewport
            val liveScene = uiState.appRunScene3D
            if (app.has3dViewport && liveScene != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                ) {
                    Gl3DViewport(scene = liveScene, compactHud = true, modifier = Modifier.fillMaxSize())
                }
            }

            // KPI Metrics Cards emitted via METRIC|Title|Value|Unit
            if (uiState.appRunMetrics.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    uiState.appRunMetrics.forEach { m ->
                        Surface(
                            color = ObsidianSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(m.title, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(m.value, style = MaterialTheme.typography.titleLarge, color = ElectricCyan)
                                if (m.unit.isNotBlank()) {
                                    Text(m.unit, style = MaterialTheme.typography.labelSmall, color = PythonAmber)
                                }
                            }
                        }
                    }
                }
            }

            // Interactive Controls Panel
            if (controls.isNotEmpty()) {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text("Live Automation Controls", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        controls.forEach { ctrl ->
                            val currentVal = values[ctrl.key] ?: ctrl.defaultValue
                            val onUpdate: (String) -> Unit = { newVal ->
                                values[ctrl.key] = newVal
                                if (trigger == TriggerMode.ON_CHANGE || trigger == TriggerMode.INTERVAL) {
                                    viewModel.executeAutomatedApp(app, values.toMap(), tick)
                                }
                            }

                            when (ctrl.type) {
                                "slider" -> {
                                    val floatVal = currentVal.toFloatOrNull()?.coerceIn(ctrl.minValue, ctrl.maxValue) ?: ctrl.minValue
                                    Column {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(ctrl.label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                            Text(
                                                String.format("%.2f", floatVal),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = ElectricCyan
                                            )
                                        }
                                        Slider(
                                            value = floatVal,
                                            onValueChange = { onUpdate(String.format("%.2f", it)) },
                                            valueRange = ctrl.minValue..ctrl.maxValue,
                                            colors = SliderDefaults.colors(
                                                thumbColor = ElectricCyan,
                                                activeTrackColor = ElectricCyan
                                            )
                                        )
                                    }
                                }
                                "toggle" -> {
                                    val checked = currentVal.equals("true", true)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(ctrl.label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                        Switch(
                                            checked = checked,
                                            onCheckedChange = { onUpdate(it.toString()) },
                                            colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = ElectricCyan)
                                        )
                                    }
                                }
                                "select" -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(ctrl.label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.horizontalScroll(rememberScrollState())
                                        ) {
                                            ctrl.options.forEach { opt ->
                                                FilterChip(
                                                    selected = currentVal == opt,
                                                    onClick = { onUpdate(opt) },
                                                    label = { Text(opt) }
                                                )
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    OutlinedTextField(
                                        value = currentVal,
                                        onValueChange = { onUpdate(it) },
                                        label = { Text(ctrl.label) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        if (trigger == TriggerMode.MANUAL) {
                            Button(
                                onClick = {
                                    tick += 1
                                    viewModel.executeAutomatedApp(app, values.toMap(), tick)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Run Automation", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Live Matplotlib Plots
            uiState.appRunPlots.forEach { plot ->
                PlotCard(spec = plot)
            }

            // Live Automation Console Log
            val res = uiState.appRunResult
            if (res != null) {
                Surface(
                    color = Color(0xFF070B15),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (res.success) ObsidianBorder else ErrorCoral),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (res.success) "AUTOMATION LOG • ${res.elapsedMs}ms" else "PYTHON ERROR",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (res.success) SuccessEmerald else ErrorCoral
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = (res.stdout + res.stderr).trim().ifBlank { "Executed successfully." },
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = if (res.success) TextPrimary else ErrorCoral
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun iconForKey(key: String): ImageVector = when (key.lowercase()) {
    "cube" -> Icons.Filled.ViewInAr
    "chart" -> Icons.Filled.ShowChart
    "timer" -> Icons.Filled.Timer
    "calculator" -> Icons.Filled.Calculate
    "rocket" -> Icons.Filled.RocketLaunch
    "science" -> Icons.Filled.Science
    else -> Icons.Filled.Bolt
}
