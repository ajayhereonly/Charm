package com.example.python

import com.example.graphics3d.MeshBuilder
import com.example.graphics3d.Vec3

object Py3DLib {

    fun createModule(name: String, interpreter: PyInterpreter): PyModule? = when (name) {
        "py3d", "trimesh", "cadquery", "open3d" -> buildPy3D(name, interpreter)
        else -> null
    }

    private fun extractVec3(v: PyVal?, default: Vec3): Vec3 {
        if (v == null || v is PyNone) return default
        val seq = when (v) {
            is PyTuple -> v.items
            is PyList -> v.items
            is PyArray -> v.data.map { PyFloat(it) }
            else -> return default
        }
        return Vec3(
            seq.getOrNull(0)?.asDouble()?.toFloat() ?: default.x,
            seq.getOrNull(1)?.asDouble()?.toFloat() ?: default.y,
            seq.getOrNull(2)?.asDouble()?.toFloat() ?: default.z
        )
    }

    private fun buildPy3D(modName: String, interpreter: PyInterpreter): PyModule {
        val m = PyModule(modName)

        fun createSceneInstance(title: String): PyInstance {
            interpreter.clearActiveScene(title)
            val cls = PyClass("Scene", emptyList())
            val inst = PyInstance(cls)
            var counter = 0

            fun nextName(prefix: String, kw: Map<String, PyVal>) =
                kw["name"]?.toDisplayString() ?: "${prefix}_${++counter}"

            inst.attrs["add_cube"] = PyBuiltin("add_cube") { a, kw, _ ->
                val size = (kw["size"] ?: a.getOrNull(0))?.asDouble()?.toFloat() ?: 1.2f
                val pos = extractVec3(kw["pos"] ?: a.getOrNull(1), Vec3(0f, 0f, 0f))
                val rot = extractVec3(kw["rot"], Vec3(0f, 0f, 0f))
                val scale = extractVec3(kw["scale"], Vec3(1f, 1f, 1f))
                val color = kw["color"]?.toDisplayString() ?: "#00E5FF"
                val metallic = kw["metallic"]?.asDouble()?.toFloat() ?: 0.45f
                val wire = kw["wireframe"]?.isTruthy() == true
                interpreter.addSceneObject(
                    MeshBuilder.createCube(nextName("Cube", kw), size, pos, rot, scale, color, metallic, wire)
                )
                inst
            }

            inst.attrs["add_box"] = inst.attrs["add_cube"]!!

            inst.attrs["add_sphere"] = PyBuiltin("add_sphere") { a, kw, _ ->
                val radius = (kw["radius"] ?: a.getOrNull(0))?.asDouble()?.toFloat() ?: 1.0f
                val segments = kw["segments"]?.asLong()?.toInt() ?: 24
                val pos = extractVec3(kw["pos"] ?: a.getOrNull(1), Vec3(0f, 0f, 0f))
                val rot = extractVec3(kw["rot"], Vec3(0f, 0f, 0f))
                val scale = extractVec3(kw["scale"], Vec3(1f, 1f, 1f))
                val color = kw["color"]?.toDisplayString() ?: "#FFB300"
                val metallic = kw["metallic"]?.asDouble()?.toFloat() ?: 0.55f
                val wire = kw["wireframe"]?.isTruthy() == true
                interpreter.addSceneObject(
                    MeshBuilder.createSphere(nextName("Sphere", kw), radius, segments, pos, rot, scale, color, metallic, wire)
                )
                inst
            }

            inst.attrs["add_torus"] = PyBuiltin("add_torus") { a, kw, _ ->
                val major = (kw["major"] ?: kw["major_radius"] ?: a.getOrNull(0))?.asDouble()?.toFloat() ?: 1.3f
                val minor = (kw["minor"] ?: kw["minor_radius"] ?: a.getOrNull(1))?.asDouble()?.toFloat() ?: 0.35f
                val segments = kw["segments"]?.asLong()?.toInt() ?: 32
                val pos = extractVec3(kw["pos"], Vec3(0f, 0f, 0f))
                val rot = extractVec3(kw["rot"], Vec3(0f, 0f, 0f))
                val scale = extractVec3(kw["scale"], Vec3(1f, 1f, 1f))
                val color = kw["color"]?.toDisplayString() ?: "#00E5FF"
                val metallic = kw["metallic"]?.asDouble()?.toFloat() ?: 0.65f
                val wire = kw["wireframe"]?.isTruthy() == true
                interpreter.addSceneObject(
                    MeshBuilder.createTorus(nextName("Torus", kw), major, minor, segments, 18, pos, rot, scale, color, metallic, wire)
                )
                inst
            }

            inst.attrs["add_cylinder"] = PyBuiltin("add_cylinder") { a, kw, _ ->
                val radius = (kw["radius"] ?: a.getOrNull(0))?.asDouble()?.toFloat() ?: 0.6f
                val height = (kw["height"] ?: a.getOrNull(1))?.asDouble()?.toFloat() ?: 1.8f
                val segments = kw["segments"]?.asLong()?.toInt() ?: 24
                val pos = extractVec3(kw["pos"], Vec3(0f, 0f, 0f))
                val rot = extractVec3(kw["rot"], Vec3(0f, 0f, 0f))
                val scale = extractVec3(kw["scale"], Vec3(1f, 1f, 1f))
                val color = kw["color"]?.toDisplayString() ?: "#C792EA"
                val metallic = kw["metallic"]?.asDouble()?.toFloat() ?: 0.5f
                val wire = kw["wireframe"]?.isTruthy() == true
                interpreter.addSceneObject(
                    MeshBuilder.createCylinder(nextName("Cylinder", kw), radius, radius, height, segments, pos, rot, scale, color, metallic, wire)
                )
                inst
            }

            inst.attrs["add_cone"] = PyBuiltin("add_cone") { a, kw, _ ->
                val radius = (kw["radius"] ?: a.getOrNull(0))?.asDouble()?.toFloat() ?: 0.8f
                val height = (kw["height"] ?: a.getOrNull(1))?.asDouble()?.toFloat() ?: 1.8f
                val segments = kw["segments"]?.asLong()?.toInt() ?: 24
                val pos = extractVec3(kw["pos"], Vec3(0f, 0f, 0f))
                val rot = extractVec3(kw["rot"], Vec3(0f, 0f, 0f))
                val scale = extractVec3(kw["scale"], Vec3(1f, 1f, 1f))
                val color = kw["color"]?.toDisplayString() ?: "#FF8A65"
                val metallic = kw["metallic"]?.asDouble()?.toFloat() ?: 0.45f
                val wire = kw["wireframe"]?.isTruthy() == true
                interpreter.addSceneObject(
                    MeshBuilder.createCone(nextName("Cone", kw), radius, height, segments, pos, rot, scale, color, metallic, wire)
                )
                inst
            }

            inst.attrs["add_pyramid"] = PyBuiltin("add_pyramid") { a, kw, _ ->
                val size = (kw["size"] ?: a.getOrNull(0))?.asDouble()?.toFloat() ?: 1.4f
                val height = (kw["height"] ?: a.getOrNull(1))?.asDouble()?.toFloat() ?: 1.6f
                val pos = extractVec3(kw["pos"], Vec3(0f, 0f, 0f))
                val rot = extractVec3(kw["rot"], Vec3(0f, 0f, 0f))
                val scale = extractVec3(kw["scale"], Vec3(1f, 1f, 1f))
                val color = kw["color"]?.toDisplayString() ?: "#FFB300"
                val metallic = kw["metallic"]?.asDouble()?.toFloat() ?: 0.5f
                val wire = kw["wireframe"]?.isTruthy() == true
                interpreter.addSceneObject(
                    MeshBuilder.createPyramid(nextName("Pyramid", kw), size, height, pos, rot, scale, color, metallic, wire)
                )
                inst
            }

            inst.attrs["add_icosahedron"] = PyBuiltin("add_icosahedron") { a, kw, _ ->
                val radius = (kw["radius"] ?: a.getOrNull(0))?.asDouble()?.toFloat() ?: 1.1f
                val pos = extractVec3(kw["pos"], Vec3(0f, 0f, 0f))
                val rot = extractVec3(kw["rot"], Vec3(0f, 0f, 0f))
                val scale = extractVec3(kw["scale"], Vec3(1f, 1f, 1f))
                val color = kw["color"]?.toDisplayString() ?: "#22D3A0"
                val metallic = kw["metallic"]?.asDouble()?.toFloat() ?: 0.65f
                val wire = kw["wireframe"]?.isTruthy() == true
                interpreter.addSceneObject(
                    MeshBuilder.createIcosahedron(nextName("Icosahedron", kw), radius, pos, rot, scale, color, metallic, wire)
                )
                inst
            }

            inst.attrs["add_helix"] = PyBuiltin("add_helix") { a, kw, _ ->
                val radius = (kw["radius"] ?: a.getOrNull(0))?.asDouble()?.toFloat() ?: 1.0f
                val height = (kw["height"] ?: a.getOrNull(1))?.asDouble()?.toFloat() ?: 3.0f
                val turns = (kw["turns"] ?: a.getOrNull(2))?.asDouble()?.toFloat() ?: 3.5f
                val tube = (kw["tube"] ?: kw["tube_radius"])?.asDouble()?.toFloat() ?: 0.12f
                val pos = extractVec3(kw["pos"], Vec3(0f, 0f, 0f))
                val rot = extractVec3(kw["rot"], Vec3(0f, 0f, 0f))
                val scale = extractVec3(kw["scale"], Vec3(1f, 1f, 1f))
                val color = kw["color"]?.toDisplayString() ?: "#00E5FF"
                val metallic = kw["metallic"]?.asDouble()?.toFloat() ?: 0.6f
                val wire = kw["wireframe"]?.isTruthy() == true
                interpreter.addSceneObject(
                    MeshBuilder.createHelix(nextName("Helix", kw), radius, height, turns, tube, 84, 10, pos, rot, scale, color, metallic, wire)
                )
                inst
            }

            inst.attrs["add_gear"] = PyBuiltin("add_gear") { a, kw, _ ->
                val teeth = (kw["teeth"] ?: a.getOrNull(0))?.asLong()?.toInt() ?: 12
                val outerR = (kw["outer_radius"] ?: kw["radius"] ?: a.getOrNull(1))?.asDouble()?.toFloat() ?: 1.4f
                val innerR = (kw["inner_radius"] ?: a.getOrNull(2))?.asDouble()?.toFloat() ?: (outerR * 0.75f)
                val thickness = (kw["thickness"] ?: kw["height"])?.asDouble()?.toFloat() ?: 0.35f
                val pos = extractVec3(kw["pos"], Vec3(0f, 0f, 0f))
                val rot = extractVec3(kw["rot"], Vec3(0f, 0f, 0f))
                val scale = extractVec3(kw["scale"], Vec3(1f, 1f, 1f))
                val color = kw["color"]?.toDisplayString() ?: "#FFB300"
                val metallic = kw["metallic"]?.asDouble()?.toFloat() ?: 0.75f
                val wire = kw["wireframe"]?.isTruthy() == true
                interpreter.addSceneObject(
                    MeshBuilder.createGear(nextName("Gear", kw), teeth, outerR, innerR, thickness, pos, rot, scale, color, metallic, wire)
                )
                inst
            }

            inst.attrs["add_surface"] = PyBuiltin("add_surface") { a, kw, interp ->
                val fn = a[0]
                val xRange = kw["x_range"]?.let { interp.toIterableList(it) }
                val zRange = kw["z_range"]?.let { interp.toIterableList(it) }
                val xMin = xRange?.getOrNull(0)?.asDouble()?.toFloat() ?: -2.4f
                val xMax = xRange?.getOrNull(1)?.asDouble()?.toFloat() ?: 2.4f
                val zMin = zRange?.getOrNull(0)?.asDouble()?.toFloat() ?: -2.4f
                val zMax = zRange?.getOrNull(1)?.asDouble()?.toFloat() ?: 2.4f
                val steps = kw["steps"]?.asLong()?.toInt() ?: 20
                val pos = extractVec3(kw["pos"], Vec3(0f, 0f, 0f))
                val rot = extractVec3(kw["rot"], Vec3(0f, 0f, 0f))
                val scale = extractVec3(kw["scale"], Vec3(1f, 1f, 1f))
                val color = kw["color"]?.toDisplayString() ?: "#00E5FF"
                val metallic = kw["metallic"]?.asDouble()?.toFloat() ?: 0.55f
                val wire = kw["wireframe"]?.isTruthy() == true
                interpreter.addSceneObject(
                    MeshBuilder.createSurface(nextName("Surface", kw), xMin, xMax, zMin, zMax, steps, pos, rot, scale, color, metallic, wire) { x, z ->
                        interp.callCallable(fn, listOf(PyFloat(x.toDouble()), PyFloat(z.toDouble())), emptyMap()).asDouble().toFloat()
                    }
                )
                inst
            }

            inst.attrs["add_mesh"] = PyBuiltin("add_mesh") { a, kw, interp ->
                val vertsRaw = interp.toIterableList(a[0])
                val facesRaw = interp.toIterableList(a[1])
                val verts = vertsRaw.map { extractVec3(it, Vec3(0f, 0f, 0f)) }
                val faces = facesRaw.map { face -> interp.toIterableList(face).map { it.asLong().toInt() }.toIntArray() }
                val pos = extractVec3(kw["pos"], Vec3(0f, 0f, 0f))
                val rot = extractVec3(kw["rot"], Vec3(0f, 0f, 0f))
                val scale = extractVec3(kw["scale"], Vec3(1f, 1f, 1f))
                val color = kw["color"]?.toDisplayString() ?: "#00E5FF"
                val metallic = kw["metallic"]?.asDouble()?.toFloat() ?: 0.5f
                val wire = kw["wireframe"]?.isTruthy() == true
                interpreter.addSceneObject(
                    MeshBuilder.createCustomMesh(nextName("Mesh", kw), verts, faces, pos, rot, scale, color, metallic, wire)
                )
                inst
            }

            inst.attrs["export_obj"] = PyBuiltin("export_obj") { _, _, _ ->
                PyStr(interpreter.getCurrentScene3D()?.exportToObj() ?: "")
            }

            inst.attrs["export_stl"] = PyBuiltin("export_stl") { _, _, _ ->
                PyStr(interpreter.getCurrentScene3D()?.exportToAsciiStl() ?: "")
            }

            inst.attrs["render"] = PyBuiltin("render") { _, _, _ ->
                val sc = interpreter.getCurrentScene3D()
                if (sc != null) {
                    interpreter.emitStdout("[3D GPU] Rendered '${sc.title}' (${sc.objects.size} objects, ${sc.totalTriangles} triangles, ${sc.totalVertices} vertices)\n")
                }
                inst
            }
            inst.attrs["show"] = inst.attrs["render"]!!

            return inst
        }

        m.attrs["Scene"] = PyBuiltin("Scene") { a, kw, _ ->
            val title = (kw["title"] ?: a.firstOrNull())?.toDisplayString() ?: "PyForge 3D Scene"
            createSceneInstance(title)
        }
        // Alias for cadquery Workplane / trimesh creation
        m.attrs["Workplane"] = m.attrs["Scene"]!!
        m.attrs["creation"] = PyModule("$modName.creation").apply {
            attrs["box"] = PyBuiltin("box") { a, kw, _ ->
                val s = createSceneInstance("Trimesh Box")
                interpreter.callCallable(s.attrs["add_cube"]!!, a, kw)
            }
            attrs["icosphere"] = PyBuiltin("icosphere") { a, kw, _ ->
                val s = createSceneInstance("Trimesh Sphere")
                interpreter.callCallable(s.attrs["add_sphere"]!!, a, kw)
            }
            attrs["torus"] = PyBuiltin("torus") { a, kw, _ ->
                val s = createSceneInstance("Trimesh Torus")
                interpreter.callCallable(s.attrs["add_torus"]!!, a, kw)
            }
            attrs["cylinder"] = PyBuiltin("cylinder") { a, kw, _ ->
                val s = createSceneInstance("Trimesh Cylinder")
                interpreter.callCallable(s.attrs["add_cylinder"]!!, a, kw)
            }
        }
        return m
    }
}
