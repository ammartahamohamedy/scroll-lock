package dev.scrolllock.dpc

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private lateinit var policy: PolicyController
    private lateinit var resultBanner: ResultBannerController

    private lateinit var statusBanner: TextView
    private lateinit var recoveryButton: Button
    private lateinit var hardeningButton: Button
    private lateinit var reapplyButton: Button
    private lateinit var siteInput: EditText
    private lateinit var addSiteButton: Button
    private lateinit var blockedSitesContainer: LinearLayout
    private lateinit var blockedSitesEmpty: TextView
    private lateinit var recoveryExitButton: Button
    private lateinit var showSystemBox: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        policy = PolicyController(applicationContext)

        window.setDecorFitsSystemWindows(false)
        setContentView(R.layout.activity_settings)

        val root = findViewById<View>(R.id.root)
        applyWindowInsetsPadding(root)

        statusBanner = findViewById(R.id.statusBanner)
        resultBanner = ResultBannerController(findViewById(R.id.resultBanner))
        recoveryButton = findViewById(R.id.recoveryButton)
        hardeningButton = findViewById(R.id.hardeningButton)
        reapplyButton = findViewById(R.id.reapplyButton)
        siteInput = findViewById(R.id.siteInput)
        addSiteButton = findViewById(R.id.addSiteButton)
        blockedSitesContainer = findViewById(R.id.blockedSitesContainer)
        blockedSitesEmpty = findViewById(R.id.blockedSitesEmpty)
        recoveryExitButton = findViewById(R.id.recoveryExitButton)
        showSystemBox = findViewById(R.id.showSystemBox)

        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }

        recoveryButton.setOnClickListener { handleRecoveryKeyButton() }
        hardeningButton.setOnClickListener { handleHardeningButton() }
        reapplyButton.setOnClickListener {
            val result = policy.reapplyAll()
            resultBanner.show(result.success, result.message)
            refresh()
        }
        addSiteButton.setOnClickListener { handleAddSite() }
        siteInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                handleAddSite()
                true
            } else {
                false
            }
        }
        recoveryExitButton.setOnClickListener {
            if (!SettingsStore.hasRecoveryKey(this)) {
                Toast.makeText(this, "No recovery key exists yet.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            UiDialogs.showRecoveryMenu(
                this,
                onClearAll = {
                    val result = policy.clearEverything()
                    resultBanner.show(result.success, result.message)
                    refresh()
                },
                onRelinquish = {
                    val result = policy.relinquishDeviceOwnerForRecovery()
                    resultBanner.show(result.success, result.message)
                    refresh()
                }
            )
        }

        showSystemBox.setOnCheckedChangeListener { _, checked ->
            SettingsStore.setShowSystemApps(this, checked)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        resultBanner.clear()
        super.onDestroy()
    }

    private fun refresh() {
        val owner = policy.isDeviceOwner()
        val hasKey = SettingsStore.hasRecoveryKey(this)
        val hardened = SettingsStore.isHardeningEnabled(this)
        val sites = SettingsStore.getBlockedUrls(this).sorted()

        applyStatusBanner(statusBanner, owner, hardened)

        recoveryButton.isEnabled = owner
        recoveryButton.text = if (hasKey) {
            getString(R.string.rotate_recovery_key)
        } else {
            getString(R.string.create_recovery_key)
        }

        hardeningButton.isEnabled = owner && hasKey
        hardeningButton.text = if (hardened) {
            getString(R.string.disable_hardening)
        } else {
            getString(R.string.enable_hardening)
        }

        reapplyButton.isEnabled = owner
        val canManageSites = owner && hasKey
        siteInput.isEnabled = canManageSites
        addSiteButton.isEnabled = canManageSites
        recoveryExitButton.isEnabled = hasKey

        renderBlockedSites(sites, canManageSites)

        showSystemBox.setOnCheckedChangeListener(null)
        showSystemBox.isChecked = SettingsStore.isShowSystemApps(this)
        showSystemBox.setOnCheckedChangeListener { _, checked ->
            SettingsStore.setShowSystemApps(this, checked)
        }
    }

    private fun renderBlockedSites(sites: List<String>, canManage: Boolean) {
        blockedSitesContainer.removeAllViews()
        if (sites.isEmpty()) {
            blockedSitesContainer.visibility = View.GONE
            blockedSitesEmpty.visibility = View.VISIBLE
            return
        }

        blockedSitesEmpty.visibility = View.GONE
        blockedSitesContainer.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(this)
        sites.forEach { site ->
            val row = inflater.inflate(R.layout.item_blocked_site, blockedSitesContainer, false)
            row.findViewById<TextView>(R.id.siteDomain).text = site
            val remove = row.findViewById<Button>(R.id.siteRemoveButton)
            remove.isEnabled = canManage
            remove.setOnClickListener { handleRemoveSite(site) }
            blockedSitesContainer.addView(row)
        }
    }

    private fun handleAddSite() {
        if (!policy.isDeviceOwner()) {
            Toast.makeText(this, "Provision ScrollLock as Device Owner first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!SettingsStore.hasRecoveryKey(this)) {
            Toast.makeText(this, R.string.recovery_key_required_sites, Toast.LENGTH_SHORT).show()
            return
        }

        val raw = siteInput.text?.toString().orEmpty().trim()
        if (raw.isEmpty()) {
            siteInput.error = getString(R.string.site_input_hint)
            return
        }

        // Same UX as locking apps: add freely; only remove is recovery-key gated.
        val result = policy.addBlockedUrl(raw)
        resultBanner.show(result.success, result.message)
        if (result.success) {
            siteInput.text = null
            siteInput.error = null
        }
        refresh()
    }

    private fun handleRemoveSite(site: String) {
        if (!SettingsStore.hasRecoveryKey(this)) {
            Toast.makeText(this, R.string.recovery_key_required_sites, Toast.LENGTH_SHORT).show()
            return
        }
        UiDialogs.promptForRecoveryKey(this, "Remove $site") {
            val result = policy.removeBlockedUrl(site)
            resultBanner.show(result.success, result.message)
            refresh()
        }
    }

    private fun handleRecoveryKeyButton() {
        if (!policy.isDeviceOwner()) {
            Toast.makeText(this, "Provision ScrollLock as Device Owner first.", Toast.LENGTH_SHORT).show()
            return
        }

        if (SettingsStore.hasRecoveryKey(this)) {
            UiDialogs.promptForRecoveryKey(this, "Rotate recovery key") {
                generateAndShowRecoveryKey()
            }
        } else {
            generateAndShowRecoveryKey()
        }
    }

    private fun generateAndShowRecoveryKey() {
        val key = SettingsStore.createAndStoreRecoveryKey(this)
        policy.reapplyAll()
        UiDialogs.showRecoveryKeyCreated(this, key) { refresh() }
        refresh()
    }

    private fun handleHardeningButton() {
        if (!SettingsStore.hasRecoveryKey(this)) {
            Toast.makeText(this, "Create a recovery key first.", Toast.LENGTH_SHORT).show()
            return
        }

        if (SettingsStore.isHardeningEnabled(this)) {
            UiDialogs.promptForRecoveryKey(this, "Disable hardening") {
                val result = policy.setHardening(false)
                resultBanner.show(result.success, result.message)
                refresh()
            }
            return
        }

        UiDialogs.confirmEnableHardening(this) {
            val result = policy.setHardening(true)
            resultBanner.show(result.success, result.message)
            refresh()
        }
    }
}
