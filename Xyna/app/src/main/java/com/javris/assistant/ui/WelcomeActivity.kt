package com.javris.assistant.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.javris.assistant.MainActivity
import com.javris.assistant.R
import com.javris.assistant.databinding.ActivityWelcomeBinding
import com.javris.assistant.util.PermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private lateinit var permissionManager: PermissionManager
    private var permissionDialog: AlertDialog? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionManager = PermissionManager(this)

        setupUI()
        checkPermissionsAndProceed()
    }

    private fun setupUI() {
        // Initialize UI components with memory-efficient settings
        binding.apply {
            // Disable hardware acceleration for complex animations if needed
            welcomeAnimation.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            
            // Set click listeners
            getStartedButton.setOnClickListener {
                checkPermissionsAndProceed()
            }

            // Load heavy resources asynchronously
            lifecycleScope.launch(Dispatchers.IO) {
                // Perform any heavy initialization here
                withContext(Dispatchers.Main) {
                    // Update UI after initialization
                    loadingIndicator.visibility = View.GONE
                    contentLayout.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun checkPermissionsAndProceed() {
        when {
            permissionManager.checkPermissions() -> {
                checkAccessibilityService()
            }
            permissionManager.shouldShowPermissionRationale(this) -> {
                showPermissionRationaleDialog()
            }
            else -> {
                permissionManager.requestPermissions(this, PERMISSION_REQUEST_CODE)
            }
        }
    }

    private fun showPermissionRationaleDialog() {
        permissionDialog?.dismiss()
        permissionDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_required_title)
            .setMessage(R.string.permission_required_message)
            .setPositiveButton(R.string.grant_permissions) { _, _ ->
                permissionManager.requestPermissions(this, PERMISSION_REQUEST_CODE)
            }
            .setNegativeButton(R.string.exit_app) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun checkAccessibilityService() {
        if (!permissionManager.isAccessibilityServiceEnabled()) {
            showAccessibilityDialog()
        } else {
            proceedToMainActivity()
        }
    }

    private fun showAccessibilityDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.accessibility_required_title)
            .setMessage(R.string.accessibility_required_message)
            .setPositiveButton(R.string.enable_accessibility) { _, _ ->
                permissionManager.openAccessibilitySettings()
            }
            .setNegativeButton(R.string.exit_app) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun proceedToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (permissionManager.checkPermissions()) {
                checkAccessibilityService()
            } else {
                showPermissionSettingsSnackbar()
            }
        }
    }

    private fun showPermissionSettingsSnackbar() {
        Snackbar.make(
            binding.root,
            R.string.permission_required_settings,
            Snackbar.LENGTH_INDEFINITE
        ).setAction(R.string.settings) {
            permissionManager.openAppSettings()
        }.show()
    }

    override fun onResume() {
        super.onResume()
        if (permissionManager.checkPermissions() && permissionManager.isAccessibilityServiceEnabled()) {
            proceedToMainActivity()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        permissionDialog?.dismiss()
        permissionDialog = null
    }
} 