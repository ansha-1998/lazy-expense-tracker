# Couple Expense Tracker — Documentation

## Table of Contents

1. [Overview](#overview)
2. [Purpose and Rationale](#purpose-and-rationale)
3. [Why It Matters](#why-it-matters)
4. [Features](#features)
5. [Architecture](#architecture)
6. [Technical Stack](#technical-stack)
7. [Database Design](#database-design)
8. [SMS Auto-Detection Engine](#sms-auto-detection-engine)
9. [Google Drive Sync](#google-drive-sync)
10. [UI Screens](#ui-screens)
11. [Home Screen Widget](#home-screen-widget)
12. [Notification System](#notification-system)
13. [Testing](#testing)
14. [Build Configuration](#build-configuration)
15. [Project Structure](#project-structure)

---

## Overview

**Couple Expense Tracker** is a private, offline-first Android application designed for two people (a couple) to independently log, categorise, and share their financial transactions. Each person installs the APK on their own phone. Expenses are captured automatically from bank SMS messages, categorised with a single tap, and synced to a shared Google Drive folder so both partners always have a combined view of household spending.

- **Package**: `com.couple.expensetracker`
- **Min SDK**: API 26 (Android 8.0 Oreo)
- **Target SDK**: API 34 (Android 14)
- **Version**: 1.0 (versionCode 1)
- **Distribution**: Sideloaded APK — not published to the Play Store

---

## Purpose and Rationale

Most expense-tracking apps are designed for a single user or require both partners to use the same subscription account, share login credentials, or surrender their data to a third-party cloud. This app was built to solve three specific problems:

### 1. Zero friction capture
Bank transaction SMS messages arrive within seconds of every payment. Instead of requiring the user to open an app and type in an amount, the app intercepts those messages in the background and immediately creates a pending transaction. The user only needs to tap one button to classify it.

### 2. Privacy-first shared tracking
Each partner's raw transaction data lives only on their own phone. The sync mechanism uploads a JSON snapshot to a shared Google Drive folder that only the two partners can access. No third-party server ever sees the data.

### 3. Minimal coupling between partners
The two instances of the app are loosely coupled. Sync is manual and pull-based — one partner's phone doesn't know or care whether the other person's phone is online, and there is no real-time push or server-side logic to maintain.

---

## Why It Matters

Tracking shared expenses manually is a constant source of friction for couples. Mismatched records, forgotten transactions, and unclear ownership of expenses lead to arguments and financial blind spots. This app:

- Eliminates manual data entry for the majority of transactions (anything paid by card or UPI is captured automatically from SMS)
- Provides a **Combined** view showing both partners' spending in one place, broken down by month
- Lets each partner independently classify transactions as **Personal** (their own expense), **Combined** (shared household expense), or **Other** (transfers, investments, etc.)
- Surfaces an **Unclassified** queue so nothing falls through the cracks
- Keeps a monthly summary that shows total personal spend, combined spend, and partner spend for any selected month

---

## Features

| Feature | Description |
|---|---|
| Auto-capture from SMS | Intercepts bank transaction messages and creates pending transactions automatically |
| Manual entry | Add transactions manually via the Manual Add screen |
| Tagging | Classify each transaction as Personal / Combined / Other / Unclassified |
| Unclassified queue | Dedicated screen listing all transactions not yet tagged |
| Summary screen | Month-by-month breakdown with personal, combined, and partner totals |
| Partner sync | Google Drive-based sync; uploads own data, downloads partner's |
| Incremental sync | On repeat syncs, only downloads partner records newer than the last known file timestamp |
| Home screen widget | Glance-based widget showing current month totals and a quick-sync button |
| Notification actions | Tap Personal / Combined / Other directly from the bank notification without opening the app |
| Offline support | All reads and writes go to local Room DB; network is only needed for sync |
| Custom SMS keywords | User can configure additional trigger keywords and exclusion keywords in Settings |
| RCS message support | NFKC normalization handles Unicode styled characters in RCS bank messages |

---

## Architecture

The app follows a single-activity, MVVM architecture with Hilt for dependency injection.

```
UI Layer (Compose Screens + ViewModels)
        |
Repository Layer (TransactionRepository, SummaryRepository)
        |
Data Layer (Room DB + DataStore Preferences + DriveSync)
        |
Android System (SMS BroadcastReceiver, NotificationListenerService)
```

### Key design decisions

- **Offline-first**: Every write goes directly to Room. Sync is a separate, manually triggered operation.
- **Synchronous summary recalculation**: After every transaction write, the repository immediately recalculates and persists the `MonthlySummaryEntity` for that month. There is no deferred or lazy aggregation — the summary is always up to date.
- **No server**: The entire backend is a shared Google Drive folder. Two JSON files (transactions + summary) per partner, upserted on every sync.
- **Merge strategy**: Partner data is replaced wholesale on full sync or merged incrementally by comparing `lastModified` timestamps on individual records.

---

## Technical Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 1.9.23 |
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose |
| DI | Hilt (Dagger) |
| ORM | Room 3.x with KSP code generation |
| Preferences | DataStore Preferences |
| Networking | Retrofit 2 + OkHttp 4 |
| JSON | Gson |
| Auth | Google Play Services Auth (OAuth 2.0) |
| Sync backend | Google Drive REST API v3 |
| Widget | Jetpack Glance (AppWidget) |
| Concurrency | Kotlin Coroutines + Flow |
| Build system | Gradle 8.x with Kotlin DSL + Version Catalog (`libs.versions.toml`) |
| Annotation processing | KSP (Room), KAPT (Hilt) |
| JVM target | Java 17 |
| Kotlin Compose compiler extension | 1.5.11 |

---

## Database Design

The Room database (`AppDatabase`, version 3) contains four tables:

### `transactions`
Stores the current user's own transactions.

| Column | Type | Notes |
|---|---|---|
| `id` | String (PK) | UUID generated at creation |
| `amount` | Double | Parsed from SMS or entered manually |
| `paymentType` | String | Card / UPI / NEFT / RTGS / IMPS / Other |
| `bankName` | String | Resolved from SMS sender ID |
| `last4OrRef` | String | Card last-4 digits or UPI 12-digit reference |
| `date` | Long | Unix timestamp (ms) |
| `tag` | String | PERSONAL / COMBINED / OTHER / UNCLASSIFIED |
| `rawMessage` | String? | Original SMS body (added in migration 1→2) |
| `category` | String? | Optional spend category (added in migration 2→3) |
| `lastModified` | Long | Timestamp of last local edit; used in sync merge |

### `monthly_summary`
Aggregated totals per calendar month for the current user.

| Column | Type | Notes |
|---|---|---|
| `monthKey` | String (PK) | Format: `YYYY-MM` |
| `personalTotal` | Double | Sum of PERSONAL-tagged transactions |
| `combinedTotal` | Double | Sum of COMBINED-tagged transactions |
| `otherTotal` | Double | Sum of OTHER-tagged transactions |

### `partner_transactions`
A mirror of the partner's transaction list downloaded from Drive.

| Column | Type | Notes |
|---|---|---|
| `id` | String (PK) | Matches the partner's own transaction UUID |
| `username` | String | Partner's username; used to scope queries |
| `amount` | Double | |
| `paymentType` | String | |
| `bankName` | String | |
| `last4OrRef` | String | |
| `date` | Long | |
| `tag` | String | |
| `lastModified` | Long | Used for incremental sync |

### `partner_summary`
A mirror of the partner's monthly summary downloaded from Drive.

| Column | Type | Notes |
|---|---|---|
| `monthKey` | String (PK) | |
| `username` | String | |
| `personalTotal` | Double | |
| `combinedTotal` | Double | |
| `otherTotal` | Double | |

**Schema migrations**

| Migration | Change |
|---|---|
| 1 → 2 | Added `rawMessage TEXT` column to `transactions` |
| 2 → 3 | Added `category TEXT` column to `transactions` |

---

## SMS Auto-Detection Engine

`SMSParser.kt` is the core of the auto-capture feature. It is a pure Kotlin object (no Android dependencies) that can be unit-tested in isolation.

### Parse pipeline

```
Incoming SMS (sender, body)
    ↓
NFKC normalization          ← handles RCS Unicode styled text
    ↓
OTP filter                  ← discard if message is an OTP
    ↓
Exclusion keyword filter    ← discard if user-configured exclusion phrase matched
    ↓
Transaction keyword filter  ← require at least one transaction verb
    ↓
Amount extraction           ← require a parseable ₹ / Rs. / INR amount
    ↓
Bank name resolution        ← map SMS sender ID to human-readable bank name
    ↓
Payment type + reference    ← Card (last 4), UPI (12-digit ref), NEFT, RTGS, IMPS
    ↓
ParsedSMS result
```

### Bank sender ID map

30+ Indian bank sender IDs are mapped to friendly names including HDFC, SBI, ICICI, Axis, Kotak, IDFC First, Yes Bank, Paytm, PNB, Bank of India, Canara Bank, Central Bank, Union Bank, Standard Chartered, IndusInd, AU Small Finance, Federal Bank, RBL Bank, Bajaj Finance, Amazon Pay, and HSBC.

### Amount patterns (tried in order)

1. `Rs. / INR / ₹` prefix followed by a number
2. `debited for/with/by/of` followed by an optional currency prefix and number
3. `spent / payment of / paid` followed by a number
4. Number followed by `debited / charged`

### OTP guard

Messages containing `OTP`, `one-time password`, `verification code`, `authentication code`, `login code`, `access code`, `passcode is`, or `security code` are immediately discarded before any further parsing.

---

## Google Drive Sync

`DriveSync.kt` implements the entire sync protocol using the Google Drive REST API v3 via Retrofit.

### Sync flow

```
sync()
  ├─ uploadTransactions()   → upsert {username}_transactions.json in shared folder
  ├─ uploadSummary()        → upsert {username}_summary.json in shared folder
  ├─ downloadPartnerTransactions()
  │    ├─ Full sync: replaceAll() — overwrites local partner table entirely
  │    └─ Incremental sync: inserts only records with lastModified > local max
  └─ downloadPartnerSummary() → upsertAll() partner summary records
```

### Conflict resolution

- The app does not resolve conflicts between the user's own edits and partner edits because they write to separate files and separate DB tables.
- Within the partner table, incremental sync resolves conflicts by `lastModified` timestamp — the record with the higher timestamp wins.

### Connectivity guard

`ConnectivityObserver` exposes a `StateFlow<Boolean>` backed by `ConnectivityManager.NetworkCallback`. `DriveSync.sync()` returns `false` immediately if the device is offline.

### Data pruning

Only the last six months of transactions are uploaded to Drive (`DateUtils.sixMonthsCutoff()`). This caps file size regardless of how long the app is used.

---

## UI Screens

| Screen | File | Description |
|---|---|---|
| Summary | `SummaryScreen.kt` | Month picker + totals for personal, combined, and partner spend |
| Transactions | `TransactionsScreen.kt` | Full scrollable list of own transactions with tag filter |
| Unclassified | `UnclassifiedScreen.kt` | Queue of transactions that have not been tagged yet |
| Manual Add | `ManualAddScreen.kt` | Form to add a transaction manually (amount, date, type, tag) |
| Discard Confirmation | `DiscardConfirmationScreen.kt` | Confirmation dialog before deleting a pending transaction |
| Settings | `SettingsScreen.kt` | Google Sign-In, username, partner username, Drive folder ID, sync, custom keywords |

### Shared components

| Component | File | Description |
|---|---|---|
| `TransactionRow` | `TransactionRow.kt` | Single transaction card used in list screens |
| `TagBottomSheet` | `TagBottomSheet.kt` | Bottom sheet for selecting Personal / Combined / Other |
| `EditTransactionDialog` | `EditTransactionDialog.kt` | Dialog for editing amount, date, or type on an existing transaction |
| `MonthPicker` | `MonthPicker.kt` | Horizontal month-scroll selector used in Summary |

---

## Home Screen Widget

`ExpenseWidget.kt` uses Jetpack Glance to render an Android AppWidget that displays:

- Current month's personal and combined totals
- A sync button that triggers a background sync without opening the app
- A tab switcher to toggle between Personal and Combined views
- A tag quick-action to reclassify the latest unclassified transaction

The widget accesses the Room database directly via a Hilt `EntryPoint` (`WidgetEntryPoint.kt`) because Glance components are not Hilt-injected by default.

---

## Notification System

`BankNotificationListener.kt` extends `NotificationListenerService` and intercepts all incoming notifications. When it detects a notification from a known bank sender, it:

1. Parses the notification body through `SMSParser`
2. If a transaction is extracted, creates a pending `TransactionEntity` tagged as `UNCLASSIFIED`
3. Posts a follow-up notification via `NotificationHelper` with four action buttons:

| Action button | Behaviour |
|---|---|
| Personal | Tags the transaction as PERSONAL without opening the app |
| Combined | Tags the transaction as COMBINED without opening the app |
| Other | Tags the transaction as OTHER without opening the app |
| Discard | Opens the Discard Confirmation screen in the app |

`NotificationActionReceiver.kt` is a `BroadcastReceiver` that handles the background tag actions so the user never needs to open the app for routine classification.

---

## Testing

The project includes both unit tests and instrumented (device) tests.

### Unit tests (`src/test/`)

| Test class | What it covers |
|---|---|
| `SMSParserTest` | Amount extraction, OTP filtering, bank name resolution, UPI/card reference parsing, RCS Unicode normalization |
| `SummaryCalculationTest` | Monthly summary aggregation logic |
| `DateUtilsTest` | Month key formatting, six-month cutoff calculation |
| `DriveJsonSerializationTest` | Round-trip Gson serialization of transaction and summary entities |
| `PurgeJobTest` | Pruning of transactions older than the six-month cutoff |

### Instrumented tests (`src/androidTest/`)

| Test class | What it covers |
|---|---|
| `AppDatabaseTest` | Room database creation and migration correctness |
| `TransactionDaoTest` | CRUD operations and query correctness on `TransactionDao` |
| `SummaryDaoTest` | Upsert and query behaviour on `MonthlySummaryDao` |
| `UnclassifiedScreenTest` | Compose UI test for the Unclassified screen |
| `SummaryScreenTest` | Compose UI test for the Summary screen |
| `ManualAddScreenTest` | Compose UI test for the Manual Add form |

Hilt test injection is handled by `HiltTestRunner`, a custom `AndroidJUnitRunner` subclass.

---

## Build Configuration

### `app/build.gradle.kts`

```
compileSdk  = 34
minSdk      = 26
targetSdk   = 34
versionCode = 1
versionName = "1.0"
jvmTarget   = "17"
Compose compiler extension = "1.5.11"
```

Annotation processors:
- `ksp` — Room (faster than KAPT for code generation)
- `kapt` — Hilt (KAPT required because Hilt uses Java annotation processing)

### Dependency highlights

| Library | Purpose |
|---|---|
| `androidx.room` | Local SQLite ORM |
| `hilt-android` | Dependency injection |
| `androidx.navigation.compose` | Screen navigation |
| `androidx.glance.appwidget` | Home screen widget |
| `androidx.datastore.preferences` | Lightweight key-value preferences (replaces SharedPreferences) |
| `retrofit` + `okhttp` | Google Drive REST API calls |
| `google.play.services.auth` | Google OAuth 2.0 Sign-In |
| `kotlinx.coroutines` | Async/structured concurrency |
| `mockk` | Mocking library for unit tests |

---

## Project Structure

```
couple_expense_tracking_apk/
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/couple/expensetracker/
│       │   │   ├── MainActivity.kt
│       │   │   ├── ExpenseTrackerApp.kt          ← Application class, Hilt entry point
│       │   │   ├── data/
│       │   │   │   ├── db/
│       │   │   │   │   ├── AppDatabase.kt        ← Room DB, 4 tables, 2 migrations
│       │   │   │   │   ├── dao/                  ← TransactionDao, MonthlySummaryDao,
│       │   │   │   │   │                            PartnerTransactionDao, PartnerSummaryDao
│       │   │   │   │   └── entities/             ← Room entity data classes
│       │   │   │   ├── preferences/
│       │   │   │   │   └── AppPreferences.kt     ← DataStore wrapper
│       │   │   │   ├── repository/
│       │   │   │   │   ├── TransactionRepository.kt
│       │   │   │   │   └── SummaryRepository.kt  ← Recalculates summary on every write
│       │   │   │   └── sync/
│       │   │   │       ├── DriveSync.kt          ← Full and incremental sync logic
│       │   │   │       └── DriveApiService.kt    ← Retrofit interface for Drive REST API
│       │   │   ├── di/
│       │   │   │   ├── DatabaseModule.kt         ← Hilt module: Room + DAO bindings
│       │   │   │   └── NetworkModule.kt          ← Hilt module: Retrofit, OkHttp, Gson
│       │   │   ├── notification/
│       │   │   │   └── NotificationHelper.kt
│       │   │   ├── receiver/
│       │   │   │   ├── BankNotificationListener.kt ← NotificationListenerService
│       │   │   │   └── NotificationActionReceiver.kt ← Background tag actions
│       │   │   ├── ui/
│       │   │   │   ├── screens/                  ← 6 Compose screens
│       │   │   │   ├── components/               ← TransactionRow, TagBottomSheet, etc.
│       │   │   │   ├── viewmodel/                ← 5 ViewModels (one per screen)
│       │   │   │   ├── navigation/
│       │   │   │   │   └── AppNavigation.kt
│       │   │   │   └── theme/                    ← Color, Type, Theme
│       │   │   ├── util/
│       │   │   │   ├── SMSParser.kt              ← Pure Kotlin SMS parsing engine
│       │   │   │   ├── DateUtils.kt
│       │   │   │   └── ConnectivityObserver.kt
│       │   │   └── widget/                       ← Glance AppWidget + callbacks
│       │   └── res/
│       │       ├── values/strings.xml            ← Contains google_oauth_client_id placeholder
│       │       └── xml/
│       │           ├── expense_widget_info.xml
│       │           └── file_paths.xml
│       ├── test/                                 ← JVM unit tests
│       └── androidTest/                          ← Instrumented device tests
├── SETUP.md                                      ← Step-by-step first-time setup guide
├── DOCUMENTATION.md                              ← This file
└── gradle/
    └── libs.versions.toml                        ← Centralised version catalog
```

---

*Last updated: May 2026*
