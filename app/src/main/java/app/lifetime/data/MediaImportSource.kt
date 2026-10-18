package app.lifetime.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.InputStream

internal const val MEDIA_DOCUMENTS_AUTHORITY = "com.android.providers.media.documents"

internal fun openMediaSource(context: Context, uri: Uri): InputStream? {
    val resolver = context.contentResolver
    // Files picks from Images/Recent are media documents that can be redacted like MediaStore
    // items, so ask for the original first. Any failure falls back to the selected document,
    // which keeps its user-granted access. Other document providers serve the file as is.
    return openOriginalOrPlain(
        useOriginal = Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED,
        openOriginal = {
            // useOriginal already checks the API level; repeated here so lint can see it.
            if (Build.VERSION.SDK_INT < 29) null
            else originalMediaUri(uri) { MediaStore.getMediaUri(context, it) }
                ?.let { resolver.openInputStream(MediaStore.setRequireOriginal(it)) }
        },
        openPlain = { resolver.openInputStream(uri) },
    )
}

/** The MediaStore item to request the original of, or null when [uri] has none. */
internal fun originalMediaUri(uri: Uri, toMediaUri: (Uri) -> Uri?): Uri? = when (uri.authority) {
    MediaStore.AUTHORITY -> uri
    MEDIA_DOCUMENTS_AUTHORITY -> toMediaUri(uri)
    else -> null
}
