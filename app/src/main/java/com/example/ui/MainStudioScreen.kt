package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.firebase.AiStudioService
import com.example.firebase.WorkspaceRepository
import com.example.firebase.signOutUser
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.PythonAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

enum class StudioTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    IDE("IDE", Icons.Filled.Code, Icons.Outlined.Code, "nav_ide"),
    STUDIO_3D("3D GPU", Icons.Filled.ViewInAr, Icons.Outlined.ViewInAr, "nav_3d"),
    TERMINAL("Terminal", Icons.Filled.Terminal, Icons.Outlined.Terminal, "nav_terminal"),
    AUTOMATOR("Automator", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_automator")
}

@Composable
fun MainStudioScreen(
    currentUserId: String,
    onSignedOut: () -> Unit
) {
    val viewModel: StudioViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                StudioViewModel(
                    repository = WorkspaceRepository(db),
                    aiService = AiStudioService(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scriptsState by viewModel.scriptsState.collectAsStateWithLifecycle()
    val appsState by viewModel.appsState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(StudioTab.IDE) }
    var showAccountDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedTab != StudioTab.IDE && uiState.runningApp == null) {
        selectedTab = StudioTab.IDE
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    val user = Firebase.auth.currentUser
    val userInitial = (user?.displayName?.firstOrNull() ?: user?.email?.firstOrNull() ?: 'P').uppercaseChar().toString()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        val useNavRail = maxWidth >= 680.dp

        Scaffold(
            containerColor = ObsidianBg,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Surface(
                    color = ObsidianSurface,
                    border = BorderStroke(0.5.dp, ObsidianBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Surface(
                            color = ElectricCyan.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f)),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Py",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ElectricCyan
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PyForge Studio",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = when (selectedTab) {
                                    StudioTab.IDE -> "${uiState.editorFilename}  •  Python 3.12"
                                    StudioTab.STUDIO_3D -> "OpenGL ES 2.0 GPU 3D Viewport"
                                    StudioTab.TERMINAL -> "Interactive REPL & 33 Bundled Libraries"
                                    StudioTab.AUTOMATOR -> "AI Automated Python App Builder"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }

                        Surface(
                            color = ObsidianSurfaceElevated,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, PythonAmber.copy(alpha = 0.55f)),
                            onClick = { showAccountDialog = true },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("account_menu_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = userInitial,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = PythonAmber
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = ObsidianSurface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        StudioTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = ElectricCyan.copy(alpha = 0.14f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = ObsidianSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        StudioTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = ElectricCyan.copy(alpha = 0.14f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (selectedTab) {
                        StudioTab.IDE -> IdeScreen(
                            uiState = uiState,
                            scriptsState = scriptsState,
                            viewModel = viewModel,
                            onJumpTo3D = { selectedTab = StudioTab.STUDIO_3D }
                        )
                        StudioTab.STUDIO_3D -> Studio3DScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onEditCodeInIde = { selectedTab = StudioTab.IDE }
                        )
                        StudioTab.TERMINAL -> TerminalScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onInsertLibraryToIde = { snippet ->
                                viewModel.insertSnippet(snippet)
                                selectedTab = StudioTab.IDE
                                viewModel.showMessage("Inserted '$snippet' into IDE")
                            }
                        )
                        StudioTab.AUTOMATOR -> AutomatorScreen(
                            uiState = uiState,
                            appsState = appsState,
                            viewModel = viewModel,
                            onOpenCodeInIde = { selectedTab = StudioTab.IDE }
                        )
                    }
                }
            }
        }
    }

    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            containerColor = ObsidianSurface,
            title = { Text("Cloud Workspace Account", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text(
                        text = user?.displayName ?: "Signed-in Developer",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = user?.email ?: currentUserId,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAccountDialog = false
                        signOutUser(
                            credentialManager = credentialManager,
                            scope = scope,
                            onSignOutComplete = onSignedOut
                        )
                    },
                    modifier = Modifier.testTag("sign_out_confirm_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color(0xFFFF5370), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sign Out", color = Color(0xFFFF5370))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccountDialog = false }) { Text("Close") }
            }
        )
    }
}
