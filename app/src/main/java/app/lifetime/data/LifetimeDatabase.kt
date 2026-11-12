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

        /** v3 makes relativePath unique so two rows cannot share one media file. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
