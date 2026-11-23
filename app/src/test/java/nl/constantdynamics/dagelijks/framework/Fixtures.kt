package nl.constantdynamics.dagelijks.framework

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlin.math.abs

/** Test pictures whose content makes every flip, turn or cut visible. */
object Fixtures {

    const val RED = Color.RED
    const val GREEN = 0xFF00AA00.toInt()
    const val BLUE = Color.BLUE
    const val YELLOW = Color.YELLOW

    /**
     * Four coloured quarters: red top left, green top right, blue bottom left and
     * yellow bottom right. No two arrangements of these look alike, so a mirrored or
     * wrongly turned picture is caught immediately.
     */
    fun quarters(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        fun fill(color: Int, left: Int, top: Int, right: Int, bottom: Int) {
            paint.color = color
            canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint)
        }
        fill(RED, 0, 0, width / 2, height / 2)
        fill(GREEN, width / 2, 0, width, height / 2)
        fill(BLUE, 0, height / 2, width / 2, height)
        fill(YELLOW, width / 2, height / 2, width, height)
        return bitmap
    }

    fun jpeg(bitmap: Bitmap, quality: Int = 95): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    /** Which of the four quarter colours a pixel is, despite JPEG noise. */
    fun colourName(pixel: Int): String {
        val candidates = mapOf("rood" to RED, "groen" to GREEN, "blauw" to BLUE, "geel" to YELLOW, "wit" to Color.WHITE, "zwart" to Color.BLACK)
        return candidates.minBy { (_, colour) ->
            abs(Color.red(pixel) - Color.red(colour)) +
                abs(Color.green(pixel) - Color.green(colour)) +
                abs(Color.blue(pixel) - Color.blue(colour))
        }.key
    }

    /** The colours in the middle of the four quarters: top left, top right, bottom left, bottom right. */
    fun corners(bitmap: Bitmap): List<String> = listOf(
        colourName(bitmap.getPixel(bitmap.width / 4, bitmap.height / 4)),
        colourName(bitmap.getPixel(bitmap.width * 3 / 4, bitmap.height / 4)),
        colourName(bitmap.getPixel(bitmap.width / 4, bitmap.height * 3 / 4)),
        colourName(bitmap.getPixel(bitmap.width * 3 / 4, bitmap.height * 3 / 4)),
    )

    /**
     * A simple "daily photo" for rendering screens: a face-like figure on a backdrop
     * whose tint depends on the day, so a series looks like a real project.
     */
    fun dailyPhoto(width: Int, height: Int, day: LocalDate, offset: Float = 0f): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val seed = day.toEpochDay().toInt()
        val backdrops = intArrayOf(0xFF8FB3C9.toInt(), 0xFFC9B38F.toInt(), 0xFF9BC98F.toInt(), 0xFFC99BB0.toInt(), 0xFFB5A7D6.toInt())
        canvas.drawColor(backdrops[Math.floorMod(seed, backdrops.size)])

        val unit = minOf(width, height) / 10f
        val centreX = width / 2f + offset * unit
        val centreY = height * 0.45f
        // Shoulders
        paint.color = 0xFF2F4F6F.toInt()
        canvas.drawOval(RectF(centreX - 4.2f * unit, centreY + 2.4f * unit, centreX + 4.2f * unit, centreY + 9f * unit), paint)
        // Head
        paint.color = 0xFFE8C4A0.toInt()
        canvas.drawOval(RectF(centreX - 2.3f * unit, centreY - 3f * unit, centreX + 2.3f * unit, centreY + 3f * unit), paint)
        // Hair
        paint.color = 0xFF5A3A22.toInt()
        canvas.drawOval(RectF(centreX - 2.4f * unit, centreY - 3.3f * unit, centreX + 2.4f * unit, centreY - 1.1f * unit), paint)
        // Eyes and mouth
        paint.color = 0xFF2A2A2A.toInt()
        canvas.drawCircle(centreX - 0.9f * unit, centreY - 0.3f * unit, 0.22f * unit, paint)
        canvas.drawCircle(centreX + 0.9f * unit, centreY - 0.3f * unit, 0.22f * unit, paint)
        paint.color = 0xFFB0584F.toInt()
        canvas.drawOval(RectF(centreX - 0.8f * unit, centreY + 1.2f * unit, centreX + 0.8f * unit, centreY + 1.6f * unit), paint)
        return bitmap
    }
}
