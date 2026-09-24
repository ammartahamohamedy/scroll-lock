package dev.scrolllock.dpc

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * Android 12+ admin-integrated provisioning entry point.
 * ScrollLock only supports fully managed (Device Owner) provisioning.
 */
class GetProvisioningModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val allowedModes = intent.getIntegerArrayListExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES
            )

            if (allowedModes != null &&
                DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE !in allowedModes
            ) {
                setResult(RESULT_CANCELED)
                finish()
                return
            }
        }

        val result = Intent().apply {
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
            )
            // This is a personal everyday phone, not a kiosk. Preserve OEM/system apps.
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED, true)
        }
        setResult(RESULT_OK, result)
        finish()
    }
}
