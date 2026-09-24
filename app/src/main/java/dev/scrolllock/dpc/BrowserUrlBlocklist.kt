package dev.scrolllock.dpc

import android.content.Context
import android.os.Bundle

/**
 * Applies Chrome-enterprise style URLBlocklist restrictions to Chromium-based browsers
 * via DevicePolicyManager.setApplicationRestrictions.
 *
 * This is the Device Owner–supported way to block sites in browsers without a VPN.
 * Browsers that ignore managed configs (some Firefox builds, niche browsers) are not covered —
 * lock those apps separately if needed.
 */
object BrowserUrlBlocklist {
    /** Packages known to honor Chrome URLBlocklist / URLAllowlist managed configs. */
    val MANAGED_BROWSER_PACKAGES = listOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "com.google.android.apps.chrome",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.brave.browser_beta",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.sec.android.app.sbrowser",
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
        "org.chromium.chrome"
    )

    fun expandUserEntry(raw: String): Set<String> {
        var input = raw.trim().lowercase()
        if (input.isEmpty()) return emptySet()

        input = input.removePrefix("https://").removePrefix("http://")
        // Keep a single stored "seed" as host or host/path; expansion happens at apply time.
        while (input.startsWith("*.")) input = input.removePrefix("*.")
        while (input.startsWith("*")) input = input.removePrefix("*")
        input = input.trimStart('.')
        if (input.endsWith("/*")) input = input.dropLast(2)
        input = input.trimEnd('/')
        if (input.isEmpty() || '.' !in input) return emptySet()
        return setOf(input)
    }

    fun chromePatternsFor(seeds: Set<String>): Array<String> {
        val out = linkedSetOf<String>()
        seeds.forEach { seed ->
            val host = seed.substringBefore('/')
            val path = seed.substringAfter('/', missingDelimiterValue = "")
            if (host.isEmpty()) return@forEach

            if (path.isNotEmpty()) {
                out += "$host/$path"
                out += "$host/$path/*"
                out += "*.$host/$path"
                out += "*.$host/$path/*"
                out += "http://$host/$path"
                out += "https://$host/$path"
                out += "http://$host/$path/*"
                out += "https://$host/$path/*"
            } else {
                // Host + all subdomains + common mobile hosts
                out += host
                out += "*.$host"
                out += "[*.]$host"
                out += "http://$host"
                out += "https://$host"
                out += "http://*.$host"
                out += "https://*.$host"
                out += "$host/*"
                out += "*.$host/*"
                out += "www.$host"
                out += "m.$host"
                out += "mobile.$host"
            }
        }
        return out.toTypedArray()
    }

    fun restrictionsBundle(patterns: Array<String>): Bundle {
        if (patterns.isEmpty()) return Bundle()
        return Bundle().apply {
            putStringArray("URLBlocklist", patterns)
        }
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
}
