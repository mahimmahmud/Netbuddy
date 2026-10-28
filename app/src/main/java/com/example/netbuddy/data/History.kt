package com.example.netbuddy.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tests")
data class TestRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val target: String,
    val time: Long = System.currentTimeMillis(),
    val status: String,
    val averageMs: Double? = null,
    val summary: String,
    val details: String
)

@Dao
interface TestDao {
    @Query("SELECT * FROM tests ORDER BY time DESC, id DESC")
    fun observe(): Flow<List<TestRecord>>
    @Insert suspend fun insert(record: TestRecord): Long
    @Query("DELETE FROM tests WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM tests") suspend fun clear()
}

@Database(entities = [TestRecord::class], version = 1, exportSchema = false)
abstract class NetBuddyDatabase : RoomDatabase() {
    abstract fun tests(): TestDao
    companion object {
        fun create(context: Context) = Room.databaseBuilder(context.applicationContext,
            NetBuddyDatabase::class.java, "netbuddy-complete.db").build()
    }
}

class HistoryRepository(private val dao: TestDao) {
    val records = dao.observe()
    suspend fun save(record: TestRecord) = dao.insert(record)
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun clear() = dao.clear()
}
