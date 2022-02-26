package com.mare5x.colorcalendar

import android.content.Context
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.appcompat.widget.ThemedSpinnerAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

typealias ProfileList = MutableList<ProfileEntry>

// TODO don't hold a reference to db because it holds a Context reference
class ProfilesViewModel(private val db: DatabaseHelper) : ViewModel() {
    private val profilesData = MutableLiveData<ProfileList>()

    init {
        fetchProfiles()
    }

    fun getProfiles() = profilesData

    fun fetchProfiles() {
        val profiles = db.queryAllProfiles()
        profilesData.postValue(profiles.toMutableList())
    }

    fun getSize(): Int = profilesData.value?.size ?: 0

    fun getProfile(position: Int): ProfileEntry {
        return profilesData.value!![position]
    }

    fun getProfile(id: Long): ProfileEntry? {
        return profilesData.value!!.find {
            it.id == id
        }
    }

    fun setProfile(profile: ProfileEntry, position: Int) {
        profilesData.value!![position] = profile
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

    fun containsProfile(profile: ProfileEntry): Boolean {
        return getProfile(profile.id) != null
    }

    fun getClosestEntry(profile: ProfileEntry, date: Date): Entry {
        return db.queryClosestEntry(profile, date)
    }
}

class ProfilesViewModelFactory(private val db: DatabaseHelper) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ProfilesViewModel(db) as T
    }
}


class ProfileSpinnerAdapter(
    val context: Context,
    var profiles: List<ProfileEntry>
) : BaseAdapter(), ThemedSpinnerAdapter {

    // Theming support for popupTheme
    // https://developer.android.com/reference/androidx/appcompat/widget/ThemedSpinnerAdapter.Helper
    private val dropDownHelper = ThemedSpinnerAdapter.Helper(context)

    val inflater = LayoutInflater.from(context)

    override fun getCount(): Int = profiles.size

    override fun getItem(position: Int): ProfileEntry {
        return profiles[position]
    }

    override fun getItemId(position: Int): Long = profiles[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view =
            if (convertView == null)
                inflater.inflate(R.layout.profile_spinner_item, parent, false)
            else convertView

        val text = view.findViewById<TextView>(R.id.profileText)
        val profile = getItem(position)
        text.text = profile.name
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view =
            if (convertView == null)
                dropDownHelper.dropDownViewInflater.inflate(R.layout.profile_spinner_dropdown_item, parent, false)
            else convertView

        val profile = getItem(position)

        val text = view.findViewById<TextView>(R.id.profileText)
        text.text = profile.name
        val color = view.findViewById<ColorRect>(R.id.profileColor)
        if (profile.flags hasFlag ProfileFlag.CUSTOM_BANNER) {
            color.setColor(profile.bannerColor ?: profile.prefColor)
        } else {
            color.setColor(profile.prefColor)
        }

        return view
    }

    override fun setDropDownViewTheme(theme: Resources.Theme?) {
        dropDownHelper.dropDownViewTheme = theme
    }

    override fun getDropDownViewTheme(): Resources.Theme? {
        return dropDownHelper.dropDownViewTheme
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
