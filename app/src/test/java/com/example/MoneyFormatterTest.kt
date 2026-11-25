package com.example

import com.example.data.model.Customer
import com.example.data.model.TransactionType
import com.example.data.model.UtangTransaction
import com.example.domain.util.MoneyFormatter
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyFormatterTest {

    @Test
    fun formatPeso_formatsPositiveZeroAndNegativeCorrectly() {
        assertEquals("₱0.00", MoneyFormatter.formatPeso(0L))
        assertEquals("₱0.05", MoneyFormatter.formatPeso(5L))
        assertEquals("₱50.50", MoneyFormatter.formatPeso(5050L))
        assertEquals("₱1,234.50", MoneyFormatter.formatPeso(123450L))
        assertEquals("₱1,000,000.99", MoneyFormatter.formatPeso(100000099L))
        assertEquals("-₱25.00", MoneyFormatter.formatPeso(-2500L))
    }

    @Test
    fun parsePesoToCentavos_parsesValidAndRejectsInvalid() {
        assertEquals(123450L, MoneyFormatter.parsePesoToCentavos("1234.50"))
        assertEquals(123450L, MoneyFormatter.parsePesoToCentavos("₱1,234.50"))
        assertEquals(5000L, MoneyFormatter.parsePesoToCentavos("50"))
        assertEquals(5050L, MoneyFormatter.parsePesoToCentavos("50.5"))
        assertEquals(1L, MoneyFormatter.parsePesoToCentavos("0.01"))

        assertNull(MoneyFormatter.parsePesoToCentavos(""))
        assertNull(MoneyFormatter.parsePesoToCentavos("0"))
        assertNull(MoneyFormatter.parsePesoToCentavos("0.00"))
        assertNull(MoneyFormatter.parsePesoToCentavos("-50"))
        assertNull(MoneyFormatter.parsePesoToCentavos("12.345"))
        assertNull(MoneyFormatter.parsePesoToCentavos("abc"))
    }

    @Test
    fun computeRunningBalances_sortsChronologicallyAndReturnsNewestFirst() {
        val t1 = UtangTransaction(
            id = "tx1",
            type = TransactionType.CREDIT.name,
            amountCentavos = 15000L,
            description = "2 Coke, 1 Lucky Me",
            date = Timestamp(1000L, 0)
        )
        val t2 = UtangTransaction(
            id = "tx2",
            type = TransactionType.CREDIT.name,
            amountCentavos = 5050L,
            description = "Rice",
            date = Timestamp(2000L, 0)
        )
        val t3 = UtangTransaction(
            id = "tx3",
            type = TransactionType.PAYMENT.name,
            amountCentavos = 10000L,
            description = "Partial payment",
            date = Timestamp(3000L, 0)
        )
        val t4 = UtangTransaction(
            id = "tx4",
            type = TransactionType.PAYMENT.name,
            amountCentavos = 10050L,
            description = "Full payment",
            date = Timestamp(4000L, 0)
        )

        // Pass out of order to verify chronological calculation
        val result = MoneyFormatter.computeRunningBalances(listOf(t3, t1, t4, t2))

        assertEquals(4, result.size)
        // Newest first
        assertEquals("tx4", result[0].transaction.id)
        assertEquals(0L, result[0].runningBalanceCentavos)

        assertEquals("tx3", result[1].transaction.id)
        assertEquals(10050L, result[1].runningBalanceCentavos)

        assertEquals("tx2", result[2].transaction.id)
        assertEquals(20050L, result[2].runningBalanceCentavos)

        assertEquals("tx1", result[3].transaction.id)
        assertEquals(15000L, result[3].runningBalanceCentavos)

        assertEquals(0L, MoneyFormatter.computeTotalBalance(listOf(t1, t2, t3, t4)))
    }

    @Test
    fun latestPaymentTimestamp_returnsLatestPaymentOrNull() {
        val creditOnly = listOf(
            UtangTransaction(id = "c1", type = TransactionType.CREDIT.name, amountCentavos = 5000L, date = Timestamp(5000L, 0))
        )
        assertNull(MoneyFormatter.latestPaymentTimestamp(creditOnly))

        val mixed = creditOnly + listOf(
            UtangTransaction(id = "p1", type = TransactionType.PAYMENT.name, amountCentavos = 1000L, date = Timestamp(2000L, 0)),
            UtangTransaction(id = "p2", type = TransactionType.PAYMENT.name, amountCentavos = 2000L, date = Timestamp(4000L, 0))
        )
        assertEquals(Timestamp(4000L, 0), MoneyFormatter.latestPaymentTimestamp(mixed))
    }

    @Test
    fun customerStatusHelpers_detectOverLimitAndThirtyDaysOverdue() {
        val overLimitCustomer = Customer(
            name = "Juan",
            creditLimitCentavos = 50000L,
            balanceCentavos = 50001L
        )
        val withinLimitCustomer = Customer(
            name = "Maria",
            creditLimitCentavos = 50000L,
            balanceCentavos = 50000L
        )
        assertTrue(overLimitCustomer.overCreditLimit())
        assertFalse(withinLimitCustomer.overCreditLimit())

        val dayMillis = 24L * 60L * 60L * 1000L
        val nowMillis = 100L * dayMillis
        val overdueCustomer = Customer(
            name = "Pedro",
            balanceCentavos = 10000L,
            lastPaymentAt = Timestamp((nowMillis - 31L * dayMillis) / 1000L, 0)
        )
        val recentCustomer = Customer(
            name = "Ana",
            balanceCentavos = 10000L,
            lastPaymentAt = Timestamp((nowMillis - 10L * dayMillis) / 1000L, 0)
        )
        val settledCustomer = Customer(
            name = "Lito",
            balanceCentavos = 0L,
            lastPaymentAt = Timestamp((nowMillis - 40L * dayMillis) / 1000L, 0)
        )
        assertTrue(overdueCustomer.overdueThirtyDays(nowMillis))
        assertFalse(recentCustomer.overdueThirtyDays(nowMillis))
        assertFalse(settledCustomer.overdueThirtyDays(nowMillis))
    }

    @Test
    fun buildReminderMessage_replacesAllPlaceholders() {
        val template = "Hi [Name], friendly reminder po na may balance kayo na ₱[amount] sa [Store Name]. Salamat po!"
        val message = MoneyFormatter.buildReminderMessage(
            template = template,
            customerName = "Aling Rosa",
            balanceCentavos = 123450L,
            storeName = "Nena Sari-Sari"
        )
        assertEquals(
            "Hi Aling Rosa, friendly reminder po na may balance kayo na ₱1,234.50 sa Nena Sari-Sari. Salamat po!",
            message
        )
    }
}
