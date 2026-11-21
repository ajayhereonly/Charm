package com.example.firebase

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ScriptCategory(val label: String) {
    PYTHON("Python"),
    MODEL_3D("3D Model"),
    DATA_SCIENCE("Data Sci"),
    AUTOMATION("Automation");

    companion object {
        fun fromRaw(raw: String): ScriptCategory =
            entries.firstOrNull { it.name == raw } ?: PYTHON
    }
}

enum class TriggerMode(val label: String) {
    MANUAL("Manual Run"),
    ON_CHANGE("Live Auto-Run"),
    INTERVAL("Scheduled Timer");

    companion object {
        fun fromRaw(raw: String): TriggerMode =
            entries.firstOrNull { it.name == raw } ?: ON_CHANGE
    }
}

@Keep
@IgnoreExtraProperties
data class PythonScript(
    @DocumentId val id: String = "",
    val userId: String = "",
    val filename: String = "main.py",
    val folder: String = "workspace",
    val content: String = "",
    val category: String = ScriptCategory.PYTHON.name,
    val description: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedCategory(): ScriptCategory = ScriptCategory.fromRaw(category)
}

@Keep
@IgnoreExtraProperties
data class AutomatedAppRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val summary: String = "",
    val iconKey: String = "bolt",
    val accentHex: String = "#00E5FF",
    val pythonCode: String = "",
    val inputsSpec: String = "",
    val triggerMode: String = TriggerMode.ON_CHANGE.name,
    val intervalSeconds: Long = 0L,
    val has3dViewport: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedTriggerMode(): TriggerMode = TriggerMode.fromRaw(triggerMode)
}

data class AutomatedInputControl(
    val type: String, // "slider", "number", "text", "toggle", "select"
    val key: String,
    val label: String,
    val defaultValue: String,
    val minValue: Float = 0f,
    val maxValue: Float = 100f,
    val options: List<String> = emptyList()
)

object InputsSpecCodec {
    /**
     * Format per line:
     * slider|key|Label|default|min|max
     * number|key|Label|default
     * text|key|Label|default
     * toggle|key|Label|true
     * select|key|Label|default|opt1,opt2,opt3
     */
    fun parse(spec: String): List<AutomatedInputControl> {
        if (spec.isBlank()) return emptyList()
        return spec.lines().mapNotNull { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@mapNotNull null
            val parts = line.split("|")
            if (parts.size < 4) return@mapNotNull null
            val type = parts[0].trim().lowercase()
            val key = parts[1].trim().replace(Regex("[^a-zA-Z0-9_]"), "_")
            val label = parts[2].trim()
            val def = parts[3].trim()
            if (key.isEmpty() || label.isEmpty()) return@mapNotNull null
            when (type) {
                "slider" -> AutomatedInputControl(
                    type = "slider",
                    key = key,
                    label = label,
                    defaultValue = def,
                    minValue = parts.getOrNull(4)?.toFloatOrNull() ?: 0f,
                    maxValue = parts.getOrNull(5)?.toFloatOrNull() ?: 100f
                )
                "select" -> AutomatedInputControl(
                    type = "select",
                    key = key,
                    label = label,
                    defaultValue = def,
                    options = parts.getOrNull(4)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                        ?: listOf(def)
                )
                "toggle" -> AutomatedInputControl(
                    type = "toggle",
                    key = key,
                    label = label,
                    defaultValue = if (def.equals("true", ignoreCase = true)) "true" else "false"
                )
                "number" -> AutomatedInputControl(
                    type = "number",
                    key = key,
                    label = label,
                    defaultValue = def
                )
                else -> AutomatedInputControl(
                    type = "text",
                    key = key,
                    label = label,
                    defaultValue = def
                )
            }
        }
    }

    fun encode(controls: List<AutomatedInputControl>): String {
        return controls.joinToString("\n") { c ->
            when (c.type) {
                "slider" -> "slider|${c.key}|${c.label}|${c.defaultValue}|${c.minValue}|${c.maxValue}"
                "select" -> "select|${c.key}|${c.label}|${c.defaultValue}|${c.options.joinToString(",")}"
                else -> "${c.type}|${c.key}|${c.label}|${c.defaultValue}"
            }
        }
    }
}
