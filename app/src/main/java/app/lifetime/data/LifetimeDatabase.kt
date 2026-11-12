package app.lifetime.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Moment::class], version = 3, exportSchema = true)
abstract class LifetimeDatabase : RoomDatabase() {
    abstract fun momentDao(): MomentDao

    companion object {
        /** v2 adds captions. Existing moments keep everything and start without one. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE moments ADD COLUMN caption TEXT")
            }
        }

        /**
         * v3 makes relativePath unique so two rows cannot share one media file. Existing
         * duplicates collapse into the lowest id, which takes a duplicate's caption if it has
         * none. Only rows are removed; the shared media file stays.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE moments SET caption = (
                        SELECT d.caption FROM moments d
                        WHERE d.relativePath = moments.relativePath AND d.id <> moments.id
                            AND d.caption IS NOT NULL AND TRIM(d.caption) <> ''
                        ORDER BY d.id LIMIT 1
                    )
                    WHERE (caption IS NULL OR TRIM(caption) = '')
                        AND id IN (SELECT MIN(id) FROM moments GROUP BY relativePath HAVING COUNT(*) > 1)
                        AND EXISTS (
                            SELECT 1 FROM moments d
                            WHERE d.relativePath = moments.relativePath AND d.id <> moments.id
                                AND d.caption IS NOT NULL AND TRIM(d.caption) <> ''
                        )
                    """.trimIndent(),
                )
                db.execSQL("DELETE FROM moments WHERE id NOT IN (SELECT MIN(id) FROM moments GROUP BY relativePath)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_moments_relativePath` ON `moments` (`relativePath`)",
                )
            }
        }

        fun create(context: Context): LifetimeDatabase =
            Room.databaseBuilder(context, LifetimeDatabase::class.java, "lifetime.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
