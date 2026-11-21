package com.example

import com.example.firebase.InputsSpecCodec
import com.example.python.PyBool
import com.example.python.PyFloat
import com.example.python.PyInterpreter
import com.example.python.PyStr
import com.example.python.PyVal
import com.example.ui.StarterTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PythonEngineTest {

    @Test
    fun allStarterScriptsExecuteWithoutError() {
        for (seed in StarterTemplates.scripts) {
            val engine = PyInterpreter()
            val result = engine.execute(seed.content)
            assertTrue(
                "Starter script '${seed.filename}' failed: ${result.stderr}",
                result.success
            )
            assertTrue(
                "Starter script '${seed.filename}' produced empty output",
                result.stdout.isNotBlank() || result.scene3D != null || result.plots.isNotEmpty()
            )
            if (seed.filename.endsWith("_3d.py")) {
                assertNotNull("3D script '${seed.filename}' should produce a Scene3D", result.scene3D)
                assertTrue(
                    "3D script '${seed.filename}' should have 3D objects",
                    result.scene3D!!.objects.isNotEmpty()
                )
            }
        }
    }

    @Test
    fun allStarterAutomatedAppsExecuteWithDefaultInputs() {
        for (app in StarterTemplates.automatedApps) {
            val engine = PyInterpreter()
            val inputs: Map<String, PyVal> = InputsSpecCodec.parse(app.inputsSpec).associate { field ->
                val raw = field.defaultValue
                val pyVal: PyVal = when (field.type) {
                    "toggle" -> PyBool(raw.equals("true", ignoreCase = true))
                    "slider", "number" -> PyFloat(raw.toDoubleOrNull() ?: 0.0)
                    else -> PyStr(raw)
                }
                field.key to pyVal
            } + mapOf("TICK" to PyFloat(1.0))

            val result = engine.execute(app.pythonCode, injectedGlobals = inputs)
            assertTrue(
                "Automated app '${app.name}' failed: ${result.stderr}",
                result.success
            )
            if (app.has3dViewport) {
                assertNotNull("3D automated app '${app.name}' should render a 3D scene", result.scene3D)
            }
        }
    }

    @Test
    fun corePython3AndScientificLibrariesWork() {
        val engine = PyInterpreter()
        val script = """
            import math, json, re, statistics, numpy as np, scipy, sympy, py3d

            class Vector:
                def __init__(self, x, y):
                    self.x = x
                    self.y = y
                def mag(self):
                    return math.sqrt(self.x ** 2 + self.y ** 2)

            v = Vector(3, 4)
            squares = [x * x for x in range(1, 6) if x % 2 == 1]
            arr = np.linspace(0, 10, 5)
            d = sympy.diff("3*x^2 + 2*x + 5")
            nums = re.findall(r"\d+", "a12b34c")
            data = json.loads('{"ok": true, "n": 42}')

            s = py3d.Scene("Test")
            s.add_gear(teeth=10, radius=1.2)
            s.render()

            print(f"mag={v.mag():.1f}|sq={squares}|sum={np.sum(arr):.1f}|diff={d}|nums={nums}|ok={data['ok']}")
        """.trimIndent()

        val result = engine.execute(script)
        assertTrue("Unexpected error: ${result.stderr}", result.success)
        assertTrue("Expected mag=5.0 in ${result.stdout}", result.stdout.contains("mag=5.0"))
        assertTrue("Expected [1, 9, 25] in ${result.stdout}", result.stdout.contains("[1, 9, 25]"))
        assertTrue("Expected sum=25.0 in ${result.stdout}", result.stdout.contains("sum=25.0"))
        assertTrue("Expected diff=6*x + 2 in ${result.stdout}", result.stdout.contains("6*x + 2"))
        val scene = result.scene3D
        assertNotNull("Expected 3D gear scene", scene)

        val obj = scene!!.exportToObj()
        val stl = scene.exportToAsciiStl()
        assertTrue(obj.contains("v ") && obj.contains("f "))
        assertTrue(stl.contains("solid ") && stl.contains("endsolid"))
        assertEquals(1, scene.objects.size)
    }
}
