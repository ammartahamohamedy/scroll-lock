package dev.scrolllock.dpc

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

object SettingsStore {
    private const val PREFS = "scrolllock_prefs"
    private const val KEY_LOCKED_PACKAGES = "locked_packages"
    private const val KEY_HARDENING_ENABLED = "hardening_enabled"
    private const val KEY_RECOVERY_HASH = "recovery_hash"
    private const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
    private const val KEY_BLOCKED_URLS = "blocked_urls"

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

    fun isShowSystemApps(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_SYSTEM_APPS, false)

    fun setShowSystemApps(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_SHOW_SYSTEM_APPS, enabled)
            .apply()
    }

    fun getBlockedUrls(context: Context): MutableSet<String> =
        prefs(context).getStringSet(KEY_BLOCKED_URLS, emptySet())
            ?.toMutableSet()
            ?: mutableSetOf()

    fun setBlockedUrls(context: Context, urls: Set<String>) {
        prefs(context).edit()
            .putStringSet(KEY_BLOCKED_URLS, urls.toSet())
            .apply()
    }

    fun addBlockedUrl(context: Context, url: String): Boolean {
        val seed = BrowserUrlBlocklist.expandUserEntry(url).firstOrNull() ?: return false
        val urls = getBlockedUrls(context)
        if (seed in urls) return true
        urls += seed
        setBlockedUrls(context, urls)
        return true
    }

    fun removeBlockedUrl(context: Context, url: String) {
        val urls = getBlockedUrls(context)
        urls.remove(url)
        // Also try normalized form
        BrowserUrlBlocklist.expandUserEntry(url).forEach { urls.remove(it) }
        setBlockedUrls(context, urls)
    }

    fun clearBlockedUrls(context: Context) {
        setBlockedUrls(context, emptySet())
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
