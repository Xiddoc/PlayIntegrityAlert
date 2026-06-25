package com.xiddoc.playintegrityalert

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

/**
 * Lets the user pick which apps to be alerted about when [Config.isWatchAll] is
 * off. The chosen package set is persisted via [Config] and read by the hook
 * through [WatchList].
 */
class AppPickerActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private var packages: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)
        title = getString(R.string.picker_title)
        listView = findViewById(R.id.list)
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        loadApps()
    }

    private fun loadApps() {
        runInBackground {
            val pm = packageManager
            val apps = pm.getInstalledApplications(0)
                .filter { it.packageName != Constants.OWN_PACKAGE }
                .map { it.packageName to pm.getApplicationLabel(it).toString() }
                .sortedBy { it.second.lowercase() }
            packages = apps.map { it.first }
            val labels = apps.map { "${it.second}\n${it.first}" }
            val watched = Config.watched(this)

            runOnUiThread {
                listView.adapter = ArrayAdapter(
                    this,
                    R.layout.row_app,
                    labels,
                )
                packages.forEachIndexed { i, pkg ->
                    listView.setItemChecked(i, pkg in watched)
                }
                listView.setOnItemClickListener { _, _, _, _ -> save() }
            }
        }
    }

    private fun save() {
        val checked = listView.checkedItemPositions
        val selected = packages.filterIndexed { i, _ -> checked.get(i) }.toSet()
        Config.setWatched(this, selected)
    }

    companion object {
        /**
         * Runs the package-list scan off the UI thread. Swappable so unit tests can
         * run it synchronously; defaults to a background thread.
         */
        internal var runInBackground: (() -> Unit) -> Unit = ::spawnThread

        internal fun spawnThread(block: () -> Unit) {
            Thread { block() }.start()
        }
    }
}
