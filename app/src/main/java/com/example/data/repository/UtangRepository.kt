package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.data.model.Customer
import com.example.data.model.TransactionType
import com.example.data.model.UserProfile
import com.example.data.model.UtangTransaction
import com.example.domain.util.MoneyFormatter
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UtangRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
) {
    private fun requireUserId(expectedUid: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == expectedUid) { "Signed-in user changed; refusing cross-user operation." }
        return uid
    }

    private fun isDeviceOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun awaitCommitSafely(
        task: Task<Void>,
        operationType: OperationType,
        path: String
    ) {
        task.addOnFailureListener { error ->
            handleFirestoreError(error, operationType, path)
        }
        if (isDeviceOnline()) {
            withTimeoutOrNull(2500L) {
                task.await()
            }
        }
    }

    fun observeUserProfile(
        uid: String,
        defaultStoreName: String,
        defaultTemplate: String
    ): Flow<UserProfile> = flow {
        if (uid.isBlank()) {
            emit(UserProfile(storeName = defaultStoreName, reminderTemplate = defaultTemplate))
            return@flow
        }
        val path = "users/$uid"
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        val profile = snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                        UserProfile(
                            storeName = profile?.storeName?.takeIf { it.isNotBlank() } ?: defaultStoreName,
                            reminderTemplate = profile?.reminderTemplate?.takeIf { it.isNotBlank() } ?: defaultTemplate,
                            createdAt = profile?.createdAt,
                            updatedAt = profile?.updatedAt
                        )
                    } else {
                        UserProfile(
                            storeName = defaultStoreName,
                            reminderTemplate = defaultTemplate
                        )
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(
        uid: String,
        defaultStoreName: String,
        defaultTemplate: String
    ): Result<Unit> {
        val path = "users/$uid"
        return try {
            requireUserId(uid)
            val docRef = db.collection("users").document(uid)
            val existing = withTimeoutOrNull(2500L) { docRef.get().await() }
            if (existing == null || !existing.exists()) {
                val payload = mapOf(
                    "storeName" to defaultStoreName.take(100),
                    "reminderTemplate" to defaultTemplate.take(500),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                awaitCommitSafely(docRef.set(payload), OperationType.CREATE, path)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserSettings(
        uid: String,
        storeName: String,
        reminderTemplate: String
    ): Result<Unit> {
        val path = "users/$uid"
        return try {
            requireUserId(uid)
            val trimmedStore = storeName.trim()
            val trimmedTemplate = reminderTemplate.trim()
            require(trimmedStore.isNotEmpty() && trimmedStore.length <= 100) {
                "Store name must be 1 to 100 characters."
            }
            require(trimmedTemplate.isNotEmpty() && trimmedTemplate.length <= 500) {
                "Reminder template must be 1 to 500 characters."
            }
            val docRef = db.collection("users").document(uid)
            val existing = if (isDeviceOnline()) {
                withTimeoutOrNull(2000L) { docRef.get().await() }
            } else {
                null
            }
            if (existing != null && existing.exists()) {
                val updateMap = mapOf(
                    "storeName" to trimmedStore,
                    "reminderTemplate" to trimmedTemplate,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                awaitCommitSafely(docRef.update(updateMap), OperationType.UPDATE, path)
            } else {
                val createMap = mapOf(
                    "storeName" to trimmedStore,
                    "reminderTemplate" to trimmedTemplate,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                awaitCommitSafely(docRef.set(createMap), OperationType.CREATE, path)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeCustomers(uid: String): Flow<List<Customer>> = flow {
        if (uid.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$uid/customers"
        emitAll(
            db.collection("users").document(uid).collection("customers")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Customer::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCustomer(uid: String, customerId: String): Flow<Customer?> = flow {
        if (uid.isBlank() || customerId.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "users/$uid/customers/$customerId"
        emitAll(
            db.collection("users").document(uid).collection("customers").document(customerId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            Customer::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeTransactions(uid: String, customerId: String): Flow<List<UtangTransaction>> = flow {
        if (uid.isBlank() || customerId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$uid/customers/$customerId/transactions"
        emitAll(
            db.collection("users").document(uid)
                .collection("customers").document(customerId)
                .collection("transactions")
                .orderBy("date", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UtangTransaction::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Observes transactions for customers that have activity since [sinceMillis] (e.g., start of week),
     * avoiding collectionGroup index setup while computing live "Collected Today" and "Collected This Week".
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeRecentTransactions(
        uid: String,
        customersFlow: Flow<List<Customer>>,
        sinceMillis: Long
    ): Flow<List<UtangTransaction>> {
        return customersFlow.flatMapLatest { customers ->
            if (uid.isBlank() || customers.isEmpty()) {
                flowOf(emptyList())
            } else {
                val activeCustomers = customers.filter { customer ->
                    val activityMs = customer.lastActivityAt?.toDate()?.time ?: Long.MAX_VALUE
                    val paymentMs = customer.lastPaymentAt?.toDate()?.time ?: 0L
                    activityMs >= sinceMillis || paymentMs >= sinceMillis
                }
                if (activeCustomers.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    val flows = activeCustomers.map { customer ->
                        observeTransactions(uid, customer.id)
                    }
                    combine(flows) { lists ->
                        lists.flatMap { it }
                    }
                }
            }
        }
    }

    suspend fun addCustomer(
        uid: String,
        name: String,
        phone: String?,
        notes: String?,
        creditLimitCentavos: Long?
    ): Result<String> {
        var docPath = "users/$uid/customers"
        return try {
            requireUserId(uid)
            val trimmedName = name.trim()
            require(trimmedName.isNotEmpty() && trimmedName.length <= 100) {
                "Customer name must be 1 to 100 characters."
            }
            val cleanPhone = phone?.trim()?.takeIf { it.isNotEmpty() }?.take(30)
            val cleanNotes = notes?.trim()?.takeIf { it.isNotEmpty() }?.take(500)
            if (creditLimitCentavos != null) {
                require(creditLimitCentavos > 0L) { "Credit limit must be greater than 0." }
            }

            val docRef = db.collection("users").document(uid).collection("customers").document()
            docPath = docRef.path

            val payload = mapOf(
                "name" to trimmedName,
                "nameLowercase" to trimmedName.lowercase(),
                "phone" to cleanPhone,
                "notes" to cleanNotes,
                "creditLimitCentavos" to creditLimitCentavos,
                "balanceCentavos" to 0L,
                "lastActivityAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            awaitCommitSafely(docRef.set(payload), OperationType.CREATE, docPath)
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateCustomer(
        uid: String,
        customerId: String,
        name: String,
        phone: String?,
        notes: String?,
        creditLimitCentavos: Long?
    ): Result<Unit> {
        val docPath = "users/$uid/customers/$customerId"
        return try {
            requireUserId(uid)
            require(customerId.isNotBlank()) { "Customer ID must not be blank." }
            val trimmedName = name.trim()
            require(trimmedName.isNotEmpty() && trimmedName.length <= 100) {
                "Customer name must be 1 to 100 characters."
            }
            val cleanPhone = phone?.trim()?.takeIf { it.isNotEmpty() }?.take(30)
            val cleanNotes = notes?.trim()?.takeIf { it.isNotEmpty() }?.take(500)
            if (creditLimitCentavos != null) {
                require(creditLimitCentavos > 0L) { "Credit limit must be greater than 0." }
            }

            val docRef = db.collection("users").document(uid)
                .collection("customers").document(customerId)

            val payload = mapOf<String, Any>(
                "name" to trimmedName,
                "nameLowercase" to trimmedName.lowercase(),
                "phone" to (cleanPhone ?: FieldValue.delete()),
                "notes" to (cleanNotes ?: FieldValue.delete()),
                "creditLimitCentavos" to (creditLimitCentavos ?: FieldValue.delete()),
                "lastActivityAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            awaitCommitSafely(docRef.update(payload), OperationType.UPDATE, docPath)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomerAndTransactions(
        uid: String,
        customerId: String,
        cachedTransactionIds: List<String> = emptyList()
    ): Result<Unit> {
        val customerPath = "users/$uid/customers/$customerId"
        return try {
            requireUserId(uid)
            require(customerId.isNotBlank()) { "Customer ID must not be blank." }

            val customerRef = db.collection("users").document(uid)
                .collection("customers").document(customerId)
            val txColRef = customerRef.collection("transactions")

            val serverIds = if (isDeviceOnline()) {
                withTimeoutOrNull(2500L) {
                    txColRef.get().await().documents.map { it.id }
                } ?: emptyList()
            } else {
                emptyList()
            }
            val allTxIds = (cachedTransactionIds + serverIds).distinct()

            // Delete transactions in chunks of 450 so every batch stays well under Firestore's 500-op limit
            val chunks = allTxIds.chunked(450)
            if (chunks.isEmpty()) {
                awaitCommitSafely(customerRef.delete(), OperationType.DELETE, customerPath)
            } else {
                chunks.forEachIndexed { index, idChunk ->
                    val batch = db.batch()
                    idChunk.forEach { txId ->
                        batch.delete(txColRef.document(txId))
                    }
                    if (index == chunks.lastIndex) {
                        batch.delete(customerRef)
                    }
                    awaitCommitSafely(batch.commit(), OperationType.DELETE, customerPath)
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, customerPath)
            Result.failure(e)
        }
    }

    suspend fun addTransaction(
        uid: String,
        customer: Customer,
        type: TransactionType,
        amountCentavos: Long,
        description: String?,
        note: String?,
        date: Timestamp
    ): Result<Unit> {
        val customerPath = "users/$uid/customers/${customer.id}"
        return try {
            requireUserId(uid)
            require(customer.id.isNotBlank()) { "Customer ID must not be blank." }
            require(amountCentavos > 0L) { "Amount must be greater than 0." }

            val cleanDesc = description?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
            val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }?.take(500)

            val customerRef = db.collection("users").document(uid)
                .collection("customers").document(customer.id)
            val txRef = customerRef.collection("transactions").document()

            val txPayload = mapOf(
                "type" to type.name,
                "amountCentavos" to amountCentavos,
                "description" to cleanDesc,
                "note" to cleanNote,
                "date" to date,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            val delta = when (type) {
                TransactionType.CREDIT -> amountCentavos
                TransactionType.PAYMENT -> -amountCentavos
            }

            val customerUpdate = mutableMapOf<String, Any>(
                "balanceCentavos" to FieldValue.increment(delta),
                "lastActivityAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (type == TransactionType.PAYMENT) {
                val existingPayment = customer.lastPaymentAt
                if (existingPayment == null ||
                    date.seconds > existingPayment.seconds ||
                    (date.seconds == existingPayment.seconds && date.nanoseconds > existingPayment.nanoseconds)
                ) {
                    customerUpdate["lastPaymentAt"] = date
                }
            }

            val batch = db.batch()
            batch.set(txRef, txPayload)
            batch.update(customerRef, customerUpdate)
            awaitCommitSafely(batch.commit(), OperationType.WRITE, txRef.path)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, customerPath)
            Result.failure(e)
        }
    }

    suspend fun updateTransaction(
        uid: String,
        customerId: String,
        oldTransaction: UtangTransaction,
        allCustomerTransactions: List<UtangTransaction>,
        newType: TransactionType,
        newAmountCentavos: Long,
        newDescription: String?,
        newNote: String?,
        newDate: Timestamp
    ): Result<Unit> {
        val txPath = "users/$uid/customers/$customerId/transactions/${oldTransaction.id}"
        return try {
            requireUserId(uid)
            require(customerId.isNotBlank() && oldTransaction.id.isNotBlank()) {
                "Customer and Transaction IDs must not be blank."
            }
            require(newAmountCentavos > 0L) { "Amount must be greater than 0." }

            val cleanDesc = newDescription?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
            val cleanNote = newNote?.trim()?.takeIf { it.isNotEmpty() }?.take(500)

            val customerRef = db.collection("users").document(uid)
                .collection("customers").document(customerId)
            val txRef = customerRef.collection("transactions").document(oldTransaction.id)

            val txUpdate = mapOf<String, Any>(
                "type" to newType.name,
                "amountCentavos" to newAmountCentavos,
                "description" to (cleanDesc ?: FieldValue.delete()),
                "note" to (cleanNote ?: FieldValue.delete()),
                "date" to newDate,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val oldSigned = when (oldTransaction.transactionType()) {
                TransactionType.CREDIT -> oldTransaction.amountCentavos
                TransactionType.PAYMENT -> -oldTransaction.amountCentavos
            }
            val newSigned = when (newType) {
                TransactionType.CREDIT -> newAmountCentavos
                TransactionType.PAYMENT -> -newAmountCentavos
            }
            val delta = newSigned - oldSigned

            val updatedTxList = allCustomerTransactions.map { tx ->
                if (tx.id == oldTransaction.id) {
                    tx.copy(
                        type = newType.name,
                        amountCentavos = newAmountCentavos,
                        description = cleanDesc,
                        note = cleanNote,
                        date = newDate
                    )
                } else {
                    tx
                }
            }
            val latestPayment = MoneyFormatter.latestPaymentTimestamp(updatedTxList)

            val customerUpdate = mapOf<String, Any>(
                "balanceCentavos" to FieldValue.increment(delta),
                "lastPaymentAt" to (latestPayment ?: FieldValue.delete()),
                "lastActivityAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.update(txRef, txUpdate)
            batch.update(customerRef, customerUpdate)
            awaitCommitSafely(batch.commit(), OperationType.UPDATE, txPath)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, txPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTransaction(
        uid: String,
        customerId: String,
        transaction: UtangTransaction,
        allCustomerTransactions: List<UtangTransaction>
    ): Result<Unit> {
        val txPath = "users/$uid/customers/$customerId/transactions/${transaction.id}"
        return try {
            requireUserId(uid)
            require(customerId.isNotBlank() && transaction.id.isNotBlank()) {
                "Customer and Transaction IDs must not be blank."
            }

            val customerRef = db.collection("users").document(uid)
                .collection("customers").document(customerId)
            val txRef = customerRef.collection("transactions").document(transaction.id)

            val removedSigned = when (transaction.transactionType()) {
                TransactionType.CREDIT -> transaction.amountCentavos
                TransactionType.PAYMENT -> -transaction.amountCentavos
            }
            val delta = -removedSigned

            val remaining = allCustomerTransactions.filter { it.id != transaction.id }
            val latestPayment = MoneyFormatter.latestPaymentTimestamp(remaining)

            val customerUpdate = mapOf<String, Any>(
                "balanceCentavos" to FieldValue.increment(delta),
                "lastPaymentAt" to (latestPayment ?: FieldValue.delete()),
                "lastActivityAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.delete(txRef)
            batch.update(customerRef, customerUpdate)
            awaitCommitSafely(batch.commit(), OperationType.DELETE, txPath)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, txPath)
            Result.failure(e)
        }
    }
}
