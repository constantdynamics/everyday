package nl.constantdynamics.dagelijks.timelapse

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Writes still images to an MP4 file as H.264 video, using only what Android itself
 * provides: MediaCodec for encoding and MediaMuxer for the MP4 container.
 *
 * Pictures reach the encoder through its input surface, drawn with a few lines of
 * OpenGL. That is the route every phone's hardware encoder supports, so there is no
 * guessing about pixel formats.
 *
 * Everything here must be called from one and the same thread.
 */
class VideoEncoder(
    output: File,
    val width: Int,
    val height: Int,
    frameRate: Int,
    bitRate: Int,
) : AutoCloseable {

    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var inputSurface: Surface? = null
    private var drawer: SurfaceDrawer? = null
    private val bufferInfo = MediaCodec.BufferInfo()
    private var videoTrack = -1
    private var muxerStarted = false
    private var samplesWritten = 0

    init {
        try {
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                // Label the colours the standard way for HD video, so computers show them correctly.
                setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
            }
            val encoder = createEncoder(format)
            codec = encoder
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = encoder.createInputSurface()
            inputSurface = surface
            encoder.start()
            drawer = SurfaceDrawer(surface)
            muxer = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (failure: Exception) {
            close()
            throw IOException("The video encoder could not be started for ${width}x$height", failure)
        }
    }

    /** Sets the picture that the following [writeFrame] calls will show. */
    fun setPicture(picture: Bitmap) {
        requireNotNull(drawer).upload(picture)
    }

    /** Adds one video frame showing the current picture at the given moment. */
    fun writeFrame(presentationTimeNanos: Long) {
        val surface = requireNotNull(drawer)
        drain(endOfStream = false)
        surface.draw()
        surface.setPresentationTime(presentationTimeNanos)
        surface.swapBuffers()
    }

    /** Flushes the encoder and completes the file. Call once, after the last frame. */
    fun finish() {
        requireNotNull(codec).signalEndOfInputStream()
        drain(endOfStream = true)
        if (samplesWritten == 0) throw IOException("The encoder produced no video")
        muxer?.stop()
        muxerStarted = false
    }

    override fun close() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { drawer?.release() }
        runCatching { inputSurface?.release() }
        if (muxerStarted) runCatching { muxer?.stop() }
        runCatching { muxer?.release() }
        codec = null
        drawer = null
        inputSurface = null
        muxer = null
    }

    /** Moves whatever the encoder has finished into the MP4 file. */
    private fun drain(endOfStream: Boolean) {
        val encoder = requireNotNull(codec)
        val writer = requireNotNull(muxer)
        var idleRounds = 0
        while (true) {
            val index = encoder.dequeueOutputBuffer(bufferInfo, if (endOfStream) TIMEOUT_MICROS else 0L)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                    // Waiting for the last frames; give up rather than hang forever.
                    if (++idleRounds > MAX_IDLE_ROUNDS) throw IOException("The video encoder stopped responding")
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) throw IOException("The video format changed half-way")
                    videoTrack = writer.addTrack(encoder.outputFormat)
                    writer.start()
                    muxerStarted = true
                }
                index >= 0 -> {
                    idleRounds = 0
                    val buffer = encoder.getOutputBuffer(index)
                    val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (buffer != null && bufferInfo.size > 0 && muxerStarted && !isConfig) {
                        buffer.position(bufferInfo.offset)
                        buffer.limit(bufferInfo.offset + bufferInfo.size)
                        writer.writeSampleData(videoTrack, buffer, bufferInfo)
                        samplesWritten++
                    }
                    encoder.releaseOutputBuffer(index, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
            }
        }
    }

    private fun createEncoder(format: MediaFormat): MediaCodec {
        // Ask for an encoder that really supports this frame size (a portrait 1080x1920
        // frame is not a given on every chip) before falling back to the default one.
        val name = runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format) }.getOrNull()
        return if (name != null) MediaCodec.createByCodecName(name) else MediaCodec.createEncoderByType(MIME_TYPE)
    }

    companion object {
        const val MIME_TYPE = "video/avc"
        private const val TIMEOUT_MICROS = 10_000L
        private const val MAX_IDLE_ROUNDS = 1_500 // 15 seconds without any output
    }
}

/**
 * Draws a bitmap across the whole encoder surface with OpenGL ES 2. The picture is
 * already composed at exactly the video size, so this is a plain full-surface copy.
 */
private class SurfaceDrawer(surface: Surface) {

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var texture = 0
    private var positionHandle = 0
    private var textureCoordinateHandle = 0
    private var samplerHandle = 0
    private val vertices: FloatBuffer = floatBuffer(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)

    // Bitmaps start at the top left, OpenGL at the bottom left: the vertical axis is flipped.
    private val textureCoordinates: FloatBuffer = floatBuffer(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f)

    init {
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) { "EGL could not be initialised" }

            val attributes = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) {
                "No suitable EGL configuration"
            }
            context = EGL14.eglCreateContext(
                display,
                configs[0],
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                0,
            )
            check(context != EGL14.EGL_NO_CONTEXT) { "No EGL context" }
            eglSurface = EGL14.eglCreateWindowSurface(display, configs[0], surface, intArrayOf(EGL14.EGL_NONE), 0)
            check(eglSurface != EGL14.EGL_NO_SURFACE) { "No EGL surface" }
            check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "EGL context could not be activated" }

            program = buildProgram()
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            textureCoordinateHandle = GLES20.glGetAttribLocation(program, "aTextureCoordinate")
            samplerHandle = GLES20.glGetUniformLocation(program, "uTexture")
            texture = createTexture()
        } catch (failure: Exception) {
            release()
            throw failure
        }
    }

    fun upload(picture: Bitmap) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, picture, 0)
    }

    fun draw() {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glUniform1i(samplerHandle, 0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glEnableVertexAttribArray(textureCoordinateHandle)
        GLES20.glVertexAttribPointer(textureCoordinateHandle, 2, GLES20.GL_FLOAT, false, 0, textureCoordinates)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(textureCoordinateHandle)
    }

    fun setPresentationTime(nanos: Long) {
        EGLExt.eglPresentationTimeANDROID(display, eglSurface, nanos)
    }

    fun swapBuffers() {
        if (!EGL14.eglSwapBuffers(display, eglSurface)) throw IOException("A video frame could not be handed to the encoder")
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

    private fun createTexture(): Int {
        val names = IntArray(1)
        GLES20.glGenTextures(1, names, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, names[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return names[0]
    }

    private fun buildProgram(): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        val handle = GLES20.glCreateProgram()
        GLES20.glAttachShader(handle, vertexShader)
        GLES20.glAttachShader(handle, fragmentShader)
        GLES20.glLinkProgram(handle)
        val status = IntArray(1)
        GLES20.glGetProgramiv(handle, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "Shader program did not link: " + GLES20.glGetProgramInfoLog(handle) }
        return handle
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "Shader did not compile: " + GLES20.glGetShaderInfoLog(shader) }
        return shader
    }

    private fun floatBuffer(vararg values: Float): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(values)
            position(0)
        }

    private companion object {
        /** Tells EGL the surface feeds a video encoder. */
        const val EGL_RECORDABLE_ANDROID = 0x3142

        const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTextureCoordinate;
            varying vec2 vTextureCoordinate;
            void main() {
                gl_Position = aPosition;
                vTextureCoordinate = aTextureCoordinate;
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTextureCoordinate;
            uniform sampler2D uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTextureCoordinate);
            }
        """
    }
}
