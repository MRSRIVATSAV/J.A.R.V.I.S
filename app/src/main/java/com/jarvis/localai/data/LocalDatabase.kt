package com.jarvis.localai.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val role: String,
    val content: String,
    val imageUri: String? = null,
    val createdAt: Long
)

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val modelPath: String,
    val projectorPath: String?,
    val format: String,
    val quantization: String,
    val fileSizeBytes: Long,
    val createdAt: Long
)

@Dao
interface LocalDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversation(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteConversationMessages(conversationId: String)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt, id")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateMessage(id: Long, content: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearMessages(conversationId: String)

    @Query("SELECT * FROM models ORDER BY createdAt DESC")
    fun observeModels(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE id = :id")
    suspend fun getModel(id: String): ModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveModel(model: ModelEntity)

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun deleteModel(id: String)
}

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, ModelEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun dao(): LocalDao

    companion object {
        fun create(context: Context): LocalDatabase =
            Room.databaseBuilder(context, LocalDatabase::class.java, "jarvis-local.db").build()
    }
}