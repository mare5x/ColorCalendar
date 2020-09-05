package com.mare5x.colorcalendar

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

typealias ProfileList = MutableList<ProfileEntry>

class ProfilesViewModel(private val db: DatabaseHelper) : ViewModel() {
    private val profilesData = MutableLiveData<ProfileList>(mutableListOf())

    init {
        fetchProfiles()
    }

    fun getProfiles() = profilesData

    private fun fetchProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val profiles = db.queryAllProfiles()
            profilesData.postValue(profiles.toMutableList())
        }
    }

    fun getSize() = profilesData.value?.size ?: 0

    fun getProfile(position: Int): ProfileEntry {
        return profilesData.value!!.get(position)
    }

    fun getProfile(id: Long): ProfileEntry? {
        return profilesData.value!!.find {
            it.id == id
        }
    }

    fun addProfile(profile: ProfileEntry) {
        profilesData.value?.add(profile)
    }

    fun getPosition(profile: ProfileEntry): Int {
        return profilesData.value!!.indexOfFirst {
            profile.id == it.id
        }
    }

    fun removeProfile(profile: ProfileEntry) {
        profilesData.value?.remove(profile)
    }
}

class ProfilesViewModelFactory(private val db: DatabaseHelper) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return ProfilesViewModel(db) as T
    }
}


// TODO use BaseAdapter ...
class ProfileSpinnerAdapter(context: Context) :
    ArrayAdapter<ProfileEntry>(context, R.layout.profile_spinner_item, R.id.profileText)
{
    init {
        setDropDownViewResource(R.layout.profile_spinner_dropdown_item)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val text = view.findViewById<TextView>(R.id.profileText)
        val profile = getItem(position) as ProfileEntry
        text.text = profile.name
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent)
        val text = view.findViewById<TextView>(R.id.profileText)
        val profile = getItem(position) as ProfileEntry
        text.text = profile.name
        return view
    }
}


class ProfileFragmentAdapter(
    parent: FragmentActivity,
    var profiles: List<ProfileEntry>
) : FragmentStateAdapter(parent) {

    override fun getItemId(position: Int): Long {
        return profiles[position].id
    }

    override fun containsItem(itemId: Long): Boolean {
        return profiles.any { p -> p.id == itemId }
    }

    override fun getItemCount(): Int {
        return profiles.size
    }

    override fun createFragment(position: Int): Fragment {
        val profile = profiles[position]
        return ColorGridFragment.create(profile)
    }
}


class ProfileDeleteDialog : DialogFragment() {
    private var listener: ProfileDeleteListener? = null

    interface ProfileDeleteListener {
        fun onProfileDelete()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val profileName = requireArguments().getString(PROFILE_NAME_KEY)
            val builder = AlertDialog.Builder(it)
            builder.setMessage("Are you sure you wish to delete '${profileName}'?")
                .setPositiveButton("Delete") { dialog, id ->
                    listener?.onProfileDelete()
                }
                .setNegativeButton("Cancel") { _, _ ->
                    if (showsDialog)
                        dismiss()
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? ProfileDeleteListener
    }

    companion object {
        const val PROFILE_NAME_KEY = "PROFILE_NAME"

        fun create(profile: ProfileEntry): ProfileDeleteDialog {
            val fragment = ProfileDeleteDialog()
            fragment.arguments = Bundle().apply { putString(PROFILE_NAME_KEY, profile.name) }
            return fragment
        }
    }
}