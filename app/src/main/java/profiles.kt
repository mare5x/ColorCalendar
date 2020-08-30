import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.mare5x.colorcalendar.ColorGridFragment
import com.mare5x.colorcalendar.DatabaseHelper
import com.mare5x.colorcalendar.ProfileEntry
import com.mare5x.colorcalendar.R
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

    fun getProfile(position: Int): ProfileEntry {
        return profilesData.value!!.get(position)
    }

    fun addProfile(profile: ProfileEntry) {
        profilesData.value?.add(profile)
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