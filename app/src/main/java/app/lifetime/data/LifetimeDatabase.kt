package app.lifetime.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Moment::class], version = 1, exportSchema = true)
abstract class LifetimeDatabase : RoomDatabase() {
    abstract fun momentDao(): MomentDao

    companion object {
        fun create(context: Context): LifetimeDatabase =
            Room.databaseBuilder(context, LifetimeDatabase::class.java, "lifetime.db").build()
    }
}
