package com.example

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.AppLanguage
import com.example.data.repository.UserPreferencesRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.BiometricLockScreen
import com.example.ui.common.LoadingStateView
import com.example.ui.navigation.UtangAuthenticatedShell
import com.example.ui.theme.UtangTrackerTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var preferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val language by preferencesRepository.languageFlow
                .collectAsStateWithLifecycle(initialValue = AppLanguage.ENGLISH)
            val biometricLockEnabled by preferencesRepository.biometricLockEnabledFlow
                .collectAsStateWithLifecycle(initialValue = null)

            LocalizedAppWrapper(language = language) {
                UtangTrackerTheme {
                    AppContent(
                        biometricLockEnabled = biometricLockEnabled,
                        credentialManager = credentialManager
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalizedAppWrapper(
    language: AppLanguage,
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val localizedContext = remember(baseContext, baseConfig, language) {
        baseContext.createLocalizedContext(baseConfig, language.code)
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        content = content
    )
}

private fun Context.createLocalizedContext(baseConfig: Configuration, languageCode: String): Context {
    val locale = Locale.forLanguageTag(languageCode)
    val config = Configuration(baseConfig)
    config.setLocale(locale)
    return createConfigurationContext(config)
}

private const val BIOMETRIC_RELOCK_TIMEOUT_MS = 30_000L

@Composable
fun AppContent(
    biometricLockEnabled: Boolean?,
    credentialManager: CredentialManager
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var isBiometricUnlocked by rememberSaveable { mutableStateOf(false) }
    var lastBackgroundedElapsedMs by rememberSaveable { mutableLongStateOf(0L) }
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val updated = auth.currentUser
            if (updated == null) {
                isBiometricUnlocked = false
            }
            currentUser = updated
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    DisposableEffect(lifecycleOwner, biometricLockEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    lastBackgroundedElapsedMs = SystemClock.elapsedRealtime()
                }
                Lifecycle.Event.ON_START -> {
                    if (biometricLockEnabled == true && lastBackgroundedElapsedMs > 0L) {
                        val elapsedAway = SystemClock.elapsedRealtime() - lastBackgroundedElapsedMs
                        if (elapsedAway >= BIOMETRIC_RELOCK_TIMEOUT_MS) {
                            isBiometricUnlocked = false
                        }
                    }
                    lastBackgroundedElapsedMs = 0L
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = {
                isBiometricUnlocked = true
                currentUser = Firebase.auth.currentUser
            }
        )
    } else if (biometricLockEnabled == null) {
        LoadingStateView()
    } else if (biometricLockEnabled && !isBiometricUnlocked) {
        BiometricLockScreen(
            onUnlocked = { isBiometricUnlocked = true },
            onSignOut = {
                coroutineScope.launch {
                    withContext(NonCancellable) {
                        try {
                            credentialManager.clearCredentialState(ClearCredentialStateRequest())
                        } catch (e: Exception) {
                            Log.w("Auth", "Failed to clear credential state: ${e.message}")
                        } finally {
                            isBiometricUnlocked = false
                            Firebase.auth.signOut()
                        }
                    }
                }
            }
        )
    } else {
        val uid = user.uid
        key(uid) {
            UtangAuthenticatedShell(
                onLockApp = { isBiometricUnlocked = false }
            )
        }
    }
}
