# Budgetly — Complete Setup Guide

## Prerequisites

| Tool | Version | Download |
|---|---|---|
| Android Studio | Hedgehog 2023.1.1+ | https://developer.android.com/studio |
| JDK | 17 | Bundled with Android Studio |
| Android SDK | API 34 | Via Android Studio SDK Manager |
| Gradle | 8.0 (auto-downloaded) | Automatic |

---

## Step 1: Open the Project

1. Extract `Budgetly_Android.zip`
2. Open Android Studio → **File → Open**
3. Navigate to the `ExpenseTracker/` folder → **OK**
4. Wait for Gradle sync to complete (downloads ~200 MB of dependencies)

---

## Step 2: Add the JitPack Repository

MPAndroidChart (the charting library) requires JitPack. Add it to `app/build.gradle`:

```groovy
// At the bottom of app/build.gradle, add:
repositories {
    maven { url 'https://jitpack.io' }
}
```

---

## Step 3: Add Inter Font (Recommended)

1. Download **Inter** font from https://fonts.google.com/specimen/Inter
2. Download these 3 weights: Regular (400), Medium (500), Bold (700)
3. Rename them:
   - `Inter-Regular.ttf` → `inter_regular.ttf`
   - `Inter-Medium.ttf`  → `inter_medium.ttf`
   - `Inter-Bold.ttf`    → `inter_bold.ttf`
4. Place all 3 files in: `app/src/main/res/font/`

**Skip fonts?** Remove `android:fontFamily="@font/inter"` from `res/values/themes.xml` to use the system font instead.

---

## Step 4: Generate Proper Launcher Icons

The included launcher icons are basic SVG vectors. For Play Store you need proper PNG icons:

1. In Android Studio: **File → New → Image Asset**
2. Icon Type: **Launcher Icons (Adaptive and Legacy)**
3. Foreground Layer: use a wallet/money emoji or your custom icon
4. Background Layer: set color to `#5C6BC0`
5. Click **Next → Finish**

This generates proper PNG icons for all density buckets automatically.

---

## Step 5: Build Debug APK

```bash
cd ExpenseTracker/
./gradlew assembleDebug

# APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

Install on device:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Step 6: Test SMS Parsing

To test the SMS auto-detection without a real bank SMS:

1. Connect a device via ADB
2. Send a test SMS:
```bash
adb shell am broadcast \
  -a android.provider.Telephony.SMS_RECEIVED \
  -n com.expensetracker.app.debug/.receiver.SmsReceiver \
  --es "pdu" "OPTIONAL_TEST"
```

Or use the Android Emulator's **Extended Controls → Phone → Send Message**:
```
Sender: HDFCBK
Message: Dear Customer, Rs.1500.00 debited from A/c XX5678 on 15-Jan-24. 
Info: UPI/Zomato India/Pay. Avl Bal:25,000.00. -HDFC Bank
```

---

## Step 7: Create Release Keystore (Play Store)

**Do this once and keep the keystore file safe — you can never change it!**

```bash
keytool -genkey -v \
  -keystore budgetly-release.jks \
  -alias budgetly \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000

# You'll be prompted for:
# - Keystore password (remember this!)
# - Key password
# - Your name, organisation, city, country
```

---

## Step 8: Configure Release Signing

Add to `app/build.gradle` inside the `android {}` block:

```groovy
android {
    signingConfigs {
        release {
            storeFile file('../budgetly-release.jks')
            storePassword System.getenv("KEYSTORE_PASSWORD") ?: "your_password"
            keyAlias "budgetly"
            keyPassword System.getenv("KEY_PASSWORD") ?: "your_key_password"
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

---

## Step 9: Build Release AAB (Play Store format)

```bash
./gradlew bundleRelease

# AAB location:
# app/build/outputs/bundle/release/app-release.aab
```

---

## Step 10: Play Store Submission

### In Google Play Console (https://play.google.com/console):

1. **Create app** → Android → Free → Developer Program Policies ✓

2. **Upload AAB** → Production → Create release → Upload `app-release.aab`

3. **App content → Privacy policy** 
   - Host your `PRIVACY_POLICY.md` content at a public URL (e.g. GitHub Pages)
   - Paste the URL in Play Console

4. **App content → Data safety**
   - Does your app collect or share user data? → **No**
   - SMS: accessed on-device only, never transmitted → Declare this
   - Provide data deletion method: Yes (Settings → Delete All Data)

5. **App content → Sensitive app permissions → SMS**
   Complete the SMS permission declaration form:
   - Core functionality: "Read bank transaction SMS to automatically detect and categorise expenses"
   - Is SMS the only way to implement this feature? **Yes**
   - The app does not share or upload SMS content

6. **Store listing**
   - Title: `Budgetly — Expense Tracker`
   - Short description: from `PLAY_STORE_LISTING.md`
   - Full description: from `PLAY_STORE_LISTING.md`
   - Screenshots: minimum 2 phone screenshots required
   - Feature graphic: 1024×500 px
   - Icon: 512×512 px (high-res)

7. **Pricing**: Free

8. **Countries**: Select target countries

9. **Submit for review** → typically 1–7 days

---

## Adding New Banks

To add support for a new bank's SMS format, edit `AppDatabase.kt`:

```kotlin
// In seedSmsParserRules(), add:
Triple(
    "New Bank Name",          // display name
    "SENDER1|SENDER2",        // regex for SMS sender ID
    "debited|paid|spent"      // keywords that indicate a debit
)
```

And add merchant keywords in `SmsParserService.kt`:

```kotlin
ExpenseCategory.FOOD_DINING to listOf(
    // existing items...
    "new_restaurant_name"
)
```

---

## Troubleshooting

| Problem | Solution |
|---|---|
| `Unresolved reference: DashboardFragmentDirections` | Build → Make Project (Safe Args generates these) |
| `Unresolved reference: inter_regular` | Add `.ttf` files to `res/font/` or remove font references from themes.xml |
| `MPAndroidChart class not found` | Add `maven { url 'https://jitpack.io' }` to repositories |
| SMS not being detected | Check `READ_SMS` permission is granted in device Settings |
| Biometric not working | Check device has fingerprint/PIN enrolled |
| `Room schema export` warning | Already configured — schemas go to `app/schemas/` |
| Build fails with Hilt errors | Ensure `kapt` is applied and all `@AndroidEntryPoint` activities are in manifest |

---

## Running Tests

```bash
# Unit tests (runs on JVM, fast)
./gradlew test

# Instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Specific test class
./gradlew test --tests "com.expensetracker.service.SmsParserServiceTest"
```

---

## Project Structure Quick Reference

```
app/src/main/java/com/expensetracker/
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt      ← Room DB, seeded SMS rules
│   │   └── Daos.kt             ← All DAO interfaces
│   ├── models/
│   │   └── Models.kt           ← Entities, enums, data classes
│   └── repository/
│       └── ExpenseRepository.kt ← All data operations
├── receiver/
│   ├── SmsReceiver.kt          ← Listens for incoming bank SMS
│   └── Receivers.kt            ← Boot, Bill alarm, Notifications
├── service/
│   ├── SmsParserService.kt     ← Regex SMS parsing engine
│   └── Workers.kt              ← WorkManager background jobs
├── ui/
│   ├── BiometricGateActivity.kt ← App entry point + lock
│   ├── SplashActivity.kt        ← First-run SMS import
│   ├── MainActivity.kt          ← Navigation host
│   ├── dashboard/               ← Home screen
│   ├── expenses/                ← Expense list + detail
│   ├── bills/                   ← Bill reminders
│   ├── subscriptions/           ← Subscription tracker
│   ├── budget/                  ← Budget manager
│   ├── graphs/                  ← Analytics charts
│   ├── more/                    ← Navigation hub
│   └── settings/                ← Settings + privacy
└── utils/
    └── Utils.kt                 ← Currency, date, prefs helpers
```
