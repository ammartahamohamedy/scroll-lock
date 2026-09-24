package dev.scrolllock.dpc

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class ScrollLockAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "ScrollLock administrator enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "ScrollLock administrator disabled", Toast.LENGTH_SHORT).show()
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        PolicyController(context.applicationContext).reapplyAll()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "ScrollLock is protecting locked apps. A Device Owner cannot normally be deactivated from Settings."
}
