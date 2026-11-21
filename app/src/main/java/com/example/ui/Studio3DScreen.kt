package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.graphics3d.Gl3DViewport
import com.example.graphics3d.MeshBuilder
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.PythonAmber
import com.example.ui.theme.SyntaxViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun Studio3DScreen(
    uiState: StudioUiState,
    viewModel: StudioViewModel,
    onEditCodeInIde: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var aiPrompt by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf("#00E5FF") }

    val exportObjLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(uiState.activeScene3D.exportToObj().toByteArray())
                }
                viewModel.showMessage("Exported Wavefront .OBJ mesh")
            } catch (e: Exception) {
                viewModel.showMessage("Export failed: ${e.localizedMessage}")
            }
        }
    }

    val exportStlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(uiState.activeScene3D.exportToAsciiStl().toByteArray())
                }
                viewModel.showMessage("Exported 3D-printable .STL mesh")
            } catch (e: Exception) {
                viewModel.showMessage("Export failed: ${e.localizedMessage}")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // Real GPU-Accelerated OpenGL ES 2.0 3D Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.54f)
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
        ) {
            Gl3DViewport(
                scene = uiState.activeScene3D,
                modifier = Modifier.fillMaxSize()
            )
            // Bottom-left scene title pill
            Surface(
                color = ObsidianBg.copy(alpha = 0.8f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Text(
                    text = "${uiState.activeScene3D.title}  •  Drag to orbit • Pinch to zoom",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        // Scrollable 3D Modeling Workbench Panel
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.46f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // AI 3D Model Generator Prompt Bar
            Surface(
                color = ObsidianSurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, SyntaxViolet.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = "AI 3D Builder",
                        tint = SyntaxViolet,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = aiPrompt,
                        onValueChange = { aiPrompt = it },
                        placeholder = {
                            Text(
                                "AI 3D CAD: e.g., futuristic satellite, spiral tower...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("studio3d_ai_prompt")
                    )
                    IconButton(
                        onClick = {
                            if (aiPrompt.isNotBlank()) {
                                viewModel.requestAiCodeAssist(aiPrompt.trim(), for3D = true)
                                aiPrompt = ""
                            }
                        },
                        enabled = !uiState.isAiBusy && aiPrompt.isNotBlank(),
                        modifier = Modifier.testTag("studio3d_ai_send")
                    ) {
                        if (uiState.isAiBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = SyntaxViolet)
                        } else {
                            Icon(Icons.Filled.Send, contentDescription = "Generate 3D Model", tint = SyntaxViolet)
                        }
                    }
                }
            }

            // Color Swatches & Quick Add CAD Primitives
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Add 3D Primitive",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    val swatches = listOf("#00E5FF", "#FFB300", "#22D3A0", "#C792EA", "#FF5370", "#60A5FA")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        swatches.forEach { hex ->
                            val rgba = MeshBuilder.parseColor(hex)
                            val c = Color(rgba[0], rgba[1], rgba[2], 1f)
                            val isSel = selectedColorHex == hex
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (isSel) 2.5.dp else 1.dp,
                                        color = if (isSel) Color.White else ObsidianBorder,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColorHex = hex }
                            )
                        }
                    }
                }

                val primitives = listOf(
                    "cube" to "Cube",
                    "sphere" to "Sphere",
                    "torus" to "Torus",
                    "cylinder" to "Cylinder",
                    "cone" to "Cone",
                    "pyramid" to "Pyramid",
                    "icosahedron" to "Crystal",
                    "helix" to "Helix",
                    "gear" to "Gear"
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    primitives.forEach { (key, label) ->
                        Surface(
                            color = ObsidianSurfaceElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier
                                .clickable { viewModel.add3DPrimitive(key, selectedColorHex) }
                                .testTag("add_primitive_$key")
                        ) {
                            Text(
                                text = "+ $label",
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricCyan,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }

            // Procedural Python 3D Presets
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Procedural Python 3D Scenes",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                val presets = listOf(
                    "gyro" to "Orbital Gyro",
                    "wave" to "Wave Surface",
                    "dna" to "DNA Helix",
                    "solar" to "Solar System"
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    presets.forEach { (key, label) ->
                        OutlinedButton(
                            onClick = { viewModel.load3DPreset(key) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            border = BorderStroke(1.dp, PythonAmber.copy(alpha = 0.55f)),
                            modifier = Modifier.testTag("preset_3d_$key")
                        ) {
                            Text(label, color = PythonAmber, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // Export & Scene Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { exportObjLauncher.launch("pyforge_model.obj") },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF001C24)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_obj_button")
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export .OBJ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = { exportStlLauncher.launch("pyforge_model.stl") },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_stl_button")
                ) {
                    Text("Export .STL", color = ElectricCyan, style = MaterialTheme.typography.labelLarge)
                }
                IconButton(
                    onClick = onEditCodeInIde,
                    modifier = Modifier
                        .size(44.dp)
                        .background(ObsidianSurfaceElevated, RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Filled.Code, contentDescription = "Edit 3D Python script", tint = TextPrimary)
                }
                IconButton(
                    onClick = { viewModel.clear3DScene() },
                    modifier = Modifier
                        .size(44.dp)
                        .background(ObsidianSurfaceElevated, RoundedCornerShape(10.dp))
                        .testTag("clear_3d_scene_button")
                ) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear 3D scene", tint = TextSecondary)
                }
            }

            // Scene Outliner Summary
            if (uiState.activeScene3D.objects.isNotEmpty()) {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Scene Meshes (${uiState.activeScene3D.objects.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        uiState.activeScene3D.objects.forEach { obj ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(Color(obj.colorRgba[0], obj.colorRgba[1], obj.colorRgba[2], 1f))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(obj.name, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                }
                                Text(
                                    "${obj.triangleCount} tris",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
