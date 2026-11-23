package nl.constantdynamics.dagelijks.framework

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import nl.constantdynamics.dagelijks.core.Lens
import nl.constantdynamics.dagelijks.core.Mirroring
import nl.constantdynamics.dagelijks.ui.screens.CameraTags
import nl.constantdynamics.dagelijks.ui.screens.PreviousPhotoOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The overlay as it is really drawn. The stored photo has red on the left and green
 * on the right (seen as others see the subject). Over the mirrored live image of the
 * front camera it must appear the other way round; over the back camera unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-420dpi")
class OverlayTest {

    @get:Rule(order = 0)
    val registerActivity = RegisterComposeActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val storedPhoto = Fixtures.quarters(300, 400)

    private fun drawnOverlay(lens: Lens, alpha: Float = 1f, backdrop: Int = Color.BLACK): Bitmap {
        compose.setContent {
            Box(Modifier.size(150.dp, 200.dp).background(androidx.compose.ui.graphics.Color(backdrop))) {
                PreviousPhotoOverlay(storedPhoto, alpha, Mirroring.isOverlayMirrored(lens), Modifier.fillMaxSize())
            }
        }
        return compose.onNodeWithTag(CameraTags.OVERLAY).captureToImage().asAndroidBitmap()
    }

    @Test
    fun `over the back camera the overlay lies the same way round as the photo`() {
        assertEquals(listOf("rood", "groen", "blauw", "geel"), Fixtures.corners(drawnOverlay(Lens.BACK)))
    }

    @Test
    fun `over the front camera the overlay is mirrored left to right`() {
        assertEquals(listOf("groen", "rood", "geel", "blauw"), Fixtures.corners(drawnOverlay(Lens.FRONT)))
    }

    @Test
    fun `mirroring flips sideways only, never upside down`() {
        val overlay = drawnOverlay(Lens.FRONT)
        // Top row still holds the colours of the photo's top row.
        assertTrue(Fixtures.colourName(overlay.getPixel(overlay.width / 4, overlay.height / 4)) in setOf("rood", "groen"))
        assertTrue(Fixtures.colourName(overlay.getPixel(overlay.width / 4, overlay.height * 3 / 4)) in setOf("blauw", "geel"))
    }

    @Test
    fun `the overlay covers the viewfinder from edge to edge`() {
        val overlay = drawnOverlay(Lens.BACK)
        assertEquals("rood", Fixtures.colourName(overlay.getPixel(1, 1)))
        assertEquals("geel", Fixtures.colourName(overlay.getPixel(overlay.width - 2, overlay.height - 2)))
    }

    @Test
    fun `at 35 percent the live image clearly shows through`() {
        // Red at 35% over a white "live image" gives a light pink: about 35% of the way to red.
        val overlay = drawnOverlay(Lens.BACK, alpha = 0.35f, backdrop = Color.WHITE)
        val pixel = overlay.getPixel(overlay.width / 4, overlay.height / 4)
        assertTrue(Color.red(pixel) > 240)
        assertTrue("green ${Color.green(pixel)}", abs(Color.green(pixel) - 166) < 12)
        assertTrue("blue ${Color.blue(pixel)}", abs(Color.blue(pixel) - 166) < 12)
    }
}
