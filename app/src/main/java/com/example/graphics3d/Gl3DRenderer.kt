package com.example.graphics3d

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class Gl3DRenderer(
    private val onFpsUpdate: (Int) -> Unit = {}
) : GLSurfaceView.Renderer {

    private val sceneRef = AtomicReference(MeshBuilder.defaultShowcaseScene())

    @Volatile var yawDeg: Float = 38f
    @Volatile var pitchDeg: Float = 26f
    @Volatile var distance: Float = 6.2f
    @Volatile var panX: Float = 0f
    @Volatile var panY: Float = 0f
    @Volatile var autoRotate: Boolean = true
    @Volatile var wireframeOverlay: Boolean = false
    @Volatile var showGrid: Boolean = true

    private var litProgram = 0
    private var lineProgram = 0

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)

    private var gridVertexBuffer: FloatBuffer? = null
    private var gridVertexCount = 0
    private var axisVertexBuffer: FloatBuffer? = null
    private var axisColorBuffer: FloatBuffer? = null

    private var cachedRevision: Long = -1L
    private data class GlMeshBuffers(
        val vertexBuffer: FloatBuffer,
        val normalBuffer: FloatBuffer,
        val vertexCount: Int,
        val colorRgba: FloatArray,
        val metallic: Float,
        val wireframeOnly: Boolean
    )
    private var glMeshes: List<GlMeshBuffers> = emptyList()

    private var frameCount = 0
    private var lastFpsTimeNs = System.nanoTime()
    private var lastFrameTimeNs = System.nanoTime()

    fun setScene(scene: Scene3D) {
        sceneRef.set(scene)
    }

    fun resetCamera() {
        yawDeg = 38f
        pitchDeg = 26f
        distance = 6.2f
        panX = 0f
        panY = 0f
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Obsidian deep studio background (#0B1020)
        GLES20.glClearColor(0.043f, 0.063f, 0.125f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        litProgram = buildProgram(LIT_VERTEX_SHADER, LIT_FRAGMENT_SHADER)
        lineProgram = buildProgram(LINE_VERTEX_SHADER, LINE_FRAGMENT_SHADER)
        buildGridAndAxes()
        cachedRevision = -1L
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 48f, aspect, 0.1f, 120f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dtSec = ((now - lastFrameTimeNs) / 1_000_000_000f).coerceIn(0f, 0.1f)
        lastFrameTimeNs = now

        if (autoRotate) {
            yawDeg = (yawDeg + 20f * dtSec) % 360f
        }

        frameCount++
        if (now - lastFpsTimeNs >= 1_000_000_000L) {
            val fps = frameCount
            frameCount = 0
            lastFpsTimeNs = now
            onFpsUpdate(fps)
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val scene = sceneRef.get()
        if (scene.revision != cachedRevision) {
            uploadSceneMeshes(scene)
            cachedRevision = scene.revision
        }

        val yawRad = (yawDeg * PI / 180.0).toFloat()
        val pitchRad = (pitchDeg.coerceIn(-82f, 82f) * PI / 180.0).toFloat()
        val dist = distance.coerceIn(1.5f, 35f)

        val camX = panX + dist * cos(pitchRad) * sin(yawRad)
        val camY = panY + dist * sin(pitchRad)
        val camZ = dist * cos(pitchRad) * cos(yawRad)

        Matrix.setLookAtM(
            viewMatrix, 0,
            camX, camY, camZ,
            panX, panY, 0f,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        if (showGrid && scene.showGrid) {
            drawGridAndAxes(scene.showAxes)
        }

        drawMeshes(camX, camY, camZ, wireframeOverlay || scene.wireframeOverlay)
    }

    private fun uploadSceneMeshes(scene: Scene3D) {
        glMeshes = scene.objects.mapNotNull { obj ->
            if (obj.vertices.isEmpty() || obj.normals.size != obj.vertices.size) return@mapNotNull null
            GlMeshBuffers(
                vertexBuffer = toFloatBuffer(obj.vertices),
                normalBuffer = toFloatBuffer(obj.normals),
                vertexCount = obj.vertexCount,
                colorRgba = obj.colorRgba,
                metallic = obj.metallic,
                wireframeOnly = obj.wireframeOnly
            )
        }
    }

    private fun drawMeshes(camX: Float, camY: Float, camZ: Float, drawWireOverlay: Boolean) {
        if (litProgram == 0 || glMeshes.isEmpty()) return

        GLES20.glUseProgram(litProgram)
        val uMvp = GLES20.glGetUniformLocation(litProgram, "uMVPMatrix")
        val uColor = GLES20.glGetUniformLocation(litProgram, "uColor")
        val uCamPos = GLES20.glGetUniformLocation(litProgram, "uCamPos")
        val uMetallic = GLES20.glGetUniformLocation(litProgram, "uMetallic")
        val uUnlit = GLES20.glGetUniformLocation(litProgram, "uUnlit")

        val aPos = GLES20.glGetAttribLocation(litProgram, "aPosition")
        val aNorm = GLES20.glGetAttribLocation(litProgram, "aNormal")

        GLES20.glUniformMatrix4fv(uMvp, 1, false, vpMatrix, 0)
        GLES20.glUniform3f(uCamPos, camX, camY, camZ)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aNorm)

        for (mesh in glMeshes) {
            mesh.vertexBuffer.position(0)
            mesh.normalBuffer.position(0)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, mesh.vertexBuffer)
            GLES20.glVertexAttribPointer(aNorm, 3, GLES20.GL_FLOAT, false, 0, mesh.normalBuffer)

            if (!mesh.wireframeOnly) {
                GLES20.glUniform1f(uUnlit, 0f)
                GLES20.glUniform1f(uMetallic, mesh.metallic)
                GLES20.glUniform4fv(uColor, 1, mesh.colorRgba, 0)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount)
            }

            if (drawWireOverlay || mesh.wireframeOnly) {
                GLES20.glUniform1f(uUnlit, 1f)
                GLES20.glUniform4f(uColor, 0f, 0.92f, 1f, if (mesh.wireframeOnly) 0.9f else 0.32f)
                GLES20.glLineWidth(1.5f)
                GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, mesh.vertexCount)
            }
        }

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aNorm)
    }

    private fun buildGridAndAxes() {
        val gridLines = ArrayList<Float>()
        val extent = 6
        val y = -1.6f
        for (i in -extent..extent) {
            val f = i.toFloat()
            gridLines.add(-extent.toFloat()); gridLines.add(y); gridLines.add(f)
            gridLines.add(extent.toFloat()); gridLines.add(y); gridLines.add(f)
            gridLines.add(f); gridLines.add(y); gridLines.add(-extent.toFloat())
            gridLines.add(f); gridLines.add(y); gridLines.add(extent.toFloat())
        }
        gridVertexCount = gridLines.size / 3
        gridVertexBuffer = toFloatBuffer(gridLines.toFloatArray())

        val axisVerts = floatArrayOf(
            0f, y, 0f, 2.2f, y, 0f,      // X (red)
            0f, y, 0f, 0f, y + 2.2f, 0f, // Y (green)
            0f, y, 0f, 0f, y, 2.2f       // Z (cyan)
        )
        val axisCols = floatArrayOf(
            1f, 0.33f, 0.44f, 0.9f, 1f, 0.33f, 0.44f, 0.9f,
            0.13f, 0.83f, 0.63f, 0.9f, 0.13f, 0.83f, 0.63f, 0.9f,
            0f, 0.9f, 1f, 0.9f, 0f, 0.9f, 1f, 0.9f
        )
        axisVertexBuffer = toFloatBuffer(axisVerts)
        axisColorBuffer = toFloatBuffer(axisCols)
    }

    private fun drawGridAndAxes(showAxes: Boolean) {
        if (lineProgram == 0) return
        val gBuf = gridVertexBuffer ?: return

        GLES20.glUseProgram(lineProgram)
        val uMvp = GLES20.glGetUniformLocation(lineProgram, "uMVPMatrix")
        val uUseVertexColor = GLES20.glGetUniformLocation(lineProgram, "uUseVertexColor")
        val uUniformColor = GLES20.glGetUniformLocation(lineProgram, "uUniformColor")
        val aPos = GLES20.glGetAttribLocation(lineProgram, "aPosition")
        val aCol = GLES20.glGetAttribLocation(lineProgram, "aColor")

        GLES20.glUniformMatrix4fv(uMvp, 1, false, vpMatrix, 0)
        GLES20.glEnableVertexAttribArray(aPos)

        // Grid
        GLES20.glUniform1f(uUseVertexColor, 0f)
        GLES20.glUniform4f(uUniformColor, 0.16f, 0.25f, 0.42f, 0.55f)
        gBuf.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, gBuf)
        GLES20.glLineWidth(1.0f)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, gridVertexCount)

        // Axes
        if (showAxes && axisVertexBuffer != null && axisColorBuffer != null) {
            GLES20.glUniform1f(uUseVertexColor, 1f)
            GLES20.glEnableVertexAttribArray(aCol)
            axisVertexBuffer!!.position(0)
            axisColorBuffer!!.position(0)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, axisVertexBuffer)
            GLES20.glVertexAttribPointer(aCol, 4, GLES20.GL_FLOAT, false, 0, axisColorBuffer)
            GLES20.glLineWidth(2.6f)
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, 6)
            GLES20.glDisableVertexAttribArray(aCol)
        }

        GLES20.glDisableVertexAttribArray(aPos)
    }

    private fun toFloatBuffer(arr: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(arr.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(arr)
                position(0)
            }
    }

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        if (vs == 0 || fs == 0) return 0
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        return program
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }

    companion object {
        private const val LIT_VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            varying vec3 vWorldPos;
            void main() {
                vNormal = normalize(aNormal);
                vWorldPos = aPosition;
                gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
            }
        """

        private const val LIT_FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            uniform vec3 uCamPos;
            uniform float uMetallic;
            uniform float uUnlit;
            varying vec3 vNormal;
            varying vec3 vWorldPos;
            void main() {
                if (uUnlit > 0.5) {
                    gl_FragColor = uColor;
                    return;
                }
                vec3 N = normalize(vNormal);
                vec3 V = normalize(uCamPos - vWorldPos);
                if (dot(N, V) < 0.0) N = -N;

                vec3 keyDir = normalize(vec3(0.65, 0.85, 0.55));
                vec3 fillDir = normalize(vec3(-0.7, 0.25, -0.5));

                float keyDiff = max(dot(N, keyDir), 0.0);
                float fillDiff = max(dot(N, fillDir), 0.0);

                vec3 H = normalize(keyDir + V);
                float specPow = mix(16.0, 72.0, uMetallic);
                float spec = pow(max(dot(N, H), 0.0), specPow) * mix(0.25, 0.85, uMetallic);

                // Subtle cyan rim highlight for CAD depth
                float rim = pow(1.0 - max(dot(N, V), 0.0), 3.0) * 0.35;

                vec3 ambient = uColor.rgb * 0.24;
                vec3 diffuse = uColor.rgb * (keyDiff * 0.78 + fillDiff * 0.22);
                vec3 specular = vec3(0.95, 0.98, 1.0) * spec;
                vec3 rimColor = vec3(0.0, 0.9, 1.0) * rim;

                vec3 finalRgb = clamp(ambient + diffuse + specular + rimColor, 0.0, 1.0);
                gl_FragColor = vec4(finalRgb, uColor.a);
            }
        """

        private const val LINE_VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            attribute vec3 aPosition;
            attribute vec4 aColor;
            varying vec4 vColor;
            void main() {
                vColor = aColor;
                gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
            }
        """

        private const val LINE_FRAGMENT_SHADER = """
            precision mediump float;
            uniform float uUseVertexColor;
            uniform vec4 uUniformColor;
            varying vec4 vColor;
            void main() {
                gl_FragColor = (uUseVertexColor > 0.5) ? vColor : uUniformColor;
            }
        """
    }
}
