package dev.scrolllock.dpc

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.telecom.TelecomManager

class AppCatalog(private val context: Context) {
    data class AppEntry(
        val label: String,
        val packageName: String,
        val icon: Drawable?,
        val isSystem: Boolean,
        val protectedReason: String? = null
    )

    private val pm = context.packageManager

    fun load(showSystemApps: Boolean): List<AppEntry> {
        val locked = SettingsStore.getLockedPackages(context)
        val protected = protectedPackages()

        val installed = pm.getInstalledApplications(0)
            .asSequence()
            .filter { it.packageName != context.packageName }
            .filter { info ->
                showSystemApps || (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            }
            .map { info ->
                val label = runCatching { pm.getApplicationLabel(info).toString() }
                    .getOrDefault(info.packageName)
                val icon = runCatching { pm.getApplicationIcon(info) }.getOrNull()
                AppEntry(
                    label = label,
                    packageName = info.packageName,
                    icon = icon,
                    isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    protectedReason = protected[info.packageName]
                )
            }
            .toMutableList()

        val knownPackages = installed.mapTo(mutableSetOf()) { it.packageName }

        // Hidden packages may not be returned consistently by every OEM PackageManager implementation.
        // Keep them visible in ScrollLock using the persisted package list.
        locked.filterNot { it in knownPackages }.forEach { packageName ->
            val info = runCatching {
                pm.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
            }.getOrNull()
            val label = info?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrNull() }
                ?: packageName
            val icon = info?.let { runCatching { pm.getApplicationIcon(it) }.getOrNull() }
            installed += AppEntry(
                label = label,
                packageName = packageName,
                icon = icon,
                isSystem = info?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: false,
                protectedReason = protected[packageName]
            )
        }

        return installed.sortedWith(
            compareByDescending<AppEntry> { it.packageName in locked }
                .thenBy { it.label.lowercase() }
        )
    }

    private fun protectedPackages(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        result[context.packageName] = "ScrollLock itself"
        result["com.android.settings"] = "Android Settings"
        result["com.android.systemui"] = "Android System UI"
        result["com.android.permissioncontroller"] = "Permission Controller"
        result["com.google.android.permissioncontroller"] = "Permission Controller"
        result["com.android.packageinstaller"] = "Package Installer"
        result["com.google.android.packageinstaller"] = "Package Installer"

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.queryIntentActivities(homeIntent, 0).forEach {
            result[it.activityInfo.packageName] = "Launcher / Home app"
        }

        val telecom = context.getSystemService(TelecomManager::class.java)
        telecom?.defaultDialerPackage?.let {
            result[it] = "Default phone app"
        }

        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        dpm.activeAdmins.orEmpty().map { it.packageName }.forEach {
            result[it] = "Active device administrator"
        }

        return result
    }
}
