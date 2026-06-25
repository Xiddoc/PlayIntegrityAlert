package com.xiddoc.playintegrityalert

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestNotificationPermissionIfNeeded()

        val chooseButton = findViewById<Button>(R.id.btn_choose)
        val watchAll = findViewById<SwitchCompat>(R.id.watch_all)
        watchAll.isChecked = Config.isWatchAll(this)
        chooseButton.isEnabled = !watchAll.isChecked
        watchAll.setOnCheckedChangeListener { _, checked ->
            Config.setWatchAll(this, checked)
            chooseButton.isEnabled = !checked
        }
        chooseButton.setOnClickListener { startActivity(Intent(this, AppPickerActivity::class.java)) }

        findViewById<Button>(R.id.btn_restart_play_store).setOnClickListener { restartPlayStore() }

        findViewById<Button>(R.id.btn_test).setOnClickListener {
            // Self-broadcast a fake event so the notification path can be verified.
            sendBroadcast(
                Intent(Constants.ACTION_DETECTED).apply {
                    component = ComponentName(this@MainActivity, DetectionReceiver::class.java)
                    putExtra(Constants.EXTRA_PACKAGE, packageName)
                    putExtra(Constants.EXTRA_DETAIL, "test event")
                    putExtra(Constants.EXTRA_TIMESTAMP, System.currentTimeMillis())
                },
            )
        }

        findViewById<Button>(R.id.btn_clear).setOnClickListener {
            DetectionStore.clear(this)
            renderHistory()
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.status).text =
            statusText(isModuleActivated(), Config.hookSeenAt(this))
        renderHistory()
    }

    /**
     * Status label, split out for testing. Three states:
     *  - module loaded *and* the hook has run inside Play Store ([hookSeenAt] > 0):
     *    we're watching. The heartbeat is what proves the Play Store hook is live —
     *    a signal that doesn't depend on our app being in any scope.
     *  - module loaded only: enabled (LSPosed auto-scopes a legacy module to
     *    itself), but Play Store hasn't been hit through the hook yet.
     *  - otherwise: not active. Gating "watching" on [moduleLoaded] means the status
     *    correctly drops back here if the module is later disabled, rather than
     *    showing a stale "watching" from an old heartbeat.
     */
    internal fun statusText(moduleLoaded: Boolean, hookSeenAt: Long): String = when {
        moduleLoaded && hookSeenAt > 0L -> getString(R.string.status_watching, formatTime(hookSeenAt))
        moduleLoaded -> getString(R.string.status_loaded)
        else -> getString(R.string.status_inactive)
    }

    private fun formatTime(epochMillis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMillis))

    /**
     * Restarts Play Store so the module (re)loads into its process — needed after
     * enabling the module or changing its scope. With root we force-stop it directly
     * via [rootShell]; the next launch reloads the hook. Without root (a normal app
     * can't force-stop another) we fall back to Play Store's App info screen so the
     * user can tap *Force stop* themselves.
     */
    private fun restartPlayStore() {
        if (rootShell.isAvailable() &&
            rootShell.exec("am force-stop ${Constants.VENDING_PACKAGE}")
        ) {
            Toast.makeText(this, R.string.restart_play_store_done, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", Constants.VENDING_PACKAGE, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        Toast.makeText(this, R.string.restart_play_store_hint, Toast.LENGTH_LONG).show()
    }

    private fun renderHistory() {
        val view = findViewById<TextView>(R.id.history)
        val detections = DetectionStore.list(this)
        if (detections.isEmpty()) {
            view.text = getString(R.string.history_empty)
            return
        }
        val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
        view.text = detections.joinToString("\n\n") { d ->
            "${df.format(Date(d.timestamp))}\n${d.label} (${d.packageName})\n→ ${d.detail}"
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    /**
     * Returns false in a normal process; the module rewrites it to return true once
     * it's loaded into our own process. LSPosed auto-scopes a *legacy* module to
     * itself (you can't — and needn't — tick your own module), so this flips without
     * any manual scoping. It only proves the module is loaded here, though; the
     * authoritative "are we watching Play Store" signal is [Config.hookSeenAt].
     */
    @Keep
    fun isModuleActivated(): Boolean = false

    companion object {
        /**
         * Root shell used to force-stop Play Store. Swappable so unit tests can drive
         * both the rooted and unrooted paths without spawning a real shell; defaults
         * to the libsu-backed [LibsuRootShell].
         */
        internal var rootShell: RootShell = LibsuRootShell
    }
}
