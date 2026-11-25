package com.example.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.repository.AppLanguage
import com.example.domain.util.MoneyFormatter
import com.example.ui.auth.BiometricAuthHelper
import com.example.ui.auth.BiometricAvailability
import com.example.ui.common.ErrorStateView
import com.example.ui.common.LoadingStateView
import com.example.ui.common.OfflineStatusBanner
import com.example.ui.common.UiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onLockApp: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSignOutDialog by remember { mutableStateOf(false) }

    var biometricAvailability by remember(context) {
        mutableStateOf(BiometricAuthHelper.checkAvailability(context))
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                biometricAvailability = BiometricAuthHelper.checkAvailability(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    val defaultTemplate = stringResource(R.string.default_reminder_template)
                    val storeNameErrorMsg = stringResource(R.string.settings_store_name_error)
                    val templateErrorMsg = stringResource(R.string.settings_template_error)

                    var storeName by remember(data.profile.storeName) {
                        mutableStateOf(data.profile.storeName)
                    }
                    var template by remember(data.profile.reminderTemplate) {
                        mutableStateOf(data.profile.reminderTemplate)
                    }
                    var storeNameError by remember { mutableStateOf<String?>(null) }
                    var templateError by remember { mutableStateOf<String?>(null) }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Store & Reminder Template Card
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Storefront,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.settings_store_section),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }

                                OutlinedTextField(
                                    value = storeName,
                                    onValueChange = {
                                        storeName = it
                                        storeNameError = null
                                    },
                                    label = { Text(stringResource(R.string.settings_store_name_label)) },
                                    placeholder = { Text(stringResource(R.string.settings_store_name_hint)) },
                                    isError = storeNameError != null,
                                    supportingText = storeNameError?.let { { Text(it) } },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_store_name_input")
                                )

                                OutlinedTextField(
                                    value = template,
                                    onValueChange = {
                                        template = it
                                        templateError = null
                                    },
                                    label = { Text(stringResource(R.string.settings_template_label)) },
                                    isError = templateError != null,
                                    supportingText = templateError?.let { { Text(it) } },
                                    minLines = 3,
                                    maxLines = 5,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_template_input")
                                )

                                Text(
                                    text = stringResource(R.string.settings_template_placeholders),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("[Name]", "[amount]", "[Store Name]").forEach { token ->
                                        AssistChip(
                                            onClick = { template = "$template $token".trimStart() },
                                            label = { Text(token) }
                                        )
                                    }
                                    AssistChip(
                                        onClick = { template = defaultTemplate },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text(stringResource(R.string.settings_reset_template)) }
                                    )
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val previewMsg = MoneyFormatter.buildReminderMessage(
                                        template = template,
                                        customerName = "Juan",
                                        balanceCentavos = 25000L,
                                        storeName = storeName.ifBlank { stringResource(R.string.default_store_name) }
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_template_preview, previewMsg),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }

                                Button(
                                    enabled = !isSaving,
                                    onClick = {
                                        if (storeName.trim().isEmpty()) {
                                            storeNameError = storeNameErrorMsg
                                            return@Button
                                        }
                                        if (template.trim().isEmpty()) {
                                            templateError = templateErrorMsg
                                            return@Button
                                        }
                                        viewModel.saveSettings(storeName, template)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 50.dp)
                                        .testTag("settings_save_button")
                                ) {
                                    if (isSaving) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text(stringResource(R.string.settings_save_changes))
                                    }
                                }
                            }
                        }

                        // Language Selection Card
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Language,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.settings_language_section),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    FilterChip(
                                        selected = data.language == AppLanguage.ENGLISH,
                                        onClick = { viewModel.setLanguage(AppLanguage.ENGLISH) },
                                        label = { Text(stringResource(R.string.settings_language_english)) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("language_english_chip")
                                    )
                                    FilterChip(
                                        selected = data.language == AppLanguage.FILIPINO,
                                        onClick = { viewModel.setLanguage(AppLanguage.FILIPINO) },
                                        label = { Text(stringResource(R.string.settings_language_filipino)) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("language_filipino_chip")
                                    )
                                }
                            }
                        }

                        // App Security / Biometric Lock Card
                        val promptTitle = stringResource(R.string.biometric_prompt_title)
                        val confirmEnableSub = stringResource(R.string.biometric_prompt_confirm_enable)
                        val confirmDisableSub = stringResource(R.string.biometric_prompt_confirm_disable)
                        val negativeBtn = stringResource(R.string.biometric_prompt_negative)
                        val failedRetryMsg = stringResource(R.string.biometric_auth_failed_retry)
                        val noneEnrolledMsg = stringResource(R.string.biometric_status_none_enrolled)
                        val unavailableMsg = stringResource(R.string.biometric_status_unavailable)

                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Fingerprint,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.biometric_settings_section),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.biometric_lock_title),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = stringResource(R.string.biometric_lock_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Switch(
                                        checked = data.biometricLockEnabled,
                                        onCheckedChange = { targetEnabled ->
                                            if (targetEnabled) {
                                                when (biometricAvailability) {
                                                    BiometricAvailability.AVAILABLE -> {
                                                        if (activity != null) {
                                                            BiometricAuthHelper.authenticate(
                                                                activity = activity,
                                                                title = promptTitle,
                                                                subtitle = confirmEnableSub,
                                                                negativeButtonText = negativeBtn,
                                                                failedMessage = failedRetryMsg,
                                                                onSuccess = {
                                                                    viewModel.setBiometricLockEnabled(true)
                                                                },
                                                                onError = { err ->
                                                                    coroutineScope.launch {
                                                                        snackbarHostState.showSnackbar(err)
                                                                    }
                                                                }
                                                            )
                                                        } else {
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar(unavailableMsg)
                                                            }
                                                        }
                                                    }
                                                    BiometricAvailability.NONE_ENROLLED -> {
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar(noneEnrolledMsg)
                                                        }
                                                    }
                                                    BiometricAvailability.UNAVAILABLE -> {
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar(unavailableMsg)
                                                        }
                                                    }
                                                }
                                            } else {
                                                if (activity != null && biometricAvailability == BiometricAvailability.AVAILABLE) {
                                                    BiometricAuthHelper.authenticate(
                                                        activity = activity,
                                                        title = promptTitle,
                                                        subtitle = confirmDisableSub,
                                                        negativeButtonText = negativeBtn,
                                                        failedMessage = failedRetryMsg,
                                                        onSuccess = {
                                                            viewModel.setBiometricLockEnabled(false)
                                                        },
                                                        onError = { err ->
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar(err)
                                                            }
                                                        }
                                                    )
                                                } else {
                                                    viewModel.setBiometricLockEnabled(false)
                                                }
                                            }
                                        },
                                        modifier = Modifier.testTag("biometric_lock_switch")
                                    )
                                }

                                val statusText = when (biometricAvailability) {
                                    BiometricAvailability.AVAILABLE -> stringResource(R.string.biometric_status_ready)
                                    BiometricAvailability.NONE_ENROLLED -> stringResource(R.string.biometric_status_none_enrolled)
                                    BiometricAvailability.UNAVAILABLE -> stringResource(R.string.biometric_status_unavailable)
                                }
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (biometricAvailability == BiometricAvailability.NONE_ENROLLED) {
                                    OutlinedButton(
                                        onClick = { BiometricAuthHelper.openDeviceLockSetup(context) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("biometric_setup_button")
                                    ) {
                                        Text(stringResource(R.string.biometric_setup_button))
                                    }
                                }

                                if (data.biometricLockEnabled) {
                                    OutlinedButton(
                                        onClick = onLockApp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("biometric_lock_now_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Lock,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.biometric_lock_now))
                                    }
                                }
                            }
                        }

                        // Theme Info Card
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Brightness6,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.settings_theme_section),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_theme_system),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Account & Sign Out Card
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = stringResource(R.string.settings_account_section),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.settings_signed_in_as,
                                                data.userEmail
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = { showSignOutDialog = true },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("sign_out_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.settings_sign_out),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(stringResource(R.string.settings_sign_out_title)) },
            text = { Text(stringResource(R.string.settings_sign_out_message)) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    onClick = {
                        showSignOutDialog = false
                        viewModel.signOut()
                    },
                    modifier = Modifier.testTag("confirm_sign_out_button")
                ) {
                    Text(stringResource(R.string.settings_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
