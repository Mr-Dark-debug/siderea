package io.github.mrdarkdebug.siderea.core.export

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Draws bitmaps onto a video encoder's input surface with OpenGL ES 2, giving each frame an exact presentation
 * time. A canvas would stamp frames with the wall clock instead, and an encoder that sees 100 "frames per second"
 * arriving for a 12 fps video starts dropping them.
 *
 * Everything here must be called from the thread that created the instance: an EGL context belongs to one thread.
 */
internal class GlFrameRenderer(
    surface: Surface,
    private val size: PixelSize,
) {
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var texture = 0
    private var gainLocation = 0
    private val vertices: FloatBuffer =
        ByteBuffer.allocateDirect(VERTEX_FLOATS * FLOAT_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer()

    /** The largest bitmap side the GPU accepts as a texture. */
    val maxTextureSize: Int

    init {
        try {
            setUpEgl(surface)
            program = buildProgram()
            gainLocation = GLES20.glGetUniformLocation(program, "uGain")
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            texture = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            val max = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, max, 0)
            maxTextureSize = max[0]
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            release()
            throw e
        }
    }

    /**
     * Draws the part [crop] (in the bitmap's own pixels) of [bitmap], multiplied by [gain], and queues it with
     * presentation time [presentationNs]. [bitmap] must be a CPU bitmap.
     */
    fun draw(
        bitmap: Bitmap,
        crop: PixelRect,
        gain: Float,
        presentationNs: Long,
    ) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        val u0 = crop.left.toFloat() / bitmap.width
        val u1 = crop.right.toFloat() / bitmap.width
        val v0 = crop.top.toFloat() / bitmap.height
        val v1 = crop.bottom.toFloat() / bitmap.height
        // x, y, u, v for a triangle strip. Bitmap rows run top to bottom, GL's y runs bottom to top.
        vertices.clear()
        vertices.put(floatArrayOf(-1f, -1f, u0, v1, 1f, -1f, u1, v1, -1f, 1f, u0, v0, 1f, 1f, u1, v0))
        GLES20.glViewport(0, 0, size.width, size.height)
        GLES20.glUseProgram(program)
        GLES20.glUniform1f(gainLocation, gain)
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        val tex = GLES20.glGetAttribLocation(program, "aTex")
        vertices.position(0)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, STRIDE, vertices)
        GLES20.glEnableVertexAttribArray(position)
        vertices.position(2)
        GLES20.glVertexAttribPointer(tex, 2, GLES20.GL_FLOAT, false, STRIDE, vertices)
        GLES20.glEnableVertexAttribArray(tex)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, QUAD_VERTICES)
        check(GLES20.glGetError() == GLES20.GL_NO_ERROR) { "OpenGL failed while drawing a frame" }
        EGLExt.eglPresentationTimeANDROID(display, eglSurface, presentationNs)
        check(EGL14.eglSwapBuffers(display, eglSurface)) { "The encoder surface refused a frame" }
    }

    fun release() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }

    private fun setUpEgl(surface: Surface) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "EGL would not start" }
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val attributes =
            intArrayOf(
                EGL14.EGL_RED_SIZE,
                BITS,
                EGL14.EGL_GREEN_SIZE,
                BITS,
                EGL14.EGL_BLUE_SIZE,
                BITS,
                EGL14.EGL_ALPHA_SIZE,
                BITS,
                EGL14.EGL_RENDERABLE_TYPE,
                EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID,
                1,
                EGL14.EGL_NONE,
            )
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) {
            "No EGL configuration can feed the encoder"
        }
        val config = checkNotNull(configs[0])
        context =
            EGL14.eglCreateContext(
                display,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                0,
            )
        check(context != EGL14.EGL_NO_CONTEXT) { "Could not create an OpenGL context" }
        eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "Could not attach OpenGL to the encoder" }
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "Could not use the OpenGL context" }
    }

    private fun buildProgram(): Int {
        val vertex = compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragment = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        val id = GLES20.glCreateProgram()
        GLES20.glAttachShader(id, vertex)
        GLES20.glAttachShader(id, fragment)
        GLES20.glLinkProgram(id)
        val status = IntArray(1)
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "Shader link failed: ${GLES20.glGetProgramInfoLog(id)}" }
        return id
    }

    private fun compile(
        type: Int,
        source: String,
    ): Int {
        val id = GLES20.glCreateShader(type)
        GLES20.glShaderSource(id, source)
        GLES20.glCompileShader(id)
        val status = IntArray(1)
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "Shader compile failed: ${GLES20.glGetShaderInfoLog(id)}" }
        return id
    }

    private companion object {
        const val EGL_RECORDABLE_ANDROID = 0x3142
        const val BITS = 8
        const val FLOAT_BYTES = 4
        const val VERTEX_FLOATS = 16
        const val QUAD_VERTICES = 4
        const val STRIDE = 4 * FLOAT_BYTES

        const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTex;
            varying vec2 vTex;
            void main() {
                gl_Position = aPosition;
                vTex = aTex;
            }
        """
        const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTex;
            uniform sampler2D uTex;
            uniform float uGain;
            void main() {
                vec4 c = texture2D(uTex, vTex);
                gl_FragColor = vec4(c.rgb * uGain, 1.0);
            }
        """
    }
}
