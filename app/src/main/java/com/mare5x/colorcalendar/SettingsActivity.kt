package com.mare5x.colorcalendar

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mare5x.colorcalendar.databinding.SettingsActivityBinding
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


fun <T> Array<T>.swap(i: Int, j: Int) {
    val t = this[i]
    this[i] = this[j]
    this[j] = t
}

class ProfileOrderViewModel(context: Context) : ViewModel() {
    lateinit var profiles: Array<ProfileEntry>

    init {
        viewModelScope.launch {
            val db = DatabaseHelper(context)
            profiles = db.queryAllProfiles()
            db.close()
        }
    }
}

class SettingsViewModel : ViewModel() {
    val changedSettings = mutableSetOf<String>()
}


class ProfileOrderViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ProfileOrderViewModel(context) as T
    }
}


class ProfileOrderAdapter(
    private val profiles: Array<ProfileEntry>
) : RecyclerView.Adapter<ProfileOrderAdapter.ViewHolder>() {

    var orderChanged = false

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val profileColor: ColorRect = view.findViewById(R.id.profileColor)
        val profileText: TextView = view.findViewById(R.id.profileText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.profile_order_item, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.profileText.text = profiles[position].name
        holder.profileColor.setColor(profiles[position].let {
            if (it.flags hasFlag ProfileFlag.CUSTOM_BANNER) it.bannerColor ?: it.prefColor
            else it.prefColor
        })
    }

    override fun getItemCount() = profiles.size

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                profiles.swap(i, i + 1)
            }
        } else {
            for (i in fromPosition downTo (toPosition + 1)) {
                profiles.swap(i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        orderChanged = true
    }
}

class ProfileOrderDialog : DialogFragment() {
    interface ProfileOrderListener {
        fun onReorderProfiles(profiles: Array<ProfileEntry>)
    }

    private lateinit var adapter: ProfileOrderAdapter
    private val viewModel: ProfileOrderViewModel by viewModels {
        ProfileOrderViewModelFactory(requireContext())
    }

    private var listener: ProfileOrderListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_profile_order, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ProfileOrderAdapter(viewModel.profiles)
        val importList = view.findViewById<RecyclerView>(R.id.profileList)
        importList.adapter = adapter
        importList.layoutManager = LinearLayoutManager(context)
        importList.setHasFixedSize(true)

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.moveItem(viewHolder.adapterPosition, target.adapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        touchHelper.attachToRecyclerView(importList)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? ProfileOrderListener
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (adapter.orderChanged) {
            listener?.onReorderProfiles(viewModel.profiles)
        }
        super.onDismiss(dialog)
    }

    companion object {
        fun create(): ProfileOrderDialog {
            return ProfileOrderDialog()
        }
    }
}


class SettingsActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener, ImportDialog.ImportDialogListener, ProfileOrderDialog.ProfileOrderListener {
    private lateinit var binding: SettingsActivityBinding
    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var db: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        db = DatabaseHelper(applicationContext)

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

            val profileOrderPreference = findPreference<Preference>("profile_order_setting")!!
            profileOrderPreference.setOnPreferenceClickListener {
                ProfileOrderDialog.create().show(childFragmentManager, "profile_order")
                true
            }

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
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_TITLE, "colorcalendar_${timestamp}.db")
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
        // If imported or profile order changed, restart the app...
        if (viewModel.changedSettings.any { it == SETTING_IMPORTED || it == SETTING_PROFILE_ORDER }) {
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

    override fun onReorderProfiles(profiles: Array<ProfileEntry>) {
        putProfileOrder(applicationContext, profiles.map { it.id })
        viewModel.changedSettings.add(SETTING_PROFILE_ORDER)
    }

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }

    companion object {
        const val SETTINGS_CODE = 1
        const val SETTINGS_CHANGED = "settings_changed"

        const val SETTING_CALENDAR_ROWS = "calendar_rows"
        const val SETTING_BADGE_ENABLED = "badge_enabled"
        const val SETTING_IMPORTED = "imported"

        const val SETTING_PROFILE_ORDER = "profile_order"

        private const val EXPORT_CODE = 69
        private const val IMPORT_CODE = 70
    }
}