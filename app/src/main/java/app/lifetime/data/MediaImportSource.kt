package app.lifetime.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.InputStream

internal fun openMediaSource(context: Context, uri: Uri): InputStream? {
    val resolver = context.contentResolver
    // Keep the document URI and its user-granted access. Converting it to MediaStore
    // can lose that grant or return a redacted stream instead of the selected original.
    // setRequireOriginal is only appropriate for direct MediaStore sources.
    return openOriginalOrPlain(
        useOriginal = Build.VERSION.SDK_INT >= 29 && uri.authority == MediaStore.AUTHORITY &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED,
        openOriginal = { resolver.openInputStream(MediaStore.setRequireOriginal(uri)) },
        openPlain = { resolver.openInputStream(uri) },
    )
}
