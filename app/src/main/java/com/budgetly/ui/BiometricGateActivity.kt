package com.budgetly.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.budgetly.service.BudgetCheckWorker
import com.budgetly.utils.AppPrefs
import dagger.hilt.android.AndroidEntryPoint

/**
 * Real launcher entry point:
 *   First run  → SplashActivity (historical SMS import) → MainActivity
 *   App lock   → BiometricPrompt → MainActivity
 *   Otherwise  → MainActivity directly
 */
@AndroidEntryPoint
class BiometricGateActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kick off background budget checker
        BudgetCheckWorker.schedule(this)

        // First run → go through Splash for SMS import
        if (AppPrefs.isFirstRun(this)) {
            AppPrefs.setFirstRunDone(this)
            startActivity(Intent(this, SplashActivity::class.java))
            finish()
            return
        }

        // Biometric lock check
        val lockEnabled = AppPrefs.isAppLockEnabled(this)
        if (!lockEnabled) { launchMain(); return }

        val canAuth = BiometricManager.from(this)
            .canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) { launchMain(); return }

        showBiometricPrompt()
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) =
                launchMain()
            override fun onAuthenticationError(code: Int, msg: CharSequence) = finishAffinity()
            override fun onAuthenticationFailed() = Unit   // retry handled by Android
        }
        BiometricPrompt(this, executor, callback).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Budgetly")
                .setSubtitle("Authenticate to access your finances")
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .build()
        )
    }

    private fun launchMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
