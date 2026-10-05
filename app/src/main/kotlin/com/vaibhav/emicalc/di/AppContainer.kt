package com.vaibhav.emicalc.di

import android.content.Context
import com.vaibhav.emicalc.data.EmiDatabase
import com.vaibhav.emicalc.data.HistoryRepository
import com.vaibhav.emicalc.data.LoanRepository
import com.vaibhav.emicalc.data.ResumeRepository

/** The whole dependency graph. Small enough to assemble by hand. */
class AppContainer(context: Context) {

    private val database by lazy { EmiDatabase.build(context) }

    val loans by lazy { LoanRepository(database.loanDao()) }
    val history by lazy { HistoryRepository(database.historyDao()) }
    val resume by lazy { ResumeRepository(context.applicationContext) }
    val settlements by lazy { database.settlementDao() }
}
