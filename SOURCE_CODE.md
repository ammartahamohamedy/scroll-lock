# ScrollLock DPC — Complete Source Code

This file is a concatenated view of every hand-written build/configuration/source file in the project. The runnable project is the adjacent folder/ZIP.

## `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ScrollLockDPC"
include(":app")
```

## `build.gradle.kts`

```kotlin
plugins {
    id("com.android.application") version "9.4.0" apply false
}
```

## `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
```

## `app/build.gradle.kts`

```kotlin
import java.util.Properties

plugins {
    id("com.android.application")
}

android {
    namespace = "dev.scrolllock.dpc"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.scrolllock.dpc"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.exists()) {
        val props = Properties().apply {
            keystorePropertiesFile.inputStream().use { load(it) }
        }

        signingConfigs {
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }

        buildTypes {
            getByName("release") {
                isMinifyEnabled = false
                signingConfig = signingConfigs.getByName("release")
            }
        }
    } else {
        buildTypes {
            getByName("release") {
                isMinifyEnabled = false
            }
        }
    }
}
```

## `app/src/main/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Needed because this app's core function is device/app management. -->
    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Android 12+ admin-integrated / QR provisioning hooks. -->
        <activity
            android:name=".GetProvisioningModeActivity"
            android:exported="true"
            android:permission="android.permission.BIND_DEVICE_ADMIN"
            android:theme="@android:style/Theme.NoDisplay">
            <intent-filter>
                <action android:name="android.app.action.GET_PROVISIONING_MODE" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

        <activity
            android:name=".PolicyComplianceActivity"
            android:exported="true"
            android:permission="android.permission.BIND_DEVICE_ADMIN"
            android:theme="@android:style/Theme.NoDisplay">
            <intent-filter>
                <action android:name="android.app.action.ADMIN_POLICY_COMPLIANCE" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

        <receiver
            android:name=".ScrollLockAdminReceiver"
            android:description="@string/admin_description"
            android:exported="true"
            android:label="@string/admin_name"
            android:permission="android.permission.BIND_DEVICE_ADMIN">
            <meta-data
                android:name="android.app.device_admin"
                android:resource="@xml/device_admin_receiver" />
            <intent-filter>
                <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
                <action android:name="android.app.action.DEVICE_ADMIN_DISABLED" />
                <action android:name="android.app.action.PROFILE_PROVISIONING_COMPLETE" />
            </intent-filter>
        </receiver>

        <receiver
            android:name=".BootReceiver"
            android:enabled="true"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

        <receiver
            android:name=".PackageChangedReceiver"
            android:enabled="true"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.PACKAGE_ADDED" />
                <action android:name="android.intent.action.PACKAGE_REPLACED" />
                <data android:scheme="package" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

## `app/src/main/res/xml/device_admin_receiver.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-policies />
</device-admin>
```

## `app/src/main/res/values/strings.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">ScrollLock</string>
    <string name="admin_name">ScrollLock Device Owner</string>
    <string name="admin_description">Locks selected apps using Android Device Policy APIs.</string>
</resources>
```

## `app/src/main/java/dev/scrolllock/dpc/SettingsStore.kt`

```kotlin
package dev.scrolllock.dpc

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

object SettingsStore {
    private const val PREFS = "scrolllock_prefs"
    private const val KEY_LOCKED_PACKAGES = "locked_packages"
    private const val KEY_HARDENING_ENABLED = "hardening_enabled"
    private const val KEY_RECOVERY_HASH = "recovery_hash"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getLockedPackages(context: Context): MutableSet<String> =
        prefs(context).getStringSet(KEY_LOCKED_PACKAGES, emptySet())
            ?.toMutableSet()
            ?: mutableSetOf()

    fun setLockedPackages(context: Context, packages: Set<String>) {
        prefs(context).edit()
            .putStringSet(KEY_LOCKED_PACKAGES, packages.toSet())
            .apply()
    }

    fun addLockedPackage(context: Context, packageName: String) {
        val packages = getLockedPackages(context)
        packages += packageName
        setLockedPackages(context, packages)
    }

    fun removeLockedPackage(context: Context, packageName: String) {
        val packages = getLockedPackages(context)
        packages -= packageName
        setLockedPackages(context, packages)
    }

    fun isHardeningEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HARDENING_ENABLED, false)

    fun setHardeningEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_HARDENING_ENABLED, enabled)
            .apply()
    }

    fun hasRecoveryKey(context: Context): Boolean =
        prefs(context).contains(KEY_RECOVERY_HASH)

    fun createAndStoreRecoveryKey(context: Context): String {
        val bytes = ByteArray(20)
        SecureRandom().nextBytes(bytes)
        val raw = bytes.joinToString("") { "%02X".format(it) }
        val formatted = raw.chunked(4).joinToString("-")
        storeRecoveryKey(context, formatted)
        return formatted
    }

    fun storeRecoveryKey(context: Context, recoveryKey: String) {
        val hash = hash(normalize(recoveryKey))
        prefs(context).edit()
            .putString(KEY_RECOVERY_HASH, hash)
            .apply()
    }

    fun verifyRecoveryKey(context: Context, candidate: String): Boolean {
        val saved = prefs(context).getString(KEY_RECOVERY_HASH, null) ?: return false
        val candidateHash = hash(normalize(candidate))
        return MessageDigest.isEqual(
            saved.toByteArray(Charsets.UTF_8),
            candidateHash.toByteArray(Charsets.UTF_8)
        )
    }

    private fun normalize(value: String): String =
        value.replace("-", "")
            .replace(" ", "")
            .trim()
            .uppercase()

    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
```

## `app/src/main/java/dev/scrolllock/dpc/ScrollLockAdminReceiver.kt`

```kotlin
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
```

## `app/src/main/java/dev/scrolllock/dpc/PolicyController.kt`

```kotlin
package dev.scrolllock.dpc

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
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
            // Restore visibility first, then execution, then uninstallability.
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
            // Never leave a failed enable attempt in a half-hardened state.
            applyHardening(false)
            SettingsStore.setHardeningEnabled(context, false)
            return Result(false, "Hardening could not be enabled: ${failures.joinToString()}")
        }

        // Some OEMs may refuse to clear a restriction immediately. Keep the state marked
        // enabled so the UI continues to offer the recovery-key-protected disable action.
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
            // Remove ScrollLock itself from user-control protection before ownership goes away.
            runCatching { syncUserControlDisabledPackages(includeScrollLock = false) }

            // Deprecated by Android for production enterprise flows, but useful here as an explicit,
            // local recovery exit after first clearing every policy this app sets.
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
            // Protect ScrollLock's own local state once a recovery key exists, so normal
            // Settings controls cannot force-stop it or clear the key/package list.
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
```

## `app/src/main/java/dev/scrolllock/dpc/AppCatalog.kt`

```kotlin
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

        pm.permissionControllerPackageName?.let {
            result[it] = "Permission Controller"
        }

        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        dpm.activeAdmins.orEmpty().map { it.packageName }.forEach {
            result[it] = "Active device administrator"
        }

        return result
    }
}
```

## `app/src/main/java/dev/scrolllock/dpc/BootReceiver.kt`

```kotlin
package dev.scrolllock.dpc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            PolicyController(context.applicationContext).reapplyAll()
        }
    }
}
```

## `app/src/main/java/dev/scrolllock/dpc/PackageChangedReceiver.kt`

```kotlin
package dev.scrolllock.dpc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PackageChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return
        if (packageName in SettingsStore.getLockedPackages(context)) {
            PolicyController(context.applicationContext).reapplyAll()
        }
    }
}
```

## `app/src/main/java/dev/scrolllock/dpc/GetProvisioningModeActivity.kt`

```kotlin
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
```

## `app/src/main/java/dev/scrolllock/dpc/PolicyComplianceActivity.kt`

```kotlin
package dev.scrolllock.dpc

import android.app.Activity
import android.os.Bundle

/**
 * Provisioning finalization hook used by Android 12+.
 * There are no server-side compliance checks: ScrollLock is local-only.
 */
class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PolicyController(applicationContext).reapplyAll()
        setResult(RESULT_OK)
        finish()
    }
}
```

## `app/src/main/java/dev/scrolllock/dpc/MainActivity.kt`

```kotlin
package dev.scrolllock.dpc

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var policy: PolicyController
    private lateinit var catalog: AppCatalog
    private lateinit var adapter: AppAdapter

    private lateinit var statusText: TextView
    private lateinit var recoveryButton: Button
    private lateinit var hardeningButton: Button
    private lateinit var lockedCountText: TextView
    private lateinit var searchBox: EditText
    private lateinit var showSystemBox: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        policy = PolicyController(applicationContext)
        catalog = AppCatalog(applicationContext)

        window.setDecorFitsSystemWindows(false)
        setContentView(buildUi())
        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            setBackgroundColor(Color.rgb(248, 248, 248))
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(dp(16), bars.top + dp(12), dp(16), bars.bottom + dp(8))
                insets
            }
        }

        root.addView(TextView(this).apply {
            text = "ScrollLock"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "Device-owner app lock. Locked apps remain installed, but are hidden, suspended, and uninstall-blocked."
            textSize = 14f
            setPadding(0, dp(4), 0, dp(8))
        })

        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(statusText, matchWidthWrap())

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(4))
        }

        recoveryButton = Button(this).apply {
            text = "Create recovery key"
            setOnClickListener { handleRecoveryKeyButton() }
        }
        controls.addView(recoveryButton, weightWrap())

        hardeningButton = Button(this).apply {
            text = "Enable hardening"
            setOnClickListener { handleHardeningButton() }
        }
        controls.addView(hardeningButton, weightWrap())
        root.addView(controls, matchWidthWrap())

        val secondControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        secondControls.addView(Button(this).apply {
            text = "Reapply"
            setOnClickListener {
                showResult(policy.reapplyAll())
                refreshAll()
            }
        }, weightWrap())

        secondControls.addView(Button(this).apply {
            text = "Recovery / exit"
            setOnClickListener { showRecoveryMenu() }
        }, weightWrap())

        root.addView(secondControls, matchWidthWrap())

        lockedCountText = TextView(this).apply {
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(4))
        }
        root.addView(lockedCountText, matchWidthWrap())

        searchBox = EditText(this).apply {
            hint = "Search apps or package names"
            isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (::adapter.isInitialized) adapter.setQuery(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        root.addView(searchBox, matchWidthWrap())

        showSystemBox = CheckBox(this).apply {
            text = "Show system apps (advanced; critical packages are protected)"
            setOnCheckedChangeListener { _, _ -> refreshApps() }
        }
        root.addView(showSystemBox, matchWidthWrap())

        val list = ListView(this).apply {
            dividerHeight = 1
        }
        adapter = AppAdapter(this)
        list.adapter = adapter
        root.addView(
            list,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        return root
    }

    private fun refreshAll() {
        val owner = policy.isDeviceOwner()
        statusText.text = if (owner) {
            "Status: DEVICE OWNER active. Android policy control is available."
        } else {
            "Status: NOT Device Owner. Build/install the APK, then provision it with adb before locking apps."
        }

        recoveryButton.isEnabled = owner
        hardeningButton.isEnabled = owner && SettingsStore.hasRecoveryKey(this)
        hardeningButton.text = if (SettingsStore.isHardeningEnabled(this)) {
            "Disable hardening…"
        } else {
            "Enable hardening"
        }

        recoveryButton.text = if (SettingsStore.hasRecoveryKey(this)) {
            "Rotate recovery key…"
        } else {
            "Create recovery key"
        }

        val locked = SettingsStore.getLockedPackages(this)
        lockedCountText.text = "Locked apps: ${locked.size}"
        refreshApps()
    }

    private fun refreshApps() {
        if (!::adapter.isInitialized) return
        val data = catalog.load(showSystemBox.isChecked)
        adapter.setData(data, SettingsStore.getLockedPackages(this))
        adapter.setQuery(searchBox.text?.toString().orEmpty())
    }

    private fun handleRecoveryKeyButton() {
        if (!policy.isDeviceOwner()) {
            toast("Provision ScrollLock as Device Owner first.")
            return
        }

        if (SettingsStore.hasRecoveryKey(this)) {
            promptForRecoveryKey("Rotate recovery key") {
                generateAndShowRecoveryKey()
            }
        } else {
            generateAndShowRecoveryKey()
        }
    }

    private fun generateAndShowRecoveryKey() {
        val key = SettingsStore.createAndStoreRecoveryKey(this)
        // Once a recovery key exists, reapply so ScrollLock itself becomes protected from
        // normal force-stop / clear-data controls via setUserControlDisabledPackages().
        policy.reapplyAll()
        AlertDialog.Builder(this)
            .setTitle("Save this recovery key")
            .setMessage(
                "This key is shown in full now. Keep it somewhere deliberately inconvenient, or give it to a trusted person. " +
                    "You will need it to unlock apps, disable hardening, or relinquish Device Owner.\n\n$key"
            )
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("ScrollLock recovery key", key))
                toast("Recovery key copied")
            }
            .setNegativeButton("I saved it", null)
            .show()
        refreshAll()
    }

    private fun handleHardeningButton() {
        if (!SettingsStore.hasRecoveryKey(this)) {
            toast("Create a recovery key first.")
            return
        }

        if (SettingsStore.isHardeningEnabled(this)) {
            promptForRecoveryKey("Disable hardening") {
                showResult(policy.setHardening(false))
                refreshAll()
            }
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Enable hardening?")
            .setMessage(
                "Hardening blocks USB debugging, factory reset from Settings, unknown-source installs, safe boot, " +
                    "and creating/switching users.\n\n" +
                    "Important: USB debugging will be disabled, so adb will disconnect. Keep your recovery key. " +
                    "Test locking one nonessential app before enabling this."
            )
            .setPositiveButton("Enable") { _, _ ->
                showResult(policy.setHardening(true))
                refreshAll()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRecoveryMenu() {
        if (!SettingsStore.hasRecoveryKey(this)) {
            toast("No recovery key exists yet.")
            return
        }

        val options = arrayOf(
            "Clear all app locks and hardening",
            "Relinquish Device Owner and exit ScrollLock"
        )

        AlertDialog.Builder(this)
            .setTitle("Recovery / exit")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> promptForRecoveryKey("Clear all policies") {
                        showResult(policy.clearEverything())
                        refreshAll()
                    }
                    1 -> promptForRecoveryKey("Relinquish Device Owner") {
                        confirmRelinquishOwnership()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmRelinquishOwnership() {
        AlertDialog.Builder(this)
            .setTitle("Final recovery exit")
            .setMessage(
                "ScrollLock will first clear every policy it set, then ask Android to relinquish Device Owner. " +
                    "Android documents non-reset owner removal as a testing/recovery mechanism, so use this only when you really want to remove ScrollLock."
            )
            .setPositiveButton("Relinquish") { _, _ ->
                showResult(policy.relinquishDeviceOwnerForRecovery())
                refreshAll()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun lock(entry: AppCatalog.AppEntry) {
        if (!policy.isDeviceOwner()) {
            toast("ScrollLock is not Device Owner.")
            return
        }
        if (!SettingsStore.hasRecoveryKey(this)) {
            toast("Create and save a recovery key before locking anything.")
            return
        }
        if (entry.protectedReason != null) {
            toast("Protected: ${entry.protectedReason}")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Lock ${entry.label}?")
            .setMessage(
                "${entry.packageName}\n\nAndroid will keep it installed but hide it, suspend it, block uninstall, " +
                    "and disable normal user control. Restoring it requires your ScrollLock recovery key."
            )
            .setPositiveButton("Lock") { _, _ ->
                showResult(policy.lockPackage(entry.packageName))
                refreshAll()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun unlock(entry: AppCatalog.AppEntry) {
        promptForRecoveryKey("Unlock ${entry.label}") {
            showResult(policy.unlockPackage(entry.packageName))
            refreshAll()
        }
    }

    private fun promptForRecoveryKey(title: String, onVerified: () -> Unit) {
        val input = EditText(this).apply {
            hint = "Recovery key"
            isSingleLine = true
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Verify", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (SettingsStore.verifyRecoveryKey(this, input.text.toString())) {
                    dialog.dismiss()
                    onVerified()
                } else {
                    input.error = "Incorrect recovery key"
                }
            }
        }
        dialog.show()
    }

    private fun showResult(result: PolicyController.Result) {
        Toast.makeText(
            this,
            (if (result.success) "✓ " else "✗ ") + result.message,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private inner class AppAdapter(private val context: Context) : BaseAdapter() {
        private var all = emptyList<AppCatalog.AppEntry>()
        private var visible = emptyList<AppCatalog.AppEntry>()
        private var locked = emptySet<String>()
        private var query = ""

        fun setData(data: List<AppCatalog.AppEntry>, lockedPackages: Set<String>) {
            all = data
            locked = lockedPackages
            applyFilter()
        }

        fun setQuery(value: String) {
            query = value.trim().lowercase()
            applyFilter()
        }

        private fun applyFilter() {
            visible = if (query.isBlank()) {
                all
            } else {
                all.filter {
                    it.label.lowercase().contains(query) ||
                        it.packageName.lowercase().contains(query)
                }
            }
            notifyDataSetChanged()
        }

        override fun getCount(): Int = visible.size
        override fun getItem(position: Int): AppCatalog.AppEntry = visible[position]
        override fun getItemId(position: Int): Long = getItem(position).packageName.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val entry = getItem(position)
            val isLocked = entry.packageName in locked

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(4), dp(8))
                setBackgroundColor(Color.WHITE)
            }

            row.addView(ImageView(context).apply {
                setImageDrawable(entry.icon)
                adjustViewBounds = true
            }, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                marginEnd = dp(10)
            })

            val textColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            textColumn.addView(TextView(context).apply {
                text = entry.label + if (isLocked) "  [LOCKED]" else ""
                textSize = 16f
                setTypeface(typeface, if (isLocked) Typeface.BOLD else Typeface.NORMAL)
            })

            textColumn.addView(TextView(context).apply {
                text = buildString {
                    append(entry.packageName)
                    if (entry.isSystem) append(" • system")
                    entry.protectedReason?.let { append(" • protected: $it") }
                }
                textSize = 11f
                setTextColor(Color.DKGRAY)
            })

            row.addView(
                textColumn,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )

            row.addView(Button(context).apply {
                text = if (isLocked) "Unlock…" else "Lock"
                isEnabled = isLocked || entry.protectedReason == null
                setOnClickListener {
                    if (isLocked) unlock(entry) else lock(entry)
                }
            })

            return row
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun matchWidthWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun weightWrap() = LinearLayout.LayoutParams(
        0,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        1f
    )
}
```

## `bootstrap-gradle.ps1`

```powershell
param(
    [string]$GradleVersion = "9.6.0"
)

$ErrorActionPreference = "Stop"
$ExpectedZipSha256 = "bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01"
$ExpectedWrapperJarSha256 = "497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7"

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    $studioJbr = Join-Path $env:ProgramFiles "Android\Android Studio\jbr"
    if (Test-Path (Join-Path $studioJbr "bin\java.exe")) {
        $env:JAVA_HOME = $studioJbr
        $env:Path = "$(Join-Path $studioJbr 'bin');$env:Path"
    } else {
        throw "Java was not found. Install Android Studio (includes JBR/JDK) or JDK 17+, then rerun this script."
    }
}

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$temp = Join-Path $root ".gradle-bootstrap"
$zip = Join-Path $temp "gradle-$GradleVersion-bin.zip"
$url = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"

New-Item -ItemType Directory -Force -Path $temp | Out-Null
Write-Host "Downloading official Gradle $GradleVersion..."
Invoke-WebRequest -Uri $url -OutFile $zip

$actual = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLowerInvariant()
if ($GradleVersion -eq "9.6.0" -and $actual -ne $ExpectedZipSha256) {
    throw "Gradle ZIP checksum mismatch. Expected $ExpectedZipSha256, got $actual"
}

Write-Host "Checksum OK. Extracting..."
Expand-Archive -Path $zip -DestinationPath $temp -Force
$gradleBat = Join-Path $temp "gradle-$GradleVersion\bin\gradle.bat"

Push-Location $root
try {
    & $gradleBat wrapper --gradle-version $GradleVersion --distribution-type bin
    if ($LASTEXITCODE -ne 0) { throw "Gradle wrapper generation failed." }

    if ($GradleVersion -eq "9.6.0") {
        $wrapperJar = Join-Path $root "gradle\wrapper\gradle-wrapper.jar"
        $wrapperHash = (Get-FileHash $wrapperJar -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($wrapperHash -ne $ExpectedWrapperJarSha256) {
            throw "Gradle wrapper JAR checksum mismatch. Expected $ExpectedWrapperJarSha256, got $wrapperHash"
        }

        $wrapperProps = Join-Path $root "gradle\wrapper\gradle-wrapper.properties"
        $content = Get-Content $wrapperProps | Where-Object { $_ -notmatch '^distributionSha256Sum=' }
        $content += "distributionSha256Sum=$ExpectedZipSha256"
        Set-Content -Path $wrapperProps -Value $content -Encoding ASCII
    }
} finally {
    Pop-Location
}

Remove-Item -Recurse -Force $temp
Write-Host "Gradle wrapper created. Next: .\gradlew.bat --version"
```

## `bootstrap-gradle.sh`

```bash
#!/usr/bin/env sh
set -eu

VERSION="${1:-9.6.0}"
EXPECTED="bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01"
EXPECTED_WRAPPER="497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7"
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TMP="$ROOT/.gradle-bootstrap"
ZIP="$TMP/gradle-$VERSION-bin.zip"
URL="https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"

command -v java >/dev/null 2>&1 || { echo "Java 17+ is required." >&2; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "curl is required." >&2; exit 1; }
command -v unzip >/dev/null 2>&1 || { echo "unzip is required." >&2; exit 1; }

mkdir -p "$TMP"
echo "Downloading official Gradle $VERSION..."
curl -fL "$URL" -o "$ZIP"

if [ "$VERSION" = "9.6.0" ]; then
  if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL=$(sha256sum "$ZIP" | awk '{print $1}')
  else
    ACTUAL=$(shasum -a 256 "$ZIP" | awk '{print $1}')
  fi
  [ "$ACTUAL" = "$EXPECTED" ] || { echo "Checksum mismatch." >&2; exit 1; }
fi

unzip -q -o "$ZIP" -d "$TMP"
cd "$ROOT"
"$TMP/gradle-$VERSION/bin/gradle" wrapper --gradle-version "$VERSION" --distribution-type bin

if [ "$VERSION" = "9.6.0" ]; then
  if command -v sha256sum >/dev/null 2>&1; then
    WRAPPER_ACTUAL=$(sha256sum "$ROOT/gradle/wrapper/gradle-wrapper.jar" | awk '{print $1}')
  else
    WRAPPER_ACTUAL=$(shasum -a 256 "$ROOT/gradle/wrapper/gradle-wrapper.jar" | awk '{print $1}')
  fi
  [ "$WRAPPER_ACTUAL" = "$EXPECTED_WRAPPER" ] || { echo "Wrapper JAR checksum mismatch." >&2; exit 1; }
  grep -v '^distributionSha256Sum=' "$ROOT/gradle/wrapper/gradle-wrapper.properties" > "$ROOT/gradle/wrapper/gradle-wrapper.properties.tmp"
  printf '%s\n' "distributionSha256Sum=$EXPECTED" >> "$ROOT/gradle/wrapper/gradle-wrapper.properties.tmp"
  mv "$ROOT/gradle/wrapper/gradle-wrapper.properties.tmp" "$ROOT/gradle/wrapper/gradle-wrapper.properties"
fi

rm -rf "$TMP"
echo "Gradle wrapper created. Next: ./gradlew --version"
```

## `tools/make-provisioning-json.ps1`

```powershell
param(
    [Parameter(Mandatory=$true)][string]$ApkPath,
    [Parameter(Mandatory=$true)][string]$HttpsUrl,
    [string]$Output = "provisioning.json"
)

$ErrorActionPreference = "Stop"
$apk = (Resolve-Path $ApkPath).Path
$bytes = [System.IO.File]::ReadAllBytes($apk)
$sha = [System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes)
$checksum = [Convert]::ToBase64String($sha).Replace('+','-').Replace('/','_')

$config = [ordered]@{
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME" = "dev.scrolllock.dpc/dev.scrolllock.dpc.ScrollLockAdminReceiver"
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION" = $HttpsUrl
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM" = $checksum
    "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED" = $true
}

$config | ConvertTo-Json | Set-Content -Encoding UTF8 $Output
Write-Host "Wrote $Output"
Write-Host "Package SHA-256 (URL-safe Base64): $checksum"
```

## `keystore.properties.example`

```text
storeFile=C:/path/to/scrolllock-release.jks
storePassword=CHANGE_ME
keyAlias=scrolllock
keyPassword=CHANGE_ME
```

## `.gitignore`

```text
.gradle/
.idea/
local.properties
*.iml
build/
**/build/
keystore.properties
*.jks
*.keystore
```
