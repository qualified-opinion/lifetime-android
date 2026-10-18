package app.lifetime.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Moment::class], version = 2, exportSchema = true)
abstract class LifetimeDatabase : RoomDatabase() {
    abstract fun momentDao(): MomentDao

    companion object {
        /** v2 adds captions. Existing moments keep everything and start without one. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE moments ADD COLUMN caption TEXT")
            }
        }

        fun create(context: Context): LifetimeDatabase =
            Room.databaseBuilder(context, LifetimeDatabase::class.java, "lifetime.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
