package app.lifetime.data

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.ZoneOffset
import java.util.zip.ZipFile

/** Real Room and real JSON under Robolectric: captions end up in the export. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CaptionStorageTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun exportWritesCaptionsOnlyForMomentsThatHaveOne() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, LifetimeDatabase::class.java).build()
        try {
            val storage = MediaStorage(File(tmp.root, "media"), File(tmp.root, "incoming")) { ZoneOffset.UTC }
            val repository = MomentRepository(context, database.momentDao(), storage, MediaMetadataReader())
            fun capture() = repository.newCaptureFile(MediaType.PHOTO).apply { writeBytes(ByteArray(16) { it.toByte() }) }
            val captioned = repository.addCaptured(capture(), MediaType.PHOTO, 1_000)
            val plain = repository.addCaptured(capture(), MediaType.PHOTO, 2_000)
            assertTrue(repository.setCaption(captioned.id, "Sunday market\nwith Oma"))

            val target = File(tmp.root, "export.zip")
            repository.exportZip(openTarget = { target.outputStream() }, deleteTarget = { target.delete() })

            val index = ZipFile(target).use { zip ->
                JSONObject(zip.getInputStream(zip.getEntry("moments.json")).bufferedReader().use { it.readText() })
            }
            assertEquals(1, index.getInt("version"))
            val entries = index.getJSONArray("moments").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            val byFile = entries.associateBy { it.getString("file") }
            assertEquals("Sunday market\nwith Oma", byFile.getValue("media/${captioned.relativePath}").getString("caption"))
            assertFalse(byFile.getValue("media/${plain.relativePath}").has("caption"))
        } finally {
            database.close()
        }
    }
}
