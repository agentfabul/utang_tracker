# Utang Tracker

A production-quality Android app built with Kotlin, Jetpack Compose, Material 3, Hilt, and Firebase for sari-sari store owners and small businesses in the Philippines to track customer credit (*utang*) and payments (*bayad*).

## Features (v1)
- **Authentication**: Private per-store account via Google Sign-In (Jetpack Credential Manager + Firebase Auth).
- **Dashboard**: Total receivables, number of customers with balances, collections today and this week, top 5 customers by balance, and a quick "Add Utang / Payment" action.
- **Customers**: Add, edit, and delete (with confirmation) customers with optional phone number, address/notes, and credit limit; search by name; sort by highest balance, name, or recent activity; automatic badges for over-credit-limit and 30+ days unpaid accounts.
- **Transactions & Running Balance**: Log `CREDIT` and `PAYMENT` entries with item descriptions, notes, and editable dates. Full chronological running balance per customer, edit/delete support, and one-tap **Pay Full Balance**. All money is stored as `Long` centavos and formatted as `₱1,234.50` only in the UI.
- **PDF & CSV Report Export**: Export any customer's transaction history as a multi-page **PDF statement** or **CSV spreadsheet** from the transaction list screen and save or send via Android's share sheet (`FileProvider`).
- **Biometric App Lock**: Secure store balances and customer ledgers using Android's `BiometricPrompt` API (fingerprint, face unlock, or device PIN/pattern), configurable in Settings.
- **Offline Persistence**: Cloud Firestore persistent local cache is enabled so records work without signal and sync automatically when online.
- **Payment Reminders**: Customizable store name and message template with one-tap **SMS** (`smsto:`) or **Android Share Sheet** (Messenger, Viber, etc.).
- **Bilingual UI & System Theme**: Instant switching between **English** and **Filipino (Tagalog)** using Android string resources, plus system Light/Dark theme support.

## Architecture
- **UI**: Jetpack Compose + Material 3, single-activity (`MainActivity`) with type-safe `@Serializable` Navigation Compose routes (`DashboardRoute`, `CustomersRoute`, `CustomerDetailRoute`, `SettingsRoute`) and adaptive `NavigationBar` / `NavigationRail`.
- **State**: MVVM (`ViewModel` + `StateFlow` + `collectAsStateWithLifecycle`).
- **DI**: Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`).
- **Data**: `UtangRepository` (Cloud Firestore with atomic `WriteBatch` + `FieldValue.increment` balance updates) and `UserPreferencesRepository` (Jetpack DataStore).

## Firebase Setup Instructions (Manual / External Project)
If deploying to your own Firebase project:
1. **Create a Firebase Project** in the [Firebase Console](https://console.firebase.google.com/).
2. **Register your Android App** with package name `com.aistudio.utangtracker.phkpvz` (or your chosen `applicationId`) and add your debug/release **SHA-1** and **SHA-256** signing certificate fingerprints.
3. **Download `google-services.json`** and place it in `app/google-services.json`.
4. **Enable Authentication Providers**:
   - In **Build > Authentication > Sign-in method**, enable **Google**.
   - *(Optional)* Enable **Email/Password** in the Firebase Console if you want to add email/password sign-in and password reset flows.
5. **Enable Cloud Firestore**:
   - In **Build > Firestore Database**, create your database and note its database ID in `app/src/main/res/values/firebase_applet_config.xml` (`firestore_database_id`).
6. **Deploy Security Rules**:
   - Deploy `firestore.rules` using the Firebase CLI:
     ```bash
     firebase deploy --only firestore:rules
     ```

## Running Tests
- **JVM & Robolectric Unit Tests**:
  ```bash
  gradle :app:testDebugUnitTest
  ```
- **Firestore Security Rules Tests** (with Firebase Firestore Emulator on port `8085`):
  ```bash
  FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
  ```

## Publishing to GitHub & Sharing the APK Download Link
1. In AI Studio, open the **Settings menu** and choose **Push to GitHub** to publish this project to your GitHub repository.
2. On push to `main`/`master` (or any `v*` tag), the included GitHub Actions workflow (`.github/workflows/build-apk.yml`) automatically runs unit tests, builds `UtangTracker-v1.0.apk`, and publishes it under **Releases > Latest**.
3. Share this direct download URL with users (replace `<username>` and `<repo>`):
   `https://github.com/<username>/<repo>/releases/latest/download/UtangTracker-v1.0.apk`

