package com.example.domain.util

import com.example.data.model.TransactionType
import com.example.data.model.TransactionWithBalance
import com.example.data.model.UtangTransaction
import com.google.firebase.Timestamp
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

object MoneyFormatter {

    private val integerFormatter: NumberFormat
        get() = NumberFormat.getIntegerInstance(Locale.US)

    /**
     * Formats centavos (Long) into Philippine Peso string (e.g. ₱1,234.50) without using Float or Double.
     */
    fun formatPeso(centavos: Long): String {
        val isNegative = centavos < 0L
        val absCentavos = if (centavos == Long.MIN_VALUE) Long.MAX_VALUE else abs(centavos)
        val wholePesos = absCentavos / 100L
        val remainderCentavos = (absCentavos % 100L).toInt()
        val formattedWhole = integerFormatter.format(wholePesos)
        val formattedCents = remainderCentavos.toString().padStart(2, '0')
        val prefix = if (isNegative) "-₱" else "₱"
        return "$prefix$formattedWhole.$formattedCents"
    }

    /**
     * Formats centavos as plain numeric string suitable for editing in a text field (e.g. 1234.50).
     */
    fun formatPlainAmount(centavos: Long): String {
        val absCentavos = if (centavos < 0L) 0L else centavos
        val wholePesos = absCentavos / 100L
        val remainderCentavos = (absCentavos % 100L).toInt()
        return "$wholePesos.${remainderCentavos.toString().padStart(2, '0')}"
    }

    /**
     * Parses a user-entered peso amount (e.g. "1,234.50", "50", "50.5") strictly using BigDecimal
     * into positive Long centavos. Returns null if invalid or <= 0.
     */
    fun parsePesoToCentavos(input: String): Long? {
        val cleaned = input.trim().replace(",", "").replace("₱", "")
        if (cleaned.isEmpty()) return null
        val decimal = cleaned.toBigDecimalOrNull() ?: return null
        if (decimal.scale() > 2) return null
        val centavosDecimal = decimal.movePointRight(2)
        return try {
            val centavos = centavosDecimal.longValueExact()
            if (centavos > 0L) centavos else null
        } catch (_: ArithmeticException) {
            null
        }
    }

    /**
     * Computes running balance chronologically (oldest -> newest), returning the list newest-first
     * paired with the exact running balance after each transaction.
     */
    fun computeRunningBalances(transactions: List<UtangTransaction>): List<TransactionWithBalance> {
        if (transactions.isEmpty()) return emptyList()
        val chronological = transactions.sortedWith(
            compareBy<UtangTransaction>(
                { it.date?.seconds ?: 0L },
                { it.date?.nanoseconds ?: 0 },
                { it.createdAt?.seconds ?: 0L },
                { it.createdAt?.nanoseconds ?: 0 },
                { it.id }
            )
        )
        var runningBalance = 0L
        val computed = ArrayList<TransactionWithBalance>(chronological.size)
        for (tx in chronological) {
            val amount = tx.amountCentavos.coerceAtLeast(0L)
            runningBalance = when (tx.transactionType()) {
                TransactionType.CREDIT -> runningBalance + amount
                TransactionType.PAYMENT -> runningBalance - amount
            }
            computed.add(
                TransactionWithBalance(
                    transaction = tx,
                    runningBalanceCentavos = runningBalance
                )
            )
        }
        return computed.asReversed()
    }

    /**
     * Computes total balance in centavos across all transactions using Long arithmetic.
     */
    fun computeTotalBalance(transactions: List<UtangTransaction>): Long {
        var balance = 0L
        for (tx in transactions) {
            val amount = tx.amountCentavos.coerceAtLeast(0L)
            balance = when (tx.transactionType()) {
                TransactionType.CREDIT -> balance + amount
                TransactionType.PAYMENT -> balance - amount
            }
        }
        return balance
    }

    /**
     * Finds the latest PAYMENT transaction timestamp, or null if no payment exists.
     */
    fun latestPaymentTimestamp(transactions: List<UtangTransaction>): Timestamp? {
        return transactions
            .asSequence()
            .filter { it.transactionType() == TransactionType.PAYMENT }
            .mapNotNull { it.date ?: it.createdAt }
            .maxWithOrNull(compareBy({ it.seconds }, { it.nanoseconds }))
    }

    /**
     * Generates a polite reminder message from a customizable template.
     */
    fun buildReminderMessage(
        template: String,
        customerName: String,
        balanceCentavos: Long,
        storeName: String
    ): String {
        val amountNoSymbol = formatPlainAmountWithCommas(balanceCentavos)
        val formattedWithSymbol = formatPeso(balanceCentavos)
        var result = template
            .replace("[Name]", customerName, ignoreCase = true)
            .replace("[Store Name]", storeName, ignoreCase = true)
        result = if (result.contains("₱[amount]", ignoreCase = true)) {
            result.replace("₱[amount]", formattedWithSymbol, ignoreCase = true)
        } else {
            result.replace("[amount]", amountNoSymbol, ignoreCase = true)
        }
        return result
    }

    private fun formatPlainAmountWithCommas(centavos: Long): String {
        val absCentavos = if (centavos < 0L) 0L else centavos
        val wholePesos = absCentavos / 100L
        val remainderCentavos = (absCentavos % 100L).toInt()
        return "${integerFormatter.format(wholePesos)}.${remainderCentavos.toString().padStart(2, '0')}"
    }
}
