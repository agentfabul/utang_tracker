package com.example.ui.settings

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.model.UserProfile
import com.example.data.repository.AppLanguage
import com.example.data.repository.UserPreferencesRepository
import com.example.data.repository.UtangRepository
import com.example.ui.common.UiState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsData(
    val profile: UserProfile,
    val language: AppLanguage,
    val biometricLockEnabled: Boolean,
    val userEmail: String
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: UtangRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val auth: FirebaseAuth,
    private val credentialManager: CredentialManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val defaultStoreName = context.getString(R.string.default_store_name)
    private val defaultTemplate = context.getString(R.string.default_reminder_template)

    private fun authUidFlow() = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser?.uid)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState<SettingsData>> = authUidFlow()
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) {
                flowOf(UiState.Loading)
            } else {
                combine(
                    repository.observeUserProfile(uid, defaultStoreName, defaultTemplate),
                    preferencesRepository.languageFlow,
                    preferencesRepository.biometricLockEnabledFlow
                ) { profile, language, biometricLockEnabled ->
                    val email = auth.currentUser?.email
                        ?: auth.currentUser?.displayName
                        ?: uid
                    UiState.Success(
                        SettingsData(
                            profile = profile,
                            language = language,
                            biometricLockEnabled = biometricLockEnabled,
                            userEmail = email
                        )
                    ) as UiState<SettingsData>
                }.catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val root = firestoreError ?: error
                    emit(UiState.Error(root.localizedMessage ?: context.getString(R.string.error_generic)))
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun saveSettings(storeName: String, reminderTemplate: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateUserSettings(uid, storeName, reminderTemplate)
            _isSaving.value = false
            result.onSuccess {
                _actionMessage.value = context.getString(R.string.settings_saved)
            }.onFailure { error ->
                _actionMessage.value = error.localizedMessage ?: context.getString(R.string.error_generic)
            }
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            preferencesRepository.setLanguage(language)
        }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBiometricLockEnabled(enabled)
            _actionMessage.value = if (enabled) {
                context.getString(R.string.biometric_enabled_message)
            } else {
                context.getString(R.string.biometric_disabled_message)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w("Auth", "Failed to clear credential state: ${e.message}")
                } finally {
                    auth.signOut()
                }
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
