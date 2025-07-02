package utils

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionManager(private val context: Context) {

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.POST_NOTIFICATIONS
        )

        private val SPECIAL_PERMISSIONS = arrayOf(
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.PACKAGE_USAGE_STATS",
            "android.permission.BIND_ACCESSIBILITY_SERVICE"
        )
    }

    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var specialPermissionCallback: (() -> Unit)? = null

    fun initialize(activity: AppCompatActivity, onPermissionsResult: (Boolean) -> Unit) {
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            onPermissionsResult(allGranted)
        }
    }

    fun checkAndRequestPermissions(activity: AppCompatActivity, onComplete: (Boolean) -> Unit) {
        // Check regular permissions
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        when {
            missingPermissions.isEmpty() -> {
                // Check special permissions
                checkSpecialPermissions(activity) { specialGranted ->
                    onComplete(specialGranted)
                }
            }
            else -> {
                permissionLauncher?.launch(missingPermissions)
            }
        }
    }

    private fun checkSpecialPermissions(activity: AppCompatActivity, onComplete: (Boolean) -> Unit) {
        specialPermissionCallback = onComplete

        // Check overlay permission
        if (!Settings.canDrawOverlays(context)) {
            requestOverlayPermission(activity)
            return
        }

        // Check usage stats permission
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission(activity)
            return
        }

        // Check accessibility service permission
        if (!isAccessibilityServiceEnabled()) {
            requestAccessibilityPermission(activity)
            return
        }

        onComplete(true)
    }

    private fun requestOverlayPermission(activity: AppCompatActivity) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        activity.startActivityForResult(intent, OVERLAY_PERMISSION_CODE)
    }

    private fun requestUsageStatsPermission(activity: AppCompatActivity) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        activity.startActivityForResult(intent, USAGE_STATS_PERMISSION_CODE)
    }

    private fun requestAccessibilityPermission(activity: AppCompatActivity) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        activity.startActivityForResult(intent, ACCESSIBILITY_PERMISSION_CODE)
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )

        if (accessibilityEnabled == 1) {
            val services = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            services?.let {
                return it.contains(context.packageName)
            }
        }
        return false
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int) {
        when (requestCode) {
            OVERLAY_PERMISSION_CODE -> {
                if (Settings.canDrawOverlays(context)) {
                    checkSpecialPermissions(context as AppCompatActivity, specialPermissionCallback ?: { })
                } else {
                    specialPermissionCallback?.invoke(false)
                }
            }
            USAGE_STATS_PERMISSION_CODE -> {
                if (hasUsageStatsPermission()) {
                    checkSpecialPermissions(context as AppCompatActivity, specialPermissionCallback ?: { })
                } else {
                    specialPermissionCallback?.invoke(false)
                }
            }
            ACCESSIBILITY_PERMISSION_CODE -> {
                if (isAccessibilityServiceEnabled()) {
                    specialPermissionCallback?.invoke(true)
                } else {
                    specialPermissionCallback?.invoke(false)
                }
            }
        }
    }

    fun hasAllPermissions(): Boolean {
        val regularPermissionsGranted = REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        val specialPermissionsGranted = Settings.canDrawOverlays(context) &&
                hasUsageStatsPermission() &&
                isAccessibilityServiceEnabled()

        return regularPermissionsGranted && specialPermissionsGranted
    }

    companion object {
        private const val OVERLAY_PERMISSION_CODE = 1001
        private const val USAGE_STATS_PERMISSION_CODE = 1002
        private const val ACCESSIBILITY_PERMISSION_CODE = 1003
    }
}