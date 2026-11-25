package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class Customer(
    @DocumentId val id: String = "",
    val name: String = "",
    val nameLowercase: String = "",
    val phone: String? = null,
    val notes: String? = null,
    val creditLimitCentavos: Long? = null,
    val balanceCentavos: Long = 0L,
    val lastPaymentAt: Timestamp? = null,
    @ServerTimestamp val lastActivityAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun overCreditLimit(): Boolean {
        val limit = creditLimitCentavos ?: return false
        return limit > 0L && balanceCentavos > limit
    }

    fun overdueThirtyDays(nowMillis: Long): Boolean {
        if (balanceCentavos <= 0L) return false
        val referenceMillis = lastPaymentAt?.toDate()?.time
            ?: createdAt?.toDate()?.time
            ?: return false
        val thirtyDaysMillis = 30L * 24L * 60L * 60L * 1000L
        return (nowMillis - referenceMillis) >= thirtyDaysMillis
    }
}

enum class CustomerSortOrder {
    HIGHEST_BALANCE,
    NAME_ASC,
    RECENT_ACTIVITY
}
