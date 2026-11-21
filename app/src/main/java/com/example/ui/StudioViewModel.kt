package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.firebase.AiStudioService
import com.example.firebase.AutomatedAppRecord
import com.example.firebase.PythonScript
import com.example.firebase.ScriptCategory
import com.example.firebase.TriggerMode
import com.example.firebase.WorkspaceRepository
import com.example.graphics3d.MeshBuilder
import com.example.graphics3d.Scene3D
import com.example.graphics3d.Vec3
import com.example.python.MetricCardData
import com.example.python.PyBool
import com.example.python.PyDict
import com.example.python.PyExecutionResult
import com.example.python.PyFloat
import com.example.python.PyInt
import com.example.python.PyInterpreter
import com.example.python.PyPlotSpec
import com.example.python.PyStr
import com.example.python.PyVal
import com.example.python.PythonLibraryCatalog
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "StudioViewModel"

sealed interface WorkspaceLoadState<out T> {
    data object Loading : WorkspaceLoadState<Nothing>
    data class Success<T>(val data: T) : WorkspaceLoadState<T>
    data class Error(val message: String) : WorkspaceLoadState<Nothing>
}

enum class TerminalLineKind { COMMAND, STDOUT, STDERR, RESULT, SYSTEM }

data class TerminalEntry(
    val id: Long = System.nanoTime(),
    val text: String,
    val kind: TerminalLineKind
)

data class StudioUiState(
    val activeScriptId: String? = null,
    val editorFilename: String = "orbital_gyro_3d.py",
    val editorFolder: String = "models_3d",
    val editorCategory: ScriptCategory = ScriptCategory.MODEL_3D,
    val editorCode: String = StarterTemplates.scripts.first().content,
    val isDirty: Boolean = false,
    val isRunningPython: Boolean = false,
    val lastExecution: PyExecutionResult? = null,
    val activeScene3D: Scene3D = MeshBuilder.defaultShowcaseScene(),
    val terminalEntries: List<TerminalEntry> = listOf(
        TerminalEntry(
            text = "PyForge Studio Python 3.12.4 • OpenGL ES 2.0 GPU Engine Ready\nType Python expressions or shell commands: ls, python <file>, pip list, pip install <pkg>, help",
            kind = TerminalLineKind.SYSTEM
        )
    ),
    val terminalHistory: List<String> = emptyList(),
    val isAiBusy: Boolean = false,
    val aiStatusMessage: String? = null,
    val snackbarMessage: String? = null,
    // Automated App Runner live state
    val runningApp: AutomatedAppRecord? = null,
    val appRunResult: PyExecutionResult? = null,
    val appRunScene3D: Scene3D? = null,
    val appRunMetrics: List<MetricCardData> = emptyList(),
    val appRunPlots: List<PyPlotSpec> = emptyList(),
    val appRunTick: Int = 1
)

class StudioViewModel(
    private val repository: WorkspaceRepository,
    private val aiService: AiStudioService,
    private val currentUserId: String
) : ViewModel() {

    private var seededScripts = false
    private var seededApps = false

    val scriptsState: StateFlow<WorkspaceLoadState<List<PythonScript>>> =
        repository.observeScripts(currentUserId)
            .map<List<PythonScript>, WorkspaceLoadState<List<PythonScript>>> { list ->
                if (list.isEmpty() && !seededScripts) {
                    seededScripts = true
                    seedStarterScripts()
                } else if (list.isNotEmpty() && _uiState.value.activeScriptId == null) {
                    openScriptInternal(list.first())
                }
                WorkspaceLoadState.Success(list)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing scripts", root)
                emit(WorkspaceLoadState.Error(root.message ?: "Failed to load Python scripts"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = WorkspaceLoadState.Loading
            )

    val appsState: StateFlow<WorkspaceLoadState<List<AutomatedAppRecord>>> =
        repository.observeAutomatedApps(currentUserId)
            .map<List<AutomatedAppRecord>, WorkspaceLoadState<List<AutomatedAppRecord>>> { list ->
                if (list.isEmpty() && !seededApps) {
                    seededApps = true
                    seedStarterApps()
                }
                WorkspaceLoadState.Success(list)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing automated apps", root)
                emit(WorkspaceLoadState.Error(root.message ?: "Failed to load automated apps"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = WorkspaceLoadState.Loading
            )

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private val replInterpreter = PyInterpreter(workspaceFilesProvider = { currentWorkspaceMap() })

    private fun currentWorkspaceMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val loaded = (scriptsState.value as? WorkspaceLoadState.Success)?.data.orEmpty()
        for (s in loaded) map[s.filename] = s.content
        map[_uiState.value.editorFilename] = _uiState.value.editorCode
        return map
    }

    private fun seedStarterScripts() {
        viewModelScope.launch {
            for (seed in StarterTemplates.scripts) {
                repository.createScript(
                    filename = seed.filename,
                    folder = seed.folder,
                    content = seed.content,
                    category = seed.category,
                    description = seed.description
                )
            }
        }
    }

    private fun seedStarterApps() {
        viewModelScope.launch {
            for (seed in StarterTemplates.automatedApps) {
                repository.createAutomatedApp(
                    name = seed.name,
                    summary = seed.summary,
                    iconKey = seed.iconKey,
                    accentHex = seed.accentHex,
                    pythonCode = seed.pythonCode,
                    inputsSpec = seed.inputsSpec,
                    triggerMode = seed.triggerMode,
                    intervalSeconds = seed.intervalSeconds,
                    has3dViewport = seed.has3dViewport
                )
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    private fun openScriptInternal(script: PythonScript) {
        _uiState.update {
            it.copy(
                activeScriptId = script.id,
                editorFilename = script.filename,
                editorFolder = script.folder,
                editorCategory = script.resolvedCategory(),
                editorCode = script.content,
                isDirty = false
            )
        }
    }

    fun openScript(script: PythonScript) {
        openScriptInternal(script)
    }

    fun updateEditorCode(newCode: String) {
        _uiState.update { it.copy(editorCode = newCode, isDirty = true) }
    }

    fun insertSnippet(snippet: String) {
        _uiState.update {
            val prefix = if (it.editorCode.isEmpty() || it.editorCode.endsWith("\n")) "" else "\n"
            it.copy(editorCode = it.editorCode + prefix + snippet, isDirty = true)
        }
    }

    fun saveCurrentScript() {
        val s = _uiState.value
        viewModelScope.launch {
            val result = if (s.activeScriptId != null) {
                repository.updateScript(
                    scriptId = s.activeScriptId,
                    filename = s.editorFilename,
                    folder = s.editorFolder,
                    content = s.editorCode,
                    category = s.editorCategory
                )
            } else {
                repository.createScript(
                    filename = s.editorFilename,
                    folder = s.editorFolder,
                    content = s.editorCode,
                    category = s.editorCategory,
                    description = "Python script"
                ).map { newId ->
                    _uiState.update { it.copy(activeScriptId = newId) }
                }
            }
            if (result.isSuccess) {
                _uiState.update { it.copy(isDirty = false, snackbarMessage = "Saved ${s.editorFilename} to cloud") }
            } else {
                _uiState.update { it.copy(snackbarMessage = "Save failed: ${result.exceptionOrNull()?.localizedMessage}") }
            }
        }
    }

    fun createNewScript(filename: String, category: ScriptCategory, initialCode: String = "") {
        val cleanName = filename.trim().let { if (it.endsWith(".py")) it else "$it.py" }
        val starter = initialCode.ifBlank {
            when (category) {
                ScriptCategory.MODEL_3D -> "import py3d\n\nscene = py3d.Scene(\"${cleanName.removeSuffix(".py")}\")\nscene.add_cube(size=1.2, color=\"#00E5FF\")\nscene.render()\n"
                ScriptCategory.DATA_SCIENCE -> "import numpy as np\nimport matplotlib.pyplot as plt\n\nx = np.linspace(0, 6.28, 30)\ny = np.sin(x)\nplt.title(\"Sine Wave\")\nplt.plot(x, y)\nplt.show()\n"
                else -> "# $cleanName\nprint(\"Hello from PyForge Studio!\")\n"
            }
        }
        viewModelScope.launch {
            val res = repository.createScript(
                filename = cleanName,
                folder = if (category == ScriptCategory.MODEL_3D) "models_3d" else "workspace",
                content = starter,
                category = category,
                description = "Created in PyForge Studio"
            )
            res.onSuccess { id ->
                _uiState.update {
                    it.copy(
                        activeScriptId = id,
                        editorFilename = cleanName,
                        editorCategory = category,
                        editorCode = starter,
                        isDirty = false,
                        snackbarMessage = "Created $cleanName"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(snackbarMessage = "Create failed: ${err.localizedMessage}") }
            }
        }
    }

    fun importExternalPythonFile(filename: String, content: String) {
        val cleanName = filename.ifBlank { "imported.py" }.let { if (it.endsWith(".py")) it else "$it.py" }
        val is3d = content.contains("py3d") || content.contains("trimesh") || content.contains("cadquery")
        createNewScript(
            filename = cleanName,
            category = if (is3d) ScriptCategory.MODEL_3D else ScriptCategory.PYTHON,
            initialCode = content
        )
    }

    fun deleteScript(script: PythonScript) {
        viewModelScope.launch {
            val res = repository.deleteScript(script.id)
            if (res.isSuccess) {
                if (_uiState.value.activeScriptId == script.id) {
                    _uiState.update { it.copy(activeScriptId = null, snackbarMessage = "Deleted ${script.filename}") }
                } else {
                    _uiState.update { it.copy(snackbarMessage = "Deleted ${script.filename}") }
                }
            }
        }
    }

    fun runCurrentScript() {
        val code = _uiState.value.editorCode
        val filename = _uiState.value.editorFilename
        if (_uiState.value.isRunningPython) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRunningPython = true) }
            val result = withContext(Dispatchers.Default) {
                val runner = PyInterpreter(workspaceFilesProvider = { currentWorkspaceMap() })
                runner.execute(code)
            }
            _uiState.update { state ->
                val nextEntries = state.terminalEntries.toMutableList()
                nextEntries.add(TerminalEntry(text = "$ python $filename", kind = TerminalLineKind.COMMAND))
                if (result.stdout.isNotBlank()) {
                    nextEntries.add(TerminalEntry(text = result.stdout.trimEnd(), kind = TerminalLineKind.STDOUT))
                }
                if (result.stderr.isNotBlank()) {
                    nextEntries.add(TerminalEntry(text = result.stderr.trimEnd(), kind = TerminalLineKind.STDERR))
                }
                state.copy(
                    isRunningPython = false,
                    lastExecution = result,
                    activeScene3D = result.scene3D ?: state.activeScene3D,
                    terminalEntries = nextEntries.takeLast(120),
                    snackbarMessage = if (result.scene3D != null) {
                        "3D GPU scene updated (${result.scene3D.totalTriangles} triangles)"
                    } else if (!result.success) {
                        "Execution error — inspect console or tap AI Fix"
                    } else null
                )
            }
        }
    }

    // --- Interactive 3D Studio Controls ---

    fun add3DPrimitive(kind: String, colorHex: String = "#00E5FF") {
        val current = _uiState.value.activeScene3D
        val idx = current.objects.size
        val angle = idx * 1.1f
        val pos = if (idx == 0) Vec3(0f, 0.2f, 0f)
        else Vec3(kotlin.math.cos(angle) * 1.6f, 0.2f, kotlin.math.sin(angle) * 1.6f)

        val mesh = when (kind) {
            "cube" -> MeshBuilder.createCube(name = "Cube_${idx + 1}", pos = pos, colorHex = colorHex)
            "sphere" -> MeshBuilder.createSphere(name = "Sphere_${idx + 1}", radius = 0.75f, pos = pos, colorHex = colorHex)
            "torus" -> MeshBuilder.createTorus(name = "Torus_${idx + 1}", majorRadius = 1.0f, minorRadius = 0.28f, pos = pos, colorHex = colorHex)
            "cylinder" -> MeshBuilder.createCylinder(name = "Cylinder_${idx + 1}", pos = pos, colorHex = colorHex)
            "cone" -> MeshBuilder.createCone(name = "Cone_${idx + 1}", pos = pos, colorHex = colorHex)
            "pyramid" -> MeshBuilder.createPyramid(name = "Pyramid_${idx + 1}", pos = pos, colorHex = colorHex)
            "icosahedron" -> MeshBuilder.createIcosahedron(name = "Crystal_${idx + 1}", radius = 0.85f, pos = pos, colorHex = colorHex)
            "helix" -> MeshBuilder.createHelix(name = "Helix_${idx + 1}", pos = pos, colorHex = colorHex)
            "gear" -> MeshBuilder.createGear(name = "Gear_${idx + 1}", pos = pos, colorHex = colorHex)
            else -> MeshBuilder.createCube(name = "Object_${idx + 1}", pos = pos, colorHex = colorHex)
        }

        _uiState.update {
            it.copy(
                activeScene3D = current.copy(
                    objects = current.objects + mesh,
                    revision = System.nanoTime()
                ),
                snackbarMessage = "Added ${mesh.name} (${mesh.triangleCount} tris)"
            )
        }
    }

    fun clear3DScene() {
        _uiState.update {
            it.copy(
                activeScene3D = Scene3D(title = "Empty Studio", objects = emptyList(), revision = System.nanoTime()),
                snackbarMessage = "Cleared 3D scene"
            )
        }
    }

    fun load3DPreset(presetKey: String) {
        val code = when (presetKey) {
            "gyro" -> StarterTemplates.scripts[0].content
            "wave" -> StarterTemplates.scripts[1].content
            "dna" -> StarterTemplates.scripts[3].content
            "solar" -> """
import math, py3d
scene = py3d.Scene("Solar System")
scene.add_sphere(radius=0.85, pos=(0, 0.2, 0), color="#FFB300")
planets = [(1.5, 0.20, "#94A3B8"), (2.1, 0.28, "#00E5FF"), (2.8, 0.24, "#FF5370"), (3.6, 0.42, "#C792EA")]
for i, (dist, r, col) in enumerate(planets):
    ang = i * 1.45
    scene.add_torus(major=dist, minor=0.025, pos=(0, 0.2, 0), color="#283554")
    scene.add_sphere(radius=r, pos=(math.cos(ang)*dist, 0.2, math.sin(ang)*dist), color=col)
scene.render()
            """.trimIndent()
            else -> StarterTemplates.scripts[0].content
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                PyInterpreter(workspaceFilesProvider = { currentWorkspaceMap() }).execute(code)
            }
            if (result.scene3D != null) {
                _uiState.update {
                    it.copy(
                        activeScene3D = result.scene3D,
                        editorCode = code,
                        editorCategory = ScriptCategory.MODEL_3D,
                        snackbarMessage = "Loaded ${result.scene3D.title}"
                    )
                }
            }
        }
    }

    // --- Interactive Built-in Terminal & REPL ---

    fun runTerminalCommand(rawInput: String) {
        val cmd = rawInput.trim()
        if (cmd.isEmpty()) return

        _uiState.update {
            it.copy(
                terminalEntries = (it.terminalEntries + TerminalEntry(text = ">>> $cmd", kind = TerminalLineKind.COMMAND)).takeLast(120),
                terminalHistory = (it.terminalHistory + cmd).takeLast(50)
            )
        }

        val lower = cmd.lowercase()
        when {
            lower == "clear" || lower == "cls" -> {
                _uiState.update { it.copy(terminalEntries = emptyList()) }
                return
            }
            lower == "reset" -> {
                replInterpreter.resetSession()
                appendTerminal("Python REPL session variables reset.", TerminalLineKind.SYSTEM)
                return
            }
            lower == "help" -> {
                appendTerminal(
                    """
                    PyForge Terminal Commands:
                      ls                     List cloud workspace .py files
                      cat <file.py>          Print file contents
                      python <file.py>       Execute a workspace file
                      pip list               List all 30+ bundled Python libraries
                      pip show <pkg>         Show package details & import snippet
                      pip install <pkg>      Verify/activate package in workspace
                      libs                   Show libraries grouped by category
                      export-obj             Preview 3D scene Wavefront .OBJ header
                      clear / reset          Clear terminal or reset REPL state
                    Or type any Python 3 statement/expression directly!
                    """.trimIndent(),
                    TerminalLineKind.SYSTEM
                )
                return
            }
            lower == "ls" -> {
                val files = currentWorkspaceMap().keys.sorted()
                appendTerminal(
                    if (files.isEmpty()) "(workspace empty)" else files.joinToString("   "),
                    TerminalLineKind.STDOUT
                )
                return
            }
            lower.startsWith("cat ") -> {
                val name = cmd.substring(4).trim()
                val content = currentWorkspaceMap()[name]
                if (content != null) appendTerminal(content, TerminalLineKind.STDOUT)
                else appendTerminal("cat: $name: No such file in workspace", TerminalLineKind.STDERR)
                return
            }
            lower.startsWith("python ") -> {
                val name = cmd.substring(7).trim()
                val content = currentWorkspaceMap()[name]
                if (content == null) {
                    appendTerminal("python: can't open file '$name': No such file", TerminalLineKind.STDERR)
                    return
                }
                viewModelScope.launch {
                    val res = withContext(Dispatchers.Default) {
                        replInterpreter.execute(content, isRepl = false)
                    }
                    handleReplOutput(res)
                }
                return
            }
            lower == "pip list" || lower == "libs" -> {
                val listStr = PythonLibraryCatalog.libraries.joinToString("\n") {
                    "${it.name.padEnd(18)} ${it.version.padEnd(8)} [${it.category}]"
                }
                appendTerminal(listStr, TerminalLineKind.STDOUT)
                return
            }
            lower.startsWith("pip show ") -> {
                val pkg = cmd.substring(9).trim().lowercase()
                val info = PythonLibraryCatalog.libraries.firstOrNull { it.name.equals(pkg, true) }
                if (info != null) {
                    appendTerminal(
                        "Name: ${info.name}\nVersion: ${info.version}\nCategory: ${info.category}\nSummary: ${info.description}\nUsage: ${info.sampleImport}",
                        TerminalLineKind.STDOUT
                    )
                } else {
                    appendTerminal("WARNING: Package '$pkg' not found in bundled catalog.", TerminalLineKind.STDERR)
                }
                return
            }
            lower.startsWith("pip install ") -> {
                val pkg = cmd.substring(12).trim().lowercase()
                val info = PythonLibraryCatalog.libraries.firstOrNull { it.name.equals(pkg, true) }
                if (info != null) {
                    PythonLibraryCatalog.installedNames.add(info.name)
                    appendTerminal(
                        "Requirement already satisfied: ${info.name}==${info.version} (bundled natively in PyForge Studio)\nImport with: ${info.sampleImport}",
                        TerminalLineKind.RESULT
                    )
                } else {
                    appendTerminal(
                        "Note: '$pkg' is not a native binary package. PyForge ships 30+ bundled stdlib/scientific/3D libraries (run 'pip list') and supports importing any pure-Python .py module from your workspace.",
                        TerminalLineKind.SYSTEM
                    )
                }
                return
            }
            lower == "export-obj" -> {
                val obj = _uiState.value.activeScene3D.exportToObj()
                val preview = obj.lines().take(16).joinToString("\n") + "\n... (${obj.lines().size} total OBJ lines)"
                appendTerminal(preview, TerminalLineKind.STDOUT)
                return
            }
        }

        // Execute as live Python REPL statement / expression
        viewModelScope.launch {
            val res = withContext(Dispatchers.Default) {
                replInterpreter.execute(cmd, isRepl = true)
            }
            handleReplOutput(res)
        }
    }

    private fun handleReplOutput(res: PyExecutionResult) {
        if (res.stdout.isNotBlank()) appendTerminal(res.stdout.trimEnd(), TerminalLineKind.STDOUT)
        if (!res.lastExprValue.isNullOrBlank()) appendTerminal(res.lastExprValue, TerminalLineKind.RESULT)
        if (res.stderr.isNotBlank()) appendTerminal(res.stderr.trimEnd(), TerminalLineKind.STDERR)
        if (res.scene3D != null) {
            _uiState.update { it.copy(activeScene3D = res.scene3D) }
            appendTerminal("[GPU 3D Viewport updated: ${res.scene3D.totalTriangles} triangles]", TerminalLineKind.SYSTEM)
        }
        if (res.plots.isNotEmpty()) {
            _uiState.update { it.copy(lastExecution = res) }
            appendTerminal("[Generated ${res.plots.size} plot(s) — view in IDE Output]", TerminalLineKind.SYSTEM)
        }
    }

    private fun appendTerminal(text: String, kind: TerminalLineKind) {
        _uiState.update {
            it.copy(terminalEntries = (it.terminalEntries + TerminalEntry(text = text, kind = kind)).takeLast(120))
        }
    }

    // --- AI Code Assistant & 3D Generator (Firebase AI Logic) ---

    fun requestAiCodeAssist(prompt: String, for3D: Boolean = false) {
        if (prompt.isBlank() || _uiState.value.isAiBusy) return
        val currentCode = _uiState.value.editorCode
        val lastErr = _uiState.value.lastExecution?.takeIf { !it.success }?.stderr
        viewModelScope.launch {
            _uiState.update { it.copy(isAiBusy = true, aiStatusMessage = if (for3D) "Generating 3D Python script..." else "Generating Python code...") }
            val res = aiService.generatePythonCode(prompt, currentCode, lastErr, for3D)
            res.onSuccess { generatedCode ->
                _uiState.update {
                    it.copy(
                        isAiBusy = false,
                        aiStatusMessage = null,
                        editorCode = generatedCode,
                        editorCategory = if (for3D) ScriptCategory.MODEL_3D else it.editorCategory,
                        isDirty = true,
                        snackbarMessage = if (for3D) "3D script generated — executing on GPU!" else "Code updated by Gemini"
                    )
                }
                if (for3D) runCurrentScript()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isAiBusy = false,
                        aiStatusMessage = null,
                        snackbarMessage = err.localizedMessage ?: "AI generation failed"
                    )
                }
            }
        }
    }

    // --- Automated App Builder & Live Runner ---

    fun generateAutomatedAppWithAi(prompt: String, onCreated: (AutomatedAppRecord) -> Unit = {}) {
        if (prompt.isBlank() || _uiState.value.isAiBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAiBusy = true, aiStatusMessage = "Designing automated Python app...") }
            val res = aiService.generateAutomatedApp(prompt)
            res.onSuccess { gen ->
                val saveRes = repository.createAutomatedApp(
                    name = gen.name,
                    summary = gen.summary,
                    iconKey = gen.iconKey,
                    accentHex = gen.accentHex,
                    pythonCode = gen.pythonCode,
                    inputsSpec = gen.inputsSpec,
                    triggerMode = gen.triggerMode,
                    intervalSeconds = gen.intervalSeconds,
                    has3dViewport = gen.has3dViewport
                )
                saveRes.onSuccess { id ->
                    val record = AutomatedAppRecord(
                        id = id,
                        userId = currentUserId,
                        name = gen.name,
                        summary = gen.summary,
                        iconKey = gen.iconKey,
                        accentHex = gen.accentHex,
                        pythonCode = gen.pythonCode,
                        inputsSpec = gen.inputsSpec,
                        triggerMode = gen.triggerMode.name,
                        intervalSeconds = gen.intervalSeconds.toLong(),
                        has3dViewport = gen.has3dViewport
                    )
                    _uiState.update {
                        it.copy(
                            isAiBusy = false,
                            aiStatusMessage = null,
                            snackbarMessage = "Created automated app '${gen.name}'"
                        )
                    }
                    onCreated(record)
                }.onFailure { saveErr ->
                    _uiState.update {
                        it.copy(
                            isAiBusy = false,
                            aiStatusMessage = null,
                            snackbarMessage = "Generated app but failed to save: ${saveErr.localizedMessage}"
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isAiBusy = false,
                        aiStatusMessage = null,
                        snackbarMessage = err.localizedMessage ?: "App generation failed"
                    )
                }
            }
        }
    }

    fun deleteAutomatedApp(app: AutomatedAppRecord) {
        viewModelScope.launch {
            repository.deleteAutomatedApp(app.id)
            if (_uiState.value.runningApp?.id == app.id) {
                _uiState.update { it.copy(runningApp = null) }
            }
            _uiState.update { it.copy(snackbarMessage = "Deleted '${app.name}'") }
        }
    }

    fun launchAutomatedApp(app: AutomatedAppRecord) {
        _uiState.update {
            it.copy(
                runningApp = app,
                appRunResult = null,
                appRunScene3D = null,
                appRunMetrics = emptyList(),
                appRunPlots = emptyList(),
                appRunTick = 1
            )
        }
    }

    fun closeAutomatedApp() {
        _uiState.update { it.copy(runningApp = null) }
    }

    fun openAutomatedAppInIde(app: AutomatedAppRecord) {
        _uiState.update {
            it.copy(
                activeScriptId = null,
                editorFilename = app.name.lowercase().replace(Regex("[^a-z0-9]+"), "_") + "_app.py",
                editorFolder = "automation",
                editorCategory = ScriptCategory.AUTOMATION,
                editorCode = app.pythonCode,
                isDirty = true,
                runningApp = null,
                snackbarMessage = "Loaded '${app.name}' code into IDE"
            )
        }
    }

    fun executeAutomatedApp(app: AutomatedAppRecord, inputValues: Map<String, String>, tick: Int) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                val globals = mutableMapOf<String, PyVal>()
                val dictEntries = LinkedHashMap<PyVal, PyVal>()
                for ((k, raw) in inputValues) {
                    val pyVal: PyVal = when {
                        raw.equals("true", true) -> PyBool(true)
                        raw.equals("false", true) -> PyBool(false)
                        raw.toLongOrNull() != null -> PyInt(raw.toLong())
                        raw.toDoubleOrNull() != null -> PyFloat(raw.toDouble())
                        else -> PyStr(raw)
                    }
                    globals[k] = pyVal
                    dictEntries[PyStr(k)] = pyVal
                }
                globals["APP_INPUTS"] = PyDict(dictEntries)
                globals["TICK"] = PyInt(tick.toLong())

                val runner = PyInterpreter(workspaceFilesProvider = { currentWorkspaceMap() })
                runner.execute(app.pythonCode, injectedGlobals = globals)
            }
            _uiState.update {
                it.copy(
                    appRunResult = result,
                    appRunScene3D = result.scene3D ?: it.appRunScene3D,
                    appRunMetrics = result.metrics,
                    appRunPlots = result.plots,
                    appRunTick = tick
                )
            }
        }
    }
}
