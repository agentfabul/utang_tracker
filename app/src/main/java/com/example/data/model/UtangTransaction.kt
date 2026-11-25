package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TransactionType {
    CREDIT,
    PAYMENT;

    companion object {
        fun fromRaw(raw: String?): TransactionType {
            return entries.firstOrNull { it.name == raw } ?: CREDIT
        }
    }
}

@Keep
@IgnoreExtraProperties
data class UtangTransaction(
    @DocumentId val id: String = "",
    val type: String = TransactionType.CREDIT.name,
    val amountCentavos: Long = 0L,
    val description: String? = null,
    val note: String? = null,
    val date: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun transactionType(): TransactionType = TransactionType.fromRaw(type)
}

data class TransactionWithBalance(
    val transaction: UtangTransaction,
    val runningBalanceCentavos: Long
)
