package app.lifetime.ui.timeline

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Ask for the actual local files, not a photo-picker copy that can have GPS removed.
 * The user grants access only to the selected documents; no storage permission is needed.
 */
internal class OriginalMediaPicker : ActivityResultContract<Unit, List<Uri>>() {
    private val documents = ActivityResultContracts.OpenMultipleDocuments()

    override fun createIntent(context: Context, input: Unit): Intent =
        documents.createIntent(context, arrayOf("image/*", "video/*")).apply {
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> =
        documents.parseResult(resultCode, intent)
}
