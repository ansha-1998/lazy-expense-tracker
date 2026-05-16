# Setup Guide — Couple Expense Tracker

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK with API 26–34 installed
- A Google account

---

## Step 1: Open the Project in Android Studio

1. Open Android Studio
2. Select **Open** → navigate to this folder (`couple_expense_tracking_apk`)
3. Let Gradle sync finish (first run downloads ~500 MB of dependencies)

---

## Step 2: Create a Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Click **New Project** → name it (e.g. "Couple Expense Tracker")
3. Note your **Project Number** and **Project ID**

---

## Step 3: Enable Google Drive API

1. In Cloud Console → **APIs & Services** → **Library**
2. Search for **Google Drive API** → click **Enable**

---

## Step 4: Create OAuth 2.0 Credentials

1. In Cloud Console → **APIs & Services** → **Credentials**
2. Click **+ Create Credentials** → **OAuth client ID**
3. Select **Android** as application type
   - Package name: `com.couple.expensetracker`
   - SHA-1 certificate fingerprint: run this in terminal:

     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore \
             -alias androiddebugkey -storepass android -keypass android
     ```

   - Copy the SHA-1 value
4. Click **Create** → note the **Client ID** (looks like `123456...apps.googleusercontent.com`)
5. Create a second credential — select **Web application**
   - Note the **Web Client ID** — this is used for `requestServerAuthCode`

---

## Step 5: Download google-services.json

1. In Cloud Console → go to your project
2. Click the **gear icon** → **Project Settings**
3. Under **Your apps** → click **Android** → **Add app** if not listed
4. Package name: `com.couple.expensetracker`
5. Download `google-services.json`
6. **Replace** `app/google-services.json` with the downloaded file

---

## Step 6: Add google-services Plugin

1. Add to `app/build.gradle.kts` plugins block:

   ```kotlin
   id("com.google.gms.google-services")
   ```

2. Add to `build.gradle.kts` (project-level) plugins block:

   ```kotlin
   id("com.google.gms.google-services") version "4.4.1" apply false
   ```

3. Add to `gradle/libs.versions.toml`:

   ```toml
   [versions]
   googleServices = "4.4.1"

   [plugins]
   google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
   ```

---

## Step 7: Update the OAuth Client ID in strings.xml

Open `app/src/main/res/values/strings.xml` and replace:

```xml
<string name="google_oauth_client_id">YOUR_WEB_CLIENT_ID.apps.googleusercontent.com</string>
```

with your actual **Web Client ID** from Step 4.

---

## Step 8: Create Shared Drive Folder

1. Go to [Google Drive](https://drive.google.com/)
2. Create a new folder called **ExpenseSync** (or any name)
3. Share it with both your Google account and your partner's Google account with **Editor** access
4. Right-click the folder → **Share** → copy the folder link
5. Extract the Folder ID from the URL:
   - URL: `https://drive.google.com/drive/folders/1AbcDeFgHiJklMnOpQrStUvWxYz`
   - Folder ID: `1AbcDeFgHiJklMnOpQrStUvWxYz`
6. Paste this ID (or the full URL) into the app's **Settings → Shared Drive Folder ID** field

---

## Step 9: Build and Install

### Option A — Android Studio (recommended)

Click the **Run** button or press `Shift+F10`. Android Studio handles the build and installs directly to a connected device.

### Option B — Terminal (macOS)

The `./gradlew` wrapper script has a compatibility issue on macOS. Use the Gradle jar directly instead:

```bash
# Set Android Studio's bundled JDK
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# Build and install on all connected devices
java -classpath "gradle/wrapper/gradle-wrapper.jar" \
     org.gradle.wrapper.GradleWrapperMain installDebug

# Build APK only (output: app/build/outputs/apk/debug/app-debug.apk)
java -classpath "gradle/wrapper/gradle-wrapper.jar" \
     org.gradle.wrapper.GradleWrapperMain assembleDebug

# Install on a specific device (when multiple are connected)
adb -s <device-serial> install -r app/build/outputs/apk/debug/app-debug.apk

# List connected devices and their serials
adb devices -l
```

---

## Step 10: First Launch

1. Grant **SMS** and **Notifications** permissions when prompted
2. Go to **Settings** tab (gear icon in top bar)
3. Sign in with Google
4. Enter your username and your partner's username
5. Paste the Drive Folder ID (or full share URL)
6. Tap **Save** for each field
7. Go to the **Combined** tab → tap the **↺ sync button** in the bottom-left to verify the Drive connection works

---

## How SMS Detection Works

The app listens for incoming SMS messages and auto-creates a transaction only when the message passes all three filters:

1. **OTP filter** — messages containing OTP/verification keywords are discarded
2. **Transaction keyword filter** — the message must contain at least one of:
   `sent`, `spent`, `paid`, `debited`, `used`, `charged`, `payment`, `transfer`, `transferred`,
   `withdrawn`, `withdrawal`, `ATM withdrawal`, `purchase`, `deducted`, `mandate`, `autopay`, `auto-debit`
3. **Amount filter** — a currency amount (₹ / Rs. / INR) must be extractable from the message

Messages that don't pass all three are silently ignored — they never appear in the app.

---

## Transaction Display

Each transaction card shows:

- **Amount** (bold)
- **Bank · Payment type · Last 4 / UPI ref**
- **Date and time** (e.g. `09 May 2026, 02:30 PM`)
- **Tag chip** — Personal / Combined / Other / Unclassified

---

## Syncing with your Partner

Sync is **manual only** — it never runs automatically.

- Go to the **Combined** tab
- Tap the **↺ button** in the **bottom-left corner**
- The button spins while syncing and is greyed out when offline
- Alternatively, go to **Settings → Sync Now**

Sync uploads your transactions to the shared Drive folder and downloads your partner's.

---

## Granting SMS Permission (Required for Auto-Detection)

On Android 10+, `READ_SMS` and `RECEIVE_SMS` must be manually granted:

1. Go to **Android Settings** → **Apps** → **Expense Tracker**
2. Tap **Permissions** → **SMS** → **Allow**

The app will prompt you to do this on first launch.

---

## Troubleshooting

| Problem | Solution |
| --- | --- |
| Gradle sync fails | Open in Android Studio and let it manage the JDK automatically |
| `./gradlew` produces no output on macOS | Use the `java -classpath gradle/wrapper/gradle-wrapper.jar ...` command from Step 9 |
| google-services.json error | Replace the placeholder file with your real download from Cloud Console |
| Sign-in fails | Check that the SHA-1 fingerprint matches the one in Cloud Console |
| Drive sync fails | Verify the folder is shared with Editor access for both accounts and the Folder ID is correct |
| SMS not detected | Grant SMS permission manually in Android Settings; check that the message contains a transaction keyword and a ₹ amount |
| Legitimate bank SMS ignored | The bank message may use an unusual phrase — check that it contains one of the keywords listed in the SMS Detection section above |
| Multiple devices connected | Use `adb devices -l` to get the serial, then `adb -s <serial> install -r ...` |
