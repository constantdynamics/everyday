package nl.constantdynamics.everyday.data.video

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
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
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Schrijft losse beelden weg als een H.264-video. De beelden worden als bitmap
 * aangeleverd en via een kleine OpenGL-brug op de invoer-surface van de encoder
 * gezet; dat is de route die op elk toestel werkt.
 */
class VideoSchrijver(
    private val breedte: Int,
    private val hoogte: Int,
    private val fps: Int,
    bestandsBeschrijver: FileDescriptor,
    private val muziek: GecodeerdeMuziek? = null,
) {

    private val codec: MediaCodec = MediaCodec.createEncoderByType(MIME)
    private val muxer: MediaMuxer = MediaMuxer(bestandsBeschrijver, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val bufferInfo = MediaCodec.BufferInfo()
    private var invoerSurface: Surface? = null
    private var tekenaar: SurfaceTekenaar? = null
    private var spoor = -1
    private var muziekSpoor = -1
    private var muxerLoopt = false

    fun start() {
        val formaat = MediaFormat.createVideoFormat(MIME, breedte, hoogte).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitsnelheid())
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec.configure(formaat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        invoerSurface = surface
        tekenaar = SurfaceTekenaar(surface)
        codec.start()
    }

    /** [presentatieNanos] bepaalt wanneer het beeld in de video verschijnt. */
    fun schrijfBeeld(beeld: Bitmap, presentatieNanos: Long) {
        val huidigeTekenaar = tekenaar ?: error("Eerst start() aanroepen")
        leegUitvoer(wachtOpEinde = false)
        huidigeTekenaar.teken(beeld)
        huidigeTekenaar.zetTijd(presentatieNanos)
        huidigeTekenaar.wissel()
    }

    fun rondAf() {
        codec.signalEndOfInputStream()
        leegUitvoer(wachtOpEinde = true)
    }

    fun sluit() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { tekenaar?.sluit() }
        runCatching { invoerSurface?.release() }
        if (muxerLoopt) runCatching { muxer.stop() }
        runCatching { muxer.release() }
        tekenaar = null
        invoerSurface = null
    }

    private fun leegUitvoer(wachtOpEinde: Boolean) {
        while (true) {
            val index = codec.dequeueOutputBuffer(bufferInfo, if (wachtOpEinde) WACHT_MICROS else 0L)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!wachtOpEinde) return
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!muxerLoopt) { "Het videoformaat mag maar één keer veranderen" }
                    spoor = muxer.addTrack(codec.outputFormat)
                    // Alle sporen moeten bekend zijn voordat de muxer start.
                    muziek?.let { muziekSpoor = muxer.addTrack(it.formaat) }
                    muxer.start()
                    muxerLoopt = true
                    schrijfMuziek()
                }
                index >= 0 -> {
                    val buffer = codec.getOutputBuffer(index)
                    if (buffer != null && bufferInfo.size > 0 && muxerLoopt &&
                        (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                    ) {
                        buffer.position(bufferInfo.offset)
                        buffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(spoor, buffer, bufferInfo)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
            }
        }
    }

    private fun schrijfMuziek() {
        val spoorMuziek = muziek ?: return
        if (muziekSpoor < 0) return
        val info = MediaCodec.BufferInfo()
        for (brok in spoorMuziek.brokken) {
            val buffer = ByteBuffer.wrap(brok.gegevens)
            info.set(0, brok.gegevens.size, brok.tijdMicros, brok.vlaggen)
            runCatching { muxer.writeSampleData(muziekSpoor, buffer, info) }
        }
    }

    private fun bitsnelheid(): Int =
        (breedte.toLong() * hoogte * fps / 8).toInt().coerceIn(2_000_000, 30_000_000)

    private companion object {
        const val MIME = "video/avc"
        const val WACHT_MICROS = 10_000L
    }
}

/**
 * Zet bitmaps op een Surface via OpenGL ES 2. Het beeld is op dat moment al volledig
 * samengesteld, dus dit is niet meer dan een volledige-schermtekening.
 */
private class SurfaceTekenaar(surface: Surface) {

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var programma = 0
    private var textuur = 0
    private val hoekpunten: ByteBuffer
    private val textuurpunten: ByteBuffer

    init {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "Geen EGL-display beschikbaar" }
        val versie = IntArray(2)
        check(EGL14.eglInitialize(display, versie, 0, versie, 1)) { "EGL kon niet starten" }

        val configKenmerken = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_OPNEEMBAAR, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val aantal = IntArray(1)
        check(
            EGL14.eglChooseConfig(display, configKenmerken, 0, configs, 0, 1, aantal, 0) &&
                aantal[0] > 0,
        ) { "Geen geschikte EGL-configuratie gevonden" }

        context = EGL14.eglCreateContext(
            display,
            configs[0],
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
            0,
        )
        check(context != EGL14.EGL_NO_CONTEXT) { "Geen EGL-context" }

        eglSurface = EGL14.eglCreateWindowSurface(
            display,
            configs[0],
            surface,
            intArrayOf(EGL14.EGL_NONE),
            0,
        )
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "Geen EGL-surface" }
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) {
            "EGL-context kon niet actief worden gemaakt"
        }

        hoekpunten = maakBuffer(
            floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f),
        )
        // Bitmaps beginnen linksboven, OpenGL linksonder: de v-as staat daarom omgekeerd.
        textuurpunten = maakBuffer(
            floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f),
        )
        programma = maakProgramma()
        textuur = maakTextuur()
    }

    fun teken(beeld: Bitmap) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(programma)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textuur)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, beeld, 0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programma, "uTextuur"), 0)

        val positie = GLES20.glGetAttribLocation(programma, "aPositie")
        val textuurPlaats = GLES20.glGetAttribLocation(programma, "aTextuur")
        GLES20.glEnableVertexAttribArray(positie)
        GLES20.glVertexAttribPointer(positie, 2, GLES20.GL_FLOAT, false, 0, hoekpunten)
        GLES20.glEnableVertexAttribArray(textuurPlaats)
        GLES20.glVertexAttribPointer(textuurPlaats, 2, GLES20.GL_FLOAT, false, 0, textuurpunten)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positie)
        GLES20.glDisableVertexAttribArray(textuurPlaats)
    }

    fun zetTijd(nanos: Long) {
        EGLExt.eglPresentationTimeANDROID(display, eglSurface, nanos)
    }

    fun wissel() {
        EGL14.eglSwapBuffers(display, eglSurface)
    }

    fun sluit() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }

    private fun maakBuffer(waarden: FloatArray): ByteBuffer =
        ByteBuffer.allocateDirect(waarden.size * 4).order(ByteOrder.nativeOrder()).apply {
            asFloatBuffer().put(waarden)
            position(0)
        }

    private fun maakTextuur(): Int {
        val namen = IntArray(1)
        GLES20.glGenTextures(1, namen, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, namen[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return namen[0]
    }

    private fun maakProgramma(): Int {
        val hoekpuntSchaduw = maakSchaduw(GLES20.GL_VERTEX_SHADER, HOEKPUNT_BRON)
        val kleurSchaduw = maakSchaduw(GLES20.GL_FRAGMENT_SHADER, KLEUR_BRON)
        val programma = GLES20.glCreateProgram()
        GLES20.glAttachShader(programma, hoekpuntSchaduw)
        GLES20.glAttachShader(programma, kleurSchaduw)
        GLES20.glLinkProgram(programma)
        val stand = IntArray(1)
        GLES20.glGetProgramiv(programma, GLES20.GL_LINK_STATUS, stand, 0)
        check(stand[0] == GLES20.GL_TRUE) {
            "Shaderprogramma kon niet worden gekoppeld: " + GLES20.glGetProgramInfoLog(programma)
        }
        return programma
    }

    private fun maakSchaduw(soort: Int, bron: String): Int {
        val schaduw = GLES20.glCreateShader(soort)
        GLES20.glShaderSource(schaduw, bron)
        GLES20.glCompileShader(schaduw)
        val stand = IntArray(1)
        GLES20.glGetShaderiv(schaduw, GLES20.GL_COMPILE_STATUS, stand, 0)
        check(stand[0] == GLES20.GL_TRUE) {
            "Shader kon niet worden vertaald: " + GLES20.glGetShaderInfoLog(schaduw)
        }
        return schaduw
    }

    private companion object {
        /** EGL_RECORDABLE_ANDROID; nodig om naar een encoder te mogen tekenen. */
        const val EGL_OPNEEMBAAR = 0x3142

        const val HOEKPUNT_BRON = """
            attribute vec4 aPositie;
            attribute vec2 aTextuur;
            varying vec2 vTextuur;
            void main() {
                gl_Position = aPositie;
                vTextuur = aTextuur;
            }
        """

        const val KLEUR_BRON = """
            precision mediump float;
            varying vec2 vTextuur;
            uniform sampler2D uTextuur;
            void main() {
                gl_FragColor = texture2D(uTextuur, vTextuur);
            }
        """
    }
}
