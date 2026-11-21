package com.example.python

import com.example.graphics3d.Scene3D
import kotlin.math.abs

sealed interface PyVal {
    fun isTruthy(): Boolean = true
    fun toDisplayString(): String = toString()
    fun toRepr(): String = toDisplayString()
    fun typeName(): String
}

data object PyNone : PyVal {
    override fun isTruthy() = false
    override fun toDisplayString() = "None"
    override fun typeName() = "NoneType"
}

data class PyBool(val value: Boolean) : PyVal {
    override fun isTruthy() = value
    override fun toDisplayString() = if (value) "True" else "False"
    override fun typeName() = "bool"
}

data class PyInt(val value: Long) : PyVal {
    override fun isTruthy() = value != 0L
    override fun toDisplayString() = value.toString()
    override fun typeName() = "int"
}

data class PyFloat(val value: Double) : PyVal {
    override fun isTruthy() = value != 0.0 && !value.isNaN()
    override fun toDisplayString(): String {
        if (value.isNaN()) return "nan"
        if (value.isInfinite()) return if (value > 0) "inf" else "-inf"
        val asLong = value.toLong()
        return if (abs(value - asLong.toDouble()) < 1e-12 && abs(value) < 1e15) {
            "$asLong.0"
        } else {
            value.toString()
        }
    }
    override fun typeName() = "float"
}

data class PyStr(val value: String) : PyVal {
    override fun isTruthy() = value.isNotEmpty()
    override fun toDisplayString() = value
    override fun toRepr() = "'${value.replace("\\", "\\\\").replace("'", "\\'")}'"
    override fun typeName() = "str"
}

class PyList(val items: MutableList<PyVal> = mutableListOf()) : PyVal {
    override fun isTruthy() = items.isNotEmpty()
    override fun toDisplayString() = "[${items.joinToString(", ") { it.toRepr() }}]"
    override fun typeName() = "list"
    override fun equals(other: Any?): Boolean = other is PyList && items == other.items
    override fun hashCode(): Int = items.hashCode()
}

data class PyTuple(val items: List<PyVal>) : PyVal {
    override fun isTruthy() = items.isNotEmpty()
    override fun toDisplayString() =
        if (items.size == 1) "(${items[0].toRepr()},)"
        else "(${items.joinToString(", ") { it.toRepr() }})"
    override fun typeName() = "tuple"
}

class PyDict(val entries: LinkedHashMap<PyVal, PyVal> = linkedMapOf()) : PyVal {
    override fun isTruthy() = entries.isNotEmpty()
    override fun toDisplayString() =
        "{${entries.entries.joinToString(", ") { "${it.key.toRepr()}: ${it.value.toRepr()}" }}}"
    override fun typeName() = "dict"
    override fun equals(other: Any?): Boolean = other is PyDict && entries == other.entries
    override fun hashCode(): Int = entries.hashCode()
}

class PySet(val items: LinkedHashSet<PyVal> = linkedSetOf()) : PyVal {
    override fun isTruthy() = items.isNotEmpty()
    override fun toDisplayString() =
        if (items.isEmpty()) "set()" else "{${items.joinToString(", ") { it.toRepr() }}}"
    override fun typeName() = "set"
    override fun equals(other: Any?): Boolean = other is PySet && items == other.items
    override fun hashCode(): Int = items.hashCode()
}

data class PyRange(val start: Long, val stop: Long, val step: Long = 1L) : PyVal {
    fun toList(): List<PyVal> {
        val out = mutableListOf<PyVal>()
        if (step > 0) {
            var v = start
            while (v < stop) { out.add(PyInt(v)); v += step }
        } else if (step < 0) {
            var v = start
            while (v > stop) { out.add(PyInt(v)); v += step }
        }
        return out
    }
    override fun isTruthy() = (step > 0 && start < stop) || (step < 0 && start > stop)
    override fun toDisplayString() = if (step == 1L) "range($start, $stop)" else "range($start, $stop, $step)"
    override fun typeName() = "range"
}

class PyEnv(val parent: PyEnv? = null) {
    val vars = LinkedHashMap<String, PyVal>()

    fun get(name: String): PyVal? {
        if (vars.containsKey(name)) return vars[name]
        return parent?.get(name)
    }

    fun set(name: String, value: PyVal) {
        vars[name] = value
    }

    fun setGlobal(name: String, value: PyVal) {
        var env = this
        while (env.parent != null) env = env.parent!!
        env.vars[name] = value
    }

    fun delete(name: String): Boolean {
        if (vars.containsKey(name)) {
            vars.remove(name)
            return true
        }
        return false
    }
}

class PyFunction(
    val name: String,
    val params: List<PyParam>,
    val body: List<PyStmt>,
    val closure: PyEnv,
    val boundSelf: PyVal? = null
) : PyVal {
    override fun toDisplayString() = "<function $name>"
    override fun typeName() = "function"
}

class PyBuiltin(
    val name: String,
    val fn: (args: List<PyVal>, kwargs: Map<String, PyVal>, interpreter: PyInterpreter) -> PyVal
) : PyVal {
    override fun toDisplayString() = "<built-in function $name>"
    override fun typeName() = "builtin_function_or_method"
}

class PyClass(
    val name: String,
    val bases: List<PyClass>,
    val attrs: LinkedHashMap<String, PyVal> = linkedMapOf()
) : PyVal {
    fun lookup(attr: String): PyVal? {
        if (attrs.containsKey(attr)) return attrs[attr]
        for (b in bases) {
            val found = b.lookup(attr)
            if (found != null) return found
        }
        return null
    }
    override fun toDisplayString() = "<class '$name'>"
    override fun typeName() = "type"
}

class PyInstance(
    val pyClass: PyClass,
    val attrs: LinkedHashMap<String, PyVal> = linkedMapOf()
) : PyVal {
    override fun toDisplayString() = "<${pyClass.name} object>"
    override fun typeName() = pyClass.name
}

class PyModule(
    val name: String,
    val attrs: LinkedHashMap<String, PyVal> = linkedMapOf()
) : PyVal {
    override fun toDisplayString() = "<module '$name'>"
    override fun typeName() = "module"
}

/** NumPy-compatible N-dimensional numeric array */
class PyArray(
    val data: DoubleArray,
    val shape: IntArray
) : PyVal {
    override fun isTruthy() = data.isNotEmpty()
    override fun typeName() = "numpy.ndarray"

    override fun toDisplayString(): String {
        if (shape.size <= 1) {
            return "array([${data.joinToString(", ") { formatNum(it) }}])"
        }
        val rows = shape[0]
        val cols = if (shape.size >= 2) shape[1] else data.size / rows.coerceAtLeast(1)
        val sb = StringBuilder("array([\n")
        for (r in 0 until rows.coerceAtMost(12)) {
            sb.append("  [")
            for (c in 0 until cols.coerceAtMost(12)) {
                if (c > 0) sb.append(", ")
                sb.append(formatNum(data[r * cols + c]))
            }
            if (cols > 12) sb.append(", ...")
            sb.append("]")
            if (r < rows - 1) sb.append(",")
            sb.append("\n")
        }
        if (rows > 12) sb.append("  ...\n")
        sb.append("])")
        return sb.toString()
    }

    companion object {
        fun formatNum(d: Double): String {
            if (d.isNaN()) return "nan"
            if (d.isInfinite()) return if (d > 0) "inf" else "-inf"
            val r = kotlin.math.round(d * 10000.0) / 10000.0
            return if (abs(r - r.toLong()) < 1e-9) "${r.toLong()}." else r.toString()
        }
    }
}

/** Pandas-compatible tabular DataFrame */
class PyDataFrame(
    val columns: List<String>,
    val rows: List<MutableList<PyVal>>
) : PyVal {
    override fun isTruthy() = rows.isNotEmpty()
    override fun typeName() = "pandas.DataFrame"

    override fun toDisplayString(): String {
        if (columns.isEmpty()) return "Empty DataFrame"
        val widths = columns.indices.map { c ->
            val maxCell = rows.take(20).maxOfOrNull { it.getOrNull(c)?.toDisplayString()?.length ?: 0 } ?: 0
            maxOf(columns[c].length, maxCell, 4).coerceAtMost(18)
        }
        val sb = StringBuilder()
        sb.append("     ")
        columns.forEachIndexed { i, col ->
            sb.append(col.take(widths[i]).padEnd(widths[i] + 2))
        }
        sb.append("\n")
        rows.take(20).forEachIndexed { rIdx, row ->
            sb.append(rIdx.toString().padEnd(5))
            columns.indices.forEach { c ->
                val v = row.getOrNull(c)?.toDisplayString() ?: ""
                sb.append(v.take(widths[c]).padEnd(widths[c] + 2))
            }
            sb.append("\n")
        }
        if (rows.size > 20) sb.append("... (${rows.size} rows total)\n")
        return sb.toString().trimEnd()
    }
}

data class PlotSeries(
    val type: String, // "line", "scatter", "bar", "hist"
    val x: List<Double>,
    val y: List<Double>,
    val labels: List<String> = emptyList(),
    val label: String = "",
    val colorHex: String = "#00E5FF"
)

data class PyPlotSpec(
    val title: String = "PyForge Plot",
    val xLabel: String = "",
    val yLabel: String = "",
    val showGrid: Boolean = true,
    val series: List<PlotSeries> = emptyList()
)

data class MetricCardData(
    val title: String,
    val value: String,
    val unit: String
)

data class PyExecutionResult(
    val stdout: String,
    val stderr: String,
    val success: Boolean,
    val elapsedMs: Long,
    val plots: List<PyPlotSpec> = emptyList(),
    val scene3D: Scene3D? = null,
    val metrics: List<MetricCardData> = emptyList(),
    val lastExprValue: String? = null
)

class PyRuntimeException(
    val pyType: String,
    val pyMessage: String,
    val line: Int = 0
) : RuntimeException(if (line > 0) "Line $line: $pyType: $pyMessage" else "$pyType: $pyMessage")

fun PyVal.asDouble(): Double = when (this) {
    is PyFloat -> value
    is PyInt -> value.toDouble()
    is PyBool -> if (value) 1.0 else 0.0
    else -> throw PyRuntimeException("TypeError", "Expected number, got ${typeName()}")
}

fun PyVal.asLong(): Long = when (this) {
    is PyInt -> value
    is PyBool -> if (value) 1L else 0L
    is PyFloat -> value.toLong()
    else -> throw PyRuntimeException("TypeError", "Expected int, got ${typeName()}")
}

fun PyVal.toDoubleList(): List<Double> = when (this) {
    is PyArray -> data.toList()
    is PyList -> items.map { it.asDouble() }
    is PyTuple -> items.map { it.asDouble() }
    is PyRange -> toList().map { it.asDouble() }
    else -> throw PyRuntimeException("TypeError", "Object of type ${typeName()} is not numeric sequence")
}
