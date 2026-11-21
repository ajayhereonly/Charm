package com.example.python

import com.example.graphics3d.MeshObject
import com.example.graphics3d.Scene3D
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round

private class ReturnSignal(val value: PyVal) : RuntimeException(null, null, false, false)
private class BreakSignal : RuntimeException(null, null, false, false)
private class ContinueSignal : RuntimeException(null, null, false, false)

class PyInterpreter(
    private val workspaceFilesProvider: () -> Map<String, String> = { emptyMap() }
) {
    val globalEnv = PyEnv()
    private val stdoutBuilder = StringBuilder()
    private val stderrBuilder = StringBuilder()
    private val capturedPlots = mutableListOf<PyPlotSpec>()
    private val capturedMetrics = mutableListOf<MetricCardData>()
    private val activeSceneObjects = mutableListOf<MeshObject>()
    private var activeSceneTitle: String = "PyForge 3D Scene"
    private var sceneModified = false

    // Plot state for matplotlib.pyplot
    internal val currentPlotSeries = mutableListOf<PlotSeries>()
    internal var currentPlotTitle = "Plot"
    internal var currentXLabel = ""
    internal var currentYLabel = ""
    internal var currentShowGrid = true

    private var stepCount = 0
    private val maxSteps = 250_000
    private var currentLine = 0

    private val loadedModules = mutableMapOf<String, PyModule>()

    init {
        installBuiltins()
    }

    fun emitStdout(text: String) {
        if (text.startsWith("METRIC|")) {
            val parts = text.split("|")
            if (parts.size >= 3) {
                capturedMetrics.add(
                    MetricCardData(
                        title = parts[1].trim(),
                        value = parts[2].trim(),
                        unit = parts.getOrNull(3)?.trim() ?: ""
                    )
                )
                return
            }
        }
        stdoutBuilder.append(text)
    }

    fun emitPlot(spec: PyPlotSpec) {
        capturedPlots.add(spec)
    }

    fun clearActiveScene(title: String = "PyForge 3D Scene") {
        activeSceneObjects.clear()
        activeSceneTitle = title
        sceneModified = true
    }

    fun addSceneObject(mesh: MeshObject) {
        activeSceneObjects.add(mesh)
        sceneModified = true
    }

    fun getCurrentScene3D(): Scene3D? {
        if (!sceneModified) return null
        return Scene3D(
            title = activeSceneTitle,
            objects = activeSceneObjects.toList(),
            showGrid = true,
            showAxes = true
        )
    }

    fun resetSession() {
        globalEnv.vars.clear()
        loadedModules.clear()
        installBuiltins()
    }

    fun execute(
        code: String,
        injectedGlobals: Map<String, PyVal> = emptyMap(),
        isRepl: Boolean = false
    ): PyExecutionResult {
        val startNs = System.nanoTime()
        stdoutBuilder.clear()
        stderrBuilder.clear()
        capturedPlots.clear()
        capturedMetrics.clear()
        activeSceneObjects.clear()
        activeSceneTitle = "PyForge 3D Scene"
        sceneModified = false
        currentPlotSeries.clear()
        currentPlotTitle = "Plot"
        currentXLabel = ""
        currentYLabel = ""
        currentShowGrid = true
        stepCount = 0

        for ((k, v) in injectedGlobals) {
            globalEnv.set(k, v)
        }
        globalEnv.set("__name__", PyStr("__main__"))

        var lastExpr: String? = null
        var success = true

        try {
            val tokens = PyLexer(code).tokenize()
            val stmts = PyParser(tokens).parseModule()
            for ((idx, stmt) in stmts.withIndex()) {
                if (isRepl && idx == stmts.lastIndex && stmt is StmtExpr) {
                    val v = evalExpr(stmt.expr, globalEnv)
                    if (v !is PyNone) {
                        lastExpr = v.toRepr()
                    }
                } else {
                    execStmt(stmt, globalEnv)
                }
            }
            // Auto-flush pending plot series if user forgot plt.show()
            if (currentPlotSeries.isNotEmpty()) {
                capturedPlots.add(
                    PyPlotSpec(
                        title = currentPlotTitle,
                        xLabel = currentXLabel,
                        yLabel = currentYLabel,
                        showGrid = currentShowGrid,
                        series = currentPlotSeries.toList()
                    )
                )
                currentPlotSeries.clear()
            }
        } catch (e: PyRuntimeException) {
            success = false
            stderrBuilder.append("Traceback (most recent call last):\n")
            if (e.line > 0) stderrBuilder.append("  File \"<studio>\", line ${e.line}\n")
            stderrBuilder.append("${e.pyType}: ${e.pyMessage}\n")
        } catch (e: Exception) {
            success = false
            stderrBuilder.append("RuntimeError (line $currentLine): ${e.message ?: e::class.simpleName}\n")
        }

        val elapsed = (System.nanoTime() - startNs) / 1_000_000L
        return PyExecutionResult(
            stdout = stdoutBuilder.toString(),
            stderr = stderrBuilder.toString(),
            success = success,
            elapsedMs = elapsed,
            plots = capturedPlots.toList(),
            scene3D = getCurrentScene3D(),
            metrics = capturedMetrics.toList(),
            lastExprValue = lastExpr
        )
    }

    private fun checkStep(line: Int) {
        currentLine = line
        if (++stepCount > maxSteps) {
            throw PyRuntimeException("TimeoutError", "Execution step limit ($maxSteps) exceeded — check for infinite loops", line)
        }
    }

    fun execBlock(stmts: List<PyStmt>, env: PyEnv) {
        for (s in stmts) execStmt(s, env)
    }

    private fun execStmt(stmt: PyStmt, env: PyEnv) {
        checkStep(stmt.line)
        when (stmt) {
            is StmtPass -> Unit
            is StmtExpr -> evalExpr(stmt.expr, env)
            is StmtReturn -> throw ReturnSignal(stmt.expr?.let { evalExpr(it, env) } ?: PyNone)
            is StmtBreak -> throw BreakSignal()
            is StmtContinue -> throw ContinueSignal()
            is StmtGlobal -> Unit
            is StmtAssert -> {
                if (!evalExpr(stmt.cond, env).isTruthy()) {
                    val msg = stmt.msg?.let { evalExpr(it, env).toDisplayString() } ?: "Assertion failed"
                    throw PyRuntimeException("AssertionError", msg, stmt.line)
                }
            }
            is StmtDel -> {
                when (val t = stmt.target) {
                    is ExprIdent -> env.delete(t.name)
                    is ExprSubscript -> {
                        val obj = evalExpr(t.obj, env)
                        val key = evalExpr(t.index, env)
                        when (obj) {
                            is PyDict -> obj.entries.remove(key)
                            is PyList -> {
                                val idx = normalizeIndex(key.asLong().toInt(), obj.items.size)
                                obj.items.removeAt(idx)
                            }
                            else -> throw PyRuntimeException("TypeError", "Cannot delete item from ${obj.typeName()}", stmt.line)
                        }
                    }
                    else -> Unit
                }
            }
            is StmtAssign -> {
                val value = evalExpr(stmt.value, env)
                for (target in stmt.targets) {
                    assignTarget(target, value, env, stmt.line)
                }
            }
            is StmtAugAssign -> {
                val current = evalExpr(stmt.target, env)
                val rhs = evalExpr(stmt.value, env)
                val binOp = when (stmt.op) {
                    TokType.PLUSEQ -> TokType.PLUS
                    TokType.MINUSEQ -> TokType.MINUS
                    TokType.STAREQ -> TokType.STAR
                    TokType.SLASHEQ -> TokType.SLASH
                    TokType.DOUBLESLASHEQ -> TokType.DOUBLESLASH
                    TokType.PERCENTEQ -> TokType.PERCENT
                    TokType.DOUBLESTAREQ -> TokType.DOUBLESTAR
                    else -> TokType.PLUS
                }
                val updated = evalBinaryOp(current, binOp, rhs, stmt.line)
                assignTarget(stmt.target, updated, env, stmt.line)
            }
            is StmtIf -> {
                var matched = false
                for ((cond, body) in stmt.branches) {
                    if (evalExpr(cond, env).isTruthy()) {
                        execBlock(body, env)
                        matched = true
                        break
                    }
                }
                if (!matched && stmt.elseBody.isNotEmpty()) {
                    execBlock(stmt.elseBody, env)
                }
            }
            is StmtWhile -> {
                while (evalExpr(stmt.cond, env).isTruthy()) {
                    checkStep(stmt.line)
                    try {
                        execBlock(stmt.body, env)
                    } catch (_: BreakSignal) {
                        break
                    } catch (_: ContinueSignal) {
                        continue
                    }
                }
            }
            is StmtFor -> {
                val iterVal = evalExpr(stmt.iterable, env)
                val items = toIterableList(iterVal, stmt.line)
                for (item in items) {
                    checkStep(stmt.line)
                    assignTarget(stmt.target, item, env, stmt.line)
                    try {
                        execBlock(stmt.body, env)
                    } catch (_: BreakSignal) {
                        break
                    } catch (_: ContinueSignal) {
                        continue
                    }
                }
            }
            is StmtDef -> {
                var fnVal: PyVal = PyFunction(stmt.name, stmt.params, stmt.body, env)
                for (dec in stmt.decorators.reversed()) {
                    val decorator = evalExpr(dec, env)
                    fnVal = callCallable(decorator, listOf(fnVal), emptyMap(), stmt.line)
                }
                env.set(stmt.name, fnVal)
            }
            is StmtClass -> {
                val bases = stmt.bases.mapNotNull { evalExpr(it, env) as? PyClass }
                val classEnv = PyEnv(env)
                execBlock(stmt.body, classEnv)
                val cls = PyClass(stmt.name, bases, LinkedHashMap(classEnv.vars))
                env.set(stmt.name, cls)
            }
            is StmtImport -> {
                for ((modName, alias) in stmt.names) {
                    val mod = resolveModule(modName, stmt.line)
                    if (alias != null) {
                        env.set(alias, mod)
                    } else if (modName.contains('.')) {
                        val rootName = modName.substringBefore('.')
                        val subName = modName.substringAfter('.')
                        val rootMod = loadedModules.getOrPut(rootName) { PyModule(rootName) }
                        rootMod.attrs[subName] = mod
                        env.set(rootName, rootMod)
                    } else {
                        env.set(modName, mod)
                    }
                }
            }
            is StmtFromImport -> {
                val mod = resolveModule(stmt.module, stmt.line)
                for ((name, alias) in stmt.names) {
                    if (name == "*") {
                        for ((k, v) in mod.attrs) {
                            if (!k.startsWith("_")) env.set(k, v)
                        }
                    } else {
                        val v = mod.attrs[name]
                            ?: throw PyRuntimeException("ImportError", "Cannot import name '$name' from '${stmt.module}'", stmt.line)
                        env.set(alias ?: name, v)
                    }
                }
            }
            is StmtTry -> {
                try {
                    execBlock(stmt.tryBody, env)
                } catch (e: PyRuntimeException) {
                    var handled = false
                    for ((excType, alias, body) in stmt.handlers) {
                        if (excType == null || excType == "Exception" || excType == "BaseException" || excType == e.pyType) {
                            if (alias != null) env.set(alias, PyStr(e.pyMessage))
                            execBlock(body, env)
                            handled = true
                            break
                        }
                    }
                    if (!handled) throw e
                } finally {
                    if (stmt.finallyBody.isNotEmpty()) {
                        execBlock(stmt.finallyBody, env)
                    }
                }
            }
            is StmtRaise -> {
                val msg = stmt.expr?.let { evalExpr(it, env).toDisplayString() } ?: "Error raised"
                throw PyRuntimeException("RuntimeError", msg, stmt.line)
            }
            is StmtWith -> {
                val ctx = evalExpr(stmt.expr, env)
                if (stmt.alias != null) env.set(stmt.alias, ctx)
                execBlock(stmt.body, env)
            }
        }
    }

    private fun assignTarget(target: PyExpr, value: PyVal, env: PyEnv, line: Int) {
        when (target) {
            is ExprIdent -> env.set(target.name, value)
            is ExprTuple -> {
                val seq = toIterableList(value, line)
                if (seq.size != target.items.size) {
                    throw PyRuntimeException("ValueError", "Cannot unpack ${seq.size} values into ${target.items.size} targets", line)
                }
                target.items.forEachIndexed { i, t -> assignTarget(t, seq[i], env, line) }
            }
            is ExprList -> {
                val seq = toIterableList(value, line)
                if (seq.size != target.items.size) {
                    throw PyRuntimeException("ValueError", "Cannot unpack ${seq.size} values into ${target.items.size} targets", line)
                }
                target.items.forEachIndexed { i, t -> assignTarget(t, seq[i], env, line) }
            }
            is ExprAttr -> {
                val obj = evalExpr(target.obj, env)
                when (obj) {
                    is PyInstance -> obj.attrs[target.attr] = value
                    is PyClass -> obj.attrs[target.attr] = value
                    is PyModule -> obj.attrs[target.attr] = value
                    else -> throw PyRuntimeException("AttributeError", "Cannot set attribute '${target.attr}' on ${obj.typeName()}", line)
                }
            }
            is ExprSubscript -> {
                val obj = evalExpr(target.obj, env)
                val key = evalExpr(target.index, env)
                when (obj) {
                    is PyList -> {
                        val idx = normalizeIndex(key.asLong().toInt(), obj.items.size)
                        obj.items[idx] = value
                    }
                    is PyDict -> obj.entries[key] = value
                    is PyArray -> {
                        val idx = normalizeIndex(key.asLong().toInt(), obj.data.size)
                        obj.data[idx] = value.asDouble()
                    }
                    else -> throw PyRuntimeException("TypeError", "'${obj.typeName()}' does not support item assignment", line)
                }
            }
            else -> throw PyRuntimeException("SyntaxError", "Invalid assignment target", line)
        }
    }

    fun evalExpr(expr: PyExpr, env: PyEnv): PyVal {
        checkStep(expr.line)
        return when (expr) {
            is ExprLiteral -> expr.value
            is ExprFString -> evalFString(expr.template, env, expr.line)
            is ExprIdent -> env.get(expr.name)
                ?: throw PyRuntimeException("NameError", "name '${expr.name}' is not defined", expr.line)
            is ExprList -> PyList(expr.items.map { evalExpr(it, env) }.toMutableList())
            is ExprTuple -> PyTuple(expr.items.map { evalExpr(it, env) })
            is ExprSet -> PySet(LinkedHashSet(expr.items.map { evalExpr(it, env) }))
            is ExprDict -> {
                val map = LinkedHashMap<PyVal, PyVal>()
                for ((k, v) in expr.entries) map[evalExpr(k, env)] = evalExpr(v, env)
                PyDict(map)
            }
            is ExprListComp -> {
                val out = mutableListOf<PyVal>()
                val compEnv = PyEnv(env)
                for (item in toIterableList(evalExpr(expr.iterable, env), expr.line)) {
                    checkStep(expr.line)
                    assignTarget(expr.target, item, compEnv, expr.line)
                    if (expr.cond == null || evalExpr(expr.cond, compEnv).isTruthy()) {
                        out.add(evalExpr(expr.expr, compEnv))
                    }
                }
                PyList(out)
            }
            is ExprDictComp -> {
                val map = LinkedHashMap<PyVal, PyVal>()
                val compEnv = PyEnv(env)
                for (item in toIterableList(evalExpr(expr.iterable, env), expr.line)) {
                    checkStep(expr.line)
                    assignTarget(expr.target, item, compEnv, expr.line)
                    if (expr.cond == null || evalExpr(expr.cond, compEnv).isTruthy()) {
                        map[evalExpr(expr.keyExpr, compEnv)] = evalExpr(expr.valExpr, compEnv)
                    }
                }
                PyDict(map)
            }
            is ExprLambda -> PyFunction("<lambda>", expr.params, listOf(StmtReturn(expr.body, expr.line)), env)
            is ExprTernary -> if (evalExpr(expr.cond, env).isTruthy()) evalExpr(expr.ifTrue, env) else evalExpr(expr.ifFalse, env)
            is ExprUnary -> {
                val v = evalExpr(expr.operand, env)
                when (expr.op) {
                    TokType.NOT -> PyBool(!v.isTruthy())
                    TokType.MINUS -> when (v) {
                        is PyInt -> PyInt(-v.value)
                        is PyFloat -> PyFloat(-v.value)
                        is PyArray -> PyArray(DoubleArray(v.data.size) { -v.data[it] }, v.shape.copyOf())
                        else -> throw PyRuntimeException("TypeError", "Bad operand for unary -", expr.line)
                    }
                    TokType.PLUS -> v
                    TokType.BITNOT -> PyInt(v.asLong().inv())
                    else -> v
                }
            }
            is ExprBoolOp -> {
                var last: PyVal = PyNone
                for (vExpr in expr.values) {
                    last = evalExpr(vExpr, env)
                    if (expr.op == TokType.AND && !last.isTruthy()) return last
                    if (expr.op == TokType.OR && last.isTruthy()) return last
                }
                last
            }
            is ExprCompare -> {
                var left = evalExpr(expr.left, env)
                for ((op, rightExpr) in expr.ops) {
                    val right = evalExpr(rightExpr, env)
                    if (!evalCompare(left, op, right, expr.line)) return PyBool(false)
                    left = right
                }
                PyBool(true)
            }
            is ExprBinary -> evalBinaryOp(evalExpr(expr.left, env), expr.op, evalExpr(expr.right, env), expr.line)
            is ExprAttr -> getAttribute(evalExpr(expr.obj, env), expr.attr, expr.line)
            is ExprSubscript -> evalSubscript(evalExpr(expr.obj, env), evalExpr(expr.index, env), expr.line)
            is ExprSlice -> evalSlice(
                evalExpr(expr.obj, env),
                expr.start?.let { evalExpr(it, env) },
                expr.stop?.let { evalExpr(it, env) },
                expr.step?.let { evalExpr(it, env) },
                expr.line
            )
            is ExprCall -> {
                val callee = evalExpr(expr.callee, env)
                val posArgs = mutableListOf<PyVal>()
                val kwArgs = LinkedHashMap<String, PyVal>()
                for (arg in expr.args) {
                    when {
                        arg.isDoubleStar -> {
                            val d = evalExpr(arg.expr, env) as? PyDict
                                ?: throw PyRuntimeException("TypeError", "** argument must be a dict", expr.line)
                            for ((k, v) in d.entries) kwArgs[k.toDisplayString()] = v
                        }
                        arg.isStar -> posArgs.addAll(toIterableList(evalExpr(arg.expr, env), expr.line))
                        arg.name != null -> kwArgs[arg.name] = evalExpr(arg.expr, env)
                        else -> posArgs.add(evalExpr(arg.expr, env))
                    }
                }
                callCallable(callee, posArgs, kwArgs, expr.line)
            }
        }
    }

    private fun evalFString(template: String, env: PyEnv, line: Int): PyStr {
        val sb = StringBuilder()
        var i = 0
        while (i < template.length) {
            when {
                template[i] == '{' && i + 1 < template.length && template[i + 1] == '{' -> {
                    sb.append('{'); i += 2
                }
                template[i] == '}' && i + 1 < template.length && template[i + 1] == '}' -> {
                    sb.append('}'); i += 2
                }
                template[i] == '{' -> {
                    var depth = 1
                    var j = i + 1
                    while (j < template.length && depth > 0) {
                        if (template[j] == '{') depth++
                        else if (template[j] == '}') depth--
                        j++
                    }
                    val inner = template.substring(i + 1, (j - 1).coerceAtLeast(i + 1))
                    val colonIdx = findTopLevelColon(inner)
                    val exprStr = if (colonIdx >= 0) inner.substring(0, colonIdx) else inner
                    val fmtSpec = if (colonIdx >= 0) inner.substring(colonIdx + 1) else null
                    val exprVal = evalExpr(PyParser(PyLexer(exprStr).tokenize()).parseExpression(), env)
                    sb.append(formatValue(exprVal, fmtSpec))
                    i = j
                }
                else -> {
                    sb.append(template[i]); i++
                }
            }
        }
        return PyStr(sb.toString())
    }

    private fun findTopLevelColon(s: String): Int {
        var depth = 0
        for (i in s.indices) {
            when (s[i]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
                ':' -> if (depth == 0) return i
            }
        }
        return -1
    }

    internal fun formatValue(v: PyVal, spec: String?): String {
        if (spec.isNullOrBlank()) return v.toDisplayString()
        val s = spec.trim()
        val dotF = Regex("""^(?:(\d*))?\.(\d+)([fFeE%])$""").find(s)
        if (dotF != null) {
            val width = dotF.groupValues[1].toIntOrNull() ?: 0
            val prec = dotF.groupValues[2].toInt()
            val kind = dotF.groupValues[3]
            val d = v.asDouble()
            val formatted = when (kind) {
                "%" -> String.format("%.${prec}f%%", d * 100.0)
                "e", "E" -> String.format("%.${prec}${kind}", d)
                else -> String.format("%.${prec}f", d)
            }
            return if (width > formatted.length) formatted.padStart(width) else formatted
        }
        if (s == ",") {
            return when (v) {
                is PyInt -> String.format("%,d", v.value)
                is PyFloat -> String.format("%,.2f", v.value)
                else -> v.toDisplayString()
            }
        }
        val commaFloat = Regex("""^,\.(\d+)f$""").find(s)
        if (commaFloat != null) {
            val prec = commaFloat.groupValues[1].toInt()
            return String.format("%,.${prec}f", v.asDouble())
        }
        val padInt = Regex("""^0?(\d+)d$""").find(s)
        if (padInt != null) {
            val w = padInt.groupValues[1].toInt()
            return if (s.startsWith("0")) String.format("%0${w}d", v.asLong()) else String.format("%${w}d", v.asLong())
        }
        val align = Regex("""^([<>^])(\d+)$""").find(s)
        if (align != null) {
            val dir = align.groupValues[1]
            val w = align.groupValues[2].toInt()
            val str = v.toDisplayString()
            return when (dir) {
                "<" -> str.padEnd(w)
                ">" -> str.padStart(w)
                else -> {
                    val pad = (w - str.length).coerceAtLeast(0)
                    " ".repeat(pad / 2) + str + " ".repeat(pad - pad / 2)
                }
            }
        }
        return v.toDisplayString()
    }

    fun callCallable(callee: PyVal, args: List<PyVal>, kwargs: Map<String, PyVal>, line: Int = 0): PyVal {
        return when (callee) {
            is PyBuiltin -> callee.fn(args, kwargs, this)
            is PyFunction -> {
                val callEnv = PyEnv(callee.closure)
                val fullArgs = if (callee.boundSelf != null) listOf(callee.boundSelf) + args else args
                var argIdx = 0
                val usedKw = mutableSetOf<String>()

                for (param in callee.params) {
                    when {
                        param.isVarArgs -> {
                            val rest = if (argIdx < fullArgs.size) fullArgs.subList(argIdx, fullArgs.size) else emptyList()
                            argIdx = fullArgs.size
                            callEnv.set(param.name, PyTuple(rest))
                        }
                        param.isKwArgs -> {
                            val extra = LinkedHashMap<PyVal, PyVal>()
                            for ((k, v) in kwargs) {
                                if (k !in usedKw) extra[PyStr(k)] = v
                            }
                            callEnv.set(param.name, PyDict(extra))
                        }
                        argIdx < fullArgs.size -> {
                            callEnv.set(param.name, fullArgs[argIdx++])
                        }
                        kwargs.containsKey(param.name) -> {
                            usedKw.add(param.name)
                            callEnv.set(param.name, kwargs[param.name]!!)
                        }
                        param.defaultExpr != null -> {
                            callEnv.set(param.name, evalExpr(param.defaultExpr, callee.closure))
                        }
                        else -> throw PyRuntimeException("TypeError", "${callee.name}() missing required argument '${param.name}'", line)
                    }
                }
                try {
                    execBlock(callee.body, callEnv)
                    PyNone
                } catch (r: ReturnSignal) {
                    r.value
                }
            }
            is PyClass -> {
                val instance = PyInstance(callee)
                val initFn = callee.lookup("__init__")
                if (initFn is PyFunction) {
                    val bound = PyFunction(initFn.name, initFn.params, initFn.body, initFn.closure, boundSelf = instance)
                    callCallable(bound, args, kwargs, line)
                } else if (initFn is PyBuiltin) {
                    initFn.fn(listOf(instance) + args, kwargs, this)
                }
                instance
            }
            else -> throw PyRuntimeException("TypeError", "'${callee.typeName()}' object is not callable", line)
        }
    }

    private fun evalBinaryOp(left: PyVal, op: TokType, right: PyVal, line: Int): PyVal {
        // NumPy vectorized operations
        if (left is PyArray || right is PyArray) {
            return PySciLib.evalArrayBinary(left, op, right, line)
        }
        // String operations
        if (left is PyStr && op == TokType.PLUS && right is PyStr) return PyStr(left.value + right.value)
        if (left is PyStr && op == TokType.STAR && right is PyInt) return PyStr(left.value.repeat(right.value.toInt().coerceAtLeast(0)))
        if (left is PyInt && op == TokType.STAR && right is PyStr) return PyStr(right.value.repeat(left.value.toInt().coerceAtLeast(0)))
        if (left is PyList && op == TokType.PLUS && right is PyList) return PyList((left.items + right.items).toMutableList())
        if (left is PyList && op == TokType.STAR && right is PyInt) {
            val out = mutableListOf<PyVal>()
            repeat(right.value.toInt().coerceAtLeast(0)) { out.addAll(left.items) }
            return PyList(out)
        }
        if (left is PyTuple && op == TokType.PLUS && right is PyTuple) return PyTuple(left.items + right.items)

        // Numeric
        val bothInt = (left is PyInt || left is PyBool) && (right is PyInt || right is PyBool)
        if (bothInt && op != TokType.SLASH) {
            val a = left.asLong()
            val b = right.asLong()
            return when (op) {
                TokType.PLUS -> PyInt(a + b)
                TokType.MINUS -> PyInt(a - b)
                TokType.STAR -> PyInt(a * b)
                TokType.DOUBLESLASH -> {
                    if (b == 0L) throw PyRuntimeException("ZeroDivisionError", "integer division by zero", line)
                    PyInt(Math.floorDiv(a, b))
                }
                TokType.PERCENT -> {
                    if (b == 0L) throw PyRuntimeException("ZeroDivisionError", "modulo by zero", line)
                    PyInt(Math.floorMod(a, b))
                }
                TokType.DOUBLESTAR -> {
                    if (b < 0) PyFloat(a.toDouble().pow(b.toDouble()))
                    else PyInt(a.toDouble().pow(b.toDouble()).toLong())
                }
                TokType.BITAND -> PyInt(a and b)
                TokType.BITOR -> PyInt(a or b)
                TokType.BITXOR -> PyInt(a xor b)
                TokType.LSHIFT -> PyInt(a shl b.toInt())
                TokType.RSHIFT -> PyInt(a shr b.toInt())
                else -> throw PyRuntimeException("TypeError", "Unsupported op $op", line)
            }
        }

        val a = left.asDouble()
        val b = right.asDouble()
        return when (op) {
            TokType.PLUS -> PyFloat(a + b)
            TokType.MINUS -> PyFloat(a - b)
            TokType.STAR -> PyFloat(a * b)
            TokType.SLASH -> {
                if (b == 0.0) throw PyRuntimeException("ZeroDivisionError", "float division by zero", line)
                PyFloat(a / b)
            }
            TokType.DOUBLESLASH -> {
                if (b == 0.0) throw PyRuntimeException("ZeroDivisionError", "float floor division by zero", line)
                PyFloat(floor(a / b))
            }
            TokType.PERCENT -> {
                if (b == 0.0) throw PyRuntimeException("ZeroDivisionError", "float modulo by zero", line)
                PyFloat(((a % b) + b) % b)
            }
            TokType.DOUBLESTAR -> PyFloat(a.pow(b))
            else -> throw PyRuntimeException("TypeError", "Unsupported operand types for $op", line)
        }
    }

    private fun evalCompare(left: PyVal, op: String, right: PyVal, line: Int): Boolean {
        return when (op) {
            "==" -> pyEquals(left, right)
            "!=" -> !pyEquals(left, right)
            "is" -> left === right || (left is PyNone && right is PyNone) || (left is PyBool && right is PyBool && left.value == right.value)
            "is not" -> !(left === right || (left is PyNone && right is PyNone))
            "in" -> pyContains(right, left, line)
            "not in" -> !pyContains(right, left, line)
            "<" -> pyCompareTo(left, right, line) < 0
            ">" -> pyCompareTo(left, right, line) > 0
            "<=" -> pyCompareTo(left, right, line) <= 0
            ">=" -> pyCompareTo(left, right, line) >= 0
            else -> false
        }
    }

    private fun pyEquals(a: PyVal, b: PyVal): Boolean {
        if ((a is PyInt || a is PyFloat || a is PyBool) && (b is PyInt || b is PyFloat || b is PyBool)) {
            return a.asDouble() == b.asDouble()
        }
        return a == b
    }

    private fun pyCompareTo(a: PyVal, b: PyVal, line: Int): Int {
        if ((a is PyInt || a is PyFloat || a is PyBool) && (b is PyInt || b is PyFloat || b is PyBool)) {
            return a.asDouble().compareTo(b.asDouble())
        }
        if (a is PyStr && b is PyStr) return a.value.compareTo(b.value)
        throw PyRuntimeException("TypeError", "Cannot compare ${a.typeName()} and ${b.typeName()}", line)
    }

    private fun pyContains(container: PyVal, item: PyVal, line: Int): Boolean = when (container) {
        is PyStr -> item is PyStr && container.value.contains(item.value)
        is PyList -> container.items.any { pyEquals(it, item) }
        is PyTuple -> container.items.any { pyEquals(it, item) }
        is PySet -> container.items.any { pyEquals(it, item) }
        is PyDict -> container.entries.keys.any { pyEquals(it, item) }
        is PyRange -> container.toList().any { pyEquals(it, item) }
        else -> throw PyRuntimeException("TypeError", "argument of type '${container.typeName()}' is not iterable", line)
    }

    private fun normalizeIndex(idx: Int, size: Int): Int {
        val i = if (idx < 0) size + idx else idx
        if (i !in 0 until size) throw PyRuntimeException("IndexError", "index $idx out of range (size $size)")
        return i
    }

    private fun evalSubscript(obj: PyVal, key: PyVal, line: Int): PyVal = when (obj) {
        is PyList -> obj.items[normalizeIndex(key.asLong().toInt(), obj.items.size)]
        is PyTuple -> obj.items[normalizeIndex(key.asLong().toInt(), obj.items.size)]
        is PyStr -> PyStr(obj.value[normalizeIndex(key.asLong().toInt(), obj.value.length)].toString())
        is PyDict -> obj.entries[key] ?: throw PyRuntimeException("KeyError", key.toRepr(), line)
        is PyArray -> {
            if (key is PyTuple && key.items.size == 2 && obj.shape.size >= 2) {
                val r = normalizeIndex(key.items[0].asLong().toInt(), obj.shape[0])
                val c = normalizeIndex(key.items[1].asLong().toInt(), obj.shape[1])
                PyFloat(obj.data[r * obj.shape[1] + c])
            } else {
                PyFloat(obj.data[normalizeIndex(key.asLong().toInt(), obj.data.size)])
            }
        }
        is PyDataFrame -> {
            val colName = key.toDisplayString()
            val cIdx = obj.columns.indexOf(colName)
            if (cIdx < 0) throw PyRuntimeException("KeyError", "'$colName'", line)
            PyList(obj.rows.map { it.getOrElse(cIdx) { PyNone } }.toMutableList())
        }
        else -> throw PyRuntimeException("TypeError", "'${obj.typeName()}' object is not subscriptable", line)
    }

    private fun evalSlice(obj: PyVal, startV: PyVal?, stopV: PyVal?, stepV: PyVal?, line: Int): PyVal {
        val step = stepV?.asLong()?.toInt() ?: 1
        if (step == 0) throw PyRuntimeException("ValueError", "slice step cannot be zero", line)

        fun <T> sliceList(src: List<T>): List<T> {
            val n = src.size
            val s = if (startV == null || startV is PyNone) (if (step > 0) 0 else n - 1)
            else startV.asLong().toInt().let { if (it < 0) (n + it).coerceAtLeast(0) else it.coerceAtMost(n) }
            val e = if (stopV == null || stopV is PyNone) (if (step > 0) n else -1)
            else stopV.asLong().toInt().let { if (it < 0) (n + it).coerceAtLeast(-1) else it.coerceAtMost(n) }
            val res = mutableListOf<T>()
            var i = s
            if (step > 0) {
                while (i < e && i < n) { if (i >= 0) res.add(src[i]); i += step }
            } else {
                while (i > e && i >= 0) { if (i < n) res.add(src[i]); i += step }
            }
            return res
        }

        return when (obj) {
            is PyList -> PyList(sliceList(obj.items).toMutableList())
            is PyTuple -> PyTuple(sliceList(obj.items))
            is PyStr -> PyStr(sliceList(obj.value.toList()).joinToString(""))
            is PyArray -> {
                val list = sliceList(obj.data.toList())
                PyArray(list.toDoubleArray(), intArrayOf(list.size))
            }
            else -> throw PyRuntimeException("TypeError", "'${obj.typeName()}' is not sliceable", line)
        }
    }

    fun toIterableList(v: PyVal, line: Int = 0): List<PyVal> = when (v) {
        is PyList -> v.items.toList()
        is PyTuple -> v.items
        is PySet -> v.items.toList()
        is PyRange -> v.toList()
        is PyStr -> v.value.map { PyStr(it.toString()) }
        is PyDict -> v.entries.keys.toList()
        is PyArray -> v.data.map { PyFloat(it) }
        else -> throw PyRuntimeException("TypeError", "'${v.typeName()}' object is not iterable", line)
    }

    private fun getAttribute(obj: PyVal, attr: String, line: Int): PyVal {
        when (obj) {
            is PyModule -> return obj.attrs[attr]
                ?: throw PyRuntimeException("AttributeError", "module '${obj.name}' has no attribute '$attr'", line)
            is PyClass -> return obj.lookup(attr)
                ?: throw PyRuntimeException("AttributeError", "class '${obj.name}' has no attribute '$attr'", line)
            is PyInstance -> {
                if (obj.attrs.containsKey(attr)) return obj.attrs[attr]!!
                val clsAttr = obj.pyClass.lookup(attr)
                    ?: throw PyRuntimeException("AttributeError", "'${obj.pyClass.name}' object has no attribute '$attr'", line)
                return when (clsAttr) {
                    is PyFunction -> PyFunction(clsAttr.name, clsAttr.params, clsAttr.body, clsAttr.closure, boundSelf = obj)
                    is PyBuiltin -> PyBuiltin(clsAttr.name) { a, kw, i -> clsAttr.fn(listOf(obj) + a, kw, i) }
                    else -> clsAttr
                }
            }
            is PyStr -> return getStrMethod(obj, attr, line)
            is PyList -> return getListMethod(obj, attr, line)
            is PyDict -> return getDictMethod(obj, attr, line)
            is PySet -> return getSetMethod(obj, attr, line)
            is PyArray -> return PySciLib.getArrayAttr(obj, attr, line)
            is PyDataFrame -> return PySciLib.getDataFrameAttr(obj, attr, line)
            else -> throw PyRuntimeException("AttributeError", "'${obj.typeName()}' object has no attribute '$attr'", line)
        }
    }

    private fun getStrMethod(s: PyStr, attr: String, line: Int): PyVal = when (attr) {
        "upper" -> PyBuiltin("upper") { _, _, _ -> PyStr(s.value.uppercase()) }
        "lower" -> PyBuiltin("lower") { _, _, _ -> PyStr(s.value.lowercase()) }
        "title" -> PyBuiltin("title") { _, _, _ -> PyStr(s.value.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }) }
        "strip" -> PyBuiltin("strip") { a, _, _ ->
            if (a.isEmpty()) PyStr(s.value.trim()) else PyStr(s.value.trim(*a[0].toDisplayString().toCharArray()))
        }
        "lstrip" -> PyBuiltin("lstrip") { _, _, _ -> PyStr(s.value.trimStart()) }
        "rstrip" -> PyBuiltin("rstrip") { _, _, _ -> PyStr(s.value.trimEnd()) }
        "split" -> PyBuiltin("split") { a, _, _ ->
            val parts = if (a.isEmpty()) s.value.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            else s.value.split(a[0].toDisplayString())
            PyList(parts.map { PyStr(it) }.toMutableList())
        }
        "splitlines" -> PyBuiltin("splitlines") { _, _, _ -> PyList(s.value.lines().map { PyStr(it) }.toMutableList()) }
        "join" -> PyBuiltin("join") { a, _, interp ->
            val seq = interp.toIterableList(a[0], line)
            PyStr(seq.joinToString(s.value) { it.toDisplayString() })
        }
        "replace" -> PyBuiltin("replace") { a, _, _ ->
            PyStr(s.value.replace(a[0].toDisplayString(), a[1].toDisplayString()))
        }
        "startswith" -> PyBuiltin("startswith") { a, _, _ -> PyBool(s.value.startsWith(a[0].toDisplayString())) }
        "endswith" -> PyBuiltin("endswith") { a, _, _ -> PyBool(s.value.endsWith(a[0].toDisplayString())) }
        "find" -> PyBuiltin("find") { a, _, _ -> PyInt(s.value.indexOf(a[0].toDisplayString()).toLong()) }
        "count" -> PyBuiltin("count") { a, _, _ ->
            val sub = a[0].toDisplayString()
            if (sub.isEmpty()) PyInt((s.value.length + 1).toLong())
            else PyInt(s.value.windowed(sub.length).count { it == sub }.toLong())
        }
        "isdigit" -> PyBuiltin("isdigit") { _, _, _ -> PyBool(s.value.isNotEmpty() && s.value.all { it.isDigit() }) }
        "isalpha" -> PyBuiltin("isalpha") { _, _, _ -> PyBool(s.value.isNotEmpty() && s.value.all { it.isLetter() }) }
        "format" -> PyBuiltin("format") { a, kw, _ ->
            var res = s.value
            a.forEachIndexed { idx, v ->
                res = res.replaceFirst("{}", v.toDisplayString()).replace("{$idx}", v.toDisplayString())
            }
            for ((k, v) in kw) res = res.replace("{$k}", v.toDisplayString())
            PyStr(res)
        }
        else -> throw PyRuntimeException("AttributeError", "'str' object has no attribute '$attr'", line)
    }

    private fun getListMethod(list: PyList, attr: String, line: Int): PyVal = when (attr) {
        "append" -> PyBuiltin("append") { a, _, _ -> list.items.add(a[0]); PyNone }
        "extend" -> PyBuiltin("extend") { a, _, interp -> list.items.addAll(interp.toIterableList(a[0], line)); PyNone }
        "insert" -> PyBuiltin("insert") { a, _, _ ->
            val idx = a[0].asLong().toInt().coerceIn(0, list.items.size)
            list.items.add(idx, a[1]); PyNone
        }
        "pop" -> PyBuiltin("pop") { a, _, _ ->
            val idx = if (a.isEmpty()) list.items.lastIndex else normalizeIndex(a[0].asLong().toInt(), list.items.size)
            list.items.removeAt(idx)
        }
        "remove" -> PyBuiltin("remove") { a, _, _ ->
            val idx = list.items.indexOfFirst { pyEquals(it, a[0]) }
            if (idx < 0) throw PyRuntimeException("ValueError", "list.remove(x): x not in list", line)
            list.items.removeAt(idx); PyNone
        }
        "clear" -> PyBuiltin("clear") { _, _, _ -> list.items.clear(); PyNone }
        "copy" -> PyBuiltin("copy") { _, _, _ -> PyList(list.items.toMutableList()) }
        "reverse" -> PyBuiltin("reverse") { _, _, _ -> list.items.reverse(); PyNone }
        "sort" -> PyBuiltin("sort") { _, kw, interp ->
            val reverse = kw["reverse"]?.isTruthy() == true
            val keyFn = kw["key"]
            list.items.sortWith { x, y ->
                val vx = if (keyFn != null) interp.callCallable(keyFn, listOf(x), emptyMap(), line) else x
                val vy = if (keyFn != null) interp.callCallable(keyFn, listOf(y), emptyMap(), line) else y
                val cmp = pyCompareTo(vx, vy, line)
                if (reverse) -cmp else cmp
            }
            PyNone
        }
        "index" -> PyBuiltin("index") { a, _, _ ->
            val idx = list.items.indexOfFirst { pyEquals(it, a[0]) }
            if (idx < 0) throw PyRuntimeException("ValueError", "item not in list", line)
            PyInt(idx.toLong())
        }
        "count" -> PyBuiltin("count") { a, _, _ -> PyInt(list.items.count { pyEquals(it, a[0]) }.toLong()) }
        else -> throw PyRuntimeException("AttributeError", "'list' object has no attribute '$attr'", line)
    }

    private fun getDictMethod(dict: PyDict, attr: String, line: Int): PyVal = when (attr) {
        "get" -> PyBuiltin("get") { a, _, _ -> dict.entries[a[0]] ?: a.getOrElse(1) { PyNone } }
        "keys" -> PyBuiltin("keys") { _, _, _ -> PyList(dict.entries.keys.toMutableList()) }
        "values" -> PyBuiltin("values") { _, _, _ -> PyList(dict.entries.values.toMutableList()) }
        "items" -> PyBuiltin("items") { _, _, _ -> PyList(dict.entries.map { PyTuple(listOf(it.key, it.value)) }.toMutableList()) }
        "pop" -> PyBuiltin("pop") { a, _, _ -> dict.entries.remove(a[0]) ?: a.getOrElse(1) { throw PyRuntimeException("KeyError", a[0].toRepr(), line) } }
        "update" -> PyBuiltin("update") { a, _, _ ->
            val other = a[0] as? PyDict ?: throw PyRuntimeException("TypeError", "Expected dict", line)
            dict.entries.putAll(other.entries); PyNone
        }
        "clear" -> PyBuiltin("clear") { _, _, _ -> dict.entries.clear(); PyNone }
        "copy" -> PyBuiltin("copy") { _, _, _ -> PyDict(LinkedHashMap(dict.entries)) }
        else -> throw PyRuntimeException("AttributeError", "'dict' object has no attribute '$attr'", line)
    }

    private fun getSetMethod(set: PySet, attr: String, line: Int): PyVal = when (attr) {
        "add" -> PyBuiltin("add") { a, _, _ -> set.items.add(a[0]); PyNone }
        "remove", "discard" -> PyBuiltin(attr) { a, _, _ -> set.items.remove(a[0]); PyNone }
        "union" -> PyBuiltin("union") { a, _, interp ->
            PySet(LinkedHashSet(set.items + interp.toIterableList(a[0], line)))
        }
        "intersection" -> PyBuiltin("intersection") { a, _, interp ->
            val o = interp.toIterableList(a[0], line).toSet()
            PySet(LinkedHashSet(set.items.filter { it in o }))
        }
        else -> throw PyRuntimeException("AttributeError", "'set' object has no attribute '$attr'", line)
    }

    private fun resolveModule(name: String, line: Int): PyModule {
        loadedModules[name]?.let { return it }

        // Check bundled stdlib, scientific, and 3D libraries
        val built = PyStdLib.createModule(name, this)
            ?: PySciLib.createModule(name, this)
            ?: Py3DLib.createModule(name, this)
        if (built != null) {
            loadedModules[name] = built
            return built
        }

        // Check workspace `.py` modules!
        val workspace = workspaceFilesProvider()
        val matchingFile = workspace["$name.py"] ?: workspace.entries.firstOrNull {
            it.key.removeSuffix(".py").equals(name, ignoreCase = true)
        }?.value
        if (matchingFile != null) {
            val modEnv = PyEnv(globalEnv)
            val tokens = PyLexer(matchingFile).tokenize()
            val stmts = PyParser(tokens).parseModule()
            execBlock(stmts, modEnv)
            val mod = PyModule(name, LinkedHashMap(modEnv.vars))
            loadedModules[name] = mod
            return mod
        }

        throw PyRuntimeException(
            "ModuleNotFoundError",
            "No module named '$name'. Run 'pip list' in Terminal to view bundled libraries or create '$name.py' in workspace.",
            line
        )
    }

    private fun installBuiltins() {
        val g = globalEnv
        g.set("print", PyBuiltin("print") { args, kwargs, interp ->
            val sep = kwargs["sep"]?.toDisplayString() ?: " "
            val end = kwargs["end"]?.toDisplayString() ?: "\n"
            val line = args.joinToString(sep) { it.toDisplayString() }
            if (line.startsWith("METRIC|")) {
                interp.emitStdout(line)
            } else {
                interp.emitStdout(line + end)
            }
            PyNone
        })
        g.set("len", PyBuiltin("len") { args, _, _ ->
            val sz = when (val v = args[0]) {
                is PyStr -> v.value.length
                is PyList -> v.items.size
                is PyTuple -> v.items.size
                is PyDict -> v.entries.size
                is PySet -> v.items.size
                is PyRange -> v.toList().size
                is PyArray -> if (v.shape.isNotEmpty()) v.shape[0] else v.data.size
                is PyDataFrame -> v.rows.size
                else -> throw PyRuntimeException("TypeError", "object of type '${v.typeName()}' has no len()")
            }
            PyInt(sz.toLong())
        })
        g.set("range", PyBuiltin("range") { args, _, _ ->
            when (args.size) {
                1 -> PyRange(0, args[0].asLong(), 1)
                2 -> PyRange(args[0].asLong(), args[1].asLong(), 1)
                3 -> PyRange(args[0].asLong(), args[1].asLong(), args[2].asLong())
                else -> throw PyRuntimeException("TypeError", "range expected 1 to 3 arguments")
            }
        })
        g.set("enumerate", PyBuiltin("enumerate") { args, _, interp ->
            val start = args.getOrNull(1)?.asLong() ?: 0L
            val seq = interp.toIterableList(args[0])
            PyList(seq.mapIndexed { idx, v -> PyTuple(listOf(PyInt(start + idx), v)) }.toMutableList())
        })
        g.set("zip", PyBuiltin("zip") { args, _, interp ->
            val lists = args.map { interp.toIterableList(it) }
            val minLen = lists.minOfOrNull { it.size } ?: 0
            PyList((0 until minLen).map { i -> PyTuple(lists.map { it[i] }) }.toMutableList())
        })
        g.set("map", PyBuiltin("map") { args, _, interp ->
            val fn = args[0]
            val seq = interp.toIterableList(args[1])
            PyList(seq.map { interp.callCallable(fn, listOf(it), emptyMap()) }.toMutableList())
        })
        g.set("filter", PyBuiltin("filter") { args, _, interp ->
            val fn = args[0]
            val seq = interp.toIterableList(args[1])
            PyList(seq.filter {
                if (fn is PyNone) it.isTruthy() else interp.callCallable(fn, listOf(it), emptyMap()).isTruthy()
            }.toMutableList())
        })
        g.set("sum", PyBuiltin("sum") { args, _, interp ->
            val seq = interp.toIterableList(args[0])
            var total = args.getOrNull(1)?.asDouble() ?: 0.0
            var allInt = args.getOrNull(1) !is PyFloat
            for (v in seq) {
                if (v is PyFloat) allInt = false
                total += v.asDouble()
            }
            if (allInt) PyInt(total.toLong()) else PyFloat(total)
        })
        g.set("min", PyBuiltin("min") { args, _, interp ->
            val seq = if (args.size == 1) interp.toIterableList(args[0]) else args
            seq.minWith { a, b -> pyCompareTo(a, b, 0) }
        })
        g.set("max", PyBuiltin("max") { args, _, interp ->
            val seq = if (args.size == 1) interp.toIterableList(args[0]) else args
            seq.maxWith { a, b -> pyCompareTo(a, b, 0) }
        })
        g.set("abs", PyBuiltin("abs") { args, _, _ ->
            when (val v = args[0]) {
                is PyInt -> PyInt(abs(v.value))
                is PyFloat -> PyFloat(abs(v.value))
                else -> PyFloat(abs(v.asDouble()))
            }
        })
        g.set("round", PyBuiltin("round") { args, _, _ ->
            val x = args[0].asDouble()
            val digits = args.getOrNull(1)?.asLong()?.toInt()
            if (digits == null) PyInt(round(x).toLong())
            else {
                val factor = 10.0.pow(digits)
                PyFloat(round(x * factor) / factor)
            }
        })
        g.set("pow", PyBuiltin("pow") { args, _, _ -> PyFloat(args[0].asDouble().pow(args[1].asDouble())) })
        g.set("divmod", PyBuiltin("divmod") { args, _, _ ->
            val a = args[0].asLong(); val b = args[1].asLong()
            PyTuple(listOf(PyInt(Math.floorDiv(a, b)), PyInt(Math.floorMod(a, b))))
        })
        g.set("sorted", PyBuiltin("sorted") { args, kw, interp ->
            val seq = interp.toIterableList(args[0]).toMutableList()
            val reverse = kw["reverse"]?.isTruthy() == true
            val keyFn = kw["key"]
            seq.sortWith { x, y ->
                val vx = if (keyFn != null) interp.callCallable(keyFn, listOf(x), emptyMap()) else x
                val vy = if (keyFn != null) interp.callCallable(keyFn, listOf(y), emptyMap()) else y
                val cmp = pyCompareTo(vx, vy, 0)
                if (reverse) -cmp else cmp
            }
            PyList(seq)
        })
        g.set("reversed", PyBuiltin("reversed") { args, _, interp ->
            PyList(interp.toIterableList(args[0]).reversed().toMutableList())
        })
        g.set("any", PyBuiltin("any") { args, _, interp -> PyBool(interp.toIterableList(args[0]).any { it.isTruthy() }) })
        g.set("all", PyBuiltin("all") { args, _, interp -> PyBool(interp.toIterableList(args[0]).all { it.isTruthy() }) })
        g.set("int", PyBuiltin("int") { args, _, _ ->
            if (args.isEmpty()) PyInt(0)
            else when (val v = args[0]) {
                is PyStr -> PyInt(v.value.trim().toLong(args.getOrNull(1)?.asLong()?.toInt() ?: 10))
                else -> PyInt(v.asLong())
            }
        })
        g.set("float", PyBuiltin("float") { args, _, _ ->
            if (args.isEmpty()) PyFloat(0.0)
            else when (val v = args[0]) {
                is PyStr -> PyFloat(v.value.trim().toDouble())
                else -> PyFloat(v.asDouble())
            }
        })
        g.set("str", PyBuiltin("str") { args, _, _ -> PyStr(args.firstOrNull()?.toDisplayString() ?: "") })
        g.set("bool", PyBuiltin("bool") { args, _, _ -> PyBool(args.firstOrNull()?.isTruthy() == true) })
        g.set("list", PyBuiltin("list") { args, _, interp ->
            if (args.isEmpty()) PyList() else PyList(interp.toIterableList(args[0]).toMutableList())
        })
        g.set("tuple", PyBuiltin("tuple") { args, _, interp ->
            if (args.isEmpty()) PyTuple(emptyList()) else PyTuple(interp.toIterableList(args[0]))
        })
        g.set("set", PyBuiltin("set") { args, _, interp ->
            if (args.isEmpty()) PySet() else PySet(LinkedHashSet(interp.toIterableList(args[0])))
        })
        g.set("dict", PyBuiltin("dict") { args, kw, _ ->
            val d = LinkedHashMap<PyVal, PyVal>()
            if (args.isNotEmpty() && args[0] is PyDict) d.putAll((args[0] as PyDict).entries)
            for ((k, v) in kw) d[PyStr(k)] = v
            PyDict(d)
        })
        g.set("type", PyBuiltin("type") { args, _, _ -> PyStr("<class '${args[0].typeName()}'>") })
        g.set("isinstance", PyBuiltin("isinstance") { args, _, _ ->
            val obj = args[0]; val t = args[1]
            val ok = when (t) {
                is PyClass -> obj is PyInstance && (obj.pyClass == t || t in obj.pyClass.bases)
                is PyBuiltin -> obj.typeName().equals(t.name, ignoreCase = true)
                else -> false
            }
            PyBool(ok)
        })
        g.set("repr", PyBuiltin("repr") { args, _, _ -> PyStr(args[0].toRepr()) })
        g.set("chr", PyBuiltin("chr") { args, _, _ -> PyStr(args[0].asLong().toInt().toChar().toString()) })
        g.set("ord", PyBuiltin("ord") { args, _, _ -> PyInt(args[0].toDisplayString().first().code.toLong()) })
        g.set("hex", PyBuiltin("hex") { args, _, _ -> PyStr("0x" + args[0].asLong().toString(16)) })
        g.set("bin", PyBuiltin("bin") { args, _, _ -> PyStr("0b" + args[0].asLong().toString(2)) })
        g.set("oct", PyBuiltin("oct") { args, _, _ -> PyStr("0o" + args[0].asLong().toString(8)) })
        g.set("dir", PyBuiltin("dir") { args, _, _ ->
            val names = when (val v = args.firstOrNull()) {
                is PyModule -> v.attrs.keys.sorted()
                is PyInstance -> (v.attrs.keys + v.pyClass.attrs.keys).sorted()
                is PyClass -> v.attrs.keys.sorted()
                else -> g.vars.keys.sorted()
            }
            PyList(names.map { PyStr(it) }.toMutableList())
        })
        g.set("hasattr", PyBuiltin("hasattr") { args, _, _ ->
            val name = args[1].toDisplayString()
            val has = when (val o = args[0]) {
                is PyInstance -> o.attrs.containsKey(name) || o.pyClass.lookup(name) != null
                is PyModule -> o.attrs.containsKey(name)
                else -> false
            }
            PyBool(has)
        })
        g.set("getattr", PyBuiltin("getattr") { args, _, _ ->
            val name = args[1].toDisplayString()
            try { getAttribute(args[0], name, 0) } catch (_: Exception) { args.getOrElse(2) { PyNone } }
        })
        g.set("setattr", PyBuiltin("setattr") { args, _, _ ->
            val o = args[0]; val name = args[1].toDisplayString(); val v = args[2]
            if (o is PyInstance) o.attrs[name] = v
            PyNone
        })
        g.set("format", PyBuiltin("format") { args, _, interp ->
            PyStr(interp.formatValue(args[0], args.getOrNull(1)?.toDisplayString()))
        })
        g.set("input", PyBuiltin("input") { args, _, interp ->
            val prompt = args.firstOrNull()?.toDisplayString() ?: ""
            if (prompt.isNotEmpty()) interp.emitStdout("$prompt\n")
            PyStr("")
        })
        g.set("Exception", PyBuiltin("Exception") { args, _, _ -> PyStr(args.firstOrNull()?.toDisplayString() ?: "Exception") })
        g.set("ValueError", PyBuiltin("ValueError") { args, _, _ -> PyStr(args.firstOrNull()?.toDisplayString() ?: "ValueError") })
        g.set("TypeError", PyBuiltin("TypeError") { args, _, _ -> PyStr(args.firstOrNull()?.toDisplayString() ?: "TypeError") })
    }
}
