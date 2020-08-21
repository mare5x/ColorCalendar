import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mare5x.colorcalendar.DatabaseHelper
import com.mare5x.colorcalendar.ProfileEntry
import com.mare5x.colorcalendar.R
import kotlinx.coroutines.launch
import java.util.*

typealias ProfileList = MutableList<ProfileEntry>

class ProfilesViewModel(private val db: DatabaseHelper) : ViewModel() {
    private val profilesData = MutableLiveData<ProfileList>(mutableListOf())

    init {
        fetchProfiles()
    }

    fun getProfiles() = profilesData

    private fun fetchProfiles() {
        viewModelScope.launch {
            val profiles = db.queryAllProfiles()
            profilesData.postValue(profiles.toMutableList())
        }
    }

    fun insertProfile(profile: ProfileEntry) {
        viewModelScope.launch {
            profile.creationDate = Date()
            profile.id = db.insertProfile(profile)
            if (profile.id != 1L) {
                profilesData.value!!.add(profile)
                // Hack to notify profilesData observers
                profilesData.postValue(profilesData.value)
            }
        }
    }
}

class ProfilesViewModelFactory(private val db: DatabaseHelper) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return ProfilesViewModel(db) as T
    }
}


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