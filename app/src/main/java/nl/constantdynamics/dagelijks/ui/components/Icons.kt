package nl.constantdynamics.dagelijks.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The handful of pictograms the app uses, drawn in the code itself as thin lines with
 * round ends, so no icon library has to be bundled. They are the app's own drawings.
 */
object AppIcons {
    val Back = icon("M19 12H5", "M11 6l-6 6 6 6")
    val Close = icon("M6 6l12 12", "M18 6L6 18")
    val Add = icon("M12 5v14", "M5 12h14", width = 2f)
    val Check = icon("M5 12.5l4.5 4.5L19 7.5", width = 2.25f)
    val Delete = icon("M4.5 7h15", "M9.5 7V4.5h5V7", "M6.5 7l0.9 12.5h9.2L17.5 7", "M10 11v5", "M14 11v5")
    val Play = icon("M8.5 5.5v13l10.5 -6.5z")
    val Camera = icon("M4 8h3l2 -3h6l2 3h3v11H4z", "M15.5 13a3.5 3.5 0 1 1 -7 0a3.5 3.5 0 0 1 7 0z")
    val FlashOn = icon("M13 3L5 14h6l-1 7 8 -11h-6z")
    val FlashOff = icon("M13 3L5 14h6l-1 7 8 -11h-6z", "M4 4l16 16")
    val Info = icon("M21 12a9 9 0 1 1 -18 0a9 9 0 0 1 18 0z", "M12 11v6", "M12 7.5v0.5")
    val Film = icon(
        "M6 5h12a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2H6a2 2 0 0 1 -2 -2V7a2 2 0 0 1 2 -2z",
        "M8 5v14", "M16 5v14", "M4 9h4", "M4 15h4", "M16 9h4", "M16 15h4",
    )
    val Folder = icon("M3.5 7.5a2 2 0 0 1 2 -2h4l2 2.5h7a2 2 0 0 1 2 2v7.5a2 2 0 0 1 -2 2h-13a2 2 0 0 1 -2 -2z")
    val More = icon("M12 5.5v0.01", "M12 12v0.01", "M12 18.5v0.01", width = 2.75f)
    val Edit = icon("M4 20l1 -4L16 5l3 3L8 19z", "M14 7l3 3")
    val Retake = icon("M19.5 12a7.5 7.5 0 1 1 -2.2 -5.3", "M19.5 4.5v4.5H15")
    val Calendar = icon(
        "M6 5.5h12a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2H6a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z",
        "M4 10h16", "M8.5 3.5v4", "M15.5 3.5v4",
    )
    val Layers = icon("M12 4l9 5 -9 5 -9 -5z", "M3 14l9 5 9 -5")

    /** Each path is one pen stroke; [width] is the thickness of the pen on a 24 by 24 sheet. */
    private fun icon(vararg paths: String, width: Float = 1.75f): ImageVector {
        val builder = ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        for (path in paths) {
            builder.addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
