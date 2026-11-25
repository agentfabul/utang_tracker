package com.example.domain.export

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.model.Customer
import com.example.data.model.TransactionType
import com.example.data.model.TransactionWithBalance
import com.example.domain.util.MoneyFormatter
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class ExportFormat(val mimeType: String, val extension: String) {
    PDF("application/pdf", "pdf"),
    CSV("text/csv", "csv")
}

data class ExportedReport(
    val file: File,
    val mimeType: String,
    val fileName: String
)

data class ReportLabels(
    val title: String,
    val storeLabel: String,
    val customerLabel: String,
    val phoneLabel: String,
    val notesLabel: String,
    val creditLimitLabel: String,
    val generatedLabel: String,
    val currentBalanceLabel: String,
    val totalCreditLabel: String,
    val totalPaidLabel: String,
    val colDate: String,
    val colType: String,
    val colDescription: String,
    val colNote: String,
    val colAmount: String,
    val colRunningBalance: String,
    val creditTypeLabel: String,
    val paymentTypeLabel: String,
    val emptyTransactionsMessage: String,
    val pageFooterFormat: String
) {
    companion object {
        fun fromContext(context: Context): ReportLabels {
            return ReportLabels(
                title = context.getString(R.string.report_title),
                storeLabel = context.getString(R.string.report_store_label),
                customerLabel = context.getString(R.string.report_customer_label),
                phoneLabel = context.getString(R.string.report_phone_label),
                notesLabel = context.getString(R.string.report_notes_label),
                creditLimitLabel = context.getString(R.string.report_credit_limit_label),
                generatedLabel = context.getString(R.string.report_generated_label),
                currentBalanceLabel = context.getString(R.string.report_current_balance_label),
                totalCreditLabel = context.getString(R.string.report_total_credit_label),
                totalPaidLabel = context.getString(R.string.report_total_paid_label),
                colDate = context.getString(R.string.report_col_date),
                colType = context.getString(R.string.report_col_type),
                colDescription = context.getString(R.string.report_col_description),
                colNote = context.getString(R.string.report_col_note),
                colAmount = context.getString(R.string.report_col_amount),
                colRunningBalance = context.getString(R.string.report_col_running_balance),
                creditTypeLabel = context.getString(R.string.tx_type_credit),
                paymentTypeLabel = context.getString(R.string.tx_type_payment),
                emptyTransactionsMessage = context.getString(R.string.report_empty_transactions),
                pageFooterFormat = context.getString(R.string.report_page_footer)
            )
        }
    }
}

object CustomerReportExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2)
    private const val FOOTER_Y = PAGE_HEIGHT - 24f
    private const val MAX_BODY_Y = PAGE_HEIGHT - 54f

    fun sanitizeFileName(name: String): String {
        val cleaned = name.trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .take(40)
        return cleaned.ifEmpty { "customer" }
    }

    fun buildCsvContent(
        customer: Customer,
        transactionsWithBalance: List<TransactionWithBalance>,
        storeName: String,
        labels: ReportLabels,
        generatedAtMillis: Long = System.currentTimeMillis(),
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", locale).apply {
            this.timeZone = timeZone
        }
        val txDateFormat = SimpleDateFormat("yyyy-MM-dd", locale).apply {
            this.timeZone = timeZone
        }

        val totalCreditCentavos = transactionsWithBalance
            .asSequence()
            .filter { it.transaction.transactionType() == TransactionType.CREDIT }
            .sumOf { it.transaction.amountCentavos.coerceAtLeast(0L) }
        val totalPaidCentavos = transactionsWithBalance
            .asSequence()
            .filter { it.transaction.transactionType() == TransactionType.PAYMENT }
            .sumOf { it.transaction.amountCentavos.coerceAtLeast(0L) }

        val sb = StringBuilder()
        // UTF-8 BOM for clean Peso sign display in Excel / Google Sheets
        sb.append('\uFEFF')

        appendCsvRow(sb, labels.title, storeName)
        appendCsvRow(sb, labels.customerLabel, customer.name)
        if (!customer.phone.isNullOrBlank()) {
            appendCsvRow(sb, labels.phoneLabel, customer.phone)
        }
        if (!customer.notes.isNullOrBlank()) {
            appendCsvRow(sb, labels.notesLabel, customer.notes)
        }
        customer.creditLimitCentavos?.let { limit ->
            appendCsvRow(sb, labels.creditLimitLabel, MoneyFormatter.formatPeso(limit))
        }
        appendCsvRow(sb, labels.currentBalanceLabel, MoneyFormatter.formatPeso(customer.balanceCentavos))
        appendCsvRow(sb, labels.totalCreditLabel, MoneyFormatter.formatPeso(totalCreditCentavos))
        appendCsvRow(sb, labels.totalPaidLabel, MoneyFormatter.formatPeso(totalPaidCentavos))
        appendCsvRow(sb, labels.generatedLabel, dateFormat.format(Date(generatedAtMillis)))
        sb.append("\r\n")

        appendCsvRow(
            sb,
            labels.colDate,
            labels.colType,
            labels.colDescription,
            labels.colNote,
            labels.colAmount,
            labels.colRunningBalance
        )

        if (transactionsWithBalance.isEmpty()) {
            appendCsvRow(sb, "", "", labels.emptyTransactionsMessage, "", "", "")
        } else {
            for (item in transactionsWithBalance) {
                val tx = item.transaction
                val isCredit = tx.transactionType() == TransactionType.CREDIT
                val txDate = (tx.date ?: tx.createdAt)?.toDate()?.let { txDateFormat.format(it) }.orEmpty()
                val typeLabel = if (isCredit) labels.creditTypeLabel else labels.paymentTypeLabel
                val signedAmount = (if (isCredit) "+" else "-") + MoneyFormatter.formatPeso(tx.amountCentavos)
                appendCsvRow(
                    sb,
                    txDate,
                    typeLabel,
                    tx.description.orEmpty(),
                    tx.note.orEmpty(),
                    signedAmount,
                    MoneyFormatter.formatPeso(item.runningBalanceCentavos)
                )
            }
        }
        return sb.toString()
    }

    fun exportReport(
        context: Context,
        customer: Customer,
        transactionsWithBalance: List<TransactionWithBalance>,
        storeName: String,
        format: ExportFormat
    ): ExportedReport {
        val labels = ReportLabels.fromContext(context)
        val exportsDir = File(context.cacheDir, "exports").apply {
            if (!exists()) mkdirs()
            // Clean previous generated reports so cache stays bounded
            listFiles()?.forEach { old ->
                if (old.isFile) old.delete()
            }
        }

        val nowMillis = System.currentTimeMillis()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(nowMillis))
        val safeCustomer = sanitizeFileName(customer.name)
        val fileName = "utang_${safeCustomer}_$stamp.${format.extension}"
        val outFile = File(exportsDir, fileName)

        when (format) {
            ExportFormat.CSV -> {
                val csv = buildCsvContent(
                    customer = customer,
                    transactionsWithBalance = transactionsWithBalance,
                    storeName = storeName,
                    labels = labels,
                    generatedAtMillis = nowMillis
                )
                outFile.writeText(csv, Charsets.UTF_8)
            }

            ExportFormat.PDF -> {
                writePdfFile(
                    outFile = outFile,
                    customer = customer,
                    transactionsWithBalance = transactionsWithBalance,
                    storeName = storeName,
                    labels = labels,
                    generatedAtMillis = nowMillis
                )
            }
        }

        return ExportedReport(
            file = outFile,
            mimeType = format.mimeType,
            fileName = fileName
        )
    }

    fun shareReport(
        context: Context,
        report: ExportedReport,
        customerName: String,
        chooserTitle: String
    ): Boolean {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            report.file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = report.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "$customerName - ${report.fileName}")
            clipData = ClipData.newRawUri(report.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(Intent.createChooser(intent, chooserTitle))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun writePdfFile(
        outFile: File,
        customer: Customer,
        transactionsWithBalance: List<TransactionWithBalance>,
        storeName: String,
        labels: ReportLabels,
        generatedAtMillis: Long
    ) {
        val primaryTeal = Color.rgb(13, 108, 110)
        val softTealBg = Color.rgb(232, 244, 244)
        val inkDark = Color.rgb(20, 28, 30)
        val inkMuted = Color.rgb(82, 96, 100)
        val creditRed = Color.rgb(194, 59, 34)
        val paymentGreen = Color.rgb(27, 127, 70)
        val dividerGray = Color.rgb(220, 226, 228)
        val zebraGray = Color.rgb(248, 250, 250)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(224, 242, 241)
            textSize = 10.5f
        }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkDark
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkMuted
            textSize = 9.5f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkDark
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val tableHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkDark
            textSize = 9f
        }
        val cellMutedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkMuted
            textSize = 8.2f
        }
        val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val balancePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkDark
            textSize = 9.2f
            textAlign = Paint.Align.RIGHT
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dividerGray
            strokeWidth = 0.8f
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkMuted
            textSize = 8.5f
            textAlign = Paint.Align.RIGHT
        }

        val totalCreditCentavos = transactionsWithBalance
            .asSequence()
            .filter { it.transaction.transactionType() == TransactionType.CREDIT }
            .sumOf { it.transaction.amountCentavos.coerceAtLeast(0L) }
        val totalPaidCentavos = transactionsWithBalance
            .asSequence()
            .filter { it.transaction.transactionType() == TransactionType.PAYMENT }
            .sumOf { it.transaction.amountCentavos.coerceAtLeast(0L) }

        val dateTimeFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val txDateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

        // Column X bounds
        val colDateX = MARGIN + 6f
        val colTypeX = MARGIN + 78f
        val colDescX = MARGIN + 158f
        val colDescWidth = 175
        val colAmountRightX = MARGIN + 422f
        val colBalanceRightX = MARGIN + CONTENT_WIDTH - 6f

        val pdfDocument = PdfDocument()
        val pages = mutableListOf<PdfDocument.Page>()

        fun drawTableHeader(canvas: Canvas, y: Float): Float {
            fillPaint.color = primaryTeal
            canvas.drawRoundRect(
                RectF(MARGIN, y, MARGIN + CONTENT_WIDTH, y + 24f),
                6f,
                6f,
                fillPaint
            )
            val textY = y + 15.5f
            canvas.drawText(labels.colDate, colDateX, textY, tableHeaderPaint)
            canvas.drawText(labels.colType, colTypeX, textY, tableHeaderPaint)
            canvas.drawText(labels.colDescription, colDescX, textY, tableHeaderPaint)
            tableHeaderPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(labels.colAmount, colAmountRightX, textY, tableHeaderPaint)
            canvas.drawText(labels.colRunningBalance, colBalanceRightX, textY, tableHeaderPaint)
            tableHeaderPaint.textAlign = Paint.Align.LEFT
            return y + 28f
        }

        fun startNewPage(pageNumber: Int): Pair<PdfDocument.Page, Float> {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = pdfDocument.startPage(pageInfo)
            pages.add(page)
            val canvas = page.canvas

            var cursorY = MARGIN
            if (pageNumber == 1) {
                // Header banner
                fillPaint.color = primaryTeal
                canvas.drawRoundRect(
                    RectF(MARGIN, cursorY, MARGIN + CONTENT_WIDTH, cursorY + 62f),
                    12f,
                    12f,
                    fillPaint
                )
                canvas.drawText(labels.title, MARGIN + 16f, cursorY + 26f, titlePaint)
                canvas.drawText(
                    "${labels.storeLabel}: $storeName  •  ${labels.generatedLabel}: ${dateTimeFormat.format(Date(generatedAtMillis))}",
                    MARGIN + 16f,
                    cursorY + 46f,
                    subtitlePaint
                )
                cursorY += 74f

                // Customer summary box
                fillPaint.color = softTealBg
                canvas.drawRoundRect(
                    RectF(MARGIN, cursorY, MARGIN + CONTENT_WIDTH, cursorY + 86f),
                    10f,
                    10f,
                    fillPaint
                )
                canvas.drawText(labels.customerLabel, MARGIN + 14f, cursorY + 20f, labelPaint)
                canvas.drawText(customer.name, MARGIN + 14f, cursorY + 36f, headingPaint)

                val metaParts = buildList {
                    customer.phone?.takeIf { it.isNotBlank() }?.let { add("${labels.phoneLabel}: $it") }
                    customer.creditLimitCentavos?.let {
                        add("${labels.creditLimitLabel}: ${MoneyFormatter.formatPeso(it)}")
                    }
                }.joinToString("   |   ")
                if (metaParts.isNotEmpty()) {
                    canvas.drawText(metaParts, MARGIN + 14f, cursorY + 54f, labelPaint)
                }
                customer.notes?.takeIf { it.isNotBlank() }?.let { note ->
                    canvas.drawText(
                        "${labels.notesLabel}: ${note.take(58)}",
                        MARGIN + 14f,
                        cursorY + 70f,
                        labelPaint
                    )
                }

                // Current Balance on right side of summary box
                labelPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(labels.currentBalanceLabel, MARGIN + CONTENT_WIDTH - 14f, cursorY + 22f, labelPaint)
                labelPaint.textAlign = Paint.Align.LEFT

                val balanceColor = if (customer.balanceCentavos > 0L) creditRed else paymentGreen
                val bigBalancePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = balanceColor
                    textSize = 18f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText(
                    MoneyFormatter.formatPeso(customer.balanceCentavos),
                    MARGIN + CONTENT_WIDTH - 14f,
                    cursorY + 46f,
                    bigBalancePaint
                )
                val totalsSummary = "${labels.totalCreditLabel}: ${MoneyFormatter.formatPeso(totalCreditCentavos)}  •  ${labels.totalPaidLabel}: ${MoneyFormatter.formatPeso(totalPaidCentavos)}"
                labelPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(totalsSummary, MARGIN + CONTENT_WIDTH - 14f, cursorY + 70f, labelPaint)
                labelPaint.textAlign = Paint.Align.LEFT

                cursorY += 100f
            } else {
                canvas.drawText(
                    "${labels.title} — ${customer.name} ($storeName)",
                    MARGIN,
                    cursorY + 14f,
                    headingPaint
                )
                cursorY += 26f
            }

            cursorY = drawTableHeader(canvas, cursorY)
            return page to cursorY
        }

        try {
            var pageNumber = 1
            var (currentPage, currentY) = startNewPage(pageNumber)

            if (transactionsWithBalance.isEmpty()) {
                currentPage.canvas.drawText(
                    labels.emptyTransactionsMessage,
                    colDateX,
                    currentY + 18f,
                    labelPaint
                )
            } else {
                transactionsWithBalance.forEachIndexed { index, item ->
                    val tx = item.transaction
                    val isCredit = tx.transactionType() == TransactionType.CREDIT
                    val dateText = (tx.date ?: tx.createdAt)?.toDate()?.let { txDateFormat.format(it) } ?: "-"
                    val typeText = if (isCredit) labels.creditTypeLabel else labels.paymentTypeLabel
                    val descText = buildString {
                        if (!tx.description.isNullOrBlank()) append(tx.description)
                        if (!tx.note.isNullOrBlank()) {
                            if (isNotEmpty()) append("\n")
                            append(tx.note)
                        }
                        if (isEmpty()) append("-")
                    }

                    val descLayout = StaticLayout.Builder
                        .obtain(descText, 0, descText.length, cellPaint, colDescWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(1.5f, 1.0f)
                        .build()

                    val rowHeight = maxOf(24f, descLayout.height + 12f)
                    if (currentY + rowHeight > MAX_BODY_Y) {
                        pdfDocument.finishPage(currentPage)
                        pageNumber += 1
                        val next = startNewPage(pageNumber)
                        currentPage = next.first
                        currentY = next.second
                    }

                    val canvas = currentPage.canvas
                    if (index % 2 == 1) {
                        fillPaint.color = zebraGray
                        canvas.drawRect(MARGIN, currentY, MARGIN + CONTENT_WIDTH, currentY + rowHeight, fillPaint)
                    }

                    val textBaselineY = currentY + 15f
                    canvas.drawText(dateText, colDateX, textBaselineY, cellMutedPaint)

                    valuePaint.color = if (isCredit) creditRed else paymentGreen
                    valuePaint.textSize = 8.8f
                    canvas.drawText(
                        if (isCredit) "UTANG" else "BAYAD",
                        colTypeX,
                        textBaselineY,
                        valuePaint
                    )
                    valuePaint.color = inkDark

                    canvas.save()
                    canvas.translate(colDescX, currentY + 6f)
                    descLayout.draw(canvas)
                    canvas.restore()

                    amountPaint.color = if (isCredit) creditRed else paymentGreen
                    val signedAmount = (if (isCredit) "+" else "-") + MoneyFormatter.formatPeso(tx.amountCentavos)
                    canvas.drawText(signedAmount, colAmountRightX, textBaselineY, amountPaint)
                    canvas.drawText(
                        MoneyFormatter.formatPeso(item.runningBalanceCentavos),
                        colBalanceRightX,
                        textBaselineY,
                        balancePaint
                    )

                    currentY += rowHeight
                    canvas.drawLine(MARGIN, currentY, MARGIN + CONTENT_WIDTH, currentY, linePaint)
                }
            }

            // Draw page footer on the last page (page numbers match sequential pages)
            val totalPages = pages.size
            // Note: earlier pages already finished; draw current page footer with pageNumber of totalPages
            currentPage.canvas.drawText(
                String.format(Locale.getDefault(), labels.pageFooterFormat, pageNumber, totalPages),
                MARGIN + CONTENT_WIDTH,
                FOOTER_Y,
                footerPaint
            )
            pdfDocument.finishPage(currentPage)

            FileOutputStream(outFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
        } finally {
            pdfDocument.close()
        }
    }

    private fun appendCsvRow(sb: StringBuilder, vararg fields: String) {
        fields.forEachIndexed { index, field ->
            if (index > 0) sb.append(',')
            sb.append(escapeCsvField(field))
        }
        sb.append("\r\n")
    }

    private fun escapeCsvField(raw: String): String {
        val cleaned = raw.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
        val needsQuoting = cleaned.contains(',') ||
            cleaned.contains('"') ||
            cleaned.startsWith(' ') ||
            cleaned.endsWith(' ')
        return if (needsQuoting) {
            "\"${cleaned.replace("\"", "\"\"")}\""
        } else {
            cleaned
        }
    }
}
