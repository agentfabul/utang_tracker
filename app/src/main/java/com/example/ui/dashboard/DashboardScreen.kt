package com.example.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.Customer
import com.example.domain.util.MoneyFormatter
import com.example.ui.common.CustomerAvatarBadge
import com.example.ui.common.CustomerFormDialog
import com.example.ui.common.CustomerWarningBadges
import com.example.ui.common.EmptyStateView
import com.example.ui.common.ErrorStateView
import com.example.ui.common.LoadingStateView
import com.example.ui.common.OfflineStatusBanner
import com.example.ui.common.TransactionFormDialog
import com.example.ui.common.UiState
import com.example.ui.theme.LocalUtangColors

@Composable
fun DashboardScreen(
    onNavigateToCustomers: () -> Unit,
    onNavigateToCustomerDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var showQuickTxDialog by remember { mutableStateOf(false) }

    LaunchedEffect(actionMessage) {
        actionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    val currentData = (uiState as? UiState.Success)?.data

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (currentData != null && currentData.allCustomers.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showQuickTxDialog = true },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null
                        )
                    },
                    text = {
                        Text(stringResource(R.string.dashboard_quick_tx_fab))
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("dashboard_quick_tx_fab")
                )
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
                    val nowMillis = remember(data) { System.currentTimeMillis() }

                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxSize()
                    ) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_app_logo),
                                    contentDescription = stringResource(R.string.app_name),
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = data.storeName,
                                        style = MaterialTheme.typography.headlineSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        item {
                            TotalReceivablesHeroCard(
                                totalReceivablesCentavos = data.totalReceivablesCentavos,
                                customersWithBalanceCount = data.customersWithBalanceCount
                            )
                        }

                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CollectionStatCard(
                                    icon = Icons.Filled.Today,
                                    label = stringResource(R.string.dashboard_collected_today),
                                    amountCentavos = data.collectedTodayCentavos,
                                    modifier = Modifier.weight(1f)
                                )
                                CollectionStatCard(
                                    icon = Icons.Filled.Payments,
                                    label = stringResource(R.string.dashboard_collected_week),
                                    amountCentavos = data.collectedThisWeekCentavos,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (data.allCustomers.isEmpty()) {
                            item {
                                DashboardFirstLaunchCard(
                                    onAddCustomerClick = { showAddCustomerDialog = true }
                                )
                            }
                        } else {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = stringResource(R.string.dashboard_top_customers),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    TextButton(
                                        onClick = onNavigateToCustomers,
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("dashboard_view_all_button")
                                    ) {
                                        Text(stringResource(R.string.dashboard_view_all))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            if (data.topCustomers.isEmpty()) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        EmptyStateView(
                                            icon = Icons.Filled.CheckCircle,
                                            title = stringResource(R.string.dashboard_no_balances_title),
                                            body = stringResource(R.string.dashboard_no_balances_body)
                                        )
                                    }
                                }
                            } else {
                                items(
                                    items = data.topCustomers,
                                    key = { it.id }
                                ) { customer ->
                                    DashboardCustomerCard(
                                        customer = customer,
                                        nowMillis = nowMillis,
                                        onClick = { onNavigateToCustomerDetail(customer.id) }
                                    )
                                }
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
                viewModel.addCustomer(name, phone, notes, creditLimit) {
                    showAddCustomerDialog = false
                }
            }
        )
    }

    if (showQuickTxDialog && currentData != null && currentData.allCustomers.isNotEmpty()) {
        TransactionFormDialog(
            customers = currentData.allCustomers,
            isSaving = isSaving,
            onDismiss = { showQuickTxDialog = false },
            onSave = { customer, type, amountCentavos, desc, note, date ->
                viewModel.addTransaction(customer, type, amountCentavos, desc, note, date) {
                    showQuickTxDialog = false
                }
            }
        )
    }
}

@Composable
private fun TotalReceivablesHeroCard(
    totalReceivablesCentavos: Long,
    customersWithBalanceCount: Int
) {
    val brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.84f)
        )
    )
    Card(
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.dashboard_total_receivables),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f)
                )
                Text(
                    text = MoneyFormatter.formatPeso(totalReceivablesCentavos),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("total_receivables_text")
                )
                Surface(
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f),
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Group,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${stringResource(R.string.dashboard_customers_with_balance)}: $customersWithBalanceCount",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionStatCard(
    icon: ImageVector,
    label: String,
    amountCentavos: Long,
    modifier: Modifier = Modifier
) {
    val utangColors = LocalUtangColors.current
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = utangColors.paymentContainer,
                contentColor = utangColors.onPaymentContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = MoneyFormatter.formatPeso(amountCentavos),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = utangColors.payment
            )
        }
    }
}

@Composable
private fun DashboardCustomerCard(
    customer: Customer,
    nowMillis: Long,
    onClick: () -> Unit
) {
    val utangColors = LocalUtangColors.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("dashboard_customer_card_${customer.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            CustomerAvatarBadge(name = customer.name)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                CustomerWarningBadges(
                    customer = customer,
                    nowMillis = nowMillis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = MoneyFormatter.formatPeso(customer.balanceCentavos),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = utangColors.credit
            )
        }
    }
}

@Composable
private fun DashboardFirstLaunchCard(onAddCustomerClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_sari_sari_hero),
                contentDescription = stringResource(R.string.dashboard_empty_title),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.dashboard_empty_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.dashboard_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAddCustomerClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("dashboard_add_first_customer_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.dashboard_add_first_customer))
            }
        }
    }
}
