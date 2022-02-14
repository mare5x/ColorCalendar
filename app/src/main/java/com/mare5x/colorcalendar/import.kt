package com.mare5x.colorcalendar

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File


// Importing process: copy selected backup file to internal storage, extract new profiles and entries.
class ImportViewModel(importPath: String, db: DatabaseHelper, context: Context) : ViewModel() {
    var profilesToImport: Array<ProfileEntry>
    var entriesToImport: Array<List<Entry>>

    init {
        // Open database with the helper class to support opening old database versions.
        val importDBHelper = DatabaseHelper(context, importPath)
        importDBHelper.setWriteAheadLoggingEnabled(false)
        val importDB = importDBHelper.readableDatabase

        profilesToImport = queryAllProfiles(importDB)
        entriesToImport = Array(profilesToImport.size) { emptyList() }
        profilesToImport.forEachIndexed { idx, profile ->
            // Profile comparison by name only
            val profiles = db.queryProfileName(profile.name)
            when {
                profiles.size >= 2 -> {
                    profile.id = -2L  // Name conflict warning
                }
                profiles.isEmpty() -> {
                    entriesToImport[idx] = queryAllEntries(importDB, profile).toList()
                    profile.id = -1L
                }
                else -> {
                    val srcEntries = db.queryAllEntries(profiles[0])
                    val entries = queryAllEntries(importDB, profile).filter { entry ->
                        srcEntries.find { e ->
                            e.value == entry.value && e.date == entry.date && e.color == entry.color
                        } == null
                    }
                    entriesToImport[idx] = entries
                    profile.id = profiles[0].id
                }
            }
        }

        importDBHelper.close()
        File(importPath).delete()
    }
}


class ImportViewModelFactory(
    private val importPath: String,
    private val db: DatabaseHelper,
    private val ctx: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ImportViewModel(importPath, db, ctx) as T
    }
}


class ImportAdapter(
    private val profiles: Array<ProfileEntry>,
    private val entries: Array<List<Entry>>
) : RecyclerView.Adapter<ImportAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val changesText: TextView = view.findViewById(R.id.importChangesText)
        val profileColor: ColorRect = view.findViewById(R.id.profileColor)
        val profileText: TextView = view.findViewById(R.id.profileText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.importer_row_item, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.profileText.text = profiles[position].name
        holder.profileColor.setColor(profiles[position].let {
            if (it.flags hasFlag ProfileFlag.CUSTOM_BANNER) it.bannerColor ?: it.prefColor
            else it.prefColor
        })
        holder.changesText.text = entries[position].size.toString()

        // Name conflict warning
        when {
            profiles[position].id == -2L -> {
                holder.changesText.text = holder.itemView.context.getString(R.string.import_name_conflict)
                holder.profileText.setTextColor(Color.RED)
                holder.changesText.setTextColor(Color.RED)
            }
            entries[position].isNotEmpty() -> {
                holder.profileText.setTextColor(Color.GREEN)
                holder.changesText.setTextColor(Color.GREEN)
            }
            else -> {
                holder.profileText.setTextColor(Color.GRAY)
                holder.changesText.setTextColor(Color.GRAY)
            }
        }
    }

    override fun getItemCount() = profiles.size
}


class ImportDialog : DialogFragment() {
    interface ImportDialogListener {
        fun onImport()
    }

    private lateinit var adapter: ImportAdapter
    private val mainModel: MainViewModel by activityViewModels()
    private val viewModel: ImportViewModel by viewModels {
        val importPath = requireArguments().getString(IMPORT_PATH_KEY)!!
        ImportViewModelFactory(importPath, mainModel.db, requireContext())
    }

    private var listener: ImportDialogListener? = null


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_importer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ImportAdapter(viewModel.profilesToImport, viewModel.entriesToImport)
        val importList = view.findViewById<RecyclerView>(R.id.importList)
        importList.adapter = adapter
        importList.layoutManager = LinearLayoutManager(context)

        val cancelButton = view.findViewById<Button>(R.id.cancelButton)
        cancelButton.setOnClickListener {
            if (showsDialog)
                dismiss()
        }

        val confirmButton = view.findViewById<Button>(R.id.confirmButton)
        confirmButton.setOnClickListener {
            val db = mainModel.db
            viewModel.profilesToImport.forEachIndexed { index, profile ->
                if (profile.id == -1L) {
                    profile.id = db.insertProfile(profile)
                }
                if (profile.id >= 0) {
                    val entries = viewModel.entriesToImport[index]
                    if (entries.isNotEmpty()) {
                        entries.forEach { it.profile = profile }
                        db.insertEntries(entries)
                    }
                }
            }

            if (showsDialog)
                dismiss()

            listener?.onImport()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? ImportDialogListener
    }

    companion object {
        const val IMPORT_PATH_KEY = "com.mare5x.colorcalendar.import_path_key"

        fun create(importPath: String) : ImportDialog {
            val frag = ImportDialog()
            frag.arguments = Bundle().apply { putString(IMPORT_PATH_KEY, importPath) }
            return frag
        }
    }
}
