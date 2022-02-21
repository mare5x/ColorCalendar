package com.mare5x.colorcalendar

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.mare5x.colorcalendar.databinding.SettingsActivityBinding
import java.io.File

class SettingsViewModel : ViewModel() {
    val changedSettings = mutableSetOf<String>()
}

class SettingsActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener, ImportDialog.ImportDialogListener {
    private lateinit var binding: SettingsActivityBinding
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Settings"
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.settings_preferences, rootKey)

            val importPreference = findPreference<Preference>("import_setting")!!
            importPreference.setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                activity?.startActivityForResult(intent, IMPORT_CODE)
                true
            }
            val exportPreference = findPreference<Preference>("export_setting")!!
            exportPreference.setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_TITLE, "colorcalendar-backup.db")
                }
                activity?.startActivityForResult(intent, EXPORT_CODE)
                true
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        // If imported, restart the app...
        if (viewModel.changedSettings.contains(SETTING_IMPORTED)) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                // flags = Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        } else {
            setResult(RESULT_OK, Intent().apply {
                putExtra(SETTINGS_CHANGED, viewModel.changedSettings.toTypedArray())
            })
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        viewModel.changedSettings.add(key)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            EXPORT_CODE -> {
                if (resultCode == RESULT_OK) {
                    val uri = data?.data
                    exportDatabase(uri)
                }
            }
            IMPORT_CODE -> {
                if (resultCode == RESULT_OK) {
                    val uri = data?.data
                    startImportDatabase(uri)
                }
            }
        }
    }

    private fun exportDatabase(uri: Uri?) {
        if (uri == null) {
            Toast.makeText(applicationContext, "Failed to export!", Toast.LENGTH_SHORT).show()
            return
        }

        val db = DatabaseHelper(applicationContext)
        db.close()  // Commit changes
        val src = File(db.writableDatabase.path)
        val out = contentResolver.openOutputStream(uri)
        if (out != null) {
            src.inputStream().copyTo(out)
            out.flush()
            out.close()

            Toast.makeText(applicationContext, "Exported successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startImportDatabase(uri: Uri?) {
        uri ?: return

        // Copy file to internal storage
        applicationContext.openFileOutput("import.db", Context.MODE_PRIVATE).use { dst ->
            val src = contentResolver.openInputStream(uri)
            src?.copyTo(dst)
            dst.flush()
            dst.close()
        }
        val importPath = applicationContext.getFileStreamPath("import.db").absolutePath

        if (isValidDatabaseFile(importPath)) {
            // TODO I'd prefere for this to be an activity instead
            val dialog = ImportDialog.create(importPath)
            dialog.show(supportFragmentManager, "importDialog")
        } else {
            Toast.makeText(applicationContext, "Import error!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onImport() {
        // Restart app to update state ...
        viewModel.changedSettings.add(SETTING_IMPORTED)

//        finish()
//        startActivity(getIntent())

        // profilesViewModel.fetchProfiles()
        // viewModelStore.clear()
        // recreate()
    }

    companion object {
        const val SETTINGS_CODE = 1
        const val SETTINGS_CHANGED = "settings_changed"

        const val SETTING_CALENDAR_ROWS = "calendar_rows"
        const val SETTING_BADGE_ENABLED = "badge_enabled"
        const val SETTING_IMPORTED = "imported"

        private const val EXPORT_CODE = 69
        private const val IMPORT_CODE = 70
    }
}