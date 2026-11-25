package com.example.ui.customers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.Customer
import com.example.data.model.CustomerSortOrder
import com.example.domain.util.MoneyFormatter
import com.example.ui.common.CustomerAvatarBadge
import com.example.ui.common.CustomerFormDialog
import com.example.ui.common.CustomerWarningBadges
import com.example.ui.common.EmptyStateView
import com.example.ui.common.ErrorStateView
import com.example.ui.common.LoadingStateView
import com.example.ui.common.OfflineStatusBanner
import com.example.ui.common.UiState
import com.example.ui.theme.LocalUtangColors
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    onCustomerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CustomersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddCustomerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(actionMessage) {
        actionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.customers_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddCustomerDialog = true },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.PersonAdd,
                        contentDescription = null
                    )
                },
                text = { Text(stringResource(R.string.customers_add_fab)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_customer_fab")
            )
        }
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OfflineStatusBanner()

            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = { Text(stringResource(R.string.customers_search_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.setSearchQuery("") },
                                modifier = Modifier.testTag("clear_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = stringResource(R.string.customers_clear_search)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_search_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = sortOrder == CustomerSortOrder.HIGHEST_BALANCE,
                        onClick = { viewModel.setSortOrder(CustomerSortOrder.HIGHEST_BALANCE) },
                        label = { Text(stringResource(R.string.sort_highest_balance)) },
                        modifier = Modifier.testTag("sort_balance_chip")
                    )
                    FilterChip(
                        selected = sortOrder == CustomerSortOrder.NAME_ASC,
                        onClick = { viewModel.setSortOrder(CustomerSortOrder.NAME_ASC) },
                        label = { Text(stringResource(R.string.sort_name)) },
                        modifier = Modifier.testTag("sort_name_chip")
                    )
                    FilterChip(
                        selected = sortOrder == CustomerSortOrder.RECENT_ACTIVITY,
                        onClick = { viewModel.setSortOrder(CustomerSortOrder.RECENT_ACTIVITY) },
                        label = { Text(stringResource(R.string.sort_recent_activity)) },
                        modifier = Modifier.testTag("sort_activity_chip")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (val state = uiState) {
                is UiState.Loading -> LoadingStateView()
                is UiState.Error -> ErrorStateView(message = state.message)
                is UiState.Success -> {
                    val data = state.data
                    val nowMillis = remember(data) { System.currentTimeMillis() }

                    if (data.totalCount == 0) {
                        EmptyStateView(
                            icon = Icons.Filled.PersonAdd,
                            title = stringResource(R.string.customers_empty_title),
                            body = stringResource(R.string.customers_empty_body),
                            actionLabel = stringResource(R.string.customers_add_fab),
                            onAction = { showAddCustomerDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                    } else if (data.customers.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Filled.SearchOff,
                            title = stringResource(R.string.customers_search_empty_title),
                            body = stringResource(R.string.customers_search_empty_body),
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxSize()
                        ) {
                            items(
                                items = data.customers,
                                key = { it.id }
                            ) { customer ->
                                CustomerRowCard(
                                    customer = customer,
                                    nowMillis = nowMillis,
                                    onClick = { onCustomerClick(customer.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddCustomerDialog) {
        CustomerFormDialog(
            isSaving = isSaving,
            onDismiss = { showAddCustomerDialog = false },
            onSave = { name, phone, notes, creditLimit ->
                viewModel.addCustomer(name, phone, notes, creditLimit) { newId ->
                    showAddCustomerDialog = false
                    onCustomerClick(newId)
                }
            }
        )
    }
}

@Composable
private fun CustomerRowCard(
    customer: Customer,
    nowMillis: Long,
    onClick: () -> Unit
) {
    val utangColors = LocalUtangColors.current
    val dateFormatter = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("customer_item_card_${customer.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            CustomerAvatarBadge(name = customer.name)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!customer.phone.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = customer.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val lastPaymentText = customer.lastPaymentAt?.toDate()?.let { date ->
                    stringResource(R.string.customer_last_payment, dateFormatter.format(date))
                } ?: stringResource(R.string.customer_no_payment_yet)

                Text(
                    text = lastPaymentText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )

                CustomerWarningBadges(
                    customer = customer,
                    nowMillis = nowMillis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                val balanceColor = when {
                    customer.balanceCentavos > 0L -> utangColors.credit
                    customer.balanceCentavos < 0L -> utangColors.payment
                    else -> utangColors.payment
                }
                Text(
                    text = if (customer.balanceCentavos == 0L) {
                        stringResource(R.string.customer_settled)
                    } else {
                        MoneyFormatter.formatPeso(customer.balanceCentavos)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = balanceColor
                )
                customer.creditLimitCentavos?.let { limit ->
                    Text(
                        text = stringResource(
                            R.string.customer_credit_limit_label,
                            MoneyFormatter.formatPeso(limit)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
