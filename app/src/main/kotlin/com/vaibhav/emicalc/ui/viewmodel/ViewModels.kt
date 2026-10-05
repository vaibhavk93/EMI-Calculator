package com.vaibhav.emicalc.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vaibhav.emicalc.core.loan.EmiCalculator
import com.vaibhav.emicalc.core.loan.Loan
import com.vaibhav.emicalc.core.loan.Portfolio
import com.vaibhav.emicalc.core.session.HistoryEntry
import com.vaibhav.emicalc.core.session.InteractionKind
import com.vaibhav.emicalc.core.session.ResumePoint
import com.vaibhav.emicalc.data.HistoryRepository
import com.vaibhav.emicalc.data.LoanRepository
import com.vaibhav.emicalc.data.StoredLoan
import com.vaibhav.emicalc.data.ResumeRepository
import com.vaibhav.emicalc.di.AppContainer
import com.vaibhav.emicalc.ui.format.MoneyFormat
import com.vaibhav.emicalc.ui.state.CalcKey
import com.vaibhav.emicalc.ui.state.CalculatorReducer
import com.vaibhav.emicalc.ui.state.CalculatorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/** Hands view models what they need from [AppContainer]. Manual, because the graph is tiny. */
class ContainerViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(CalculatorViewModel::class.java) ->
            CalculatorViewModel(container.history) as T
        modelClass.isAssignableFrom(LoansViewModel::class.java) ->
            LoansViewModel(container.loans, container.history, container.resume) as T
        modelClass.isAssignableFrom(HistoryViewModel::class.java) ->
            HistoryViewModel(container.history) as T
        modelClass.isAssignableFrom(RunwayViewModel::class.java) ->
            RunwayViewModel(container.loans) as T
        modelClass.isAssignableFrom(FnfViewModel::class.java) ->
            FnfViewModel(container.history, container.resume) as T
        else -> error("Unknown view model ${modelClass.name}")
    }
}

@Composable
fun rememberCalculatorViewModel(container: AppContainer): CalculatorViewModel =
    viewModel(factory = remember(container) { ContainerViewModelFactory(container) })

@Composable
fun rememberLoansViewModel(container: AppContainer): LoansViewModel =
    viewModel(factory = remember(container) { ContainerViewModelFactory(container) })

@Composable
fun rememberHistoryViewModel(container: AppContainer): HistoryViewModel =
    viewModel(factory = remember(container) { ContainerViewModelFactory(container) })

@Composable
fun rememberRunwayViewModel(container: AppContainer): RunwayViewModel =
    viewModel(factory = remember(container) { ContainerViewModelFactory(container) })

@Composable
fun rememberFnfViewModel(container: AppContainer): FnfViewModel =
    viewModel(factory = remember(container) { ContainerViewModelFactory(container) })

/**
 * The calculator.
 *
 * All keypad behaviour lives in [CalculatorReducer], which is pure and unit-tested; this
 * class only persists what the reducer reports as committed.
 */
class CalculatorViewModel(private val history: HistoryRepository) : ViewModel() {

    private val _state = MutableStateFlow(CalculatorState())
    val state: StateFlow<CalculatorState> = _state.asStateFlow()

    private val _noteTarget = MutableStateFlow<String?>(null)
    val noteTarget: StateFlow<String?> = _noteTarget.asStateFlow()

    val tape: StateFlow<List<HistoryEntry>> = history
        .observeByKind(InteractionKind.CALCULATION)
        .map { it.take(30) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onKey(key: CalcKey) {
        val transition = CalculatorReducer.reduce(_state.value, key)
        _state.value = transition.state
        transition.committed?.let { committed ->
            viewModelScope.launch {
                history.record(
                    kind = InteractionKind.CALCULATION,
                    title = committed.expression,
                    summary = MoneyFormat.group(committed.result),
                    payload = committed.expression,
                )
            }
        }
    }

    /** Tapping a tape row puts that expression back on the display to build on. */
    fun reuse(entry: HistoryEntry) {
        _state.value = CalculatorState(expression = entry.payload)
        onKey(CalcKey.Equals)
    }

    fun promptNote(entryId: String) { _noteTarget.value = entryId }

    fun dismissNote() { _noteTarget.value = null }

    fun saveNote(entryId: String, text: String?) {
        viewModelScope.launch {
            history.annotate(entryId, text)
            _noteTarget.value = null
        }
    }
}

class LoansViewModel(
    private val loans: LoanRepository,
    private val history: HistoryRepository,
    private val resume: ResumeRepository,
) : ViewModel() {

    val stored: StateFlow<List<StoredLoan>> = loans.observeStored()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val portfolio: StateFlow<Portfolio> = loans.observeStored()
        .map { rows -> Portfolio(rows.map(StoredLoan::loan)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Portfolio(emptyList()))

    fun save(id: String?, loan: Loan) {
        viewModelScope.launch {
            val key = loans.save(id, loan)
            val emi = EmiCalculator.emi(loan.principal, loan.annualRatePercent, loan.tenureMonths)
            history.record(
                kind = InteractionKind.LOAN,
                title = loan.name,
                summary = "EMI ${MoneyFormat.rupees(emi)}",
                payload = key,
            )
            resume.clear(InteractionKind.LOAN)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { loans.delete(id) }
    }

    /** Called as the user edits, so a half-filled loan survives the app being killed. */
    fun saveDraft(route: String, draft: String, entryId: String?, focusField: String?) {
        viewModelScope.launch {
            resume.save(
                ResumePoint(
                    kind = InteractionKind.LOAN,
                    route = route,
                    draft = draft,
                    entryId = entryId,
                    savedAt = Instant.now(),
                    focusField = focusField,
                ),
            )
        }
    }

    suspend fun loadDraft(): ResumePoint? = resume.latestFor(InteractionKind.LOAN)

    suspend fun find(id: String): Loan? = loans.find(id)
}

class HistoryViewModel(private val history: HistoryRepository) : ViewModel() {

    private val _filter = MutableStateFlow<InteractionKind?>(null)
    val filter: StateFlow<InteractionKind?> = _filter.asStateFlow()

    val entries: StateFlow<List<HistoryEntry>> = history.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val deleted: StateFlow<List<HistoryEntry>> = history.observeDeleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(kind: InteractionKind?) { _filter.value = kind }

    fun annotate(id: String, text: String?) { viewModelScope.launch { history.annotate(id, text) } }

    fun setPinned(id: String, pinned: Boolean) { viewModelScope.launch { history.setPinned(id, pinned) } }

    fun delete(id: String) { viewModelScope.launch { history.delete(id) } }

    fun restore(id: String) { viewModelScope.launch { history.restore(id) } }

    fun purge(id: String) { viewModelScope.launch { history.purge(id) } }

    fun clearAll(kind: InteractionKind?) { viewModelScope.launch { history.clear(kind) } }
}

class RunwayViewModel(loans: LoanRepository) : ViewModel() {
    val portfolio: StateFlow<Portfolio> = loans.observeLoans()
        .map(::Portfolio)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Portfolio(emptyList()))
}

/**
 * The settlement form.
 *
 * This is the flow where resuming matters most. A settlement has twenty-odd inputs that
 * people look up across several sittings — a payslip here, an offer letter there — so
 * the draft is written on every change and offered back when they return.
 */
class FnfViewModel(
    private val history: HistoryRepository,
    private val resume: ResumeRepository,
) : ViewModel() {

    private val _draft = MutableStateFlow<String?>(null)
    val draft: StateFlow<String?> = _draft.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        viewModelScope.launch {
            _draft.value = resume.latestFor(InteractionKind.SETTLEMENT)?.draft
        }
    }

    fun saveDraft(draft: String, focusField: String? = null) {
        viewModelScope.launch {
            resume.save(
                ResumePoint(
                    kind = InteractionKind.SETTLEMENT,
                    route = "fnf",
                    draft = draft,
                    entryId = null,
                    savedAt = Instant.now(),
                    focusField = focusField,
                ),
            )
        }
    }

    /** Commits the finished settlement to history, then drops the draft. */
    fun save(label: String, netPayable: String, payload: String) {
        viewModelScope.launch {
            history.record(
                kind = InteractionKind.SETTLEMENT,
                title = label,
                summary = netPayable,
                payload = payload,
            )
            resume.clear(InteractionKind.SETTLEMENT)
            _saved.value = true
        }
    }

    fun acknowledgeSaved() { _saved.value = false }
}
