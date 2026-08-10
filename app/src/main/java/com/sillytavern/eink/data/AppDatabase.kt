package com.sillytavern.eink.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "chat_snapshots", primaryKeys = ["serverId", "avatar", "fileId"])
data class CachedChatEntity(
    val serverId: String,
    val avatar: String,
    val fileId: String,
    val revision: String?,
    val json: String,
    val updatedAt: Long,
)

@Dao
interface CachedChatDao {
    @Query("SELECT * FROM chat_snapshots WHERE serverId = :serverId AND avatar = :avatar AND fileId = :fileId")
    suspend fun get(serverId: String, avatar: String, fileId: String): CachedChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: CachedChatEntity)
}

@Database(entities = [CachedChatEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedChatDao(): CachedChatDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "eink-client.db")
                .build()
                .also { instance = it }
        }
    }
}
