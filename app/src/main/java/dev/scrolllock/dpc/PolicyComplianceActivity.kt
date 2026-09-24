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
