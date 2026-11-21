package com.example.python

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.*
import kotlin.random.Random

data class PyLibraryInfo(
    val name: String,
    val version: String,
    val category: String,
    val description: String,
    val sampleImport: String
)

object PythonLibraryCatalog {
    val libraries: List<PyLibraryInfo> = listOf(
        // 3D & Graphics
        PyLibraryInfo("py3d", "2.4.0", "3D & CAD", "GPU-accelerated OpenGL ES 3D modeling, primitives, parametric surfaces, gears, and OBJ/STL export", "import py3d"),
        PyLibraryInfo("trimesh", "4.3.1", "3D & CAD", "Triangular mesh creation, primitives, and scene assembly for OpenGL ES viewport", "import trimesh"),
        PyLibraryInfo("cadquery", "2.4.0", "3D & CAD", "Parametric CAD Workplane API (box, sphere, cylinder, torus, gear)", "import cadquery as cq"),
        PyLibraryInfo("open3d", "0.18.0", "3D & CAD", "3D geometry creation and point-cloud / mesh visualization", "import open3d as o3d"),
        // Scientific & Data
        PyLibraryInfo("numpy", "1.26.4", "Scientific", "N-dimensional arrays, linear algebra, vectorized math, linspace, meshgrid, statistics", "import numpy as np"),
        PyLibraryInfo("pandas", "2.2.2", "Data Science", "DataFrames, Series, describe, groupby, sorting, and CSV summaries", "import pandas as pd"),
        PyLibraryInfo("matplotlib", "3.9.0", "Visualization", "Interactive line, scatter, bar, and histogram plots rendered directly on Canvas", "import matplotlib.pyplot as plt"),
        PyLibraryInfo("scipy", "1.13.1", "Scientific", "Scientific stats, numerical integration, optimization, and signal processing", "import scipy"),
        PyLibraryInfo("sympy", "1.13.0", "Symbolic Math", "Symbolic algebra, derivatives, expansion, and polynomial equation solving", "import sympy as sp"),
        PyLibraryInfo("sklearn", "1.5.0", "Machine Learning", "LinearRegression, KMeans clustering, StandardScaler, train_test_split, metrics", "import sklearn"),
        PyLibraryInfo("networkx", "3.3", "Graph Theory", "Graph construction, shortest_path (Dijkstra/BFS), degrees, and topology analysis", "import networkx as nx"),
        PyLibraryInfo("tabulate", "0.9.0", "Formatting", "Pretty-print tabular data in terminal and console output", "from tabulate import tabulate"),
        // Networking & Web
        PyLibraryInfo("requests", "2.32.3", "Networking", "Real HTTP GET/POST client powered by OkHttp with JSON response parsing", "import requests"),
        PyLibraryInfo("urllib.parse", "3.12", "Networking", "URL encoding, decoding, and query string parsing", "import urllib.parse"),
        // Standard Library
        PyLibraryInfo("math", "3.12", "StdLib", "Full trigonometric, logarithmic, combinatorial, and floating-point math", "import math"),
        PyLibraryInfo("cmath", "3.12", "StdLib", "Complex math functions and phase/polar conversions", "import cmath"),
        PyLibraryInfo("random", "3.12", "StdLib", "PRNG distributions: uniform, gauss, choice, sample, shuffle, randint", "import random"),
        PyLibraryInfo("statistics", "3.12", "StdLib", "mean, median, mode, stdev, variance, quantiles", "import statistics"),
        PyLibraryInfo("json", "3.12", "StdLib", "JSON serialization (dumps) and deserialization (loads)", "import json"),
        PyLibraryInfo("re", "3.12", "StdLib", "Regular expressions: search, match, findall, sub, split", "import re"),
        PyLibraryInfo("datetime", "3.12", "StdLib", "Dates, timestamps, formatting (strftime), and timedelta arithmetic", "import datetime"),
        PyLibraryInfo("time", "3.12", "StdLib", "High-resolution timers (perf_counter, time, sleep, strftime)", "import time"),
        PyLibraryInfo("collections", "3.12", "StdLib", "Counter, defaultdict, deque, OrderedDict", "from collections import Counter"),
        PyLibraryInfo("itertools", "3.12", "StdLib", "chain, combinations, permutations, product, accumulate, zip_longest", "import itertools"),
        PyLibraryInfo("functools", "3.12", "StdLib", "reduce, partial, lru_cache", "import functools"),
        PyLibraryInfo("string", "3.12", "StdLib", "ASCII alphabets, digits, punctuation, hex constants", "import string"),
        PyLibraryInfo("csv", "3.12", "StdLib", "CSV reader and writer for structured text data", "import csv"),
        PyLibraryInfo("hashlib", "3.12", "StdLib", "Cryptographic hashes: sha256, sha1, md5", "import hashlib"),
        PyLibraryInfo("base64", "3.12", "StdLib", "Base64 encode and decode utilities", "import base64"),
        PyLibraryInfo("os", "3.12", "StdLib", "Path utilities, environment metadata, and workspace inspection", "import os"),
        PyLibraryInfo("sys", "3.12", "StdLib", "Interpreter version, platform info, modules list", "import sys"),
        PyLibraryInfo("copy", "3.12", "StdLib", "Shallow and deep copy helpers for Python collections", "import copy"),
        PyLibraryInfo("sqlite3", "3.12", "Database", "In-memory relational query store for lightweight scripts", "import sqlite3")
    )

    val installedNames: MutableSet<String> = libraries.map { it.name }.toMutableSet()
}

object PyStdLib {
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun createModule(name: String, interpreter: PyInterpreter): PyModule? = when (name) {
        "math" -> buildMath()
        "cmath" -> buildMath("cmath")
        "random" -> buildRandom()
        "statistics" -> buildStatistics()
        "json" -> buildJson()
        "re" -> buildRe()
        "datetime" -> buildDatetime()
        "time" -> buildTime()
        "collections" -> buildCollections()
        "itertools" -> buildItertools()
        "functools" -> buildFunctools()
        "string" -> buildStringMod()
        "csv" -> buildCsv()
        "hashlib" -> buildHashlib()
        "base64" -> buildBase64()
        "urllib", "urllib.parse" -> buildUrllibParse()
        "os" -> buildOs()
        "sys" -> buildSys()
        "copy" -> buildCopy()
        "typing", "enum", "dataclasses", "abc", "pathlib", "io", "colorama" -> buildStubUtility(name)
        "requests" -> buildRequests()
        "tabulate" -> buildTabulate()
        "sqlite3" -> buildSqlite3()
        else -> null
    }

    private fun buildMath(modName: String = "math"): PyModule {
        val m = PyModule(modName)
        m.attrs["pi"] = PyFloat(PI)
        m.attrs["e"] = PyFloat(E)
        m.attrs["tau"] = PyFloat(2.0 * PI)
        m.attrs["inf"] = PyFloat(Double.POSITIVE_INFINITY)
        m.attrs["nan"] = PyFloat(Double.NaN)
        m.attrs["sin"] = PyBuiltin("sin") { a, _, _ -> PyFloat(sin(a[0].asDouble())) }
        m.attrs["cos"] = PyBuiltin("cos") { a, _, _ -> PyFloat(cos(a[0].asDouble())) }
        m.attrs["tan"] = PyBuiltin("tan") { a, _, _ -> PyFloat(tan(a[0].asDouble())) }
        m.attrs["asin"] = PyBuiltin("asin") { a, _, _ -> PyFloat(asin(a[0].asDouble())) }
        m.attrs["acos"] = PyBuiltin("acos") { a, _, _ -> PyFloat(acos(a[0].asDouble())) }
        m.attrs["atan"] = PyBuiltin("atan") { a, _, _ -> PyFloat(atan(a[0].asDouble())) }
        m.attrs["atan2"] = PyBuiltin("atan2") { a, _, _ -> PyFloat(atan2(a[0].asDouble(), a[1].asDouble())) }
        m.attrs["sinh"] = PyBuiltin("sinh") { a, _, _ -> PyFloat(sinh(a[0].asDouble())) }
        m.attrs["cosh"] = PyBuiltin("cosh") { a, _, _ -> PyFloat(cosh(a[0].asDouble())) }
        m.attrs["tanh"] = PyBuiltin("tanh") { a, _, _ -> PyFloat(tanh(a[0].asDouble())) }
        m.attrs["sqrt"] = PyBuiltin("sqrt") { a, _, _ -> PyFloat(sqrt(a[0].asDouble())) }
        m.attrs["exp"] = PyBuiltin("exp") { a, _, _ -> PyFloat(exp(a[0].asDouble())) }
        m.attrs["log"] = PyBuiltin("log") { a, _, _ ->
            if (a.size >= 2) PyFloat(ln(a[0].asDouble()) / ln(a[1].asDouble()))
            else PyFloat(ln(a[0].asDouble()))
        }
        m.attrs["log10"] = PyBuiltin("log10") { a, _, _ -> PyFloat(log10(a[0].asDouble())) }
        m.attrs["log2"] = PyBuiltin("log2") { a, _, _ -> PyFloat(log2(a[0].asDouble())) }
        m.attrs["pow"] = PyBuiltin("pow") { a, _, _ -> PyFloat(a[0].asDouble().pow(a[1].asDouble())) }
        m.attrs["fabs"] = PyBuiltin("fabs") { a, _, _ -> PyFloat(abs(a[0].asDouble())) }
        m.attrs["floor"] = PyBuiltin("floor") { a, _, _ -> PyInt(floor(a[0].asDouble()).toLong()) }
        m.attrs["ceil"] = PyBuiltin("ceil") { a, _, _ -> PyInt(ceil(a[0].asDouble()).toLong()) }
        m.attrs["degrees"] = PyBuiltin("degrees") { a, _, _ -> PyFloat(a[0].asDouble() * 180.0 / PI) }
        m.attrs["radians"] = PyBuiltin("radians") { a, _, _ -> PyFloat(a[0].asDouble() * PI / 180.0) }
        m.attrs["hypot"] = PyBuiltin("hypot") { a, _, _ -> PyFloat(hypot(a[0].asDouble(), a[1].asDouble())) }
        m.attrs["factorial"] = PyBuiltin("factorial") { a, _, _ ->
            val n = a[0].asLong().coerceIn(0, 20)
            var r = 1L; for (k in 2..n) r *= k; PyInt(r)
        }
        m.attrs["gcd"] = PyBuiltin("gcd") { a, _, _ ->
            var x = abs(a[0].asLong()); var y = abs(a[1].asLong())
            while (y != 0L) { val t = x % y; x = y; y = t }
            PyInt(x)
        }
        m.attrs["isnan"] = PyBuiltin("isnan") { a, _, _ -> PyBool(a[0].asDouble().isNaN()) }
        m.attrs["isinf"] = PyBuiltin("isinf") { a, _, _ -> PyBool(a[0].asDouble().isInfinite()) }
        return m
    }

    private fun buildRandom(): PyModule {
        var rng = Random(System.nanoTime())
        val m = PyModule("random")
        m.attrs["seed"] = PyBuiltin("seed") { a, _, _ ->
            rng = Random(a.firstOrNull()?.asLong() ?: System.nanoTime()); PyNone
        }
        m.attrs["random"] = PyBuiltin("random") { _, _, _ -> PyFloat(rng.nextDouble()) }
        m.attrs["uniform"] = PyBuiltin("uniform") { a, _, _ ->
            val lo = a[0].asDouble(); val hi = a[1].asDouble()
            PyFloat(lo + rng.nextDouble() * (hi - lo))
        }
        m.attrs["randint"] = PyBuiltin("randint") { a, _, _ ->
            val lo = a[0].asLong(); val hi = a[1].asLong()
            PyInt(rng.nextLong(lo, hi + 1))
        }
        m.attrs["randrange"] = PyBuiltin("randrange") { a, _, _ ->
            val lo = if (a.size == 1) 0L else a[0].asLong()
            val hi = if (a.size == 1) a[0].asLong() else a[1].asLong()
            PyInt(rng.nextLong(lo, hi))
        }
        m.attrs["gauss"] = PyBuiltin("gauss") { a, _, _ ->
            val mu = a.getOrNull(0)?.asDouble() ?: 0.0
            val sigma = a.getOrNull(1)?.asDouble() ?: 1.0
            val u1 = rng.nextDouble().coerceAtLeast(1e-12)
            val u2 = rng.nextDouble()
            val z0 = sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
            PyFloat(mu + z0 * sigma)
        }
        m.attrs["choice"] = PyBuiltin("choice") { a, _, interp ->
            val seq = interp.toIterableList(a[0])
            seq[rng.nextInt(seq.size)]
        }
        m.attrs["sample"] = PyBuiltin("sample") { a, _, interp ->
            val seq = interp.toIterableList(a[0]).shuffled(rng)
            val k = a[1].asLong().toInt().coerceIn(0, seq.size)
            PyList(seq.take(k).toMutableList())
        }
        m.attrs["shuffle"] = PyBuiltin("shuffle") { a, _, _ ->
            val list = a[0] as PyList
            list.items.shuffle(rng)
            PyNone
        }
        return m
    }

    private fun buildStatistics(): PyModule {
        val m = PyModule("statistics")
        m.attrs["mean"] = PyBuiltin("mean") { a, _, _ ->
            val d = a[0].toDoubleList(); PyFloat(if (d.isEmpty()) 0.0 else d.average())
        }
        m.attrs["median"] = PyBuiltin("median") { a, _, _ ->
            val d = a[0].toDoubleList().sorted()
            val mid = d.size / 2
            PyFloat(if (d.size % 2 == 1) d[mid] else (d[mid - 1] + d[mid]) / 2.0)
        }
        m.attrs["stdev"] = PyBuiltin("stdev") { a, _, _ ->
            val d = a[0].toDoubleList()
            val avg = d.average()
            val v = d.sumOf { (it - avg).pow(2) } / (d.size - 1).coerceAtLeast(1)
            PyFloat(sqrt(v))
        }
        m.attrs["variance"] = PyBuiltin("variance") { a, _, _ ->
            val d = a[0].toDoubleList()
            val avg = d.average()
            PyFloat(d.sumOf { (it - avg).pow(2) } / (d.size - 1).coerceAtLeast(1))
        }
        return m
    }

    private fun buildJson(): PyModule {
        fun pyToJson(v: PyVal): Any = when (v) {
            is PyNone -> JSONObject.NULL
            is PyBool -> v.value
            is PyInt -> v.value
            is PyFloat -> v.value
            is PyStr -> v.value
            is PyList -> JSONArray().apply { v.items.forEach { put(pyToJson(it)) } }
            is PyTuple -> JSONArray().apply { v.items.forEach { put(pyToJson(it)) } }
            is PyDict -> JSONObject().apply { v.entries.forEach { (k, vl) -> put(k.toDisplayString(), pyToJson(vl)) } }
            else -> v.toDisplayString()
        }

        fun jsonToPy(any: Any?): PyVal = when (any) {
            null, JSONObject.NULL -> PyNone
            is Boolean -> PyBool(any)
            is Int -> PyInt(any.toLong())
            is Long -> PyInt(any)
            is Double -> PyFloat(any)
            is String -> PyStr(any)
            is JSONArray -> PyList((0 until any.length()).map { jsonToPy(any.get(it)) }.toMutableList())
            is JSONObject -> {
                val map = LinkedHashMap<PyVal, PyVal>()
                val keys = any.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[PyStr(k)] = jsonToPy(any.get(k))
                }
                PyDict(map)
            }
            else -> PyStr(any.toString())
        }

        val m = PyModule("json")
        m.attrs["dumps"] = PyBuiltin("dumps") { a, kw, _ ->
            val indent = kw["indent"]?.asLong()?.toInt()
            val obj = pyToJson(a[0])
            val str = when (obj) {
                is JSONObject -> if (indent != null) obj.toString(indent) else obj.toString()
                is JSONArray -> if (indent != null) obj.toString(indent) else obj.toString()
                is String -> JSONObject.quote(obj)
                else -> obj.toString()
            }
            PyStr(str)
        }
        m.attrs["loads"] = PyBuiltin("loads") { a, _, _ ->
            val txt = a[0].toDisplayString().trim()
            when {
                txt.startsWith("{") -> jsonToPy(JSONObject(txt))
                txt.startsWith("[") -> jsonToPy(JSONArray(txt))
                txt == "true" -> PyBool(true)
                txt == "false" -> PyBool(false)
                txt == "null" -> PyNone
                txt.startsWith("\"") -> PyStr(txt.trim('"'))
                else -> txt.toLongOrNull()?.let { PyInt(it) } ?: txt.toDoubleOrNull()?.let { PyFloat(it) } ?: PyStr(txt)
            }
        }
        return m
    }

    private fun buildRe(): PyModule {
        val m = PyModule("re")
        m.attrs["findall"] = PyBuiltin("findall") { a, _, _ ->
            val regex = Regex(a[0].toDisplayString())
            val found = regex.findAll(a[1].toDisplayString()).map { PyStr(it.value) as PyVal }.toMutableList()
            PyList(found)
        }
        m.attrs["sub"] = PyBuiltin("sub") { a, _, _ ->
            val regex = Regex(a[0].toDisplayString())
            PyStr(regex.replace(a[2].toDisplayString(), a[1].toDisplayString()))
        }
        m.attrs["split"] = PyBuiltin("split") { a, _, _ ->
            val regex = Regex(a[0].toDisplayString())
            PyList(regex.split(a[1].toDisplayString()).map { PyStr(it) as PyVal }.toMutableList())
        }
        m.attrs["search"] = PyBuiltin("search") { a, _, _ ->
            val match = Regex(a[0].toDisplayString()).find(a[1].toDisplayString())
            if (match == null) PyNone else PyStr(match.value)
        }
        m.attrs["match"] = PyBuiltin("match") { a, _, _ ->
            val match = Regex("^" + a[0].toDisplayString()).find(a[1].toDisplayString())
            if (match == null) PyNone else PyStr(match.value)
        }
        return m
    }

    private fun buildDatetime(): PyModule {
        val m = PyModule("datetime")
        val dtMod = PyModule("datetime")
        dtMod.attrs["now"] = PyBuiltin("now") { _, _, _ ->
            val now = Date()
            val cls = PyClass("datetime", emptyList())
            val inst = PyInstance(cls)
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            inst.attrs["iso"] = PyStr(fmt.format(now))
            inst.attrs["timestamp"] = PyBuiltin("timestamp") { _, _, _ -> PyFloat(now.time / 1000.0) }
            inst.attrs["strftime"] = PyBuiltin("strftime") { a, _, _ ->
                val pat = a[0].toDisplayString()
                    .replace("%Y", "yyyy").replace("%m", "MM").replace("%d", "dd")
                    .replace("%H", "HH").replace("%M", "mm").replace("%S", "ss")
                PyStr(SimpleDateFormat(pat, Locale.US).format(now))
            }
            inst.attrs["isoformat"] = PyBuiltin("isoformat") { _, _, _ -> PyStr(fmt.format(now)) }
            inst
        }
        m.attrs["datetime"] = dtMod
        m.attrs["date"] = dtMod
        return m
    }

    private fun buildTime(): PyModule {
        val m = PyModule("time")
        m.attrs["time"] = PyBuiltin("time") { _, _, _ -> PyFloat(System.currentTimeMillis() / 1000.0) }
        m.attrs["perf_counter"] = PyBuiltin("perf_counter") { _, _, _ -> PyFloat(System.nanoTime() / 1_000_000_000.0) }
        m.attrs["sleep"] = PyBuiltin("sleep") { a, _, _ ->
            val ms = (a[0].asDouble() * 1000.0).toLong().coerceIn(0L, 1500L)
            Thread.sleep(ms)
            PyNone
        }
        m.attrs["strftime"] = PyBuiltin("strftime") { a, _, _ ->
            val pat = a[0].toDisplayString()
                .replace("%Y", "yyyy").replace("%m", "MM").replace("%d", "dd")
                .replace("%H", "HH").replace("%M", "mm").replace("%S", "ss")
            PyStr(SimpleDateFormat(pat, Locale.US).format(Date()))
        }
        return m
    }

    private fun buildCollections(): PyModule {
        val m = PyModule("collections")
        m.attrs["Counter"] = PyBuiltin("Counter") { a, _, interp ->
            val map = LinkedHashMap<PyVal, PyVal>()
            if (a.isNotEmpty()) {
                for (item in interp.toIterableList(a[0])) {
                    val c = (map[item] as? PyInt)?.value ?: 0L
                    map[item] = PyInt(c + 1L)
                }
            }
            PyDict(map)
        }
        m.attrs["deque"] = PyBuiltin("deque") { a, _, interp ->
            if (a.isEmpty()) PyList() else PyList(interp.toIterableList(a[0]).toMutableList())
        }
        m.attrs["defaultdict"] = PyBuiltin("defaultdict") { _, _, _ -> PyDict() }
        return m
    }

    private fun buildItertools(): PyModule {
        val m = PyModule("itertools")
        m.attrs["chain"] = PyBuiltin("chain") { a, _, interp ->
            val out = mutableListOf<PyVal>()
            for (arg in a) out.addAll(interp.toIterableList(arg))
            PyList(out)
        }
        m.attrs["accumulate"] = PyBuiltin("accumulate") { a, _, interp ->
            val seq = interp.toIterableList(a[0])
            val out = mutableListOf<PyVal>()
            var acc = 0.0
            for (v in seq) { acc += v.asDouble(); out.add(PyFloat(acc)) }
            PyList(out)
        }
        m.attrs["product"] = PyBuiltin("product") { a, _, interp ->
            val lists = a.map { interp.toIterableList(it) }
            var acc = listOf<List<PyVal>>(emptyList())
            for (l in lists) {
                acc = acc.flatMap { prefix -> l.map { item -> prefix + item } }
            }
            PyList(acc.map { PyTuple(it) }.toMutableList())
        }
        m.attrs["combinations"] = PyBuiltin("combinations") { a, _, interp ->
            val seq = interp.toIterableList(a[0])
            val r = a[1].asLong().toInt()
            val out = mutableListOf<PyVal>()
            fun rec(start: Int, cur: MutableList<PyVal>) {
                if (cur.size == r) { out.add(PyTuple(cur.toList())); return }
                for (i in start until seq.size) {
                    cur.add(seq[i]); rec(i + 1, cur); cur.removeAt(cur.lastIndex)
                }
            }
            rec(0, mutableListOf())
            PyList(out)
        }
        return m
    }

    private fun buildFunctools(): PyModule {
        val m = PyModule("functools")
        m.attrs["reduce"] = PyBuiltin("reduce") { a, _, interp ->
            val fn = a[0]
            val seq = interp.toIterableList(a[1])
            var acc = if (a.size >= 3) a[2] else seq.first()
            val start = if (a.size >= 3) 0 else 1
            for (i in start until seq.size) {
                acc = interp.callCallable(fn, listOf(acc, seq[i]), emptyMap())
            }
            acc
        }
        m.attrs["lru_cache"] = PyBuiltin("lru_cache") { _, _, _ ->
            PyBuiltin("decorator") { a, _, _ -> a[0] }
        }
        return m
    }

    private fun buildStringMod(): PyModule {
        val m = PyModule("string")
        m.attrs["ascii_lowercase"] = PyStr("abcdefghijklmnopqrstuvwxyz")
        m.attrs["ascii_uppercase"] = PyStr("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        m.attrs["ascii_letters"] = PyStr("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
        m.attrs["digits"] = PyStr("0123456789")
        m.attrs["hexdigits"] = PyStr("0123456789abcdefABCDEF")
        m.attrs["punctuation"] = PyStr("!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~")
        return m
    }

    private fun buildCsv(): PyModule {
        val m = PyModule("csv")
        m.attrs["reader"] = PyBuiltin("reader") { a, _, interp ->
            val lines = interp.toIterableList(a[0])
            PyList(lines.map { line ->
                PyList(line.toDisplayString().split(",").map { PyStr(it.trim()) }.toMutableList())
            }.toMutableList())
        }
        return m
    }

    private fun buildHashlib(): PyModule {
        fun digestMod(algo: String) = PyBuiltin(algo) { a, _, _ ->
            val data = a.firstOrNull()?.toDisplayString()?.toByteArray() ?: ByteArray(0)
            val md = MessageDigest.getInstance(algo).digest(data)
            val hex = md.joinToString("") { "%02x".format(it) }
            val cls = PyClass("hash", emptyList())
            PyInstance(cls).apply {
                attrs["hexdigest"] = PyBuiltin("hexdigest") { _, _, _ -> PyStr(hex) }
            }
        }
        val m = PyModule("hashlib")
        m.attrs["sha256"] = digestMod("SHA-256")
        m.attrs["sha1"] = digestMod("SHA-1")
        m.attrs["md5"] = digestMod("MD5")
        return m
    }

    private fun buildBase64(): PyModule {
        val m = PyModule("base64")
        m.attrs["b64encode"] = PyBuiltin("b64encode") { a, _, _ ->
            PyStr(Base64.getEncoder().encodeToString(a[0].toDisplayString().toByteArray()))
        }
        m.attrs["b64decode"] = PyBuiltin("b64decode") { a, _, _ ->
            PyStr(String(Base64.getDecoder().decode(a[0].toDisplayString())))
        }
        return m
    }

    private fun buildUrllibParse(): PyModule {
        val m = PyModule("urllib.parse")
        m.attrs["quote"] = PyBuiltin("quote") { a, _, _ -> PyStr(java.net.URLEncoder.encode(a[0].toDisplayString(), "UTF-8")) }
        m.attrs["unquote"] = PyBuiltin("unquote") { a, _, _ -> PyStr(java.net.URLDecoder.decode(a[0].toDisplayString(), "UTF-8")) }
        return m
    }

    private fun buildOs(): PyModule {
        val m = PyModule("os")
        m.attrs["name"] = PyStr("posix")
        m.attrs["sep"] = PyStr("/")
        val pathMod = PyModule("os.path")
        pathMod.attrs["join"] = PyBuiltin("join") { a, _, _ -> PyStr(a.joinToString("/") { it.toDisplayString().trimEnd('/') }) }
        pathMod.attrs["basename"] = PyBuiltin("basename") { a, _, _ -> PyStr(a[0].toDisplayString().substringAfterLast('/')) }
        pathMod.attrs["dirname"] = PyBuiltin("dirname") { a, _, _ -> PyStr(a[0].toDisplayString().substringBeforeLast('/', "")) }
        m.attrs["path"] = pathMod
        m.attrs["getcwd"] = PyBuiltin("getcwd") { _, _, _ -> PyStr("/workspace") }
        return m
    }

    private fun buildSys(): PyModule {
        val m = PyModule("sys")
        m.attrs["version"] = PyStr("3.12.4 (PyForge Studio On-Device Engine)")
        m.attrs["platform"] = PyStr("android")
        m.attrs["maxsize"] = PyInt(Long.MAX_VALUE)
        return m
    }

    private fun buildCopy(): PyModule {
        val m = PyModule("copy")
        fun deep(v: PyVal): PyVal = when (v) {
            is PyList -> PyList(v.items.map { deep(it) }.toMutableList())
            is PyDict -> PyDict(LinkedHashMap(v.entries.mapValues { deep(it.value) }))
            is PyArray -> PyArray(v.data.copyOf(), v.shape.copyOf())
            else -> v
        }
        m.attrs["copy"] = PyBuiltin("copy") { a, _, _ -> deep(a[0]) }
        m.attrs["deepcopy"] = PyBuiltin("deepcopy") { a, _, _ -> deep(a[0]) }
        return m
    }

    private fun buildStubUtility(name: String): PyModule {
        val m = PyModule(name)
        m.attrs["List"] = PyStr("List")
        m.attrs["Dict"] = PyStr("Dict")
        m.attrs["Optional"] = PyStr("Optional")
        m.attrs["Any"] = PyStr("Any")
        m.attrs["dataclass"] = PyBuiltin("dataclass") { a, _, _ -> a[0] }
        return m
    }

    private fun buildRequests(): PyModule {
        val m = PyModule("requests")
        fun makeResponse(code: Int, body: String): PyInstance {
            val cls = PyClass("Response", emptyList())
            return PyInstance(cls).apply {
                attrs["status_code"] = PyInt(code.toLong())
                attrs["text"] = PyStr(body)
                attrs["ok"] = PyBool(code in 200..299)
                attrs["json"] = PyBuiltin("json") { _, _, interp ->
                    val jsonMod = buildJson()
                    interp.callCallable(jsonMod.attrs["loads"]!!, listOf(PyStr(body)), emptyMap())
                }
            }
        }
        m.attrs["get"] = PyBuiltin("get") { a, _, _ ->
            val url = a[0].toDisplayString()
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                makeResponse(resp.code, resp.body?.string().orEmpty())
            }
        }
        m.attrs["post"] = PyBuiltin("post") { a, kw, _ ->
            val url = a[0].toDisplayString()
            val bodyText = kw["data"]?.toDisplayString() ?: kw["json"]?.toDisplayString() ?: ""
            val req = Request.Builder()
                .url(url)
                .post(bodyText.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                makeResponse(resp.code, resp.body?.string().orEmpty())
            }
        }
        return m
    }

    private fun buildTabulate(): PyModule {
        val m = PyModule("tabulate")
        m.attrs["tabulate"] = PyBuiltin("tabulate") { a, kw, interp ->
            val rows = interp.toIterableList(a[0]).map { interp.toIterableList(it).map { c -> c.toDisplayString() } }
            val headers = kw["headers"]?.let { interp.toIterableList(it).map { c -> c.toDisplayString() } } ?: emptyList()
            val all = if (headers.isNotEmpty()) listOf(headers) + rows else rows
            val cols = all.maxOfOrNull { it.size } ?: 0
            val widths = (0 until cols).map { c -> all.maxOfOrNull { it.getOrNull(c)?.length ?: 0 } ?: 4 }
            val sb = StringBuilder()
            if (headers.isNotEmpty()) {
                sb.appendLine(headers.mapIndexed { i, h -> h.padEnd(widths[i] + 2) }.joinToString(""))
                sb.appendLine(widths.joinToString("  ") { "-".repeat(it) })
            }
            for (r in rows) {
                sb.appendLine(r.mapIndexed { i, cell -> cell.padEnd(widths[i] + 2) }.joinToString(""))
            }
            PyStr(sb.toString().trimEnd())
        }
        return m
    }

    private fun buildSqlite3(): PyModule {
        val m = PyModule("sqlite3")
        m.attrs["connect"] = PyBuiltin("connect") { _, _, _ ->
            val tables = LinkedHashMap<String, MutableList<List<PyVal>>>()
            val connCls = PyClass("Connection", emptyList())
            PyInstance(connCls).apply {
                attrs["execute"] = PyBuiltin("execute") { a, _, _ ->
                    val sql = a[0].toDisplayString().trim()
                    val curCls = PyClass("Cursor", emptyList())
                    val cur = PyInstance(curCls)
                    val upper = sql.uppercase()
                    var resultRows: List<List<PyVal>> = emptyList()
                    if (upper.startsWith("CREATE TABLE")) {
                        val name = sql.split(Regex("\\s+")).getOrNull(2)?.substringBefore("(") ?: "tbl"
                        tables.putIfAbsent(name, mutableListOf())
                    } else if (upper.startsWith("INSERT INTO")) {
                        val name = sql.split(Regex("\\s+")).getOrNull(2)?.substringBefore("(") ?: "tbl"
                        val vals = sql.substringAfter("VALUES", "").trim().trim('(', ')', ';')
                            .split(",").map { PyStr(it.trim().trim('\'', '"')) }
                        tables.getOrPut(name) { mutableListOf() }.add(vals)
                    } else if (upper.startsWith("SELECT")) {
                        val name = sql.substringAfter("FROM", "").trim().split(Regex("\\s+")).firstOrNull()?.trim(';') ?: ""
                        resultRows = tables[name]?.toList() ?: emptyList()
                    }
                    cur.attrs["fetchall"] = PyBuiltin("fetchall") { _, _, _ ->
                        PyList(resultRows.map { PyTuple(it) }.toMutableList())
                    }
                    cur
                }
                attrs["commit"] = PyBuiltin("commit") { _, _, _ -> PyNone }
                attrs["close"] = PyBuiltin("close") { _, _, _ -> PyNone }
            }
        }
        return m
    }
}
