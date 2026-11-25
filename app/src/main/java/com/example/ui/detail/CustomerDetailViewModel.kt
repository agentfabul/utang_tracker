package com.example.ui.detail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.R
import com.example.data.model.Customer
import com.example.data.model.TransactionType
import com.example.data.model.TransactionWithBalance
import com.example.data.model.UserProfile
import com.example.data.model.UtangTransaction
import com.example.data.repository.UtangRepository
import com.example.domain.export.CustomerReportExporter
import com.example.domain.export.ExportFormat
import com.example.domain.export.ExportedReport
import com.example.domain.util.MoneyFormatter
import com.example.ui.common.UiState
import com.example.ui.navigation.CustomerDetailRoute
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CustomerDetailData(
    val customer: Customer?,
    val transactions: List<UtangTransaction>,
    val transactionsWithBalance: List<TransactionWithBalance>,
    val storeName: String,
    val reminderTemplate: String
)

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: UtangRepository,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val route = savedStateHandle.toRoute<CustomerDetailRoute>()
    val customerId: String = route.customerId

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

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
    val uiState: StateFlow<UiState<CustomerDetailData>> = authUidFlow()
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) {
                flowOf(UiState.Loading)
            } else {
                combine(
                    repository.observeCustomer(uid, customerId),
                    repository.observeTransactions(uid, customerId),
                    repository.observeUserProfile(uid, defaultStoreName, defaultTemplate)
                ) { customer, transactions, profile ->
                    val withBalances = MoneyFormatter.computeRunningBalances(transactions)
                    UiState.Success(
                        CustomerDetailData(
                            customer = customer,
                            transactions = transactions,
                            transactionsWithBalance = withBalances,
                            storeName = profile.storeName,
                            reminderTemplate = profile.reminderTemplate
                        )
                    ) as UiState<CustomerDetailData>
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

    fun updateCustomer(
        name: String,
        phone: String?,
        notes: String?,
        creditLimitCentavos: Long?,
        onSuccess: () -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateCustomer(uid, customerId, name, phone, notes, creditLimitCentavos)
            _isSaving.value = false
            result.onSuccess {
                _actionMessage.value = context.getString(R.string.customer_saved_message)
                onSuccess()
            }.onFailure { error ->
                _actionMessage.value = error.localizedMessage ?: context.getString(R.string.error_generic)
            }
        }
    }

    fun deleteCustomer(
        transactions: List<UtangTransaction>,
        onDeleted: () -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteCustomerAndTransactions(
                uid = uid,
                customerId = customerId,
                cachedTransactionIds = transactions.map { it.id }
            )
            _isSaving.value = false
            result.onSuccess {
                _actionMessage.value = context.getString(R.string.customer_deleted_message)
                onDeleted()
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

    fun payFullBalance(customer: Customer, onSuccess: () -> Unit) {
        if (customer.balanceCentavos <= 0L) return
        addTransaction(
            customer = customer,
            type = TransactionType.PAYMENT,
            amountCentavos = customer.balanceCentavos,
            description = context.getString(R.string.pay_full_description),
            note = null,
            date = Timestamp.now(),
            onSuccess = onSuccess
        )
    }

    fun updateTransaction(
        oldTransaction: UtangTransaction,
        allTransactions: List<UtangTransaction>,
        newType: TransactionType,
        newAmountCentavos: Long,
        newDescription: String?,
        newNote: String?,
        newDate: Timestamp,
        onSuccess: () -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateTransaction(
                uid = uid,
                customerId = customerId,
                oldTransaction = oldTransaction,
                allCustomerTransactions = allTransactions,
                newType = newType,
                newAmountCentavos = newAmountCentavos,
                newDescription = newDescription,
                newNote = newNote,
                newDate = newDate
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

    fun deleteTransaction(
        transaction: UtangTransaction,
        allTransactions: List<UtangTransaction>,
        onSuccess: () -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteTransaction(
                uid = uid,
                customerId = customerId,
                transaction = transaction,
                allCustomerTransactions = allTransactions
            )
            _isSaving.value = false
            result.onSuccess {
                _actionMessage.value = context.getString(R.string.tx_deleted_message)
                onSuccess()
            }.onFailure { error ->
                _actionMessage.value = error.localizedMessage ?: context.getString(R.string.error_generic)
            }
        }
    }

    fun exportReport(
        customer: Customer,
        transactionsWithBalance: List<TransactionWithBalance>,
        storeName: String,
        format: ExportFormat,
        onExportReady: (ExportedReport) -> Unit
    ) {
        if (_isExporting.value) return
        viewModelScope.launch {
            _isExporting.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    CustomerReportExporter.exportReport(
                        context = context,
                        customer = customer,
                        transactionsWithBalance = transactionsWithBalance,
                        storeName = storeName,
                        format = format
                    )
                }
            }
            _isExporting.value = false
            result.onSuccess { report ->
                onExportReady(report)
            }.onFailure {
                _actionMessage.value = context.getString(R.string.export_error_failed)
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
