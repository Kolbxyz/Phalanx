package org.phalanx.android.ui.screens.room

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import org.phalanx.android.R

private const val MAX_PICKED = 10

/** Starts the system camera, photo picker, file picker or microphone permission prompt. */
@Stable
internal class AttachLaunchers(
    val photo: () -> Unit,
    val video: () -> Unit,
    val gallery: () -> Unit,
    val file: () -> Unit,
    val record: () -> Unit
)

/**
 * None of these need a storage or camera permission: the photo picker and document picker grant
 * access to what the user chose, and the camera app writes into one file this app shares with it.
 */
@Composable
internal fun rememberAttachLaunchers(viewModel: RoomViewModel): AttachLaunchers {
    val context = LocalContext.current
    val composer = viewModel.composer
    // Saved, because the camera app can push this one out of memory while it is open.
    var capture by rememberSaveable { mutableStateOf<Uri?>(null) }
    val onCaptured = { taken: Boolean ->
        capture?.takeIf { taken }?.let { composer.addAttachments(listOf(it)) }
        capture = null
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), onCaptured)
    val captureVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo(), onCaptured)
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED)) {
        composer.addAttachments(it)
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        composer.addAttachments(it)
    }
    val askMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) composer.startRecording() else viewModel.notify(R.string.composer_mic_denied)
    }

    fun camera(video: Boolean) {
        val uri = viewModel.captureUri(video)
        capture = uri
        try {
            if (video) captureVideo.launch(uri) else takePicture.launch(uri)
        } catch (_: ActivityNotFoundException) {
            capture = null
            viewModel.notify(R.string.composer_camera_missing)
        }
    }

    return AttachLaunchers(
        photo = { camera(video = false) },
        video = { camera(video = true) },
        gallery = {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        },
        file = { pickFiles.launch(arrayOf("*/*")) },
        record = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) composer.startRecording() else askMicrophone.launch(Manifest.permission.RECORD_AUDIO)
        }
    )
}
