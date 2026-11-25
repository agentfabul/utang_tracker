package com.example.ui.customers

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.model.Customer
import com.example.data.model.CustomerSortOrder
import com.example.data.repository.UtangRepository
import com.example.ui.common.UiState
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
import javax.inject.Inject

data class CustomersListData(
    val customers: List<Customer>,
    val totalCount: Int,
    val searchQuery: String,
    val sortOrder: CustomerSortOrder
)

@HiltViewModel
class CustomersViewModel @Inject constructor(
    private val repository: UtangRepository,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(CustomerSortOrder.HIGHEST_BALANCE)
    val sortOrder: StateFlow<CustomerSortOrder> = _sortOrder.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private fun authUidFlow() = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser?.uid)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState<CustomersListData>> = authUidFlow()
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) {
                flowOf(UiState.Loading)
            } else {
                combine(
                    repository.observeCustomers(uid),
                    _searchQuery,
                    _sortOrder
                ) { customers, query, sort ->
                    val normalizedQuery = query.trim().lowercase()
                    val filtered = if (normalizedQuery.isEmpty()) {
                        customers
                    } else {
                        customers.filter { it.nameLowercase.contains(normalizedQuery) }
                    }

                    val sorted = when (sort) {
                        CustomerSortOrder.HIGHEST_BALANCE -> filtered.sortedWith(
                            compareByDescending<Customer> { it.balanceCentavos }
                                .thenBy { it.nameLowercase }
                        )
                        CustomerSortOrder.NAME_ASC -> filtered.sortedBy { it.nameLowercase }
                        CustomerSortOrder.RECENT_ACTIVITY -> filtered.sortedWith(
                            compareByDescending<Customer> { it.lastActivityAt?.seconds ?: 0L }
                                .thenByDescending { it.lastActivityAt?.nanoseconds ?: 0 }
                                .thenBy { it.nameLowercase }
                        )
                    }

                    UiState.Success(
                        CustomersListData(
                            customers = sorted,
                            totalCount = customers.size,
                            searchQuery = query,
                            sortOrder = sort
                        )
                    ) as UiState<CustomersListData>
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

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: CustomerSortOrder) {
        _sortOrder.value = order
    }

    fun addCustomer(
        name: String,
        phone: String?,
        notes: String?,
        creditLimitCentavos: Long?,
        onSuccess: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.addCustomer(uid, name, phone, notes, creditLimitCentavos)
            _isSaving.value = false
            result.onSuccess { customerId ->
                _actionMessage.value = context.getString(R.string.customer_saved_message)
                onSuccess(customerId)
            }.onFailure { error ->
                _actionMessage.value = error.localizedMessage ?: context.getString(R.string.error_generic)
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
