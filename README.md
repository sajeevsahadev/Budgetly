# 💰 Budgetly — Android Expense Tracker

> **Automatic. Private. On-Device.**  
> Reads bank SMS to track expenses automatically, no cloud sync ever.

---

## 📱 Features

| Feature | Details |
|---|---|
| 🤖 **Auto SMS Tracking** | Parses bank/UPI SMS in real-time, extracts amount, merchant, bank |
| 📊 **Smart Categorization** | 17 categories auto-detected from 200+ merchant keywords |
| ✏️ **Category Override** | Change one → changes all expenses from that vendor |
| 💵 **Cash Expenses** | Manual entry for cash transactions |
| 📅 **Bill Reminders** | Recurring bills with exact alarm notifications |
| 📱 **Subscription Tracker** | Track all subscriptions, monthly/yearly cost rollup |
| 🎯 **Budget Manager** | Per-category budgets with 90% threshold alerts |
| 📈 **Visual Insights** | Pie chart (categories), Bar chart (budget vs spent), Line chart (trend) |
| 🔒 **Biometric Lock** | Optional fingerprint/face lock |
| 📤 **CSV Export** | Full export of all transactions |
| 🔄 **Re-scan SMS** | Re-import historical SMS anytime |

---

## 🏗️ Architecture

```
Budgetly
├── data/
│   ├── models/          Room entities + enums + data classes
│   ├── db/              AppDatabase + DAOs (5 entities)
│   └── repository/      Single source of truth
├── service/
│   ├── SmsParserService Regex engine for 8+ banks, UPI, wallets
│   └── Workers          WorkManager (BudgetCheck, SmsImport)
├── receiver/
│   ├── SmsReceiver      Broadcast receiver for incoming SMS
│   ├── BillReminderReceiver  Alarm-based notifications
│   └── BootReceiver     Restores alarms after reboot
├── ui/
│   ├── dashboard/       Home summary screen
│   ├── expenses/        List, search, filter, detail, add
│   ├── bills/           Bill reminders + mark paid
│   ├── subscriptions/   Sub tracker + quick-add popular subs
│   ├── budget/          Budget vs spent with progress bars
│   ├── graphs/          MPAndroidChart pie/bar/line charts
│   ├── settings/        Lock, export, re-scan, delete
│   └── more/            Navigation hub
└── utils/               Currency, date, prefs helpers
```

**Tech stack:** Kotlin · Room · Hilt · Kotlin Flow · Navigation Component · MPAndroidChart · WorkManager · BiometricPrompt · Material 3

---

## 🚀 Setup & Build

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 34
- JDK 17
- Gradle 8.0

### 1. Clone / open the project
```bash
# Open in Android Studio: File → Open → ExpenseTracker/
```

### 2. Add fonts (optional but recommended)
Download **Inter** from https://fonts.google.com/specimen/Inter  
Place these in `app/src/main/res/font/`:
- `inter_regular.ttf`
- `inter_medium.ttf`  
- `inter_bold.ttf`

Or remove `android:fontFamily="@font/inter"` from themes.xml to use the system font.

### 3. Sync & Build
```bash
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

### 4. Release Build (for Play Store)
```bash
# Create keystore first (once):
keytool -genkey -v -keystore budgetly.jks \
  -alias budgetly -keyalg RSA -keysize 2048 -validity 10000

# Add to app/build.gradle signingConfigs block, then:
./gradlew assembleRelease
# AAB for Play Store:
./gradlew bundleRelease
```

---

## 🏦 Supported Banks & Payment Methods

| Bank / Service | SMS Detected |
|---|---|
| HDFC Bank | ✅ Debit/Credit/UPI |
| ICICI Bank | ✅ Debit/Credit/UPI |
| SBI | ✅ Debit/Credit |
| Axis Bank | ✅ Debit/Credit/UPI |
| Kotak Mahindra | ✅ Debit/Credit |
| IDFC First | ✅ |
| Yes Bank | ✅ |
| IndusInd | ✅ |
| Amazon Pay | ✅ |
| PhonePe | ✅ |
| Google Pay | ✅ |
| Paytm | ✅ |
| CRED | ✅ |

**Payment modes auto-detected:** UPI · Credit Card · Debit Card · Net Banking (NEFT/RTGS/IMPS) · Wallet · EMI

---

## 📂 Auto-Categories (200+ merchant keywords)

| Category | Examples |
|---|---|
| 🍔 Food & Dining | Zomato, Swiggy, Dominos, McDonald's, KFC, Starbucks |
| 🛒 Groceries | BigBasket, Blinkit, D-Mart, Reliance Fresh, Zepto |
| 🛍️ Shopping | Amazon, Flipkart, Myntra, Ajio, Nykaa, Croma |
| 🎬 Entertainment | Netflix, Spotify, Hotstar, PVR, BookMyShow |
| 🚗 Transport | Uber, Ola, Rapido, IRCTC, IndiGo, FastTag |
| 💊 Health | PharmEasy, 1mg, Apollo, Cult.fit, Practo |
| 💡 Bills | Jio, Airtel, BESCOM, Electricity, Broadband |
| ⛽ Fuel | HP, BPCL, IOCL, Petrol, Diesel, CNG |
| 📚 Education | Byju's, Unacademy, Vedantu, Coursera |
| 📈 Investments | Zerodha, Groww, Upstox, Mutual Fund, SIP |
| 🛡️ Insurance | LIC, HDFC Life, Star Health, Premium |
| 🏦 EMI/Loan | EMI, Loan, Bajaj Finance |
| 📱 Subscriptions | Netflix, Prime, Hotstar, Spotify |

---

## 🔒 Privacy & Data Security

- ✅ **All data stored on-device** — Room SQLite database, never uploaded
- ✅ **Cloud backup disabled** — `allowBackup="false"`, `data_extraction_rules.xml` excludes all domains
- ✅ **No internet access for data** — `network_security_config.xml` blocks cleartext; no data endpoints
- ✅ **Biometric app lock** — Optional fingerprint/face unlock
- ✅ **No analytics, no ads, no tracking**
- ✅ **Encrypted SharedPreferences** for sensitive settings
- ✅ **ProGuard enabled** in release builds

---

## 🏪 Play Store Submission Checklist

### Permissions declared with rationale:
- `READ_SMS` / `RECEIVE_SMS` — Core feature: auto-tracking bank transactions
- `POST_NOTIFICATIONS` — Bill reminders and budget alerts
- `SCHEDULE_EXACT_ALARM` — Precise bill reminder timing
- `RECEIVE_BOOT_COMPLETED` — Restore reminders after reboot
- `USE_BIOMETRIC` — Optional app lock

### Required before submission:
- [ ] Generate release keystore and sign APK/AAB
- [ ] Add actual Inter font `.ttf` files to `res/font/`  
- [ ] Create proper launcher PNG icons for all densities (use Android Studio's Image Asset tool)
- [ ] Write Play Store listing (title, description, screenshots, feature graphic)
- [ ] Add Privacy Policy URL (required for SMS permission)
- [ ] Complete Data Safety section in Play Console — declare:
  - No data collected or shared externally
  - SMS data processed on-device only
- [ ] Test on Android 7.0 (API 24) through Android 14 (API 34)
- [ ] Test SMS parsing with your bank's actual message format

### SMS Permission Declaration:
Google requires a **Privacy Policy** and a **Declaration Form** for apps using READ_SMS.  
In Play Console → App Content → Sensitive Permissions → SMS:
- Explain: "Used to automatically detect bank transactions for expense tracking. SMS data is processed locally and never uploaded."

---

## 🔧 Customization

### Add a new bank SMS pattern:
In `AppDatabase.kt` → `DatabaseCallback.seedSmsParserRules()`, add:
```kotlin
Triple("Your Bank", "SENDERCODE|ALTERNATE", "debited|paid|spent")
```

### Add new merchant category mapping:
In `SmsParserService.kt` → `CATEGORY_KEYWORDS`, add to any category's list:
```kotlin
ExpenseCategory.FOOD_DINING to listOf(..., "your_merchant_name")
```

### Add a new expense category:
In `Models.kt` → `ExpenseCategory` enum, add:
```kotlin
MY_CATEGORY("My Category", "🎯", "#FF6B6B")
```

---

## 📁 Project Stats

| Metric | Count |
|---|---|
| Kotlin files | 25 |
| XML layout/resource files | 74 |
| Lines of Kotlin | ~4,600 |
| Lines of XML | ~3,200 |
| Room entities | 6 |
| Navigation destinations | 9 |
| Supported banks | 13+ |
| Auto-category keywords | 200+ |

---

## 🤝 Dependencies

| Library | Version | Purpose |
|---|---|---|
| Room | 2.6.1 | Local SQLite ORM |
| Hilt | 2.48 | Dependency injection |
| Navigation Component | 2.7.5 | Fragment navigation |
| MPAndroidChart | 3.1.0 | Pie, bar, line charts |
| WorkManager | 2.9.0 | Background budget checks |
| Biometric | 1.2.0 | App lock |
| Material 3 | 1.11.0 | UI components |
| Security Crypto | 1.1.0 | Encrypted preferences |
| Kotlin Coroutines | 1.7.3 | Async/Flow |
| Lottie | 6.1.0 | Animations |

---

*Built with ❤️ for financial privacy. Your money, your data, your device.*
