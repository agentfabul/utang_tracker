package com.example.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.Customer
import com.example.data.model.TransactionType
import com.example.data.model.TransactionWithBalance
import com.example.data.model.UtangTransaction
import com.example.domain.export.CustomerReportExporter
import com.example.domain.export.ExportFormat
import com.example.domain.util.MoneyFormatter
import com.example.ui.common.CustomerFormDialog
import com.example.ui.common.CustomerWarningBadges
import com.example.ui.common.EmptyStateView
import com.example.ui.common.ErrorStateView
import com.example.ui.common.LoadingStateView
import com.example.ui.common.OfflineStatusBanner
import com.example.ui.common.ReminderBottomSheet
import com.example.ui.common.TransactionFormDialog
import com.example.ui.common.UiState
import com.example.ui.theme.LocalUtangColors
import kotlinx.coroutines.launch
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CustomerDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showEditCustomerDialog by remember { mutableStateOf(false) }
    var showDeleteCustomerDialog by remember { mutableStateOf(false) }
    var txDialogInitialType by remember { mutableStateOf<TransactionType?>(null) }
    var editingTransaction by remember { mutableStateOf<UtangTransaction?>(null) }
    var deletingTransaction by remember { mutableStateOf<UtangTransaction?>(null) }
    var showPayFullDialog by remember { mutableStateOf(false) }
    var showReminderSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

    val exportChooserTitle = stringResource(R.string.export_share_chooser)
    val exportNoAppMsg = stringResource(R.string.export_no_app_error)

    LaunchedEffect(actionMessage) {
        actionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    val successData = (uiState as? UiState.Success)?.data
    val customer = successData?.customer

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = customer?.name ?: stringResource(R.string.customers_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (customer != null) {
                        IconButton(
                            onClick = { showExportSheet = true },
                            modifier = Modifier.testTag("detail_top_export_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileDownload,
                                contentDescription = stringResource(R.string.export_report_button)
                            )
                        }
                        IconButton(
                            onClick = { showEditCustomerDialog = true },
                            modifier = Modifier.testTag("detail_edit_customer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.edit_customer_title)
                            )
                        }
                        IconButton(
                            onClick = { showDeleteCustomerDialog = true },
                            modifier = Modifier.testTag("detail_delete_customer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.delete_customer_title),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (customer != null) {
                FloatingActionButton(
                    onClick = { txDialogInitialType = TransactionType.CREDIT },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("detail_add_tx_fab")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.tx_add_title)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OfflineStatusBanner()

            when (val state = uiState) {
                is UiState.Loading -> LoadingStateView()
                is UiState.Error -> ErrorStateView(message = state.message)
                is UiState.Success -> {
                    val data = state.data
                    val cust = data.customer
                    if (cust == null) {
                        ErrorStateView(
                            message = stringResource(R.string.detail_customer_not_found),
                            onRetry = onBack
                        )
                    } else {
                        val nowMillis = remember(cust) { System.currentTimeMillis() }
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxSize()
                        ) {
                            item {
                                CustomerBalanceSummaryCard(
                                    customer = cust,
                                    nowMillis = nowMillis,
                                    onAddUtang = { txDialogInitialType = TransactionType.CREDIT },
                                    onRecordPayment = { txDialogInitialType = TransactionType.PAYMENT },
                                    onPayFullBalance = { showPayFullDialog = true },
                                    onSendReminder = { showReminderSheet = true }
                                )
                            }

                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.detail_history_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilledTonalButton(
                                        onClick = { showExportSheet = true },
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("detail_export_report_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.FileDownload,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.export_report_button))
                                    }
                                }
                            }

                            if (data.transactionsWithBalance.isEmpty()) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        EmptyStateView(
                                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                            title = stringResource(R.string.detail_empty_transactions_title),
                                            body = stringResource(R.string.detail_empty_transactions_body)
                                        )
                                    }
                                }
                            } else {
                                items(
                                    items = data.transactionsWithBalance,
                                    key = { it.transaction.id }
                                ) { item ->
                                    TransactionHistoryCard(
                                        item = item,
                                        onEdit = { editingTransaction = item.transaction },
                                        onDelete = { deletingTransaction = item.transaction }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditCustomerDialog && customer != null) {
        CustomerFormDialog(
            existingCustomer = customer,
            isSaving = isSaving,
            onDismiss = { showEditCustomerDialog = false },
            onSave = { name, phone, notes, creditLimit ->
                viewModel.updateCustomer(name, phone, notes, creditLimit) {
                    showEditCustomerDialog = false
                }
            }
        )
    }

    if (showDeleteCustomerDialog && customer != null) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) showDeleteCustomerDialog = false },
            title = { Text(stringResource(R.string.delete_customer_title)) },
            text = {
                Text(stringResource(R.string.delete_customer_message, customer.name))
            },
            confirmButton = {
                Button(
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    onClick = {
                        viewModel.deleteCustomer(successData.transactions) {
                            showDeleteCustomerDialog = false
                            onBack()
                        }
                    },
                    modifier = Modifier.testTag("confirm_delete_customer_button")
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSaving,
                    onClick = { showDeleteCustomerDialog = false }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    val initialType = txDialogInitialType
    if (initialType != null && customer != null) {
        TransactionFormDialog(
            fixedCustomer = customer,
            initialType = initialType,
            isSaving = isSaving,
            onDismiss = { txDialogInitialType = null },
            onSave = { targetCust, type, amountCentavos, desc, note, date ->
                viewModel.addTransaction(targetCust, type, amountCentavos, desc, note, date) {
                    txDialogInitialType = null
                }
            }
        )
    }

    val txToEdit = editingTransaction
    if (txToEdit != null && customer != null) {
        TransactionFormDialog(
            fixedCustomer = customer,
            existingTransaction = txToEdit,
            isSaving = isSaving,
            onDismiss = { editingTransaction = null },
            onSave = { _, type, amountCentavos, desc, note, date ->
                viewModel.updateTransaction(
                    oldTransaction = txToEdit,
                    allTransactions = successData.transactions,
                    newType = type,
                    newAmountCentavos = amountCentavos,
                    newDescription = desc,
                    newNote = note,
                    newDate = date
                ) {
                    editingTransaction = null
                }
            }
        )
    }

    val txToDelete = deletingTransaction
    if (txToDelete != null && successData != null) {
        val typeLabel = if (txToDelete.transactionType() == TransactionType.CREDIT) {
            stringResource(R.string.tx_type_credit)
        } else {
            stringResource(R.string.tx_type_payment)
        }
        AlertDialog(
            onDismissRequest = { if (!isSaving) deletingTransaction = null },
            title = { Text(stringResource(R.string.delete_tx_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.delete_tx_message,
                        typeLabel,
                        MoneyFormatter.formatPeso(txToDelete.amountCentavos)
                    )
                )
            },
            confirmButton = {
                Button(
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    onClick = {
                        viewModel.deleteTransaction(txToDelete, successData.transactions) {
                            deletingTransaction = null
                        }
                    },
                    modifier = Modifier.testTag("confirm_delete_tx_button")
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSaving,
                    onClick = { deletingTransaction = null }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showPayFullDialog && customer != null && customer.balanceCentavos > 0L) {
        val formatted = MoneyFormatter.formatPeso(customer.balanceCentavos)
        AlertDialog(
            onDismissRequest = { if (!isSaving) showPayFullDialog = false },
            title = { Text(stringResource(R.string.pay_full_title)) },
            text = {
                Text(stringResource(R.string.pay_full_message, formatted, customer.name))
            },
            confirmButton = {
                Button(
                    enabled = !isSaving,
                    onClick = {
                        viewModel.payFullBalance(customer) {
                            showPayFullDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_pay_full_button")
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSaving,
                    onClick = { showPayFullDialog = false }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showReminderSheet && customer != null) {
        val reminderMessage = MoneyFormatter.buildReminderMessage(
            template = successData.reminderTemplate,
            customerName = customer.name,
            balanceCentavos = customer.balanceCentavos,
            storeName = successData.storeName
        )
        ReminderBottomSheet(
            customer = customer,
            initialMessage = reminderMessage,
            onDismiss = { showReminderSheet = false },
            onMessageFeedback = { msg ->
                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    }

    if (showExportSheet && customer != null && successData != null) {
        ExportReportBottomSheet(
            customerName = customer.name,
            isExporting = isExporting,
            onDismiss = { if (!isExporting) showExportSheet = false },
            onSelectFormat = { format ->
                viewModel.exportReport(
                    customer = customer,
                    transactionsWithBalance = successData.transactionsWithBalance,
                    storeName = successData.storeName,
                    format = format
                ) { report ->
                    showExportSheet = false
                    val launched = CustomerReportExporter.shareReport(
                        context = context,
                        report = report,
                        customerName = customer.name,
                        chooserTitle = exportChooserTitle
                    )
                    if (!launched) {
                        coroutineScope.launch { snackbarHostState.showSnackbar(exportNoAppMsg) }
                    }
                }
            }
        )
    }
}

@Composable
private fun CustomerBalanceSummaryCard(
    customer: Customer,
    nowMillis: Long,
    onAddUtang: () -> Unit,
    onRecordPayment: () -> Unit,
    onPayFullBalance: () -> Unit,
    onSendReminder: () -> Unit
) {
    val utangColors = LocalUtangColors.current
    val hasBalance = customer.balanceCentavos > 0L

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.detail_current_balance),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = MoneyFormatter.formatPeso(customer.balanceCentavos),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (hasBalance) utangColors.credit else utangColors.payment,
                        modifier = Modifier.testTag("detail_balance_text")
                    )
                }
            }

            CustomerWarningBadges(customer = customer, nowMillis = nowMillis)

            if (!customer.phone.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Phone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = customer.phone,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (!customer.notes.isNullOrBlank()) {
                Text(
                    text = customer.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            customer.creditLimitCentavos?.let { limit ->
                Text(
                    text = stringResource(
                        R.string.customer_credit_limit_label,
                        MoneyFormatter.formatPeso(limit)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onAddUtang,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = utangColors.creditContainer,
                        contentColor = utangColors.onCreditContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("detail_add_utang_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.detail_add_utang),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onRecordPayment,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = utangColors.paymentContainer,
                        contentColor = utangColors.onPaymentContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("detail_record_payment_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Payments,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.detail_record_payment),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (hasBalance) {
                FilledTonalButton(
                    onClick = onPayFullBalance,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("detail_pay_full_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.detail_pay_full_balance,
                            MoneyFormatter.formatPeso(customer.balanceCentavos)
                        )
                    )
                }

                OutlinedButton(
                    onClick = onSendReminder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("detail_send_reminder_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.detail_send_reminder))
                }
            }
        }
    }
}

@Composable
private fun TransactionHistoryCard(
    item: TransactionWithBalance,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tx = item.transaction
    val isCredit = tx.transactionType() == TransactionType.CREDIT
    val utangColors = LocalUtangColors.current
    val dateFormatter = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    var menuExpanded by remember { mutableStateOf(false) }

    val badgeContainer = if (isCredit) utangColors.creditContainer else utangColors.paymentContainer
    val badgeContent = if (isCredit) utangColors.onCreditContainer else utangColors.onPaymentContainer
    val amountColor = if (isCredit) utangColors.credit else utangColors.payment
    val amountPrefix = if (isCredit) "+" else "-"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tx_item_card_${tx.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = badgeContainer,
                        contentColor = badgeContent,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isCredit) {
                                stringResource(R.string.tx_type_credit)
                            } else {
                                stringResource(R.string.tx_type_payment)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    val displayDate = (tx.date ?: tx.createdAt)?.toDate()
                    if (displayDate != null) {
                        Text(
                            text = dateFormatter.format(displayDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!tx.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = tx.description,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (!tx.note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tx.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$amountPrefix${MoneyFormatter.formatPeso(tx.amountCentavos)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                Text(
                    text = stringResource(
                        R.string.detail_running_balance,
                        MoneyFormatter.formatPeso(item.runningBalanceCentavos)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("tx_more_button_${tx.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.action_edit)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit)) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportReportBottomSheet(
    customerName: String,
    isExporting: Boolean,
    onDismiss: () -> Unit,
    onSelectFormat: (ExportFormat) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = stringResource(R.string.export_sheet_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.export_sheet_subtitle, customerName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isExporting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Text(
                        text = stringResource(R.string.export_generating),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Button(
                enabled = !isExporting,
                onClick = { onSelectFormat(ExportFormat.PDF) },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag("export_pdf_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.export_pdf_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.export_pdf_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }

            OutlinedButton(
                enabled = !isExporting,
                onClick = { onSelectFormat(ExportFormat.CSV) },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag("export_csv_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.TableChart,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.export_csv_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.export_csv_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
