# Budgetly — Privacy Policy

**Last updated:** January 2024  
**App:** Budgetly — Automatic Expense Tracker  
**Developer:** [Your Name / Company]

---

## 1. Introduction

Budgetly ("the App") is an expense tracking application for Android. This Privacy Policy explains what information the App accesses, how it is used, and how it is protected.

**Our core principle: Your financial data never leaves your device.**

---

## 2. Information We Access

### 2.1 SMS Messages
- **What we read:** Bank transaction SMS messages from your inbox
- **Why:** To automatically detect and categorise your expenses from bank alerts
- **How it's stored:** Locally in an encrypted SQLite database on your device only
- **What we do NOT do:** Upload, transmit, share, or analyse your SMS content on any server

### 2.2 Notifications
- **What we use:** The POST_NOTIFICATIONS permission
- **Why:** To send bill reminders and budget threshold alerts
- **These notifications are generated locally** — no server communication involved

### 2.3 Device Storage
- **What we store:** Expense records, bill reminders, subscription data, and budget settings
- **Where:** On your device's internal storage only, in a Room SQLite database
- **Encryption:** App preferences are stored using Android's EncryptedSharedPreferences

---

## 3. Information We Do NOT Collect

We do not collect, store, transmit, or share:
- Your personal identity or contact information
- Your bank account numbers or financial credentials
- Your SMS message content on any external server
- Your location data
- Device identifiers for advertising purposes
- Any analytics or crash reports (unless you explicitly opt in via a future update)

---

## 4. Third-Party Services

Budgetly does **not** integrate with any third-party analytics, advertising, or data collection services.

All libraries used (Room, MPAndroidChart, WorkManager, etc.) operate entirely on-device and do not transmit data externally.

---

## 5. Data Storage & Security

- All expense data is stored in a local SQLite database managed by Android's Room library
- The database file is stored in the app's private internal storage, inaccessible to other apps
- Cloud backup is disabled (`android:allowBackup="false"`) — your data does not sync to Google Drive or any cloud service
- The app supports optional biometric authentication (fingerprint / face) to prevent unauthorised access

---

## 6. Data Deletion

You can delete all app data at any time:
- **In-app:** Settings → Delete All Data
- **System level:** Android Settings → Apps → Budgetly → Clear Data

Uninstalling the app removes all locally stored data permanently.

---

## 7. Permissions Explained

| Permission | Reason |
|---|---|
| `READ_SMS` / `RECEIVE_SMS` | Read and listen for bank transaction SMS to auto-track expenses |
| `POST_NOTIFICATIONS` | Bill reminders and budget alerts |
| `SCHEDULE_EXACT_ALARM` | Precise bill reminder timing |
| `RECEIVE_BOOT_COMPLETED` | Restore reminders after device reboot |
| `USE_BIOMETRIC` | Optional app lock feature |
| `VIBRATE` | Vibration for notifications |

---

## 8. Children's Privacy

Budgetly is not directed at children under the age of 13. We do not knowingly collect any information from children.

---

## 9. Changes to This Policy

We may update this Privacy Policy. Changes will be reflected in the "Last updated" date above. Continued use of the App after changes constitutes acceptance.

---

## 10. Contact

For privacy questions or data requests:  
📧 **[your-email@example.com]**  
🌐 **[your-website.com/privacy]**

---

*Budgetly is committed to your financial privacy. Your data belongs to you.*
