package com.budgetly.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.budgetly.R
import com.budgetly.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            handlePermissionsResult(permissions)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        requestRequiredPermissions()
        handleDeepLinkIntent(intent)
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        // Show/hide bottom nav based on destination
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val topLevelDestinations = setOf(
                R.id.dashboardFragment,
                R.id.expensesFragment,
                R.id.graphsFragment,
                R.id.billsFragment,
                R.id.moreFragment
            )
            binding.bottomNavigation.visibility =
                if (destination.id in topLevelDestinations) View.VISIBLE else View.GONE
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.RECEIVE_SMS)
            permissionsNeeded.add(Manifest.permission.READ_SMS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            showPermissionsRationale(permissionsNeeded)
        }

        // Schedule exact alarm permission (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                // Don't block the user, just note it
            }
        }
    }

    private fun showPermissionsRationale(permissions: List<String>) {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage(
                "Budgetly needs the following permissions to work:\n\n" +
                "• SMS: To automatically track bank transactions\n" +
                "• Notifications: For bill reminders and budget alerts\n\n" +
                "All data stays on your device — we never upload your financial data."
            )
            .setPositiveButton("Grant") { _, _ ->
                requestPermissionsLauncher.launch(permissions.toTypedArray())
            }
            .setNegativeButton("Later") { _, _ -> }
            .setCancelable(false)
            .show()
    }

    private fun handlePermissionsResult(permissions: Map<String, Boolean>) {
        val smsGranted = permissions[Manifest.permission.RECEIVE_SMS] == true
        if (!smsGranted) {
            // Show a gentle nudge
            AlertDialog.Builder(this)
                .setTitle("SMS Permission Denied")
                .setMessage("Without SMS permission, expenses won't be tracked automatically from bank messages. You can still add expenses manually.\n\nTo enable later, go to App Settings > Permissions.")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    })
                }
                .setNegativeButton("Continue Manually") { _, _ -> }
                .show()
        }
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        intent?.getStringExtra("open_tab")?.let { tab ->
            when (tab) {
                "bills" -> binding.bottomNavigation.selectedItemId = R.id.billsFragment
                "budget" -> navController.navigate(R.id.budgetFragment)
                "expenses" -> binding.bottomNavigation.selectedItemId = R.id.expensesFragment
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleDeepLinkIntent(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
