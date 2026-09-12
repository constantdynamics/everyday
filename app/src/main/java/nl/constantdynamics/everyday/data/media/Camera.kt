package nl.constantdynamics.everyday.data.media

import android.content.Context
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** CameraX levert een ListenableFuture; hier maken we er een gewone suspend-aanroep van. */
suspend fun Context.cameraAanbieder(): ProcessCameraProvider = suspendCancellableCoroutine { vervolg ->
    val toekomst = ProcessCameraProvider.getInstance(this)
    toekomst.addListener(
        {
            try {
                vervolg.resume(toekomst.get())
            } catch (fout: Exception) {
                vervolg.resumeWithException(fout)
            }
        },
        ContextCompat.getMainExecutor(this),
    )
}

/** Maakt één foto en geeft de uri terug van het bestand zoals MediaStore het heeft opgeslagen. */
suspend fun ImageCapture.maakFoto(
    opties: ImageCapture.OutputFileOptions,
    uitvoerder: Executor,
): Uri = suspendCancellableCoroutine { vervolg ->
    takePicture(
        opties,
        uitvoerder,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(resultaat: ImageCapture.OutputFileResults) {
                val uri = resultaat.savedUri
                if (uri != null) {
                    vervolg.resume(uri)
                } else {
                    vervolg.resumeWithException(IllegalStateException("Geen uri na opslaan"))
                }
            }

            override fun onError(fout: ImageCaptureException) {
                vervolg.resumeWithException(fout)
            }
        },
    )
}
