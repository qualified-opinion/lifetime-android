package app.lifetime.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Every Room version bump gets a case here: build the old database by hand, open it with the
 * current app code and its migrations, and check nothing was lost. Room itself throws if the
 * migrated tables don't match the current entities.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MigrationTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun migrationFromV1KeepsEveryMomentAndAddsAnEmptyCaption() {
        val name = "migration-test.db"
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }

        // A database exactly as version 1 of the app left it (from app/schemas/.../1.json).
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `moments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, " +
                    "`source` TEXT NOT NULL, `relativePath` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `capturedAt` INTEGER NOT NULL, " +
                    "`addedAt` INTEGER NOT NULL, `latitude` REAL, `longitude` REAL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, " +
                    "`durationMs` INTEGER NOT NULL, `sizeBytes` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_moments_capturedAt` ON `moments` (`capturedAt`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'e7b1db6f3eef4801fa58e3d429519f6f')")
            db.execSQL(
                "INSERT INTO moments VALUES (7, 'PHOTO', 'CAMERA', '2026/10/IMG_1.jpg', 'image/jpeg', 1760000000000, 1760000001000, " +
                    "52.52, 13.405, 4000, 3000, 0, 123456)",
            )
            db.version = 1
        }

        val database = Room.databaseBuilder(context, LifetimeDatabase::class.java, name)
            .addMigrations(LifetimeDatabase.MIGRATION_1_2)
            .build()
        try {
            val row = runBlocking { database.momentDao().get(7) }!!
            assertEquals(
                Moment(
                    id = 7, type = MediaType.PHOTO, source = MomentSource.CAMERA, relativePath = "2026/10/IMG_1.jpg",
                    mimeType = "image/jpeg", capturedAt = 1760000000000, addedAt = 1760000001000,
                    latitude = 52.52, longitude = 13.405, width = 4000, height = 3000, durationMs = 0, sizeBytes = 123456,
                    caption = null,
                ),
                row,
            )
            assertEquals(2, database.openHelper.readableDatabase.version)
            runBlocking { database.momentDao().updateCaption(7, "Saved after the upgrade") }
            assertEquals("Saved after the upgrade", runBlocking { database.momentDao().get(7) }!!.caption)
        } finally {
            database.close()
        }
    }
}
