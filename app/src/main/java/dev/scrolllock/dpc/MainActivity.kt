package dev.scrolllock.dpc

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var policy: PolicyController
    private lateinit var catalog: AppCatalog
    private lateinit var adapter: AppListAdapter
    private lateinit var resultBanner: ResultBannerController

    private lateinit var statusBanner: TextView
    private lateinit var lockedCountText: TextView
    private lateinit var searchBox: EditText
    private lateinit var onboardingCard: LinearLayout
    private lateinit var onboardingPrimaryButton: Button
    private lateinit var settingsButton: Button
    private lateinit var appList: ListView
    private lateinit var emptyState: LinearLayout
    private lateinit var emptyTitle: TextView
    private lateinit var emptyBody: TextView
    private lateinit var loadingIndicator: ProgressBar

    private val executor = Executors.newSingleThreadExecutor()
    private var loadGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        policy = PolicyController(applicationContext)
        catalog = AppCatalog(applicationContext)

        window.setDecorFitsSystemWindows(false)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        applyWindowInsetsPadding(root)

        statusBanner = findViewById(R.id.statusBanner)
        resultBanner = ResultBannerController(findViewById(R.id.resultBanner))
        lockedCountText = findViewById(R.id.lockedCountText)
        searchBox = findViewById(R.id.searchBox)
        onboardingCard = findViewById(R.id.onboardingCard)
        onboardingPrimaryButton = findViewById(R.id.onboardingPrimaryButton)
        settingsButton = findViewById(R.id.settingsButton)
        appList = findViewById(R.id.appList)
        emptyState = findViewById(R.id.emptyState)
        emptyTitle = findViewById(R.id.emptyTitle)
        emptyBody = findViewById(R.id.emptyBody)
        loadingIndicator = findViewById(R.id.loadingIndicator)

        adapter = AppListAdapter(this) { entry, isLocked ->
            if (isLocked) unlock(entry) else lock(entry)
        }
        appList.adapter = adapter

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        onboardingPrimaryButton.setOnClickListener { createRecoveryKey() }

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.setQuery(s?.toString().orEmpty())
                updateEmptyState()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    override fun onResume() {
        super.onResume()
        refreshChrome()
        loadAppsAsync()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        resultBanner.clear()
        super.onDestroy()
    }

    private fun refreshChrome() {
        val owner = policy.isDeviceOwner()
        val hasKey = SettingsStore.hasRecoveryKey(this)
        val hardened = SettingsStore.isHardeningEnabled(this)

        applyStatusBanner(statusBanner, owner, hardened)

        lockedCountText.text = getString(
            R.string.locked_count,
            SettingsStore.getLockedPackages(this).size
        )

        val showOnboarding = owner && !hasKey
        onboardingCard.visibility = if (showOnboarding) View.VISIBLE else View.GONE
        onboardingPrimaryButton.isEnabled = owner

        updateEmptyState()
    }

    private fun loadAppsAsync() {
        val generation = ++loadGeneration
        loadingIndicator.visibility = View.VISIBLE
        appList.visibility = View.INVISIBLE
        emptyState.visibility = View.GONE

        val showSystem = SettingsStore.isShowSystemApps(this)
        executor.execute {
            val data = runCatching { catalog.load(showSystem) }.getOrElse { emptyList() }
            runOnUiThread {
                if (generation != loadGeneration || isFinishing) return@runOnUiThread
                loadingIndicator.visibility = View.GONE
                adapter.setData(data, SettingsStore.getLockedPackages(this))
                adapter.setQuery(searchBox.text?.toString().orEmpty())
                updateEmptyState()
            }
        }
    }

    private fun updateEmptyState() {
        val owner = policy.isDeviceOwner()
        val hasKey = SettingsStore.hasRecoveryKey(this)
        val loading = loadingIndicator.visibility == View.VISIBLE

        if (loading) {
            emptyState.visibility = View.GONE
            appList.visibility = View.INVISIBLE
            return
        }

        when {
            !owner -> {
                emptyTitle.setText(R.string.empty_not_owner_title)
                emptyBody.setText(R.string.empty_not_owner_body)
                emptyState.visibility = View.VISIBLE
                appList.visibility = View.GONE
            }
            !hasKey -> {
                emptyTitle.setText(R.string.empty_no_key_title)
                emptyBody.setText(R.string.empty_no_key_body)
                emptyState.visibility = View.VISIBLE
                appList.visibility = View.GONE
            }
            adapter.visibleCount() == 0 -> {
                emptyTitle.setText(R.string.empty_no_results_title)
                emptyBody.setText(R.string.empty_no_results_body)
                emptyState.visibility = View.VISIBLE
                appList.visibility = View.GONE
            }
            else -> {
                emptyState.visibility = View.GONE
                appList.visibility = View.VISIBLE
            }
        }
    }

    private fun createRecoveryKey() {
        if (!policy.isDeviceOwner()) {
            Toast.makeText(this, "Provision ScrollLock as Device Owner first.", Toast.LENGTH_SHORT).show()
            return
        }
        val key = SettingsStore.createAndStoreRecoveryKey(this)
        policy.reapplyAll()
        UiDialogs.showRecoveryKeyCreated(this, key) {
            refreshChrome()
            loadAppsAsync()
        }
        refreshChrome()
    }

    private fun lock(entry: AppCatalog.AppEntry) {
        if (!policy.isDeviceOwner()) {
            resultBanner.show(false, "ScrollLock is not Device Owner.")
            return
        }
        if (!SettingsStore.hasRecoveryKey(this)) {
            resultBanner.show(false, "Create and save a recovery key before locking anything.")
            return
        }
        if (entry.protectedReason != null) {
            resultBanner.show(false, "Protected: ${entry.protectedReason}")
            return
        }

        UiDialogs.confirmLock(this, entry.label, entry.packageName) {
            val result = policy.lockPackage(entry.packageName)
            resultBanner.show(result.success, result.message)
            refreshChrome()
            loadAppsAsync()
        }
    }

    private fun unlock(entry: AppCatalog.AppEntry) {
        UiDialogs.promptForRecoveryKey(this, "Unlock ${entry.label}") {
            val result = policy.unlockPackage(entry.packageName)
            resultBanner.show(result.success, result.message)
            refreshChrome()
            loadAppsAsync()
        }
    }
}
