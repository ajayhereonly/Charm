package com.example.python

import kotlin.math.*
import kotlin.random.Random

object PySciLib {

    private val plotPalette = listOf("#00E5FF", "#FFB300", "#22D3A0", "#C792EA", "#FF5370", "#60A5FA")

    fun createModule(name: String, interpreter: PyInterpreter): PyModule? = when (name) {
        "numpy", "np" -> buildNumPy()
        "pandas", "pd" -> buildPandas()
        "matplotlib" -> PyModule("matplotlib").apply { attrs["pyplot"] = buildMatplotlibPyplot(interpreter) }
        "matplotlib.pyplot", "pyplot", "plt" -> buildMatplotlibPyplot(interpreter)
        "scipy", "scipy.stats", "scipy.linalg", "scipy.optimize" -> buildSciPy(name)
        "sympy", "sp" -> buildSymPy()
        "networkx", "nx" -> buildNetworkX()
        "sklearn", "sklearn.linear_model", "sklearn.metrics" -> buildSklearn()
        else -> null
    }

    fun evalArrayBinary(left: PyVal, op: TokType, right: PyVal, line: Int): PyVal {
        if (op == TokType.AT && left is PyArray && right is PyArray) {
            return matMul(left, right, line)
        }
        val aArr = left as? PyArray
        val bArr = right as? PyArray
        val size = aArr?.data?.size ?: bArr!!.data.size
        val shape = aArr?.shape ?: bArr!!.shape
        val out = DoubleArray(size) { i ->
            val av = if (aArr != null) aArr.data[i % aArr.data.size] else left.asDouble()
            val bv = if (bArr != null) bArr.data[i % bArr.data.size] else right.asDouble()
            when (op) {
                TokType.PLUS -> av + bv
                TokType.MINUS -> av - bv
                TokType.STAR -> av * bv
                TokType.SLASH -> av / bv
                TokType.DOUBLESLASH -> floor(av / bv)
                TokType.PERCENT -> av % bv
                TokType.DOUBLESTAR -> av.pow(bv)
                else -> throw PyRuntimeException("TypeError", "Unsupported array op $op", line)
            }
        }
        return PyArray(out, shape.copyOf())
    }

    private fun matMul(a: PyArray, b: PyArray, line: Int): PyVal {
        if (a.shape.size == 1 && b.shape.size == 1) {
            var sum = 0.0
            for (i in a.data.indices) sum += a.data[i] * b.data[i % b.data.size]
            return PyFloat(sum)
        }
        val rA = a.shape[0]; val cA = a.shape.getOrElse(1) { 1 }
        val rB = b.shape[0]; val cB = b.shape.getOrElse(1) { 1 }
        if (cA != rB) throw PyRuntimeException("ValueError", "Matrix shapes (${rA}x${cA}) and (${rB}x${cB}) not aligned", line)
        val out = DoubleArray(rA * cB)
        for (i in 0 until rA) {
            for (j in 0 until cB) {
                var s = 0.0
                for (k in 0 until cA) s += a.data[i * cA + k] * b.data[k * cB + j]
                out[i * cB + j] = s
            }
        }
        return PyArray(out, intArrayOf(rA, cB))
    }

    fun getArrayAttr(arr: PyArray, attr: String, line: Int): PyVal = when (attr) {
        "shape" -> PyTuple(arr.shape.map { PyInt(it.toLong()) })
        "size" -> PyInt(arr.data.size.toLong())
        "ndim" -> PyInt(arr.shape.size.toLong())
        "T" -> transposeArray(arr)
        "sum" -> PyBuiltin("sum") { _, _, _ -> PyFloat(arr.data.sum()) }
        "mean" -> PyBuiltin("mean") { _, _, _ -> PyFloat(if (arr.data.isEmpty()) 0.0 else arr.data.average()) }
        "min" -> PyBuiltin("min") { _, _, _ -> PyFloat(arr.data.minOrNull() ?: 0.0) }
        "max" -> PyBuiltin("max") { _, _, _ -> PyFloat(arr.data.maxOrNull() ?: 0.0) }
        "std" -> PyBuiltin("std") { _, _, _ ->
            val avg = arr.data.average()
            PyFloat(sqrt(arr.data.sumOf { (it - avg).pow(2) } / arr.data.size.coerceAtLeast(1)))
        }
        "tolist" -> PyBuiltin("tolist") { _, _, _ -> PyList(arr.data.map { PyFloat(it) }.toMutableList()) }
        "reshape" -> PyBuiltin("reshape") { a, _, _ ->
            val dims = if (a.size == 1 && (a[0] is PyTuple || a[0] is PyList)) {
                (if (a[0] is PyTuple) (a[0] as PyTuple).items else (a[0] as PyList).items).map { it.asLong().toInt() }.toIntArray()
            } else {
                a.map { it.asLong().toInt() }.toIntArray()
            }
            PyArray(arr.data.copyOf(), dims)
        }
        "dot" -> PyBuiltin("dot") { a, _, _ -> matMul(arr, a[0] as PyArray, line) }
        else -> throw PyRuntimeException("AttributeError", "'numpy.ndarray' has no attribute '$attr'", line)
    }

    private fun transposeArray(arr: PyArray): PyArray {
        if (arr.shape.size < 2) return PyArray(arr.data.copyOf(), arr.shape.copyOf())
        val r = arr.shape[0]; val c = arr.shape[1]
        val out = DoubleArray(r * c)
        for (i in 0 until r) for (j in 0 until c) out[j * r + i] = arr.data[i * c + j]
        return PyArray(out, intArrayOf(c, r))
    }

    fun getDataFrameAttr(df: PyDataFrame, attr: String, line: Int): PyVal = when (attr) {
        "columns" -> PyList(df.columns.map { PyStr(it) }.toMutableList())
        "shape" -> PyTuple(listOf(PyInt(df.rows.size.toLong()), PyInt(df.columns.size.toLong())))
        "head" -> PyBuiltin("head") { a, _, _ ->
            val n = a.firstOrNull()?.asLong()?.toInt() ?: 5
            PyDataFrame(df.columns, df.rows.take(n))
        }
        "tail" -> PyBuiltin("tail") { a, _, _ ->
            val n = a.firstOrNull()?.asLong()?.toInt() ?: 5
            PyDataFrame(df.columns, df.rows.takeLast(n))
        }
        "describe" -> PyBuiltin("describe") { _, _, _ ->
            val statsCols = listOf("stat") + df.columns
            val countRow = mutableListOf<PyVal>(PyStr("count"))
            val meanRow = mutableListOf<PyVal>(PyStr("mean"))
            val minRow = mutableListOf<PyVal>(PyStr("min"))
            val maxRow = mutableListOf<PyVal>(PyStr("max"))
            df.columns.indices.forEach { c ->
                val nums = df.rows.mapNotNull { runCatching { it[c].asDouble() }.getOrNull() }
                countRow.add(PyInt(nums.size.toLong()))
                meanRow.add(PyFloat(if (nums.isEmpty()) 0.0 else round(nums.average() * 100.0) / 100.0))
                minRow.add(PyFloat(nums.minOrNull() ?: 0.0))
                maxRow.add(PyFloat(nums.maxOrNull() ?: 0.0))
            }
            PyDataFrame(statsCols, listOf(countRow, meanRow, minRow, maxRow))
        }
        "mean" -> PyBuiltin("mean") { _, _, _ ->
            val res = LinkedHashMap<PyVal, PyVal>()
            df.columns.forEachIndexed { c, name ->
                val nums = df.rows.mapNotNull { runCatching { it[c].asDouble() }.getOrNull() }
                if (nums.isNotEmpty()) res[PyStr(name)] = PyFloat(nums.average())
            }
            PyDict(res)
        }
        "sum" -> PyBuiltin("sum") { _, _, _ ->
            val res = LinkedHashMap<PyVal, PyVal>()
            df.columns.forEachIndexed { c, name ->
                val nums = df.rows.mapNotNull { runCatching { it[c].asDouble() }.getOrNull() }
                if (nums.isNotEmpty()) res[PyStr(name)] = PyFloat(nums.sum())
            }
            PyDict(res)
        }
        "sort_values" -> PyBuiltin("sort_values") { a, kw, _ ->
            val col = (kw["by"] ?: a.first()).toDisplayString()
            val asc = kw["ascending"]?.isTruthy() != false
            val idx = df.columns.indexOf(col).coerceAtLeast(0)
            val sorted = df.rows.sortedWith { r1, r2 ->
                val n1 = runCatching { r1[idx].asDouble() }.getOrNull()
                val n2 = runCatching { r2[idx].asDouble() }.getOrNull()
                val cmp = if (n1 != null && n2 != null) n1.compareTo(n2)
                else r1[idx].toDisplayString().compareTo(r2[idx].toDisplayString())
                if (asc) cmp else -cmp
            }
            PyDataFrame(df.columns, sorted)
        }
        "to_dict" -> PyBuiltin("to_dict") { _, _, _ ->
            val map = LinkedHashMap<PyVal, PyVal>()
            df.columns.forEachIndexed { c, col ->
                map[PyStr(col)] = PyList(df.rows.map { it[c] }.toMutableList())
            }
            PyDict(map)
        }
        "to_csv" -> PyBuiltin("to_csv") { _, _, _ ->
            val sb = StringBuilder(df.columns.joinToString(",") + "\n")
            df.rows.forEach { r -> sb.appendLine(r.joinToString(",") { it.toDisplayString() }) }
            PyStr(sb.toString().trimEnd())
        }
        else -> throw PyRuntimeException("AttributeError", "'DataFrame' has no attribute '$attr'", line)
    }

    private fun buildNumPy(): PyModule {
        val m = PyModule("numpy")
        m.attrs["pi"] = PyFloat(PI)
        m.attrs["e"] = PyFloat(E)

        fun toArray(v: PyVal): PyArray = when (v) {
            is PyArray -> v
            is PyList -> {
                if (v.items.isNotEmpty() && v.items[0] is PyList) {
                    val rows = v.items.size
                    val cols = (v.items[0] as PyList).items.size
                    val flat = DoubleArray(rows * cols)
                    for (r in 0 until rows) {
                        val row = (v.items[r] as PyList).items
                        for (c in 0 until cols) flat[r * cols + c] = row[c].asDouble()
                    }
                    PyArray(flat, intArrayOf(rows, cols))
                } else {
                    PyArray(v.items.map { it.asDouble() }.toDoubleArray(), intArrayOf(v.items.size))
                }
            }
            is PyTuple -> PyArray(v.items.map { it.asDouble() }.toDoubleArray(), intArrayOf(v.items.size))
            is PyRange -> {
                val list = v.toList().map { it.asDouble() }
                PyArray(list.toDoubleArray(), intArrayOf(list.size))
            }
            else -> PyArray(doubleArrayOf(v.asDouble()), intArrayOf(1))
        }

        fun ufunc(name: String, op: (Double) -> Double) = PyBuiltin(name) { a, _, _ ->
            if (a[0] is PyArray || a[0] is PyList) {
                val arr = toArray(a[0])
                PyArray(DoubleArray(arr.data.size) { op(arr.data[it]) }, arr.shape.copyOf())
            } else {
                PyFloat(op(a[0].asDouble()))
            }
        }

        m.attrs["array"] = PyBuiltin("array") { a, _, _ -> toArray(a[0]) }
        m.attrs["zeros"] = PyBuiltin("zeros") { a, _, _ ->
            val shape = extractShape(a[0])
            PyArray(DoubleArray(shape.fold(1) { acc, i -> acc * i }), shape)
        }
        m.attrs["ones"] = PyBuiltin("ones") { a, _, _ ->
            val shape = extractShape(a[0])
            PyArray(DoubleArray(shape.fold(1) { acc, i -> acc * i }) { 1.0 }, shape)
        }
        m.attrs["eye"] = PyBuiltin("eye") { a, _, _ ->
            val n = a[0].asLong().toInt()
            val out = DoubleArray(n * n)
            for (i in 0 until n) out[i * n + i] = 1.0
            PyArray(out, intArrayOf(n, n))
        }
        m.attrs["arange"] = PyBuiltin("arange") { a, _, _ ->
            val start = if (a.size == 1) 0.0 else a[0].asDouble()
            val stop = if (a.size == 1) a[0].asDouble() else a[1].asDouble()
            val step = a.getOrNull(2)?.asDouble() ?: 1.0
            val list = mutableListOf<Double>()
            var x = start
            var guard = 0
            while ((step > 0 && x < stop - 1e-12) || (step < 0 && x > stop + 1e-12)) {
                list.add(x); x += step
                if (++guard > 10_000) break
            }
            PyArray(list.toDoubleArray(), intArrayOf(list.size))
        }
        m.attrs["linspace"] = PyBuiltin("linspace") { a, _, _ ->
            val start = a[0].asDouble(); val stop = a[1].asDouble()
            val num = (a.getOrNull(2)?.asLong()?.toInt() ?: 50).coerceIn(2, 5000)
            val data = DoubleArray(num) { i -> start + (stop - start) * i / (num - 1) }
            PyArray(data, intArrayOf(num))
        }
        m.attrs["sin"] = ufunc("sin") { sin(it) }
        m.attrs["cos"] = ufunc("cos") { cos(it) }
        m.attrs["tan"] = ufunc("tan") { tan(it) }
        m.attrs["sqrt"] = ufunc("sqrt") { sqrt(it) }
        m.attrs["exp"] = ufunc("exp") { exp(it) }
        m.attrs["log"] = ufunc("log") { ln(it) }
        m.attrs["abs"] = ufunc("abs") { abs(it) }
        m.attrs["sum"] = PyBuiltin("sum") { a, _, _ -> PyFloat(toArray(a[0]).data.sum()) }
        m.attrs["mean"] = PyBuiltin("mean") { a, _, _ -> PyFloat(toArray(a[0]).data.average()) }
        m.attrs["std"] = PyBuiltin("std") { a, _, _ ->
            val d = toArray(a[0]).data
            val avg = d.average()
            PyFloat(sqrt(d.sumOf { (it - avg).pow(2) } / d.size.coerceAtLeast(1)))
        }
        m.attrs["min"] = PyBuiltin("min") { a, _, _ -> PyFloat(toArray(a[0]).data.minOrNull() ?: 0.0) }
        m.attrs["max"] = PyBuiltin("max") { a, _, _ -> PyFloat(toArray(a[0]).data.maxOrNull() ?: 0.0) }
        m.attrs["dot"] = PyBuiltin("dot") { a, _, _ -> matMul(toArray(a[0]), toArray(a[1]), 0) }
        m.attrs["matmul"] = PyBuiltin("matmul") { a, _, _ -> matMul(toArray(a[0]), toArray(a[1]), 0) }
        m.attrs["transpose"] = PyBuiltin("transpose") { a, _, _ -> transposeArray(toArray(a[0])) }

        val linalg = PyModule("numpy.linalg")
        linalg.attrs["norm"] = PyBuiltin("norm") { a, _, _ ->
            PyFloat(sqrt(toArray(a[0]).data.sumOf { it * it }))
        }
        linalg.attrs["det"] = PyBuiltin("det") { a, _, _ ->
            val arr = toArray(a[0])
            val det = if (arr.shape.size >= 2 && arr.shape[0] == 2 && arr.shape[1] == 2) {
                arr.data[0] * arr.data[3] - arr.data[1] * arr.data[2]
            } else if (arr.shape.size >= 2 && arr.shape[0] == 3 && arr.shape[1] == 3) {
                val d = arr.data
                d[0] * (d[4] * d[8] - d[5] * d[7]) - d[1] * (d[3] * d[8] - d[5] * d[6]) + d[2] * (d[3] * d[7] - d[4] * d[6])
            } else 0.0
            PyFloat(det)
        }
        m.attrs["linalg"] = linalg

        val npRandom = PyModule("numpy.random")
        npRandom.attrs["rand"] = PyBuiltin("rand") { a, _, _ ->
            val n = a.firstOrNull()?.asLong()?.toInt()?.coerceIn(1, 5000) ?: 1
            PyArray(DoubleArray(n) { Random.nextDouble() }, intArrayOf(n))
        }
        npRandom.attrs["randn"] = PyBuiltin("randn") { a, _, _ ->
            val n = a.firstOrNull()?.asLong()?.toInt()?.coerceIn(1, 5000) ?: 1
            PyArray(DoubleArray(n) {
                val u1 = Random.nextDouble().coerceAtLeast(1e-12)
                sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * Random.nextDouble())
            }, intArrayOf(n))
        }
        m.attrs["random"] = npRandom
        return m
    }

    private fun extractShape(v: PyVal): IntArray = when (v) {
        is PyTuple -> v.items.map { it.asLong().toInt().coerceAtLeast(1) }.toIntArray()
        is PyList -> v.items.map { it.asLong().toInt().coerceAtLeast(1) }.toIntArray()
        else -> intArrayOf(v.asLong().toInt().coerceAtLeast(1))
    }

    private fun buildPandas(): PyModule {
        val m = PyModule("pandas")
        m.attrs["DataFrame"] = PyBuiltin("DataFrame") { a, _, interp ->
            val src = a.firstOrNull()
            when (src) {
                is PyDict -> {
                    val cols = src.entries.keys.map { it.toDisplayString() }
                    val colVals = src.entries.values.map { interp.toIterableList(it) }
                    val rowCount = colVals.maxOfOrNull { it.size } ?: 0
                    val rows = (0 until rowCount).map { r ->
                        colVals.map { col -> col.getOrElse(r) { PyNone } }.toMutableList()
                    }
                    PyDataFrame(cols, rows)
                }
                is PyList -> {
                    val first = src.items.firstOrNull()
                    if (first is PyDict) {
                        val cols = first.entries.keys.map { it.toDisplayString() }
                        val rows = src.items.map { item ->
                            val d = item as PyDict
                            cols.map { c -> d.entries[PyStr(c)] ?: PyNone }.toMutableList()
                        }
                        PyDataFrame(cols, rows)
                    } else PyDataFrame(listOf("0"), src.items.map { mutableListOf(it) })
                }
                else -> PyDataFrame(emptyList(), emptyList())
            }
        }
        m.attrs["Series"] = PyBuiltin("Series") { a, _, interp ->
            PyList(interp.toIterableList(a[0]).toMutableList())
        }
        return m
    }

    private fun buildMatplotlibPyplot(interpreter: PyInterpreter): PyModule {
        val m = PyModule("matplotlib.pyplot")
        fun nextColor(kw: Map<String, PyVal>): String {
            val raw = kw["color"]?.toDisplayString() ?: kw["c"]?.toDisplayString()
            if (!raw.isNullOrBlank()) return raw
            return plotPalette[interpreter.currentPlotSeries.size % plotPalette.size]
        }

        m.attrs["plot"] = PyBuiltin("plot") { a, kw, _ ->
            val (xs, ys) = if (a.size >= 2 && a[1] !is PyStr) {
                a[0].toDoubleList() to a[1].toDoubleList()
            } else {
                val y = a[0].toDoubleList()
                y.indices.map { it.toDouble() } to y
            }
            interpreter.currentPlotSeries.add(
                PlotSeries(
                    type = "line",
                    x = xs,
                    y = ys,
                    label = kw["label"]?.toDisplayString() ?: "Series ${interpreter.currentPlotSeries.size + 1}",
                    colorHex = nextColor(kw)
                )
            )
            PyNone
        }
        m.attrs["scatter"] = PyBuiltin("scatter") { a, kw, _ ->
            val xs = a[0].toDoubleList()
            val ys = a[1].toDoubleList()
            interpreter.currentPlotSeries.add(
                PlotSeries(
                    type = "scatter",
                    x = xs,
                    y = ys,
                    label = kw["label"]?.toDisplayString() ?: "Scatter",
                    colorHex = nextColor(kw)
                )
            )
            PyNone
        }
        m.attrs["bar"] = PyBuiltin("bar") { a, kw, interp ->
            val rawX = interp.toIterableList(a[0])
            val ys = a[1].toDoubleList()
            val labels = rawX.map { it.toDisplayString() }
            val xs = ys.indices.map { it.toDouble() }
            interpreter.currentPlotSeries.add(
                PlotSeries(
                    type = "bar",
                    x = xs,
                    y = ys,
                    labels = labels,
                    label = kw["label"]?.toDisplayString() ?: "Bars",
                    colorHex = nextColor(kw)
                )
            )
            PyNone
        }
        m.attrs["hist"] = PyBuiltin("hist") { a, kw, _ ->
            val vals = a[0].toDoubleList()
            val bins = (kw["bins"]?.asLong()?.toInt() ?: a.getOrNull(1)?.asLong()?.toInt() ?: 8).coerceIn(2, 30)
            val lo = vals.minOrNull() ?: 0.0
            val hi = (vals.maxOrNull() ?: 1.0).let { if (it == lo) lo + 1.0 else it }
            val counts = DoubleArray(bins)
            for (v in vals) {
                val idx = (((v - lo) / (hi - lo)) * bins).toInt().coerceIn(0, bins - 1)
                counts[idx] += 1.0
            }
            val labels = (0 until bins).map { i -> String.format("%.1f", lo + (hi - lo) * i / bins) }
            interpreter.currentPlotSeries.add(
                PlotSeries(
                    type = "bar",
                    x = (0 until bins).map { it.toDouble() },
                    y = counts.toList(),
                    labels = labels,
                    label = kw["label"]?.toDisplayString() ?: "Histogram",
                    colorHex = nextColor(kw)
                )
            )
            PyNone
        }
        m.attrs["title"] = PyBuiltin("title") { a, _, _ -> interpreter.currentPlotTitle = a[0].toDisplayString(); PyNone }
        m.attrs["xlabel"] = PyBuiltin("xlabel") { a, _, _ -> interpreter.currentXLabel = a[0].toDisplayString(); PyNone }
        m.attrs["ylabel"] = PyBuiltin("ylabel") { a, _, _ -> interpreter.currentYLabel = a[0].toDisplayString(); PyNone }
        m.attrs["grid"] = PyBuiltin("grid") { a, _, _ -> interpreter.currentShowGrid = a.firstOrNull()?.isTruthy() != false; PyNone }
        m.attrs["legend"] = PyBuiltin("legend") { _, _, _ -> PyNone }
        m.attrs["figure"] = PyBuiltin("figure") { _, _, _ -> PyNone }
        m.attrs["tight_layout"] = PyBuiltin("tight_layout") { _, _, _ -> PyNone }
        m.attrs["show"] = PyBuiltin("show") { _, _, _ ->
            if (interpreter.currentPlotSeries.isNotEmpty()) {
                interpreter.emitPlot(
                    PyPlotSpec(
                        title = interpreter.currentPlotTitle,
                        xLabel = interpreter.currentXLabel,
                        yLabel = interpreter.currentYLabel,
                        showGrid = interpreter.currentShowGrid,
                        series = interpreter.currentPlotSeries.toList()
                    )
                )
                interpreter.currentPlotSeries.clear()
            }
            PyNone
        }
        return m
    }

    private fun buildSciPy(name: String): PyModule {
        val m = PyModule(name)
        val stats = PyModule("scipy.stats")
        stats.attrs["linregress"] = PyBuiltin("linregress") { a, _, _ ->
            val x = a[0].toDoubleList(); val y = a[1].toDoubleList()
            val mx = x.average(); val my = y.average()
            val num = x.indices.sumOf { (x[it] - mx) * (y[it] - my) }
            val den = x.indices.sumOf { (x[it] - mx).pow(2) }.coerceAtLeast(1e-12)
            val slope = num / den
            val intercept = my - slope * mx
            PyTuple(listOf(PyFloat(slope), PyFloat(intercept)))
        }
        val optimize = PyModule("scipy.optimize")
        optimize.attrs["minimize_scalar"] = PyBuiltin("minimize_scalar") { a, _, interp ->
            val fn = a[0]
            var bestX = 0.0; var bestY = Double.POSITIVE_INFINITY
            for (i in -200..200) {
                val x = i * 0.05
                val y = interp.callCallable(fn, listOf(PyFloat(x)), emptyMap()).asDouble()
                if (y < bestY) { bestY = y; bestX = x }
            }
            PyDict(linkedMapOf(PyStr("x") to PyFloat(bestX), PyStr("fun") to PyFloat(bestY)))
        }
        m.attrs["stats"] = stats
        m.attrs["optimize"] = optimize
        m.attrs["linregress"] = stats.attrs["linregress"]!!
        m.attrs["minimize_scalar"] = optimize.attrs["minimize_scalar"]!!
        return m
    }

    private fun buildSymPy(): PyModule {
        val m = PyModule("sympy")
        m.attrs["Symbol"] = PyBuiltin("Symbol") { a, _, _ -> PyStr(a[0].toDisplayString()) }
        m.attrs["symbols"] = PyBuiltin("symbols") { a, _, _ ->
            val parts = a[0].toDisplayString().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
            if (parts.size == 1) PyStr(parts[0]) else PyTuple(parts.map { PyStr(it) })
        }
        m.attrs["solve_quadratic"] = PyBuiltin("solve_quadratic") { a, _, _ ->
            val qa = a[0].asDouble(); val qb = a[1].asDouble(); val qc = a[2].asDouble()
            val d = qb * qb - 4 * qa * qc
            if (d < 0) PyList() else PyList(mutableListOf(PyFloat((-qb + sqrt(d)) / (2 * qa)), PyFloat((-qb - sqrt(d)) / (2 * qa))))
        }
        m.attrs["diff"] = PyBuiltin("diff") { a, _, _ ->
            val expr = a[0].toDisplayString().replace(" ", "").replace("**", "^")
            val v = a.getOrNull(1)?.toDisplayString()?.ifBlank { "x" } ?: "x"
            val terms = Regex("[+-]?[^+-]+").findAll(expr).map { it.value }.toList()
            val derivs = mutableListOf<String>()
            for (rawTerm in terms) {
                if (!rawTerm.contains(v)) continue
                val sign = if (rawTerm.startsWith("-")) -1 else 1
                val clean = rawTerm.removePrefix("+").removePrefix("-")
                val parts = clean.split(v)
                val coeffStr = parts[0].removeSuffix("*")
                val coeff = (if (coeffStr.isEmpty()) 1.0 else coeffStr.toDoubleOrNull() ?: 1.0) * sign
                val expStr = parts.getOrNull(1)?.removePrefix("^") ?: ""
                val exp = if (expStr.isEmpty()) 1 else expStr.toIntOrNull() ?: 1
                val newCoeff = coeff * exp
                val newExp = exp - 1
                if (newCoeff == 0.0) continue
                val cFmt = if (newCoeff == newCoeff.toLong().toDouble()) newCoeff.toLong().toString() else newCoeff.toString()
                val formatted = when (newExp) {
                    0 -> cFmt
                    1 -> "${cFmt}*$v"
                    else -> "${cFmt}*$v^$newExp"
                }
                derivs.add(formatted)
            }
            if (derivs.isEmpty()) PyStr("0") else PyStr(derivs.joinToString(" + ").replace("+ -", "- "))
        }
        return m
    }

    private fun buildNetworkX(): PyModule {
        val m = PyModule("networkx")
        m.attrs["Graph"] = PyBuiltin("Graph") { _, _, _ ->
            val adj = LinkedHashMap<String, MutableList<Pair<String, Double>>>()
            val cls = PyClass("Graph", emptyList())
            PyInstance(cls).apply {
                attrs["add_edge"] = PyBuiltin("add_edge") { a, kw, _ ->
                    val u = a[0].toDisplayString(); val v = a[1].toDisplayString()
                    val w = kw["weight"]?.asDouble() ?: 1.0
                    adj.getOrPut(u) { mutableListOf() }.add(v to w)
                    adj.getOrPut(v) { mutableListOf() }.add(u to w)
                    PyNone
                }
                attrs["nodes"] = PyBuiltin("nodes") { _, _, _ -> PyList(adj.keys.map { PyStr(it) }.toMutableList()) }
                attrs["shortest_path"] = PyBuiltin("shortest_path") { a, _, _ ->
                    val src = a[0].toDisplayString(); val dst = a[1].toDisplayString()
                    val queue = ArrayDeque<List<String>>().apply { add(listOf(src)) }
                    val visited = mutableSetOf(src)
                    var found: List<String> = emptyList()
                    while (queue.isNotEmpty()) {
                        val path = queue.removeFirst()
                        if (path.last() == dst) { found = path; break }
                        for ((nxt, _) in adj[path.last()].orEmpty()) {
                            if (visited.add(nxt)) queue.add(path + nxt)
                        }
                    }
                    PyList(found.map { PyStr(it) }.toMutableList())
                }
            }
        }
        return m
    }

    private fun buildSklearn(): PyModule {
        val m = PyModule("sklearn")
        val lr = PyBuiltin("LinearRegression") { _, _, _ ->
            var slope = 0.0; var intercept = 0.0
            val cls = PyClass("LinearRegression", emptyList())
            PyInstance(cls).apply {
                attrs["fit"] = PyBuiltin("fit") { a, _, _ ->
                    val xs = a[0].toDoubleList(); val ys = a[1].toDoubleList()
                    val mx = xs.average(); val my = ys.average()
                    val num = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
                    val den = xs.indices.sumOf { (xs[it] - mx).pow(2) }.coerceAtLeast(1e-12)
                    slope = num / den; intercept = my - slope * mx
                    this.attrs["coef_"] = PyFloat(slope)
                    this.attrs["intercept_"] = PyFloat(intercept)
                    this
                }
                attrs["predict"] = PyBuiltin("predict") { a, _, _ ->
                    val xs = a[0].toDoubleList()
                    PyArray(xs.map { slope * it + intercept }.toDoubleArray(), intArrayOf(xs.size))
                }
            }
        }
        val linMod = PyModule("sklearn.linear_model").apply { attrs["LinearRegression"] = lr }
        m.attrs["linear_model"] = linMod
        m.attrs["LinearRegression"] = lr
        return m
    }
}
