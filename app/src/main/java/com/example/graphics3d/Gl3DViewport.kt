package com.example.graphics3d

import android.opengl.GLSurfaceView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.PythonAmber

@Composable
fun Gl3DViewport(
    scene: Scene3D,
    modifier: Modifier = Modifier,
    compactHud: Boolean = false
) {
    var fps by remember { mutableIntStateOf(60) }
    var autoRotate by remember { mutableStateOf(true) }
    var wireframe by remember { mutableStateOf(scene.wireframeOverlay) }
    var showGrid by remember { mutableStateOf(scene.showGrid) }

    val renderer = remember {
        Gl3DRenderer(onFpsUpdate = { measured -> fps = measured })
    }
    var glSurfaceViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }

    LaunchedEffect(scene) {
        renderer.setScene(scene)
    }
    LaunchedEffect(autoRotate) {
        renderer.autoRotate = autoRotate
    }
    LaunchedEffect(wireframe) {
        renderer.wireframeOverlay = wireframe
    }
    LaunchedEffect(showGrid) {
        renderer.showGrid = showGrid
    }

    DisposableEffect(Unit) {
        onDispose {
            glSurfaceViewRef?.onPause()
        }
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1f) {
                        renderer.distance = (renderer.distance / zoom).coerceIn(1.8f, 32f)
                    }
                    if (pan.x != 0f || pan.y != 0f) {
                        autoRotate = false
                        renderer.yawDeg = (renderer.yawDeg - pan.x * 0.35f) % 360f
                        renderer.pitchDeg = (renderer.pitchDeg + pan.y * 0.35f).coerceIn(-80f, 80f)
                    }
                }
            }
            .testTag("gl_3d_viewport")
    ) {
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(2)
                    setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    glSurfaceViewRef = this
                }
            },
            update = { view ->
                glSurfaceViewRef = view
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top-left GPU Telemetry HUD
        Surface(
            color = ObsidianBg.copy(alpha = 0.78f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "GLES 2.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
                Text(
                    text = "${fps} FPS",
                    style = MaterialTheme.typography.labelSmall,
                    color = PythonAmber
                )
                if (!compactHud) {
                    Text(
                        text = "${scene.objects.size} objs • ${scene.totalTriangles} tris",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Top-right quick viewport controls
        Surface(
            color = ObsidianBg.copy(alpha = 0.8f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { autoRotate = !autoRotate },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("viewport_toggle_rotate")
                ) {
                    Icon(
                        imageVector = if (autoRotate) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (autoRotate) "Pause auto-orbit" else "Resume auto-orbit",
                        tint = if (autoRotate) ElectricCyan else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { wireframe = !wireframe },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("viewport_toggle_wireframe")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Texture,
                        contentDescription = "Toggle wireframe overlay",
                        tint = if (wireframe) PythonAmber else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { showGrid = !showGrid },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("viewport_toggle_grid")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Grid4x4,
                        contentDescription = "Toggle reference grid",
                        tint = if (showGrid) ElectricCyan else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { renderer.resetCamera() },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("viewport_reset_camera")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CenterFocusStrong,
                        contentDescription = "Reset camera",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
