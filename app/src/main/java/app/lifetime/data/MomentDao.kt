package app.lifetime.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MomentDao {
    /** Newest first: the top of the timeline is "now", scrolling down goes back in time. */
    @Query("SELECT * FROM moments ORDER BY capturedAt DESC, id DESC")
    fun observeAll(): Flow<List<Moment>>

    @Query("SELECT * FROM moments ORDER BY capturedAt ASC")
    suspend fun getAll(): List<Moment>

    @Query("SELECT * FROM moments WHERE id = :id")
    suspend fun get(id: Long): Moment?

    @Insert
    suspend fun insert(moment: Moment): Long

    @Query("UPDATE moments SET latitude = :latitude, longitude = :longitude WHERE id = :id AND latitude IS NULL AND longitude IS NULL")
    suspend fun updateLocation(id: Long, latitude: Double, longitude: Double): Int

    @Delete
    suspend fun delete(moment: Moment)
}
