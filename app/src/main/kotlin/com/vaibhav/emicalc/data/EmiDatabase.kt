package com.vaibhav.emicalc.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [LoanEntity::class, HistoryEntryEntity::class, SettlementEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class EmiDatabase : RoomDatabase() {

    abstract fun loanDao(): LoanDao
    abstract fun historyDao(): HistoryDao
    abstract fun settlementDao(): SettlementDao

    companion object {
        fun build(context: Context): EmiDatabase =
            Room.databaseBuilder(context, EmiDatabase::class.java, "emicalc.db")
                // No fallbackToDestructiveMigration: this database holds settlements the
                // user may have spent half an hour on, so a schema change must ship a
                // real migration rather than quietly wiping their history.
                .build()
    }
}
