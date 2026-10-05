package com.vaibhav.emicalc.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {

    @Query("SELECT * FROM loans ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun find(id: String): LoanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(loan: LoanEntity)

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history WHERE deletedAt IS NULL ORDER BY pinned DESC, createdAt DESC")
    fun observeActive(): Flow<List<HistoryEntryEntity>>

    @Query(
        "SELECT * FROM history WHERE deletedAt IS NULL AND kind = :kind " +
            "ORDER BY pinned DESC, createdAt DESC",
    )
    fun observeByKind(kind: String): Flow<List<HistoryEntryEntity>>

    @Query("SELECT * FROM history WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<HistoryEntryEntity>>

    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun find(id: String): HistoryEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: HistoryEntryEntity)

    @Query("UPDATE history SET noteText = :text, noteUpdatedAt = :at WHERE id = :id")
    suspend fun setNote(id: String, text: String?, at: Long?)

    @Query("UPDATE history SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    /** Soft delete. Pinned entries are skipped, so a pin also protects. */
    @Query("UPDATE history SET deletedAt = :at WHERE id = :id AND pinned = 0 AND deletedAt IS NULL")
    suspend fun softDelete(id: String, at: Long)

    @Query("UPDATE history SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun purge(id: String)

    @Query("DELETE FROM history WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeDeletedBefore(before: Long): Int

    @Query("UPDATE history SET deletedAt = :at WHERE pinned = 0 AND deletedAt IS NULL")
    suspend fun softDeleteAll(at: Long): Int

    @Query(
        "UPDATE history SET deletedAt = :at " +
            "WHERE kind = :kind AND pinned = 0 AND deletedAt IS NULL",
    )
    suspend fun softDeleteKind(kind: String, at: Long): Int
}

@Dao
interface SettlementDao {

    @Query("SELECT * FROM settlements ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SettlementEntity>>

    @Query("SELECT * FROM settlements WHERE id = :id")
    suspend fun find(id: String): SettlementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settlement: SettlementEntity)

    @Query("DELETE FROM settlements WHERE id = :id")
    suspend fun delete(id: String)
}
