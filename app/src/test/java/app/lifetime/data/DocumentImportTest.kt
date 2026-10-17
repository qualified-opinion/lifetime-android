package app.lifetime.data

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.time.ZoneOffset
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33], application = Application::class)
class DocumentImportTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun selectedOriginalPhotoKeepsEmbeddedGpsDateAndBytesWithoutStoragePermission() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val original = tmp.newFile("original.jpg")
        // A real tiny JPEG fixture, independent of Robolectric's simulated bitmap encoder.
        val jpeg = javaClass.getResourceAsStream("/original-photo.jpg.base64")!!.bufferedReader().use { it.readText() }
        original.writeBytes(Base64.getMimeDecoder().decode(jpeg))
        ExifInterface(original).apply {
            setLatLong(10.3157, 123.8854)
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:10:16 14:32:15")
            setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, "+08:00")
            saveAttributes()
        }
        val authority = "com.android.providers.media.documents"
        val selected = Uri.parse("content://$authority/document/image%3A12")
        val provider = OriginalPhotoProvider(original)
        provider.attachInfo(context, ProviderInfo().apply {
            this.authority = authority
            exported = true
            grantUriPermissions = true
        })
        ShadowContentResolver.registerProviderInternal(authority, provider)
        val dao = TestDao()
        val storage = MediaStorage(File(tmp.root, "media"), File(tmp.root, "incoming")) { ZoneOffset.UTC }
        val repository = MomentRepository(context, dao, storage, MediaMetadataReader())

        val result = repository.import(listOf(selected))

        assertEquals("Original document should import with its embedded date", MomentRepository.ImportResult(1, 0), result)
        assertEquals(listOf(selected), provider.opened)
        val row = dao.rows.single()
        assertEquals(10.3157, row.latitude!!, 0.00001)
        assertEquals(123.8854, row.longitude!!, 0.00001)
        assertEquals(java.time.Instant.parse("2026-10-16T06:32:15Z").toEpochMilli(), row.capturedAt)
        assertArrayEquals(original.readBytes(), repository.fileFor(row).readBytes())
        val copiedGps = ExifInterface(repository.fileFor(row)).latLong!!
        assertEquals(row.latitude!!, copiedGps[0], 0.00001)
        assertEquals(row.longitude!!, copiedGps[1], 0.00001)
        assertTrue(original.exists())
    }

    private class OriginalPhotoProvider(private val file: File) : ContentProvider() {
        val opened = mutableListOf<Uri>()
        override fun onCreate() = true
        override fun getType(uri: Uri) = "image/jpeg"
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            assertEquals("r", mode)
            opened.add(uri)
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(file.name)) }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Read only")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = error("Read only")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = error("Read only")
    }

    private class TestDao : MomentDao {
        val rows = mutableListOf<Moment>()
        override fun observeAll() = flowOf(emptyList<Moment>())
        override suspend fun getAll() = rows.toList()
        override suspend fun get(id: Long) = rows.find { it.id == id }
        override suspend fun insert(moment: Moment): Long = (rows.size + 1L).also { rows.add(moment.copy(id = it)) }
        override suspend fun updateLocation(id: Long, latitude: Double, longitude: Double) = 0
        override suspend fun delete(moment: Moment) { rows.remove(moment) }
    }
}
