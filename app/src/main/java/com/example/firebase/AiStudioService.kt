package com.example.firebase

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiStudioService"
private const val MODEL_NAME = "gemini-3.8-flash"

data class GeneratedAutomatedApp(
    val name: String,
    val summary: String,
    val iconKey: String,
    val accentHex: String,
    val triggerMode: TriggerMode,
    val intervalSeconds: Int,
    val has3dViewport: Boolean,
    val inputsSpec: String,
    val pythonCode: String
)

class AiStudioService {

    private val codeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME
        )
    }

    private val automatedAppSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "name" to Schema.string(description = "Short app name (2-4 words, max 40 chars)"),
                "summary" to Schema.string(description = "Concise one-sentence summary of what the automated app does"),
                "iconKey" to Schema.enumeration(
                    values = listOf("bolt", "cube", "chart", "timer", "calculator", "rocket", "science"),
                    description = "Icon identifier"
                ),
                "accentHex" to Schema.string(description = "Hex color string like #00E5FF or #FFB300 or #22D3A0"),
                "triggerMode" to Schema.enumeration(
                    values = listOf("ON_CHANGE", "INTERVAL", "MANUAL"),
                    description = "ON_CHANGE for reactive apps, INTERVAL for auto-ticking apps, MANUAL for button-triggered apps"
                ),
                "intervalSeconds" to Schema.integer(description = "Seconds between auto-ticks if INTERVAL (2-60), otherwise 0"),
                "has3dViewport" to Schema.boolean(description = "True if the app constructs a 3D model using import py3d"),
                "inputsSpec" to Schema.string(
                    description = "Newline-separated interactive control definitions using pipe delimiters: " +
                        "slider|key|Label|default|min|max OR number|key|Label|default OR text|key|Label|default OR toggle|key|Label|true OR select|key|Label|default|opt1,opt2"
                ),
                "pythonCode" to Schema.string(
                    description = "Complete runnable Python 3 script. All input keys are pre-injected as Python variables (floats/strings/bools) AND available in dict APP_INPUTS. " +
                        "Use print('METRIC|Label|Value|Unit') to emit rich KPI metric cards. " +
                        "Use standard print() for live automation logs. " +
                        "Optionally use import matplotlib.pyplot as plt (plt.plot/bar/scatter, plt.title, plt.show()) for charts, " +
                        "and import py3d (scene = py3d.Scene(); scene.add_cube/add_sphere/add_torus/add_cylinder/add_cone/add_helix/add_surface(...)) when has3dViewport is true."
                )
            )
        )
    }

    private val automatedAppModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = automatedAppSchema
            }
        )
    }

    suspend fun generateAutomatedApp(prompt: String): Result<GeneratedAutomatedApp> {
        return try {
            val fullPrompt = """
                You are an expert Python & interactive app architect inside PyForge Studio.
                Design a complete, working, fully automated micro-application for this user request:
                "$prompt"

                Runtime environment rules:
                - Supported imports: math, random, statistics, json, datetime, time, collections, itertools, numpy as np, pandas as pd, matplotlib.pyplot as plt, scipy, sympy, py3d, trimesh.
                - Every input key defined in inputsSpec is automatically injected into Python globals (numbers as float/int, toggles as bool, text/select as str) and also in dict `APP_INPUTS`.
                - Also `TICK` (integer counter starting at 1) is injected for INTERVAL apps so animations/simulations evolve every tick!
                - Emit 2 to 4 KPI metric cards using `print("METRIC|Title|FormattedValue|UnitOrSubtitle")`.
                - Emit helpful calculation details or status output using normal `print(...)`.
                - If data visualization helps, use `import matplotlib.pyplot as plt` with `plt.plot(...)` or `plt.bar(...)`, `plt.title(...)`, and `plt.show()`.
                - If 3D visualization helps (or user asks for 3D), set `has3dViewport: true` and use `import py3d`:
                  `scene = py3d.Scene()`
                  `scene.add_cube(size=1.0, pos=(x,y,z), rot=(rx,ry,rz), color="#00E5FF")`
                  `scene.add_sphere(radius=0.8, segments=20, pos=(x,y,z), color="#FFB300")`
                  `scene.add_torus(major=1.2, minor=0.3, pos=(x,y,z), rot=(rx,ry,rz), color="#22D3A0")`
                  `scene.add_cylinder(radius=0.4, height=1.5, pos=(x,y,z), color="#C792EA")`
                  `scene.add_cone(radius=0.6, height=1.4, pos=(x,y,z), color="#FF8A65")`
                  `scene.add_helix(radius=1.0, height=3.0, turns=4, tube=0.12, color="#00E5FF")`
                  `scene.add_surface(fn, x_range=(-2,2), z_range=(-2,2), steps=18, color="#00E5FF")`
                - Keep Python code clean, self-contained, fast (no infinite loops), and guaranteed to run without syntax errors.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                automatedAppModel.generateContent(fullPrompt)
            }
            val jsonString = response.text
                ?: throw IllegalStateException("AI returned an empty response.")
            val json = JSONObject(jsonString)
            Result.success(
                GeneratedAutomatedApp(
                    name = json.optString("name", "Automated App").take(80),
                    summary = json.optString("summary", "Generated automated Python app").take(300),
                    iconKey = json.optString("iconKey", "bolt"),
                    accentHex = json.optString("accentHex", "#00E5FF"),
                    triggerMode = TriggerMode.fromRaw(json.optString("triggerMode", "ON_CHANGE")),
                    intervalSeconds = json.optInt("intervalSeconds", 0).coerceIn(0, 3600),
                    has3dViewport = json.optBoolean("has3dViewport", false),
                    inputsSpec = json.optString("inputsSpec", ""),
                    pythonCode = json.optString("pythonCode", "print('Ready')")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Automated app generation timed out", e)
            Result.failure(Exception("Generation timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Automated app generation failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Failed to generate automated app."))
        }
    }

    suspend fun generatePythonCode(
        userRequest: String,
        currentCode: String,
        lastError: String? = null,
        for3D: Boolean = false
    ): Result<String> {
        return try {
            val systemContext = if (for3D) {
                """
                Generate a complete, runnable Python 3 script for PyForge Studio's GPU-accelerated 3D OpenGL ES engine using `import py3d` and `import math`.
                Available py3d Scene methods:
                - scene = py3d.Scene()
                - scene.add_cube(size=1.0, pos=(x,y,z), rot=(rx,ry,rz), scale=(sx,sy,sz), color="#00E5FF")
                - scene.add_sphere(radius=0.8, segments=20, pos=(x,y,z), color="#FFB300")
                - scene.add_torus(major=1.2, minor=0.35, segments=28, pos=(x,y,z), rot=(rx,ry,rz), color="#22D3A0")
                - scene.add_cylinder(radius=0.4, height=1.6, segments=20, pos=(x,y,z), rot=(rx,ry,rz), color="#C792EA")
                - scene.add_cone(radius=0.6, height=1.5, segments=20, pos=(x,y,z), rot=(rx,ry,rz), color="#FF8A65")
                - scene.add_pyramid(size=1.2, height=1.5, pos=(x,y,z), color="#60A5FA")
                - scene.add_icosahedron(radius=1.0, pos=(x,y,z), color="#00E5FF")
                - scene.add_helix(radius=1.0, height=3.0, turns=4, tube=0.1, color="#00E5FF")
                - scene.add_surface(lambda x, z: math.sin(x)*math.cos(z), x_range=(-2.5,2.5), z_range=(-2.5,2.5), steps=20, color="#00E5FF")
                - scene.add_mesh(vertices, faces, color="#00E5FF")
                Return ONLY raw executable Python code without markdown code fences or prose explanations.
                """.trimIndent()
            } else {
                """
                Generate clean, runnable Python 3 code for PyForge Studio.
                Available libraries: math, cmath, random, statistics, json, re, datetime, time, collections, itertools, functools, string, csv, hashlib, base64, numpy as np, pandas as pd, matplotlib.pyplot as plt, scipy, sympy, py3d, trimesh, requests, sqlite3.
                Return ONLY raw executable Python code without markdown backticks or surrounding explanation.
                """.trimIndent()
            }

            val prompt = buildString {
                appendLine(systemContext)
                appendLine()
                appendLine("User request: $userRequest")
                if (currentCode.isNotBlank()) {
                    appendLine()
                    appendLine("Current editor code:")
                    appendLine(currentCode.take(4000))
                }
                if (!lastError.isNullOrBlank()) {
                    appendLine()
                    appendLine("Last execution error to fix:")
                    appendLine(lastError.take(1500))
                }
            }

            val response = withTimeout(60_000L) {
                codeModel.generateContent(prompt)
            }
            val raw = response.text?.trim()
                ?: throw IllegalStateException("Model returned empty code.")
            // Strip optional markdown fences if model included them
            val cleaned = raw
                .removePrefix("```python")
                .removePrefix("```py")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            Result.success(cleaned)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Code generation timed out", e)
            Result.failure(Exception("Code generation timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Code generation failed", e)
            Result.failure(Exception(e.localizedMessage ?: "AI code generation failed."))
        }
    }
}
