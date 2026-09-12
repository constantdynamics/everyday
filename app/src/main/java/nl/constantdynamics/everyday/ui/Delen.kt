package nl.constantdynamics.everyday.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Deelt een foto via de Android-sharesheet. */
fun deelFoto(context: Context, uri: Uri) {
    val delen = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(delen, "Foto delen"))
}

/** Deelt een gerenderde video via de Android-sharesheet. */
fun deelVideo(context: Context, uri: Uri) {
    val delen = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(delen, "Video delen"))
}
