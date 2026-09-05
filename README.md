# Couple Expense Tracker

A private Android app for two people to track, classify, and share their daily expenses — with zero manual entry for most transactions.

Bank SMS messages are intercepted the moment they arrive, a transaction is created automatically, and you classify it with a single tap from the notification shade. A shared Google Drive folder keeps both partners in sync without any server or subscription.

---

## Why this exists

Most expense apps either demand a shared login (privacy trade-off) or require both people to type in every purchase (friction). This app solves both:

- **Auto-capture** — debit card, credit card, and UPI payments show up the instant the bank SMS lands
- **Private by default** — raw data never leaves your phone except to a Drive folder only you two can access
- **Minimal effort** — classify a transaction as Personal, Combined, or Other without ever opening the app

---

## Features

- **SMS auto-detection** — parses transaction messages from 30+ Indian banks (HDFC, SBI, ICICI, Axis, Kotak, Paytm, and more)
- **Notification quick-actions** — tag a transaction as Personal / Combined / Other directly from the bank notification
- **Unclassified queue** — nothing falls through the cracks; every auto-captured transaction waits for your tag
- **Monthly summary** — see your personal spend, combined household spend, and your partner's spend, month by month
- **Partner sync** — one tap uploads your data to a shared Google Drive folder and pulls your partner's latest
- **Home screen widget** — current month totals at a glance with a quick-sync button
- **Manual entry** — add cash or offline transactions by hand
- **Offline-first** — all reads and writes are local; internet is only needed to sync
- **RCS support** — handles Unicode-styled text in RCS bank messages via NFKC normalization
- **Custom keywords** — configure additional trigger or exclusion words in Settings

---

## How it works

```text
Bank sends SMS
      ↓
App intercepts it in the background
      ↓
SMSParser checks: not an OTP? has a transaction keyword? has a ₹ amount?
      ↓
Transaction created as "Unclassified"
      ↓
Notification appears with 3 tag buttons (Personal / Combined / Other)
      ↓
Tap one → transaction is classified, notification dismissed
      ↓
(Optional) Sync → your JSON is uploaded, partner's JSON is downloaded
```

---

## Tech stack

| Layer | Technology |
| --- | --- |
| Language | Kotlin 1.9.23 |
| UI | Jetpack Compose + Material 3 |
| Database | Room (SQLite) |
| Dependency injection | Hilt |
| Preferences | DataStore |
| Networking | Retrofit + OkHttp |
| Auth | Google Sign-In (OAuth 2.0) |
| Sync backend | Google Drive REST API v3 |
| Widget | Jetpack Glance |
| Concurrency | Kotlin Coroutines + Flow |

---

## Requirements

- Android 8.0 or higher (API 26+)
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- A Google account (for Drive sync)
- SMS permission granted manually on Android 10+

---

## Quick start

### 1. Clone and open

```bash
git clone <your-repo-url>
```

Open the project folder in Android Studio and let Gradle sync finish (first run downloads ~500 MB).

### 2. Set up Google credentials

1. Create a project in [Google Cloud Console](https://console.cloud.google.com/)
2. Enable the **Google Drive API**
3. Create an **Android OAuth client ID** (package: `com.couple.expensetracker`, SHA-1 from your debug keystore)
4. Create a **Web OAuth client ID** (needed for `requestServerAuthCode`)
5. Download `google-services.json` and replace `app/google-services.json` with it
6. Open `app/src/main/res/values/strings.xml` and paste your Web Client ID:

```xml
<string name="google_oauth_client_id">YOUR_WEB_CLIENT_ID.apps.googleusercontent.com</string>
```

> Full step-by-step instructions are in [SETUP.md](SETUP.md).

### 3. Create a shared Drive folder

1. Create a folder in Google Drive (e.g. `ExpenseSync`)
2. Share it with **Editor** access for both Google accounts
3. Copy the folder ID from the URL: `drive.google.com/drive/folders/<FOLDER_ID>`

### 4. Build and install

**Via Android Studio** — click Run (`Shift+F10`).

**Via terminal** (macOS):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# Build APK
java -classpath "gradle/wrapper/gradle-wrapper.jar" \
     org.gradle.wrapper.GradleWrapperMain assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 5. First launch

1. Grant SMS and Notification permissions when prompted
2. Open **Settings** → sign in with Google
3. Enter your username and your partner's username
4. Paste the Drive folder ID
5. Tap **Save** for each field
6. Go to **Combined** tab → tap the sync button to verify the Drive connection

---

## Project structure

```text
app/src/main/java/com/couple/expensetracker/
├── data/
│   ├── db/              # Room database, 4 tables, DAOs, entity classes
│   ├── preferences/     # DataStore wrapper
│   ├── repository/      # TransactionRepository, SummaryRepository
│   └── sync/            # DriveSync, DriveApiService (Retrofit)
├── di/                  # Hilt modules (Database, Network)
├── notification/        # NotificationHelper
├── receiver/            # BankNotificationListener, NotificationActionReceiver
├── ui/
│   ├── screens/         # Summary, Transactions, Unclassified, ManualAdd, Settings, Discard
│   ├── components/      # TransactionRow, TagBottomSheet, EditTransactionDialog, MonthPicker
│   ├── viewmodel/       # One ViewModel per screen
│   ├── navigation/      # AppNavigation
│   └── theme/           # Color, Type, Theme
├── util/                # SMSParser, DateUtils, ConnectivityObserver
└── widget/              # Glance AppWidget + action callbacks
```

---

## Database

Four Room tables, all stored locally on-device:

| Table | Purpose |
| --- | --- |
| `transactions` | Your own expense records |
| `monthly_summary` | Aggregated monthly totals (recalculated on every write) |
| `partner_transactions` | Your partner's records downloaded from Drive |
| `partner_summary` | Your partner's monthly totals downloaded from Drive |

---

## Sync

Sync is **manual only** — it never runs automatically in the background.

- Trigger it from the **Combined** tab (↺ button, bottom-left) or from **Settings → Sync Now**
- Each sync uploads two JSON files (`{username}_transactions.json`, `{username}_summary.json`) to the shared Drive folder and downloads two from your partner
- Only the last **6 months** of transactions are uploaded, keeping file sizes small
- Repeat syncs are incremental: only partner records newer than the last known file timestamp are downloaded

---

## SMS detection

A transaction is created only when an incoming message passes all three filters:

1. **Not an OTP** — messages containing OTP/verification keywords are discarded
2. **Has a transaction keyword** — must include at least one of: `sent`, `spent`, `paid`, `debited`, `used`, `charged`, `payment`, `transfer`, `transferred`, `withdrawn`, `withdrawal`, `ATM withdrawal`, `purchase`, `deducted`, `mandate`, `autopay`, `auto-debit`
3. **Has a parseable amount** — must contain a ₹ / Rs. / INR value

Messages that don't pass all three are silently ignored.

---

## Troubleshooting

| Problem | Fix |
| --- | --- |
| Gradle sync fails | Open in Android Studio; let it manage the JDK automatically |
| `./gradlew` produces no output on macOS | Use the `java -classpath gradle/wrapper/gradle-wrapper.jar ...` command above |
| `google-services.json` error | Replace the placeholder file with your real download from Cloud Console |
| Sign-in fails | Check that the SHA-1 fingerprint in Cloud Console matches your debug keystore |
| Drive sync fails | Verify the folder has Editor access for both accounts and the folder ID is correct |
| SMS not auto-detected | Grant SMS permission manually in Android Settings → Apps → Expense Tracker → Permissions |
| Legitimate bank SMS ignored | The message may use an unusual phrase not in the keyword list; add it via Settings → Custom Keywords |

---

## Documentation

For in-depth technical details — architecture decisions, database schema, SMS parsing pipeline, sync protocol, and full test coverage — see [DOCUMENTATION.md](DOCUMENTATION.md).

For step-by-step Google Cloud setup — see [SETUP.md](SETUP.md).

---

## License

Private project — not licensed for redistribution.
