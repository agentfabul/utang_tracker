package com.example.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.model.Customer
import com.example.data.model.TransactionType
import com.example.data.model.UtangTransaction
import com.example.data.repository.UtangRepository
import com.example.ui.common.UiState
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DashboardData(
    val storeName: String,
    val totalReceivablesCentavos: Long,
    val customersWithBalanceCount: Int,
    val collectedTodayCentavos: Long,
    val collectedThisWeekCentavos: Long,
    val topCustomers: List<Customer>,
    val allCustomers: List<Customer>
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: UtangRepository,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val defaultStoreName = context.getString(R.string.default_store_name)
    private val defaultTemplate = context.getString(R.string.default_reminder_template)

    private fun authUidFlow() = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser?.uid)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState<DashboardData>> = authUidFlow()
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) {
                flowOf(UiState.Loading)
            } else {
                val weekStartMillis = startOfWeekMillis()
                val todayStartMillis = startOfTodayMillis()
                val customersFlow = repository.observeCustomers(uid)
                val profileFlow = repository.observeUserProfile(uid, defaultStoreName, defaultTemplate)
                val recentTxFlow = repository.observeRecentTransactions(uid, customersFlow, weekStartMillis)

                combine(profileFlow, customersFlow, recentTxFlow) { profile, customers, recentTransactions ->
                    val totalReceivables = customers.sumOf { it.balanceCentavos }
                    val withBalanceCount = customers.count { it.balanceCentavos > 0L }
                    val top5 = customers
                        .filter { it.balanceCentavos > 0L }
                        .sortedByDescending { it.balanceCentavos }
                        .take(5)

                    val collectedToday = recentTransactions
                        .asSequence()
                        .filter { it.transactionType() == TransactionType.PAYMENT }
                        .filter { (it.date?.toDate()?.time ?: 0L) >= todayStartMillis }
                        .sumOf { it.amountCentavos }

                    val collectedWeek = recentTransactions
                        .asSequence()
                        .filter { it.transactionType() == TransactionType.PAYMENT }
                        .filter { (it.date?.toDate()?.time ?: 0L) >= weekStartMillis }
                        .sumOf { it.amountCentavos }

                    UiState.Success(
                        DashboardData(
                            storeName = profile.storeName,
                            totalReceivablesCentavos = totalReceivables,
                            customersWithBalanceCount = withBalanceCount,
                            collectedTodayCentavos = collectedToday,
                            collectedThisWeekCentavos = collectedWeek,
                            topCustomers = top5,
                            allCustomers = customers.sortedBy { it.nameLowercase }
                        )
                    ) as UiState<DashboardData>
                }.catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val root = firestoreError ?: error
                    emit(UiState.Error(root.localizedMessage ?: context.getString(R.string.error_generic)))
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    init {
        val uid = auth.currentUser?.uid
        if (!uid.isNullOrBlank()) {
            viewModelScope.launch {
                repository.ensureUserProfile(uid, defaultStoreName, defaultTemplate)
            }
        }
    }

    fun addCustomer(
        name: String,
        phone: String?,
        notes: String?,
        creditLimitCentavos: Long?,
        onSuccess: () -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.addCustomer(uid, name, phone, notes, creditLimitCentavos)
            _isSaving.value = false
            result.onSuccess {
                _actionMessage.value = context.getString(R.string.customer_saved_message)
                onSuccess()
            }.onFailure { error ->
                _actionMessage.value = error.localizedMessage ?: context.getString(R.string.error_generic)
            }
        }
    }

    fun addTransaction(
        customer: Customer,
        type: TransactionType,
        amountCentavos: Long,
        description: String?,
        note: String?,
        date: Timestamp,
        onSuccess: () -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.addTransaction(
                uid = uid,
                customer = customer,
                type = type,
                amountCentavos = amountCentavos,
                description = description,
                note = note,
                date = date
            )
            _isSaving.value = false
            result.onSuccess {
                _actionMessage.value = context.getString(R.string.tx_saved_message)
                onSuccess()
            }.onFailure { error ->
                _actionMessage.value = error.localizedMessage ?: context.getString(R.string.error_generic)
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    private fun startOfTodayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun startOfWeekMillis(): Long {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis > System.currentTimeMillis()) {
            cal.add(Calendar.WEEK_OF_YEAR, -1)
        }
        return cal.timeInMillis
    }
}
