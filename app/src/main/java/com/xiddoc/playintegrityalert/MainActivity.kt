package com.xiddoc.playintegrityalert

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
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
            if (isModuleActivated()) getString(R.string.status_active) else getString(R.string.status_inactive)
        renderHistory()
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
     * Returns false in a normal process; the Xposed module rewrites this to
     * return true when our own app is in the LSPosed scope, proving the module
     * is loaded and active.
     */
    @Keep
    fun isModuleActivated(): Boolean = false
}
