package nl.constantdynamics.dagelijks.timelapse

import java.io.File
import java.io.RandomAccessFile

/**
 * Removes what Android's MP4 writer adds to a video on its own account: the Android
 * version, and on some phones the make and model of the phone.
 *
 * An MP4 file is a row of boxes, each starting with its length and a four-letter type.
 * The writer puts those details in a "meta" box directly inside the "moov" box (and a
 * location, when it is given one, in a "udta" box next to it). Such a box is relabelled
 * as "free", which is padding that every player skips, and its content is wiped.
 * Nothing moves or changes size, so the picture itself is not touched.
 */
object Mp4Metadata {

    private val UNWANTED = setOf("meta", "udta")

    /** @return the number of boxes that were wiped */
    fun strip(file: File): Int {
        RandomAccessFile(file, "rw").use { video ->
            val moov = boxes(video, 0, video.length()).firstOrNull { it.type == "moov" } ?: return 0
            val unwanted = boxes(video, moov.contentStart, moov.end).filter { it.type in UNWANTED }
            unwanted.forEach { wipe(video, it) }
            if (unwanted.isNotEmpty()) video.fd.sync()
            return unwanted.size
        }
    }

    private class Box(val start: Long, val headerSize: Int, val size: Long, val type: String) {
        val contentStart: Long get() = start + headerSize
        val end: Long get() = start + size
    }

    /** The boxes lying one after another between [from] and [to]; stops at anything malformed. */
    private fun boxes(video: RandomAccessFile, from: Long, to: Long): List<Box> {
        val found = ArrayList<Box>()
        val header = ByteArray(8)
        var position = from
        while (position + 8 <= to) {
            video.seek(position)
            video.readFully(header)
            var size = ((header[0].toLong() and 0xFF) shl 24) or ((header[1].toLong() and 0xFF) shl 16) or
                ((header[2].toLong() and 0xFF) shl 8) or (header[3].toLong() and 0xFF)
            val type = String(header, 4, 4, Charsets.ISO_8859_1)
            var headerSize = 8
            when (size) {
                // The real length follows as a 64-bit number.
                1L -> {
                    if (position + 16 > to) return found
                    size = video.readLong()
                    headerSize = 16
                }
                // "Up to the end".
                0L -> size = to - position
            }
            if (size < headerSize || size > to - position) return found
            found += Box(position, headerSize, size, type)
            position += size
        }
        return found
    }

    private fun wipe(video: RandomAccessFile, box: Box) {
        video.seek(box.start + 4)
        video.write("free".toByteArray(Charsets.ISO_8859_1))
        video.seek(box.contentStart)
        val zeros = ByteArray(8 * 1024)
        var left = box.end - box.contentStart
        while (left > 0) {
            val count = minOf(left, zeros.size.toLong()).toInt()
            video.write(zeros, 0, count)
            left -= count
        }
    }
}
