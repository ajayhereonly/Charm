package com.example.graphics3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )
    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun normalized(): Vec3 {
        val len = length()
        return if (len > 1e-6f) Vec3(x / len, y / len, z / len) else Vec3(0f, 1f, 0f)
    }
}

data class MeshObject(
    val id: String,
    val name: String,
    val vertices: FloatArray, // 3 floats per vertex (triangles, non-indexed)
    val normals: FloatArray,  // 3 floats per vertex
    val colorRgba: FloatArray = floatArrayOf(0f, 0.898f, 1f, 1f),
    val metallic: Float = 0.45f,
    val wireframeOnly: Boolean = false
) {
    val vertexCount: Int get() = vertices.size / 3
    val triangleCount: Int get() = vertices.size / 9
}

data class Scene3D(
    val title: String = "PyForge 3D Scene",
    val objects: List<MeshObject> = emptyList(),
    val showGrid: Boolean = true,
    val showAxes: Boolean = true,
    val wireframeOverlay: Boolean = false,
    val revision: Long = System.nanoTime()
) {
    val totalVertices: Int get() = objects.sumOf { it.vertexCount }
    val totalTriangles: Int get() = objects.sumOf { it.triangleCount }

    fun exportToObj(): String {
        val sb = StringBuilder()
        sb.appendLine("# Exported from PyForge Studio GPU 3D Engine")
        sb.appendLine("# Scene: $title | Objects: ${objects.size} | Triangles: $totalTriangles")
        var vertexOffset = 1
        for (obj in objects) {
            sb.appendLine("o ${obj.name.replace(" ", "_")}")
            val vCount = obj.vertexCount
            for (i in 0 until vCount) {
                val idx = i * 3
                sb.appendLine("v ${obj.vertices[idx]} ${obj.vertices[idx + 1]} ${obj.vertices[idx + 2]}")
            }
            for (i in 0 until vCount) {
                val idx = i * 3
                sb.appendLine("vn ${obj.normals[idx]} ${obj.normals[idx + 1]} ${obj.normals[idx + 2]}")
            }
            val triCount = obj.triangleCount
            for (t in 0 until triCount) {
                val a = vertexOffset + t * 3
                val b = a + 1
                val c = a + 2
                sb.appendLine("f $a//$a $b//$b $c//$c")
            }
            vertexOffset += vCount
        }
        return sb.toString()
    }

    fun exportToAsciiStl(): String {
        val sb = StringBuilder()
        val safeName = title.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "pyforge_model" }
        sb.appendLine("solid $safeName")
        for (obj in objects) {
            val triCount = obj.triangleCount
            for (t in 0 until triCount) {
                val base = t * 9
                val nx = obj.normals[base]
                val ny = obj.normals[base + 1]
                val nz = obj.normals[base + 2]
                sb.appendLine("  facet normal $nx $ny $nz")
                sb.appendLine("    outer loop")
                sb.appendLine("      vertex ${obj.vertices[base]} ${obj.vertices[base + 1]} ${obj.vertices[base + 2]}")
                sb.appendLine("      vertex ${obj.vertices[base + 3]} ${obj.vertices[base + 4]} ${obj.vertices[base + 5]}")
                sb.appendLine("      vertex ${obj.vertices[base + 6]} ${obj.vertices[base + 7]} ${obj.vertices[base + 8]}")
                sb.appendLine("    endloop")
                sb.appendLine("  endfacet")
            }
        }
        sb.appendLine("endsolid $safeName")
        return sb.toString()
    }
}

object MeshBuilder {

    fun parseColor(hex: String?, defaultColor: FloatArray = floatArrayOf(0f, 0.898f, 1f, 1f)): FloatArray {
        if (hex.isNullOrBlank()) return defaultColor.copyOf()
        val named = when (hex.trim().lowercase()) {
            "cyan" -> "#00E5FF"
            "amber", "gold", "yellow" -> "#FFB300"
            "red", "coral" -> "#FF5370"
            "green", "emerald" -> "#22D3A0"
            "blue" -> "#3B82F6"
            "purple", "violet" -> "#C792EA"
            "orange" -> "#FF8A65"
            "white" -> "#F1F5F9"
            "silver", "gray", "grey" -> "#94A3B8"
            "pink", "magenta" -> "#EC4899"
            else -> hex.trim()
        }
        val clean = named.removePrefix("#")
        return try {
            when (clean.length) {
                6 -> {
                    val r = clean.substring(0, 2).toInt(16) / 255f
                    val g = clean.substring(2, 4).toInt(16) / 255f
                    val b = clean.substring(4, 6).toInt(16) / 255f
                    floatArrayOf(r, g, b, 1f)
                }
                8 -> {
                    val a = clean.substring(0, 2).toInt(16) / 255f
                    val r = clean.substring(2, 4).toInt(16) / 255f
                    val g = clean.substring(4, 6).toInt(16) / 255f
                    val b = clean.substring(6, 8).toInt(16) / 255f
                    floatArrayOf(r, g, b, a)
                }
                else -> defaultColor.copyOf()
            }
        } catch (_: Exception) {
            defaultColor.copyOf()
        }
    }

    private fun transformPoint(p: Vec3, pos: Vec3, rotDeg: Vec3, scale: Vec3): Vec3 {
        var x = p.x * scale.x
        var y = p.y * scale.y
        var z = p.z * scale.z

        val rx = (rotDeg.x * PI / 180.0).toFloat()
        val ry = (rotDeg.y * PI / 180.0).toFloat()
        val rz = (rotDeg.z * PI / 180.0).toFloat()

        // Rotate X
        if (rx != 0f) {
            val c = cos(rx); val s = sin(rx)
            val ny = y * c - z * s
            val nz = y * s + z * c
            y = ny; z = nz
        }
        // Rotate Y
        if (ry != 0f) {
            val c = cos(ry); val s = sin(ry)
            val nx = x * c + z * s
            val nz = -x * s + z * c
            x = nx; z = nz
        }
        // Rotate Z
        if (rz != 0f) {
            val c = cos(rz); val s = sin(rz)
            val nx = x * c - y * s
            val ny = x * s + y * c
            x = nx; y = ny
        }
        return Vec3(x + pos.x, y + pos.y, z + pos.z)
    }

    private fun transformNormal(n: Vec3, rotDeg: Vec3): Vec3 {
        var x = n.x; var y = n.y; var z = n.z
        val rx = (rotDeg.x * PI / 180.0).toFloat()
        val ry = (rotDeg.y * PI / 180.0).toFloat()
        val rz = (rotDeg.z * PI / 180.0).toFloat()
        if (rx != 0f) {
            val c = cos(rx); val s = sin(rx)
            val ny = y * c - z * s; val nz = y * s + z * c; y = ny; z = nz
        }
        if (ry != 0f) {
            val c = cos(ry); val s = sin(ry)
            val nx = x * c + z * s; val nz = -x * s + z * c; x = nx; z = nz
        }
        if (rz != 0f) {
            val c = cos(rz); val s = sin(rz)
            val nx = x * c - y * s; val ny = x * s + y * c; x = nx; y = ny
        }
        return Vec3(x, y, z).normalized()
    }

    private class TriCollector {
        val verts = ArrayList<Float>(1024)
        val norms = ArrayList<Float>(1024)

        fun addTriFlat(a: Vec3, b: Vec3, c: Vec3) {
            val n = (b - a).cross(c - a).normalized()
            addTriSmooth(a, b, c, n, n, n)
        }

        fun addTriSmooth(a: Vec3, b: Vec3, c: Vec3, na: Vec3, nb: Vec3, nc: Vec3) {
            verts.add(a.x); verts.add(a.y); verts.add(a.z)
            verts.add(b.x); verts.add(b.y); verts.add(b.z)
            verts.add(c.x); verts.add(c.y); verts.add(c.z)
            norms.add(na.x); norms.add(na.y); norms.add(na.z)
            norms.add(nb.x); norms.add(nb.y); norms.add(nb.z)
            norms.add(nc.x); norms.add(nc.y); norms.add(nc.z)
        }

        fun build(
            name: String,
            colorHex: String?,
            metallic: Float,
            wireframeOnly: Boolean
        ): MeshObject = MeshObject(
            id = "${name}_${System.nanoTime()}",
            name = name,
            vertices = verts.toFloatArray(),
            normals = norms.toFloatArray(),
            colorRgba = parseColor(colorHex),
            metallic = metallic,
            wireframeOnly = wireframeOnly
        )
    }

    fun createCube(
        name: String = "Cube",
        size: Float = 1.2f,
        pos: Vec3 = Vec3(0f, 0f, 0f),
        rot: Vec3 = Vec3(0f, 0f, 0f),
        scale: Vec3 = Vec3(1f, 1f, 1f),
        colorHex: String? = "#00E5FF",
        metallic: Float = 0.45f,
        wireframeOnly: Boolean = false
    ): MeshObject {
        val h = size / 2f
        val c = TriCollector()
        val faces = listOf(
            // Front (+Z)
            listOf(Vec3(-h, -h, h), Vec3(h, -h, h), Vec3(h, h, h), Vec3(-h, h, h)),
            // Back (-Z)
            listOf(Vec3(h, -h, -h), Vec3(-h, -h, -h), Vec3(-h, h, -h), Vec3(h, h, -h)),
            // Left (-X)
            listOf(Vec3(-h, -h, -h), Vec3(-h, -h, h), Vec3(-h, h, h), Vec3(-h, h, -h)),
            // Right (+X)
            listOf(Vec3(h, -h, h), Vec3(h, -h, -h), Vec3(h, h, -h), Vec3(h, h, h)),
            // Top (+Y)
            listOf(Vec3(-h, h, h), Vec3(h, h, h), Vec3(h, h, -h), Vec3(-h, h, -h)),
            // Bottom (-Y)
            listOf(Vec3(-h, -h, -h), Vec3(h, -h, -h), Vec3(h, -h, h), Vec3(-h, -h, h))
        )
        for (f in faces) {
            val p0 = transformPoint(f[0], pos, rot, scale)
            val p1 = transformPoint(f[1], pos, rot, scale)
            val p2 = transformPoint(f[2], pos, rot, scale)
            val p3 = transformPoint(f[3], pos, rot, scale)
            c.addTriFlat(p0, p1, p2)
            c.addTriFlat(p0, p2, p3)
        }
        return c.build(name, colorHex, metallic, wireframeOnly)
    }

    fun createSphere(
        name: String = "Sphere",
        radius: Float = 1.0f,
        segments: Int = 24,
        pos: Vec3 = Vec3(0f, 0f, 0f),
        rot: Vec3 = Vec3(0f, 0f, 0f),
        scale: Vec3 = Vec3(1f, 1f, 1f),
        colorHex: String? = "#FFB300",
        metallic: Float = 0.55f,
        wireframeOnly: Boolean = false
    ): MeshObject {
        val segs = segments.coerceIn(6, 48)
        val rings = (segs * 3 / 4).coerceIn(5, 36)
        val c = TriCollector()

        fun spherePt(r: Int, s: Int): Pair<Vec3, Vec3> {
            val phi = (PI * r / rings).toFloat()
            val theta = (2.0 * PI * s / segs).toFloat()
            val nx = sin(phi) * cos(theta)
            val ny = cos(phi)
            val nz = sin(phi) * sin(theta)
            val local = Vec3(nx * radius, ny * radius, nz * radius)
            return transformPoint(local, pos, rot, scale) to transformNormal(Vec3(nx, ny, nz), rot)
        }

        for (r in 0 until rings) {
            for (s in 0 until segs) {
                val (p00, n00) = spherePt(r, s)
                val (p10, n10) = spherePt(r + 1, s)
                val (p11, n11) = spherePt(r + 1, s + 1)
                val (p01, n01) = spherePt(r, s + 1)
                c.addTriSmooth(p00, p10, p11, n00, n10, n11)
                c.addTriSmooth(p00, p11, p01, n00, n11, n01)
            }
        }
        return c.build(name, colorHex, metallic, wireframeOnly)
    }

    fun createTorus(
        name: String = "Torus",
        majorRadius: Float = 1.3f,
        minorRadius: Float = 0.38f,
        segments: Int = 32,
        tubeSegments: Int = 18,
        pos: Vec3 = Vec3(0f, 0f, 0f),
        rot: Vec3 = Vec3(0f, 0f, 0f),
        scale: Vec3 = Vec3(1f, 1f, 1f),
        colorHex: String? = "#00E5FF",
        metallic: Float = 0.65f,
        wireframeOnly: Boolean = false
    ): MeshObject {
        val segs = segments.coerceIn(8, 60)
        val tSegs = tubeSegments.coerceIn(6, 36)
        val c = TriCollector()

        fun torusPt(i: Int, j: Int): Pair<Vec3, Vec3> {
            val u = (2.0 * PI * i / segs).toFloat()
            val v = (2.0 * PI * j / tSegs).toFloat()
            val cu = cos(u); val su = sin(u)
            val cv = cos(v); val sv = sin(v)
            val x = (majorRadius + minorRadius * cv) * cu
            val y = minorRadius * sv
            val z = (majorRadius + minorRadius * cv) * su
            val nx = cv * cu
            val ny = sv
            val nz = cv * su
            return transformPoint(Vec3(x, y, z), pos, rot, scale) to transformNormal(Vec3(nx, ny, nz), rot)
        }

        for (i in 0 until segs) {
            for (j in 0 until tSegs) {
                val (p00, n00) = torusPt(i, j)
                val (p10, n10) = torusPt(i + 1, j)
                val (p11, n11) = torusPt(i + 1, j + 1)
                val (p01, n01) = torusPt(i, j + 1)
                c.addTriSmooth(p00, p10, p11, n00, n10, n11)
                c.addTriSmooth(p00, p11, p01, n00, n11, n01)
            }
        }
        return c.build(name, colorHex, metallic, wireframeOnly)
    }

    fun createCylinder(
        name: String = "Cylinder",
        radiusTop: Float = 0.6f,
        radiusBottom: Float = 0.6f,
        height: Float = 1.8f,
        segments: Int = 24,
        pos: Vec3 = Vec3(0f, 0f, 0f),
        rot: Vec3 = Vec3(0f, 0f, 0f),
        scale: Vec3 = Vec3(1f, 1f, 1f),
        colorHex: String? = "#C792EA",
        metallic: Float = 0.5f,
        wireframeOnly: Boolean = false
    ): MeshObject {
        val segs = segments.coerceIn(6, 48)
        val halfH = height / 2f
        val c = TriCollector()
        val topCenter = transformPoint(Vec3(0f, halfH, 0f), pos, rot, scale)
        val botCenter = transformPoint(Vec3(0f, -halfH, 0f), pos, rot, scale)

        for (i in 0 until segs) {
            val a0 = (2.0 * PI * i / segs).toFloat()
            val a1 = (2.0 * PI * (i + 1) / segs).toFloat()
            val t0 = transformPoint(Vec3(cos(a0) * radiusTop, halfH, sin(a0) * radiusTop), pos, rot, scale)
            val t1 = transformPoint(Vec3(cos(a1) * radiusTop, halfH, sin(a1) * radiusTop), pos, rot, scale)
            val b0 = transformPoint(Vec3(cos(a0) * radiusBottom, -halfH, sin(a0) * radiusBottom), pos, rot, scale)
            val b1 = transformPoint(Vec3(cos(a1) * radiusBottom, -halfH, sin(a1) * radiusBottom), pos, rot, scale)

            val n0 = transformNormal(Vec3(cos(a0), 0f, sin(a0)), rot)
            val n1 = transformNormal(Vec3(cos(a1), 0f, sin(a1)), rot)

            c.addTriSmooth(b0, t0, t1, n0, n0, n1)
            c.addTriSmooth(b0, t1, b1, n0, n1, n1)
            if (radiusTop > 1e-4f) c.addTriFlat(topCenter, t1, t0)
            if (radiusBottom > 1e-4f) c.addTriFlat(botCenter, b0, b1)
        }
        return c.build(name, colorHex, metallic, wireframeOnly)
    }

    fun createCone(
        name: String = "Cone",
        radius: Float = 0.8f,
        height: Float = 1.8f,
        segments: Int = 24,
        pos: Vec3 = Vec3(0f, 0f, 0f),
        rot: Vec3 = Vec3(0f, 0f, 0f),
        scale: Vec3 = Vec3(1f, 1f, 1f),
        colorHex: String? = "#FF8A65",
        metallic: Float = 0.45f,
        wireframeOnly: Boolean = false
    ): MeshObject = createCylinder(
        name = name,
        radiusTop = 0.001f,
        radiusBottom = radius,
        height = height,
        segments = segments,
        pos = pos,
        rot = rot,
        scale = scale,
        colorHex = colorHex,
        metallic = metallic,
        wireframeOnly = wireframeOnly
    )

    fun createPyramid(
        name: String = "Pyramid",
        size: Float = 1.4f,
        height: Float = 1.6f,
        pos: Vec3 = Vec3(0f, 0f, 0f),
        rot: Vec3 = Vec3(0f, 0f, 0f),
        scale: Vec3 = Vec3(1f, 1f, 1f),
        colorHex: String? = "#FFB300",
        metallic: Float = 0.5f,
        wireframeOnly: Boolean = false
    ): MeshObject {
        val h = size / 2f
        val yB = -height / 2f
        val yT = height / 2f
        val apex = transformPoint(Vec3(0f, yT, 0f), pos, rot, scale)
        val b0 = transformPoint(Vec3(-h, yB, -h), pos, rot, scale)
        val b1 = transformPoint(Vec3(h, yB, -h), pos, rot, scale)
        val b2 = transformPoint(Vec3(h, yB, h), pos, rot, scale)
        val b3 = transformPoint(Vec3(-h, yB, h), pos, rot, scale)
        val c = TriCollector()
        c.addTriFlat(b0, apex, b1)
        c.addTriFlat(b1, apex, b2)
        c.addTriFlat(b2, apex, b3)
        c.addTriFlat(b3, apex, b0)
        c.addTriFlat(b0, b1, b2)
        c.addTriFlat(b0, b2, b3)
        return c.build(name, colorHex, metallic, wireframeOnly)
    }

    fun createIcosahedron(
        name: String = "Icosahedron",
        radius: Float = 1.1f,
        pos: Vec3 = Vec3(0f, 0f, 0f),
        rot: Vec3 = Vec3(0f, 0f, 0f),
        scale: Vec3 = Vec3(1f, 1f, 1f),
        colorHex: String? = "#22D3A0",
        metallic: Float = 0.6f,
        wireframeOnly: Boolean = false
    ): MeshObject {
        val t = ((1.0 + sqrt(5.0)) / 2.0).toFloat()
        val raw = listOf(
            Vec3(-1f, t, 0f), Vec3(1f, t, 0f), Vec3(-1f, -t, 0f), Vec3(1f, -t, 0f),
            Vec3(0f, -1f, t), Vec3(0f, 1f, t), Vec3(0f, -1f, -t), Vec3(0f, 1f, -t),
            Vec3(t, 0f, -1f), Vec3(t, 0f, 1f), Vec3(-t, 0f, -1f), Vec3(-t, 0f, 1f)
        ).map { transformPoint(it.normalized() * radius, pos, rot, scale) }

        val faces = listOf(
            intArrayOf(0, 11, 5), intArrayOf(0, 5, 1), intArrayOf(0, 1, 7), intArrayOf(0, 7, 10), intArrayOf(0, 10, 11),
            intArrayOf(1, 5, 9), intArrayOf(5, 11, 4), intArrayOf(11, 10, 2), intArrayOf(10, 7, 6), intArrayOf(7, 1, 8),
            intArrayOf(3, 9, 4), intArrayOf(3, 4, 2), intArrayOf(3, 2, 6), intArrayOf(3, 6, 8), intArrayOf(3, 8, 9),
            intArrayOf(4, 9, 5), intArrayOf(2, 4, 11), intArrayOf(6, 2, 10), intArrayOf(8, 6, 7), intArrayOf(9, 8, 1)
        )
        val c = TriCollector()
        for (f in faces) {
            c.addTriFlat(raw[f[0]], raw[f[1]], raw[f[2]])
        }
        return c.build(name, colorHex, metallic, wireframeOnly)
    }

    fun createHelix(
        name: String = "Helix",
        radius: Float = 1.0f,
        height: Float = 3.0f,
        turns: Float = 3.5f,
        tubeRadius: Float = 0.14f,
        steps: Int = 96,
        tubeSegments: Int = 10,
        pos: Vec3 = Vec3(0f, 0f, 0f),
        rot: Vec3 = Vec3(0f, 0f, 0f),
        scale: Vec3 = Vec3(1f, 1f, 1f),
        colorHex: String? = "#00E5FF",
        metallic: Float = 0.6f,
        wireframeOnly: Boolean = false
    ): MeshObject {
        val nSteps = steps.coerceIn(24, 180)
        val nTube = tubeSegments.coerceIn(6, 18)
        val c = TriCollector()

        fun centerAt(i: Int): Vec3 {
            val frac = i.toFloat() / nSteps
            val ang = (2.0 * PI * turns * frac).toFloat()
            val y = (frac - 0.5f) * height
            return Vec3(cos(ang) * radius, y, sin(ang) * radius)
        }

        val rings = Array(nSteps + 1) { i ->
            val p = centerAt(i)
            val next = centerAt((i + 1).coerceAtMost(nSteps))
            val prev = centerAt((i - 1).coerceAtLeast(0))
            val tangent = (next - prev).normalized()
            val up = Vec3(0f, 1f, 0f)
            val right = tangent.cross(up).normalized()
            val binormal = right.cross(tangent).normalized()

            Array(nTube) { j ->
                val phi = (2.0 * PI * j / nTube).toFloat()
                val offset = right * (cos(phi) * tubeRadius) + binormal * (sin(phi) * tubeRadius)
                val worldPt = transformPoint(p + offset, pos, rot, scale)
                val worldN = transformNormal(offset.normalized(), rot)
                worldPt to worldN
            }
        }

        for (i in 0 until nSteps) {
            for (j in 0 until nTube) {
                val jn = (j + 1) % nTube
                val (p00, n00) = rings[i][j]
                val (p10, n10) = rings[i + 1][j]
                val (p11, n11) = rings[i + 1][jn]
                val (p01, n01) = rings[i][jn]
                c.addTriSmooth(p00, p10, p11, n00, n10, n11)
                c.addTriSmooth(p00, p11, p01, n00, n11, n01)
            }
        }
        return c.build(name, colorHex, metallic, wireframeOnly)
    }

    fun createSurface(
        name: String = "ParametricSurface",
        xMin: Float = -2.5f,
        xMax: Float = 2.5f,
        zMin: Float = -2.5f,
        zMax: Float = 2.5f,
        steps: Int = 22,
        pos: Vec3 = Vec3(0f, 0f, 0f),
        rot: Vec3 = Vec3(0f, 0f, 0f),
        scale: Vec3 = Vec3(1f, 1f, 1f),
        colorHex: String? = "#00E5FF",
        metallic: Float = 0.5f,
        wireframeOnly: Boolean = false,
        heightFn: (Float, Float) -> Float
    ): MeshObject {
        val n = steps.coerceIn(6, 44)
        val grid = Array(n + 1) { ix ->
            val x = xMin + (xMax - xMin) * ix / n
            Array(n + 1) { iz ->
                val z = zMin + (zMax - zMin) * iz / n
                val y = heightFn(x, z).let { if (it.isFinite()) it.coerceIn(-20f, 20f) else 0f }
                transformPoint(Vec3(x, y, z), pos, rot, scale)
            }
        }
        val c = TriCollector()
        for (ix in 0 until n) {
            for (iz in 0 until n) {
                val p00 = grid[ix][iz]
                val p10 = grid[ix + 1][iz]
                val p11 = grid[ix + 1][iz + 1]
                val p01 = grid[ix][iz + 1]
                c.addTriFlat(p00, p01, p11)
                c.addTriFlat(p00, p11, p10)
            }
        }
        return c.build(name, colorHex, metallic, wireframeOnly)
    }

    fun createGear(
        name: String = "Gear",
        teeth: Int = 12,
        outerRadius: Float = 1.4f,
        innerRadius: Float = 1.05f,
        thickness: Float = 0.35f,
        pos: Vec3 = Vec3(0f, 0f, 0f),
        rot: Vec3 = Vec3(0f, 0f, 0f),
        scale: Vec3 = Vec3(1f, 1f, 1f),
        colorHex: String? = "#FFB300",
        metallic: Float = 0.75f,
        wireframeOnly: Boolean = false
    ): MeshObject {
        val nTeeth = teeth.coerceIn(6, 36)
        val steps = nTeeth * 4
        val halfT = thickness / 2f
        val ring = Array(steps) { i ->
            val ang = (2.0 * PI * i / steps).toFloat()
            val r = if (i % 4 == 1 || i % 4 == 2) outerRadius else innerRadius
            cos(ang) * r to sin(ang) * r
        }
        val c = TriCollector()
        val topCenter = transformPoint(Vec3(0f, halfT, 0f), pos, rot, scale)
        val botCenter = transformPoint(Vec3(0f, -halfT, 0f), pos, rot, scale)
        for (i in 0 until steps) {
            val next = (i + 1) % steps
            val (x0, z0) = ring[i]
            val (x1, z1) = ring[next]
            val t0 = transformPoint(Vec3(x0, halfT, z0), pos, rot, scale)
            val t1 = transformPoint(Vec3(x1, halfT, z1), pos, rot, scale)
            val b0 = transformPoint(Vec3(x0, -halfT, z0), pos, rot, scale)
            val b1 = transformPoint(Vec3(x1, -halfT, z1), pos, rot, scale)
            c.addTriFlat(topCenter, t1, t0)
            c.addTriFlat(botCenter, b0, b1)
            c.addTriFlat(b0, t0, t1)
            c.addTriFlat(b0, t1, b1)
        }
        return c.build(name, colorHex, metallic, wireframeOnly)
    }

    fun createCustomMesh(
        name: String = "CustomMesh",
        vertices: List<Vec3>,
        faces: List<IntArray>,
        pos: Vec3 = Vec3(0f, 0f, 0f),
        rot: Vec3 = Vec3(0f, 0f, 0f),
        scale: Vec3 = Vec3(1f, 1f, 1f),
        colorHex: String? = "#00E5FF",
        metallic: Float = 0.5f,
        wireframeOnly: Boolean = false
    ): MeshObject {
        val transformed = vertices.map { transformPoint(it, pos, rot, scale) }
        val c = TriCollector()
        for (face in faces) {
            if (face.size < 3) continue
            val i0 = face[0]
            if (i0 !in transformed.indices) continue
            for (k in 1 until face.size - 1) {
                val i1 = face[k]
                val i2 = face[k + 1]
                if (i1 in transformed.indices && i2 in transformed.indices) {
                    c.addTriFlat(transformed[i0], transformed[i1], transformed[i2])
                }
            }
        }
        return c.build(name, colorHex, metallic, wireframeOnly)
    }

    fun defaultShowcaseScene(): Scene3D {
        return Scene3D(
            title = "Orbital Geodesic Gyroscope",
            objects = listOf(
                createIcosahedron(
                    name = "Core_Crystal",
                    radius = 0.85f,
                    pos = Vec3(0f, 0.2f, 0f),
                    colorHex = "#FFB300",
                    metallic = 0.7f
                ),
                createTorus(
                    name = "Outer_Ring_A",
                    majorRadius = 1.65f,
                    minorRadius = 0.12f,
                    rot = Vec3(28f, 0f, 18f),
                    pos = Vec3(0f, 0.2f, 0f),
                    colorHex = "#00E5FF",
                    metallic = 0.65f
                ),
                createTorus(
                    name = "Outer_Ring_B",
                    majorRadius = 1.35f,
                    minorRadius = 0.1f,
                    rot = Vec3(-35f, 45f, -20f),
                    pos = Vec3(0f, 0.2f, 0f),
                    colorHex = "#C792EA",
                    metallic = 0.65f
                ),
                createSphere(
                    name = "Satellite_Alpha",
                    radius = 0.22f,
                    pos = Vec3(1.65f, 0.5f, 0.4f),
                    colorHex = "#22D3A0",
                    metallic = 0.5f
                )
            ),
            showGrid = true,
            showAxes = true
        )
    }
}
