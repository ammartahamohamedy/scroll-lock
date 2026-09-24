package dev.scrolllock.dpc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * If a user-locked package is installed or updated (Play Store or APK),
 * immediately re-apply hide/suspend/uninstall-block for that package only.
 * Also re-applies browser URL blocklists when a managed browser is installed/updated.
 */
class PackageChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_PACKAGE_ADDED && action != Intent.ACTION_PACKAGE_REPLACED) {
            return
        }

        val packageName = intent.data?.schemeSpecificPart ?: return
        if (packageName == context.packageName) return

        val appContext = context.applicationContext
        val policy = PolicyController(appContext)

        if (packageName in SettingsStore.getLockedPackages(appContext)) {
            policy.lockPackage(packageName)
        }

        if (packageName in BrowserUrlBlocklist.MANAGED_BROWSER_PACKAGES &&
            SettingsStore.getBlockedUrls(appContext).isNotEmpty()
        ) {
            policy.applyUrlBlocklist()
        }
    }
}
