package app.lifetime.ui.timeline

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33], application = Application::class)
class OriginalMediaPickerTest {
    private val picker = OriginalMediaPicker()

    @Test fun requestsOriginalLocalDocumentsEvenWhenPhotoPickerIsAvailable() {
        val intent = picker.createIntent(RuntimeEnvironment.getApplication(), Unit)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE))
        assertEquals("*/*", intent.type)
        assertArrayEquals(arrayOf("image/*", "video/*"), intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false))
    }

    @Test fun cancellationIsEmptyAndSelectedDocumentUrisAreNotRewritten() {
        val first = Uri.parse("content://com.android.providers.media.documents/document/image%3A12")
        val second = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADCIM%2Fvideo.mp4")
        val result = Intent().apply {
            clipData = ClipData.newRawUri("Photos and videos", first).apply { addItem(ClipData.Item(second)) }
        }
        assertEquals(emptyList<Uri>(), picker.parseResult(Activity.RESULT_CANCELED, result))
        assertEquals(listOf(first, second), picker.parseResult(Activity.RESULT_OK, result))
        assertEquals(listOf(first), picker.parseResult(Activity.RESULT_OK, Intent().setData(first)))
    }
}
