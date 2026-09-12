package nl.constantdynamics.everyday.data.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.roundToInt

/** Eén gecodeerd stukje geluid, klaar om in de video te worden gemuxed. */
class AudioBrok(val gegevens: ByteArray, val tijdMicros: Long, val vlaggen: Int)

class GecodeerdeMuziek(val formaat: MediaFormat, val brokken: List<AudioBrok>)

/**
 * Zet een muziekbestand van je toestel om in een AAC-spoor dat precies zo lang is
 * als de video, met een volumeregeling en een fade-out aan het eind.
 *
 * Het geluid wordt gedecodeerd naar PCM, bewerkt en opnieuw gecodeerd; anders kun je
 * niet inkorten en niet uitfaden.
 */
class MuziekEncoder(private val context: Context) {

    suspend fun maakSpoor(
        bronUri: Uri,
        duurMicros: Long,
        volume: Float,
        fadeSeconden: Float,
    ): GecodeerdeMuziek? = withContext(Dispatchers.Default) {
        if (duurMicros <= 0L) return@withContext null

        val extractor = MediaExtractor()
        val brokken = mutableListOf<AudioBrok>()
        var uitvoerFormaat: MediaFormat? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null

        try {
            context.contentResolver.openFileDescriptor(bronUri, "r").use { beschrijver ->
                requireNotNull(beschrijver) { "Het muziekbestand kon niet worden geopend" }
                extractor.setDataSource(beschrijver.fileDescriptor)
            }

            val spoor = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return@withContext null
            extractor.selectTrack(spoor)

            val bronFormaat = extractor.getTrackFormat(spoor)
            val bronMime = bronFormaat.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            val bemonstering = bronFormaat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val kanalen = bronFormaat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            // Meer dan stereo komt bij muziek nauwelijks voor en zou downmixen vergen.
            if (kanalen !in 1..2) return@withContext null

            decoder = MediaCodec.createDecoderByType(bronMime).apply {
                configure(bronFormaat, null, null, 0)
                start()
            }

            val encoderFormaat = MediaFormat.createAudioFormat(AAC, bemonstering, kanalen).apply {
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                )
                setInteger(MediaFormat.KEY_BIT_RATE, BITSNELHEID)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, INVOER_GROOTTE)
            }
            encoder = MediaCodec.createEncoderByType(AAC).apply {
                configure(encoderFormaat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            val decoderInfo = MediaCodec.BufferInfo()
            val encoderInfo = MediaCodec.BufferInfo()
            var bronKlaar = false
            var decoderKlaar = false
            var encoderKlaar = false
            var verwerkteFrames = 0L
            val totaalFrames = duurMicros * bemonstering / 1_000_000L
            val fadeFrames = (fadeSeconden * bemonstering).toLong().coerceAtLeast(1L)

            while (!encoderKlaar) {
                // 1. Brongeluid in de decoder.
                if (!bronKlaar) {
                    val index = decoder.dequeueInputBuffer(WACHT_MICROS)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index)
                        val gelezen = if (buffer != null) extractor.readSampleData(buffer, 0) else -1
                        if (gelezen < 0) {
                            decoder.queueInputBuffer(
                                index,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            bronKlaar = true
                        } else {
                            decoder.queueInputBuffer(index, 0, gelezen, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // 2. PCM eruit, bewerken en in de encoder.
                if (!decoderKlaar) {
                    val index = decoder.dequeueOutputBuffer(decoderInfo, WACHT_MICROS)
                    if (index >= 0) {
                        val buffer = decoder.getOutputBuffer(index)
                        if (buffer != null && decoderInfo.size > 0) {
                            buffer.position(decoderInfo.offset)
                            buffer.limit(decoderInfo.offset + decoderInfo.size)
                            verwerkteFrames = voerNaarEncoder(
                                encoder = encoder,
                                pcm = buffer,
                                kanalen = kanalen,
                                bemonstering = bemonstering,
                                volume = volume,
                                verwerkteFrames = verwerkteFrames,
                                totaalFrames = totaalFrames,
                                fadeFrames = fadeFrames,
                            )
                        }
                        val eindeBereikt =
                            (decoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 ||
                                verwerkteFrames >= totaalFrames
                        decoder.releaseOutputBuffer(index, false)
                        if (eindeBereikt) {
                            decoderKlaar = true
                            val einde = encoder.dequeueInputBuffer(WACHT_LANG_MICROS)
                            if (einde >= 0) {
                                encoder.queueInputBuffer(
                                    einde,
                                    0,
                                    0,
                                    verwerkteFrames * 1_000_000L / bemonstering,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                            }
                        }
                    }
                }

                // 3. AAC eruit en bewaren.
                val index = encoder.dequeueOutputBuffer(encoderInfo, WACHT_MICROS)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                        uitvoerFormaat = encoder.outputFormat
                    index >= 0 -> {
                        val buffer = encoder.getOutputBuffer(index)
                        val isConfig =
                            (encoderInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (buffer != null && encoderInfo.size > 0 && !isConfig) {
                            val gegevens = ByteArray(encoderInfo.size)
                            buffer.position(encoderInfo.offset)
                            buffer.get(gegevens)
                            brokken += AudioBrok(
                                gegevens = gegevens,
                                tijdMicros = encoderInfo.presentationTimeUs,
                                vlaggen = encoderInfo.flags,
                            )
                        }
                        encoder.releaseOutputBuffer(index, false)
                        if ((encoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderKlaar = true
                        }
                    }
                }
            }

            val formaat = uitvoerFormaat ?: return@withContext null
            if (brokken.isEmpty()) return@withContext null
            GecodeerdeMuziek(formaat, brokken)
        } catch (fout: Exception) {
            null
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { extractor.release() }
        }
    }

    /** Past volume en fade-out toe en schuift het resultaat de encoder in. */
    private fun voerNaarEncoder(
        encoder: MediaCodec,
        pcm: ByteBuffer,
        kanalen: Int,
        bemonstering: Int,
        volume: Float,
        verwerkteFrames: Long,
        totaalFrames: Long,
        fadeFrames: Long,
    ): Long {
        var frames = verwerkteFrames
        val monsters = pcm.remaining() / 2
        if (monsters <= 0) return frames

        val bewerkt = ByteBuffer.allocate(monsters * 2).order(ByteOrder.nativeOrder())
        val bron = pcm.order(ByteOrder.nativeOrder()).asShortBuffer()
        val doel = bewerkt.asShortBuffer()

        var geschreven = 0
        var teller = 0
        while (teller < monsters && frames < totaalFrames) {
            val restFrames = totaalFrames - frames
            val vervaging = if (restFrames < fadeFrames) {
                restFrames.toFloat() / fadeFrames
            } else {
                1f
            }
            repeat(kanalen) { kanaal ->
                if (teller + kanaal < monsters) {
                    val waarde = (bron.get(teller + kanaal) * volume * vervaging)
                        .roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    doel.put(geschreven + kanaal, waarde.toShort())
                }
            }
            teller += kanalen
            geschreven += kanalen
            frames++
        }
        if (geschreven == 0) return frames

        val index = encoder.dequeueInputBuffer(WACHT_LANG_MICROS)
        if (index < 0) return frames
        val invoer = encoder.getInputBuffer(index) ?: return frames
        invoer.clear()
        bewerkt.position(0)
        bewerkt.limit(geschreven * 2)
        val teSchrijven = min(invoer.remaining(), bewerkt.remaining())
        bewerkt.limit(teSchrijven)
        invoer.put(bewerkt)
        val startFrame = frames - geschreven / kanalen
        encoder.queueInputBuffer(
            index,
            0,
            teSchrijven,
            startFrame * 1_000_000L / bemonstering,
            0,
        )
        return frames
    }

    private companion object {
        const val AAC = "audio/mp4a-latm"
        const val BITSNELHEID = 128_000
        const val INVOER_GROOTTE = 16384
        const val WACHT_MICROS = 5_000L
        const val WACHT_LANG_MICROS = 50_000L
    }
}
