package nl.constantdynamics.dagelijks

import java.io.File

/**
 * The app's private scratch space, inside its cache folder: the photo that was just
 * taken and the video that is being rendered live here until they are stored in the
 * user's own folder.
 */
object WorkFiles {

    private const val CAPTURE_FOLDER = "opname"
    private const val TIMELAPSE_FOLDER = "timelapse"

    fun captureFolder(cacheDir: File): File = File(cacheDir, CAPTURE_FOLDER).apply { mkdirs() }

    fun timelapseFolder(cacheDir: File): File = File(cacheDir, TIMELAPSE_FOLDER).apply { mkdirs() }

    /**
     * Removes what was left when the app was stopped in the middle of a photo or a
     * render. Only for the moment the app starts, when nothing can be in use yet.
     */
    fun clearLeftovers(cacheDir: File) {
        for (name in listOf(CAPTURE_FOLDER, TIMELAPSE_FOLDER)) {
            File(cacheDir, name).listFiles()?.forEach { it.deleteRecursively() }
        }
    }
}
