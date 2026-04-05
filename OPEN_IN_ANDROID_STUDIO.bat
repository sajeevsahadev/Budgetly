@echo off
echo ================================================
echo   Budgetly - Opening in Android Studio
echo ================================================
echo.
echo This batch file will help you open the project.
echo.
echo STEP 1: Make sure Android Studio is installed.
echo   Download from: https://developer.android.com/studio
echo.
echo STEP 2: When Android Studio opens, select:
echo   File ^> Open ^> navigate to THIS folder
echo.
echo STEP 3: When prompted "Select Gradle JVM":
echo   Click "Use JVM 17"
echo.
echo STEP 4: Wait for Gradle sync (2-5 minutes)
echo.
echo STEP 5: Press the green Play button to run!
echo.
echo ================================================
echo   SDK Path helper:
echo   Usually at: C:\Users\%USERNAME%\AppData\Local\Android\Sdk
echo ================================================
echo.

:: Try to open Android Studio if it's installed in default location
set AS_PATH="%LOCALAPPDATA%\Programs\Android Studio\bin\studio64.exe"
if exist %AS_PATH% (
    echo Starting Android Studio...
    start "" %AS_PATH% "%~dp0"
) else (
    echo Android Studio not found at default location.
    echo Please open Android Studio manually and use File > Open
    echo to open this folder: %~dp0
)
pause
