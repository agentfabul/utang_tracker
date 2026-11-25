package com.example

import com.example.data.model.Customer
import com.example.data.model.TransactionType
import com.example.data.model.TransactionWithBalance
import com.example.data.model.UtangTransaction
import com.example.domain.export.CustomerReportExporter
import com.example.domain.export.ReportLabels
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class CustomerReportExporterTest {

    private val testLabels = ReportLabels(
        title = "Utang Statement",
        storeLabel = "Store",
        customerLabel = "Customer",
        phoneLabel = "Phone",
        notesLabel = "Notes",
        creditLimitLabel = "Credit Limit",
        generatedLabel = "Generated",
        currentBalanceLabel = "Current Balance",
        totalCreditLabel = "Total Utang",
        totalPaidLabel = "Total Paid",
        colDate = "Date",
        colType = "Type",
        colDescription = "Description",
        colNote = "Note",
        colAmount = "Amount",
        colRunningBalance = "Running Balance",
        creditTypeLabel = "Utang (Credit)",
        paymentTypeLabel = "Bayad (Payment)",
        emptyTransactionsMessage = "No recorded transactions yet.",
        pageFooterFormat = "Page %1\$d of %2\$d"
    )

    @Test
    fun sanitizeFileName_handlesSpecialCharsAndBlanks() {
        assertEquals("Aling_Rosa_Store", CustomerReportExporter.sanitizeFileName("Aling Rosa / Store!"))
        assertEquals("customer", CustomerReportExporter.sanitizeFileName("   ///   "))
    }

    @Test
    fun buildCsvContent_includesBomEscapesCommasAndFormatsPesos() {
        val customer = Customer(
            id = "c1",
            name = "Aling Rosa",
            phone = "09171234567",
            notes = "Near corner, Phase 2",
            creditLimitCentavos = 100000L,
            balanceCentavos = 15050L
        )
        val transactions = listOf(
            TransactionWithBalance(
                transaction = UtangTransaction(
                    id = "tx2",
                    type = TransactionType.PAYMENT.name,
                    amountCentavos = 5000L,
                    description = "Partial \"cash\" payment",
                    note = "Paid today",
                    date = Timestamp(1700003600L, 0)
                ),
                runningBalanceCentavos = 15050L
            ),
            TransactionWithBalance(
                transaction = UtangTransaction(
                    id = "tx1",
                    type = TransactionType.CREDIT.name,
                    amountCentavos = 20050L,
                    description = "2 Coke, 1 Lucky Me",
                    note = null,
                    date = Timestamp(1700000000L, 0)
                ),
                runningBalanceCentavos = 20050L
            )
        )

        val csv = CustomerReportExporter.buildCsvContent(
            customer = customer,
            transactionsWithBalance = transactions,
            storeName = "Nena Sari-Sari",
            labels = testLabels,
            generatedAtMillis = 1700004000000L,
            locale = Locale.US,
            timeZone = TimeZone.getTimeZone("UTC")
        )

        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("Customer,Aling Rosa"))
        assertTrue(csv.contains("Notes,\"Near corner, Phase 2\""))
        assertTrue(csv.contains("Current Balance,₱150.50"))
        assertTrue(csv.contains("Total Utang,₱200.50"))
        assertTrue(csv.contains("Total Paid,₱50.00"))
        assertTrue(csv.contains("\"Partial \"\"cash\"\" payment\""))
        assertTrue(csv.contains("\"2 Coke, 1 Lucky Me\""))
        assertTrue(csv.contains("-₱50.00,₱150.50"))
        assertTrue(csv.contains("+₱200.50,₱200.50"))
    }
}
