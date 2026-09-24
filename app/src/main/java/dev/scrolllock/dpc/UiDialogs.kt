package dev.scrolllock.dpc

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

object UiDialogs {
    private fun builder(activity: Activity): AlertDialog.Builder =
        AlertDialog.Builder(activity, R.style.Theme_ScrollLock_Dialog)

    private fun styleDialogButtons(dialog: AlertDialog) {
        val color = dialog.context.getColor(R.color.accent)
        listOf(
            AlertDialog.BUTTON_POSITIVE,
            AlertDialog.BUTTON_NEGATIVE,
            AlertDialog.BUTTON_NEUTRAL
        ).forEach { which ->
            dialog.getButton(which)?.let { button: Button ->
                button.setTextColor(color)
                button.isAllCaps = false
            }
        }
    }

    private fun showStyled(dialog: AlertDialog) {
        dialog.setOnShowListener { styleDialogButtons(dialog) }
        dialog.show()
        // Some OEMs apply button styles after show; force once more.
        styleDialogButtons(dialog)
    }

    fun promptForRecoveryKey(
        activity: Activity,
        title: String,
        onVerified: () -> Unit
    ) {
        val pad = (16 * activity.resources.displayMetrics.density).toInt()
        val padV = (12 * activity.resources.displayMetrics.density).toInt()
        val input = EditText(activity).apply {
            hint = "Recovery key"
            isSingleLine = true
            setTextColor(activity.getColor(R.color.ink))
            setHintTextColor(activity.getColor(R.color.ink_secondary))
            setPadding(pad, padV, pad, padV)
        }

        val dialog = builder(activity)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Verify", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            styleDialogButtons(dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (SettingsStore.verifyRecoveryKey(activity, input.text.toString())) {
                    dialog.dismiss()
                    onVerified()
                } else {
                    input.error = "Incorrect recovery key"
                }
            }
        }
        dialog.show()
        styleDialogButtons(dialog)
    }

    fun showRecoveryKeyCreated(
        activity: Activity,
        key: String,
        onDismiss: () -> Unit = {}
    ) {
        val dialog = builder(activity)
            .setTitle("Save this recovery key")
            .setMessage(
                "This key is shown in full now. Keep it somewhere deliberately inconvenient, or give it to a trusted person. " +
                    "You will need it to unlock apps, disable hardening, or relinquish Device Owner.\n\n$key"
            )
            .setNeutralButton(R.string.copy_to_clipboard, null)
            .setPositiveButton(R.string.i_saved_it) { _, _ -> onDismiss() }
            .setOnCancelListener { onDismiss() }
            .create()

        dialog.setOnShowListener {
            styleDialogButtons(dialog)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val clipboard = activity.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("ScrollLock recovery key", key))
                Toast.makeText(
                    activity,
                    R.string.recovery_key_copied,
                    Toast.LENGTH_SHORT
                ).show()
                // Keep dialog open so the key stays visible after copying.
            }
        }
        dialog.show()
        styleDialogButtons(dialog)
    }

    fun confirmEnableHardening(activity: Activity, onConfirm: () -> Unit) {
        val dialog = builder(activity)
            .setTitle("Enable hardening?")
            .setMessage(
                "Hardening blocks USB debugging, factory reset from Settings, unknown-source installs, safe boot, " +
                    "and creating/switching users.\n\n" +
                    "Important: USB debugging will be disabled, so adb will disconnect. Keep your recovery key. " +
                    "Test locking one nonessential app before enabling this."
            )
            .setPositiveButton("Enable") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .create()
        showStyled(dialog)
    }

    fun showRecoveryMenu(
        activity: Activity,
        onClearAll: () -> Unit,
        onRelinquish: () -> Unit
    ) {
        val options = arrayOf(
            "Clear all app locks, site blocks, and hardening",
            "Relinquish Device Owner and exit ScrollLock"
        )
        val dialog = builder(activity)
            .setTitle("Recovery / exit")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> promptForRecoveryKey(activity, "Clear all policies", onClearAll)
                    1 -> promptForRecoveryKey(activity, "Relinquish Device Owner") {
                        confirmRelinquishOwnership(activity, onRelinquish)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        showStyled(dialog)
    }

    private fun confirmRelinquishOwnership(activity: Activity, onConfirm: () -> Unit) {
        val dialog = builder(activity)
            .setTitle("Final recovery exit")
            .setMessage(
                "ScrollLock will first clear every policy it set, then ask Android to relinquish Device Owner. " +
                    "Android documents non-reset owner removal as a testing/recovery mechanism, so use this only when you really want to remove ScrollLock."
            )
            .setPositiveButton("Relinquish") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .create()
        showStyled(dialog)
    }

    fun promptForText(
        activity: Activity,
        title: String,
        hint: String,
        onSubmit: (String) -> Unit
    ) {
        val pad = (16 * activity.resources.displayMetrics.density).toInt()
        val padV = (12 * activity.resources.displayMetrics.density).toInt()
        val input = EditText(activity).apply {
            this.hint = hint
            isSingleLine = true
            setTextColor(activity.getColor(R.color.ink))
            setHintTextColor(activity.getColor(R.color.ink_secondary))
            setPadding(pad, padV, pad, padV)
        }

        val dialog = builder(activity)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            styleDialogButtons(dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text?.toString().orEmpty().trim()
                if (value.isEmpty()) {
                    input.error = "Enter a site"
                    return@setOnClickListener
                }
                dialog.dismiss()
                onSubmit(value)
            }
        }
        dialog.show()
        styleDialogButtons(dialog)
    }

    fun pickBlockedSite(
        activity: Activity,
        sites: List<String>,
        onPicked: (String) -> Unit
    ) {
        if (sites.isEmpty()) {
            Toast.makeText(activity, R.string.no_blocked_sites, Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = builder(activity)
            .setTitle("Remove blocked site")
            .setItems(sites.toTypedArray()) { _, which -> onPicked(sites[which]) }
            .setNegativeButton("Cancel", null)
            .create()
        showStyled(dialog)
    }

    fun confirmLock(
        activity: Activity,
        label: String,
        packageName: String,
        onConfirm: () -> Unit
    ) {
        val dialog = builder(activity)
            .setTitle("Lock $label?")
            .setMessage(
                "$packageName\n\nAndroid will keep it installed but hide it, suspend it, block uninstall, " +
                    "and disable normal user control. Restoring it requires your ScrollLock recovery key."
            )
            .setPositiveButton("Lock") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .create()
        showStyled(dialog)
    }
}

class ResultBannerController(
    private val banner: TextView,
    private val hideDelayMs: Long = 4500L
) {
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { banner.visibility = View.GONE }

    fun show(success: Boolean, message: String) {
        handler.removeCallbacks(hideRunnable)
        val bg = if (success) R.color.banner_ok_bg else R.color.banner_error_bg
        val fg = if (success) R.color.banner_ok_fg else R.color.banner_error_fg
        applySolidBackground(banner, banner.context.getColor(bg))
        banner.setTextColor(banner.context.getColor(fg))
        banner.text = message
        banner.visibility = View.VISIBLE
        handler.postDelayed(hideRunnable, hideDelayMs)
    }

    fun clear() {
        handler.removeCallbacks(hideRunnable)
        banner.visibility = View.GONE
    }
}

fun applyStatusBanner(banner: TextView, owner: Boolean, hardened: Boolean) {
    val (bg, fg, text) = when {
        !owner -> Triple(
            R.color.banner_warn_bg,
            R.color.banner_warn_fg,
            R.string.status_not_owner
        )
        hardened -> Triple(
            R.color.banner_ok_bg,
            R.color.banner_ok_fg,
            R.string.status_owner_hardened
        )
        else -> Triple(
            R.color.banner_ok_bg,
            R.color.banner_ok_fg,
            R.string.status_owner
        )
    }
    applySolidBackground(banner, banner.context.getColor(bg))
    banner.setTextColor(banner.context.getColor(fg))
    banner.setText(text)
}

fun applySolidBackground(view: View, color: Int) {
    val radius = view.resources.getDimension(R.dimen.radius_sm)
    view.background = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }
}

fun applyWindowInsetsPadding(root: View) {
    root.setOnApplyWindowInsetsListener { view, insets ->
        val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
        val horizontal = view.resources.getDimensionPixelSize(R.dimen.space_lg)
        val topExtra = view.resources.getDimensionPixelSize(R.dimen.space_sm)
        val bottomExtra = view.resources.getDimensionPixelSize(R.dimen.space_sm)
        view.setPadding(horizontal, bars.top + topExtra, horizontal, bars.bottom + bottomExtra)
        insets
    }
}
