# Budgetly — Quick Start Guide

## You have Android Studio installed — here's exactly what to do:

### Step 1: Open the project
- Android Studio → **File → Open**
- Navigate to this `Budgetly` folder → Click **OK**

### Step 2: Fix JVM (if prompted)
- Click **"Use JVM 17"** when the dialog appears

### Step 3: Wait for Gradle sync
- Progress bar at the bottom — takes 2–5 min on first run
- ✅ JitPack is already configured — no extra steps needed

### Step 4: Fix local.properties  
Open `local.properties` in the project and update the SDK path:
```
sdk.dir=C\:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk
```
Replace `YOUR_USERNAME` with your actual Windows username (e.g. `sajeev.sahadevan`).

### Step 5: Run the app
- Connect your Android phone via USB **or** use the Pixel 7 emulator you set up
- Press the green **▶ Run** button

### If you see build errors:
- **"Unresolved reference: BudgetlyFragmentDirections"** → Normal before first build. Press ▶ Run anyway.
- **"SDK location not found"** → Update `local.properties` with your SDK path (Step 4)
- **Any other error** → Share a screenshot and we'll fix it immediately

---
App name: **Budgetly**  
Package: `com.budgetly.app`  
Min Android: 7.0 (API 24)  
Target Android: 14 (API 34)
