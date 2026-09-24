package dev.scrolllock.dpc

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.UserManager

class PolicyController(private val context: Context) {
    private val dpm: DevicePolicyManager =
        context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, ScrollLockAdminReceiver::class.java)

    data class Result(val success: Boolean, val message: String)

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    fun lockPackage(packageName: String): Result {
        if (!isDeviceOwner()) return Result(false, "ScrollLock is not the Device Owner.")
        if (packageName == context.packageName) return Result(false, "ScrollLock cannot lock itself.")

        return try {
            dpm.setUninstallBlocked(admin, packageName, true)
            val notSuspended = dpm.setPackagesSuspended(
                admin,
                arrayOf(packageName),
                true
            ).contains(packageName)
            dpm.setApplicationHidden(admin, packageName, true)

            val hidden = runCatching {
                dpm.isApplicationHidden(admin, packageName)
            }.getOrDefault(false)
            val suspended = runCatching {
                dpm.isPackageSuspended(admin, packageName)
            }.getOrDefault(!notSuspended)
            val uninstallBlocked = runCatching {
                dpm.isUninstallBlocked(admin, packageName)
            }.getOrDefault(false)

            if (!hidden || !suspended || !uninstallBlocked || notSuspended) {
                rollbackPackage(packageName)
                Result(
                    false,
                    "Android refused to fully lock this package. It may be a protected system component."
                )
            } else {
                SettingsStore.addLockedPackage(context, packageName)
                syncUserControlDisabledPackages()
                Result(true, "Package locked: $packageName")
            }
        } catch (t: Throwable) {
            rollbackPackage(packageName)
            Result(false, t.message ?: t.javaClass.simpleName)
        }
    }

    fun unlockPackage(packageName: String): Result {
        if (!isDeviceOwner()) return Result(false, "ScrollLock is not the Device Owner.")

        return try {
            dpm.setApplicationHidden(admin, packageName, false)
            dpm.setPackagesSuspended(admin, arrayOf(packageName), false)
            dpm.setUninstallBlocked(admin, packageName, false)
            SettingsStore.removeLockedPackage(context, packageName)
            syncUserControlDisabledPackages()
            Result(true, "Package unlocked: $packageName")
        } catch (t: Throwable) {
            Result(false, t.message ?: t.javaClass.simpleName)
        }
    }

    fun addBlockedUrl(raw: String): Result {
        if (!isDeviceOwner()) return Result(false, "ScrollLock is not the Device Owner.")
        if (!SettingsStore.addBlockedUrl(context, raw)) {
            return Result(false, "Could not parse that site. Try something like facebook.com")
        }
        return applyUrlBlocklist()
    }

    fun removeBlockedUrl(raw: String): Result {
        if (!isDeviceOwner()) return Result(false, "ScrollLock is not the Device Owner.")
        SettingsStore.removeBlockedUrl(context, raw)
        return applyUrlBlocklist()
    }

    fun applyUrlBlocklist(): Result {
        if (!isDeviceOwner()) return Result(false, "ScrollLock is not the Device Owner.")

        val seeds = SettingsStore.getBlockedUrls(context)
        val patterns = BrowserUrlBlocklist.chromePatternsFor(seeds)
        val bundle = BrowserUrlBlocklist.restrictionsBundle(patterns)

        var applied = 0
        val failures = mutableListOf<String>()

        BrowserUrlBlocklist.MANAGED_BROWSER_PACKAGES.forEach { pkg ->
            if (!BrowserUrlBlocklist.isPackageInstalled(context, pkg)) {
                return@forEach
            }
            runCatching {
                dpm.setApplicationRestrictions(admin, pkg, Bundle(bundle))
                applied++
            }.onFailure { failures += pkg }
        }

        return when {
            failures.isNotEmpty() && applied == 0 ->
                Result(false, "Could not apply URL blocklist: ${failures.joinToString()}")
            seeds.isEmpty() ->
                Result(true, "URL blocklist cleared on $applied browser(s).")
            applied == 0 ->
                Result(
                    false,
                    "No supported browser installed yet. Install Chrome (or Edge/Brave/Opera/Samsung Internet), then tap Reapply."
                )
            failures.isNotEmpty() ->
                Result(
                    true,
                    "Blocked ${seeds.size} site(s) on $applied browser(s). Some failed: ${failures.joinToString()}"
                )
            else ->
                Result(
                    true,
                    "Blocked ${seeds.size} site(s) in browsers (${patterns.size} URL patterns → $applied browser(s))."
                )
        }
    }

    fun reapplyAll(): Result {
        if (!isDeviceOwner()) return Result(false, "ScrollLock is not the Device Owner.")

        val failures = mutableListOf<String>()
        SettingsStore.getLockedPackages(context).forEach { packageName ->
            try {
                dpm.setUninstallBlocked(admin, packageName, true)
                val notSuspended = dpm.setPackagesSuspended(
                    admin, arrayOf(packageName), true
                ).contains(packageName)
                dpm.setApplicationHidden(admin, packageName, true)

                val hidden = runCatching { dpm.isApplicationHidden(admin, packageName) }
                    .getOrDefault(false)
                val suspended = runCatching { dpm.isPackageSuspended(admin, packageName) }
                    .getOrDefault(!notSuspended)
                val uninstallBlocked = runCatching { dpm.isUninstallBlocked(admin, packageName) }
                    .getOrDefault(false)

                if (!hidden || !suspended || !uninstallBlocked || notSuspended) failures += packageName
            } catch (_: Throwable) {
                failures += packageName
            }
        }

        runCatching { syncUserControlDisabledPackages() }
            .onFailure { failures += "user-control policy" }

        val urlResult = applyUrlBlocklist()
        if (!urlResult.success && SettingsStore.getBlockedUrls(context).isNotEmpty()) {
            failures += "url-blocklist"
        }

        if (SettingsStore.isHardeningEnabled(context)) {
            val hardeningFailures = applyHardening(true)
            if (hardeningFailures.isNotEmpty()) {
                failures += hardeningFailures.map { "hardening:$it" }
            }
        }

        return if (failures.isEmpty()) {
            Result(true, "All policies reapplied.")
        } else {
            Result(false, "Could not reapply: ${failures.distinct().joinToString()}")
        }
    }

    fun setHardening(enabled: Boolean): Result {
        if (!isDeviceOwner()) return Result(false, "ScrollLock is not the Device Owner.")

        val failures = applyHardening(enabled)
        if (failures.isEmpty()) {
            SettingsStore.setHardeningEnabled(context, enabled)
            return Result(true, if (enabled) "Hardening enabled." else "Hardening disabled.")
        }

        if (enabled) {
            applyHardening(false)
            SettingsStore.setHardeningEnabled(context, false)
            return Result(false, "Hardening could not be enabled: ${failures.joinToString()}")
        }

        SettingsStore.setHardeningEnabled(context, true)
        return Result(false, "Some hardening restrictions remain: ${failures.joinToString()}")
    }

    fun clearEverything(): Result {
        if (!isDeviceOwner()) return Result(false, "ScrollLock is not the Device Owner.")

        val failures = mutableListOf<String>()
        SettingsStore.getLockedPackages(context).toList().forEach { packageName ->
            val result = unlockPackage(packageName)
            if (!result.success) failures += packageName
        }

        SettingsStore.clearBlockedUrls(context)
        val urlClear = applyUrlBlocklist()
        if (!urlClear.success) failures += "url-blocklist"

        val hardeningResult = setHardening(false)
        if (!hardeningResult.success) failures += "hardening"

        return if (failures.isEmpty()) {
            Result(true, "All ScrollLock policies were cleared.")
        } else {
            Result(false, "Some policies could not be cleared: ${failures.joinToString()}")
        }
    }

    @Suppress("DEPRECATION")
    fun relinquishDeviceOwnerForRecovery(): Result {
        if (!isDeviceOwner()) return Result(true, "ScrollLock is already not Device Owner.")

        val cleared = clearEverything()
        if (!cleared.success) return cleared

        return try {
            runCatching { syncUserControlDisabledPackages(includeScrollLock = false) }
            dpm.clearDeviceOwnerApp(context.packageName)
            Result(
                true,
                "Device Owner relinquished. Reboot once, then ScrollLock can be uninstalled normally."
            )
        } catch (t: Throwable) {
            Result(false, t.message ?: t.javaClass.simpleName)
        }
    }

    private fun syncUserControlDisabledPackages(includeScrollLock: Boolean = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val protectedPackages = SettingsStore.getLockedPackages(context).toMutableSet()
            if (includeScrollLock && SettingsStore.hasRecoveryKey(context)) {
                protectedPackages += context.packageName
            }
            dpm.setUserControlDisabledPackages(admin, protectedPackages.sorted())
        }
    }

    private fun hardeningRestrictions(): List<String> {
        val restrictions = mutableListOf(
            UserManager.DISALLOW_DEBUGGING_FEATURES,
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_USER_SWITCH,
            UserManager.DISALLOW_SAFE_BOOT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            restrictions += UserManager.DISALLOW_ADD_PRIVATE_PROFILE
        }

        return restrictions
    }

    private fun applyHardening(enabled: Boolean): List<String> {
        val failures = mutableListOf<String>()
        hardeningRestrictions().forEach { restriction ->
            runCatching {
                if (enabled) {
                    dpm.addUserRestriction(admin, restriction)
                } else {
                    dpm.clearUserRestriction(admin, restriction)
                }
            }.onFailure { failures += restriction }
        }
        return failures
    }

    private fun rollbackPackage(packageName: String) {
        runCatching { dpm.setApplicationHidden(admin, packageName, false) }
        runCatching { dpm.setPackagesSuspended(admin, arrayOf(packageName), false) }
        runCatching { dpm.setUninstallBlocked(admin, packageName, false) }
        SettingsStore.removeLockedPackage(context, packageName)
        runCatching { syncUserControlDisabledPackages() }
    }
}
