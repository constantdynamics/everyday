package nl.constantdynamics.dagelijks.timelapse

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Android's video writer notes the Android version, and on some phones the make and
 * model, in every MP4 it writes. These tests build small files with the same layout and
 * check that exactly that part disappears.
 */
class Mp4MetadataTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private fun box(type: String, vararg content: ByteArray): ByteArray {
        val body = content.fold(ByteArray(0)) { all, part -> all + part }
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            writeInt(8 + body.size)
            write(type.toByteArray(Charsets.ISO_8859_1))
            write(body)
        }
        return out.toByteArray()
    }

    /** A box whose length is written as a 64-bit number, the way the video data usually is. */
    private fun largeBox(type: String, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            writeInt(1)
            write(type.toByteArray(Charsets.ISO_8859_1))
            writeLong(16L + body.size)
            write(body)
        }
        return out.toByteArray()
    }

    private fun text(value: String) = value.toByteArray(Charsets.ISO_8859_1)

    private fun ByteArray.contains(needle: String): Boolean {
        val wanted = text(needle)
        return (0..size - wanted.size).any { start -> wanted.indices.all { this[start + it] == wanted[it] } }
    }

    private fun ByteArray.indexOf(part: ByteArray): Int =
        (0..size - part.size).first { start -> part.indices.all { this[start + it] == part[it] } }

    private val fileType = box("ftyp", text("mp42"), ByteArray(8))
    private val pictures = largeBox("mdat", ByteArray(5000) { (it % 251).toByte() })
    private val movieHeader = box("mvhd", ByteArray(100) { 7 })
    private val deviceDetails = box(
        "meta",
        box("hdlr", ByteArray(8), text("mdta"), ByteArray(13)),
        box("keys", text("com.android.version"), text("com.android.manufacturer"), text("com.android.model")),
        box("ilst", text("16"), text("Google"), text("Pixel 9")),
    )
    private val place = box("udta", box("©xyz", text("+52.3702+004.8952/")))
    private val track = box("trak", box("tkhd", ByteArray(84) { 3 }), box("mdia", ByteArray(300) { 5 }))

    private fun video(vararg movieParts: ByteArray): File =
        temporary.newFile().apply { writeBytes(fileType + pictures + box("moov", *movieParts)) }

    @Test
    fun `the phone's details are gone and everything else is exactly as it was`() {
        val file = video(movieHeader, place, deviceDetails, track)
        val before = file.readBytes()
        assertTrue(before.contains("com.android.version"))

        assertEquals(2, Mp4Metadata.strip(file))

        val after = file.readBytes()
        assertEquals(before.size, after.size)
        for (gone in listOf("com.android", "Google", "Pixel 9", "+52.3702", "meta", "udta", "mdta")) {
            assertFalse("\"$gone\" is still in the file", after.contains(gone))
        }
        // The picture data, the movie header and the track have not moved or changed.
        for (kept in listOf(fileType, pictures, movieHeader, track)) {
            assertEquals(before.indexOf(kept), after.indexOf(kept))
        }
        // Where the details were there is now padding of the same length.
        val start = before.indexOf(deviceDetails)
        assertEquals("free", String(after, start + 4, 4, Charsets.ISO_8859_1))
        assertArrayEquals(before.copyOfRange(start, start + 4), after.copyOfRange(start, start + 4))
        assertTrue(after.copyOfRange(start + 8, start + deviceDetails.size).all { it == 0.toByte() })
    }

    @Test
    fun `doing it twice changes nothing more`() {
        val file = video(movieHeader, deviceDetails, track)
        Mp4Metadata.strip(file)
        val once = file.readBytes()

        assertEquals(0, Mp4Metadata.strip(file))
        assertArrayEquals(once, file.readBytes())
    }

    @Test
    fun `a video without such details is left alone`() {
        val file = video(movieHeader, track)
        val before = file.readBytes()

        assertEquals(0, Mp4Metadata.strip(file))
        assertArrayEquals(before, file.readBytes())
    }

    @Test
    fun `a metadata box deeper down, inside a track, is none of our business`() {
        val trackWithItsOwn = box("trak", box("tkhd", ByteArray(84)), box("meta", text("iets van de track zelf")))
        val file = video(movieHeader, trackWithItsOwn)
        val before = file.readBytes()

        assertEquals(0, Mp4Metadata.strip(file))
        assertArrayEquals(before, file.readBytes())
    }

    @Test
    fun `a file that is not a proper video is not touched`() {
        val nonsense = temporary.newFile().apply { writeBytes(ByteArray(300) { (it * 31).toByte() }) }
        val before = nonsense.readBytes()
        assertEquals(0, Mp4Metadata.strip(nonsense))
        assertArrayEquals(before, nonsense.readBytes())

        // A box that claims to be longer than the file: stop there rather than guess.
        val cutShort = temporary.newFile().apply { writeBytes((fileType + box("moov", movieHeader, deviceDetails)).dropLast(20).toByteArray()) }
        val cut = cutShort.readBytes()
        assertEquals(0, Mp4Metadata.strip(cutShort))
        assertArrayEquals(cut, cutShort.readBytes())

        val empty = temporary.newFile()
        assertEquals(0, Mp4Metadata.strip(empty))
        assertEquals(0L, empty.length())
    }
}
