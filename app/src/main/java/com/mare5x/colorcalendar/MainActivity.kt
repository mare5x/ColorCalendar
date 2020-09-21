package com.mare5x.colorcalendar

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.Spinner
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.max

// NOTE: for constraint layout see the developer guide at:
// https://developer.android.com/reference/androidx/constraintlayout/widget/ConstraintLayout
// NOTE: fragment's parent must implement interface ... Using function callbacks doesn't work because of configuration changes ...!
// NOTE: Dialog's use wrap_content for layout width and height ...

class MainViewModel(val db: DatabaseHelper) : ViewModel() {
    private val currentProfile = MutableLiveData<ProfileEntry>()
    private val insertedProfile = MutableLiveData<ProfileEntry>()
    private val updatedProfile = MutableLiveData<ProfileEntry>()
    private val deletedProfile = MutableLiveData<ProfileEntry>()
    private val insertedEntry = MutableLiveData<Entry>()

    fun getCurrentProfile() = currentProfile
    fun setCurrentProfile(profile: ProfileEntry) {
        currentProfile.postValue(profile)
    }

    fun getInsertedProfile() = insertedProfile
    fun insertProfile(profile: ProfileEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            profile.creationDate = Date()
            profile.id = db.insertProfile(profile)
            insertedProfile.postValue(profile)
        }
    }
    fun getUpdatedProfile() = updatedProfile
    fun updateProfile(profile: ProfileEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            db.updateProfile(profile)
            updatedProfile.postValue(profile)
        }
    }
    fun getDeletedProfile() = deletedProfile
    fun deleteProfile(profile: ProfileEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteProfile(profile)
            deletedProfile.postValue(profile)
        }
    }

    fun getInsertedEntry() = insertedEntry
    fun insertEntry(entry: Entry) {
        viewModelScope.launch(Dispatchers.IO) {
            entry.id = db.insertEntry(entry)
            insertedEntry.postValue(entry)
        }
    }
}

class MainViewModelFactory(private val db: DatabaseHelper) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return MainViewModel(db) as T
    }
}


class MainActivity : AppCompatActivity(), EntryEditorDialog.EntryEditorListener, ProfileDeleteDialog.ProfileDeleteListener {

    private lateinit var db: DatabaseHelper
    private lateinit var viewPager: ViewPager2

    private val mainViewModel: MainViewModel by viewModels { MainViewModelFactory(db) }
    private val profilesViewModel: ProfilesViewModel by viewModels { ProfilesViewModelFactory(db) }

    private var currentProfile: ProfileEntry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        db = DatabaseHelper(this)

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            val dialog = EntryEditorDialog.create(currentProfile!!)
            dialog.show(supportFragmentManager, "entryEditor")
        }

        // Set up a spinner instead of the title in the app bar.
        // The spinner is used to select the currently displayed profile.
        val actionBar = supportActionBar
        actionBar?.setCustomView(R.layout.toolbar_profile_spinner)
        actionBar?.setDisplayShowCustomEnabled(true)
        actionBar?.setDisplayShowTitleEnabled(false)

        val profileSpinner = findViewById<Spinner>(R.id.profileSpinner)

        val profileSpinnerAdapter = ProfileSpinnerAdapter(actionBar?.themedContext ?: this, profilesViewModel.getProfiles().value ?: emptyList())
        // https://developer.android.com/reference/android/widget/Spinner#setAdapter(android.widget.SpinnerAdapter)
        // Popup theme!
        profileSpinner.adapter = profileSpinnerAdapter
        profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>) {}

            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                changeProfile(parent.getItemAtPosition(position) as ProfileEntry)
            }
        }

        // Set up swipe page viewer for swiping between profiles.
        viewPager = findViewById(R.id.pager)
        val profileFragmentAdapter = ProfileFragmentAdapter(this,
            profilesViewModel.getProfiles().value ?: emptyList())
        viewPager.adapter = profileFragmentAdapter
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position < profilesViewModel.getSize())
                    changeProfile(profilesViewModel.getProfile(position))
            }
        })

        // NOTE: live data observe functions get called also after configuration changes.
        // Make sure not to repeat operations!
        mainViewModel.getInsertedProfile().observe(this) { profile ->
            if (!profilesViewModel.containsProfile(profile)) {
                profilesViewModel.addProfile(profile)
                val position = profilesViewModel.getPosition(profile)
                profileFragmentAdapter.notifyItemInserted(position)
                profileSpinnerAdapter.notifyDataSetChanged()

                changeProfile(profile)
            }
        }

        mainViewModel.getUpdatedProfile().observe(this) { profile ->
            val position = profilesViewModel.getPosition(profile)
            if (position != -1 && profilesViewModel.getProfile(position) != profile) {
                profilesViewModel.setProfile(profile, position)
                profileFragmentAdapter.notifyItemChanged(position)
                profileSpinnerAdapter.notifyDataSetChanged()

                changeProfile(profile)
            }
        }

        mainViewModel.getDeletedProfile().observe(this) { profile ->
            val index = profilesViewModel.getPosition(profile)
            if (index != -1) {
                profilesViewModel.removeProfile(profile)
                profileFragmentAdapter.notifyItemRemoved(index)
                profileSpinnerAdapter.notifyDataSetChanged()

                if (profilesViewModel.getSize() > 0) {
                    val newProfile = profilesViewModel.getProfile(max(0, index - 1))
                    changeProfile(newProfile)
                } else {
                    // Last profile has just been deleted. Prompt for a new profile.
                    forcePromptNewProfile()
                }
            }
        }

        profilesViewModel.getProfiles().observe(this) { profiles ->
            profileSpinnerAdapter.profiles = profiles
            profileSpinnerAdapter.notifyDataSetChanged()

            profileFragmentAdapter.profiles = profiles
            profileFragmentAdapter.notifyDataSetChanged()

            if (profiles.isEmpty()) {
                forcePromptNewProfile()
            }
        }

        mainViewModel.getCurrentProfile().observe(this) { profile ->
            currentProfile = profile
            setUIColor(profile.prefColor)

            val position = profilesViewModel.getPosition(profile)
            profileSpinner.setSelection(position)
            viewPager.currentItem = position
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            R.id.action_create_profile -> {
                val intent = Intent(this, ProfileEditorActivity::class.java)
                startActivityForResult(intent, PROFILE_EDITOR_CODE)
                true
            }
            R.id.action_delete_profile -> {
                val dialog = ProfileDeleteDialog.create(currentProfile!!)
                dialog.show(supportFragmentManager, "profileDelete")
                true
            }
            R.id.action_edit_profile -> {
                val profile = currentProfile
                val intent = Intent(this, ProfileEditorActivity::class.java)
                if (profile != null) {
                    intent.run {
                        putExtra(ProfileEditorActivity.PROFILE_ID_KEY, profile.id)
                        putExtra(ProfileEditorActivity.PROFILE_NAME_KEY, profile.name)
                        putExtra(ProfileEditorActivity.PROFILE_MIN_COLOR_KEY, profile.minColor)
                        putExtra(ProfileEditorActivity.PROFILE_MAX_COLOR_KEY, profile.maxColor)
                        putExtra(ProfileEditorActivity.PROFILE_PREF_COLOR_KEY, profile.prefColor)
                        putExtra(ProfileEditorActivity.PROFILE_CREATION_DATE_KEY, profile.creationDate.time)
                    }
                }
                startActivityForResult(intent, PROFILE_EDITOR_CODE)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PROFILE_EDITOR_CODE) {
            if (resultCode == RESULT_OK) {
                val bundle = data?.extras
                if (bundle != null) {
                    val profile = ProfileEntry()
                    profile.id = bundle.getLong(ProfileEditorActivity.PROFILE_ID_KEY, -1L)
                    profile.name = bundle.getString(ProfileEditorActivity.PROFILE_NAME_KEY).toString()
                    profile.minColor = bundle.getInt(ProfileEditorActivity.PROFILE_MIN_COLOR_KEY)
                    profile.maxColor = bundle.getInt(ProfileEditorActivity.PROFILE_MAX_COLOR_KEY)
                    profile.prefColor = bundle.getInt(ProfileEditorActivity.PROFILE_PREF_COLOR_KEY)
                    profile.creationDate = bundle.getLong(ProfileEditorActivity.PROFILE_CREATION_DATE_KEY, -1L).let { date ->
                        if (date < 0) Date()
                        else Date(date)
                    }
                    if (profile.id == -1L) {
                        mainViewModel.insertProfile(profile)
                    } else {
                        mainViewModel.updateProfile(profile)
                    }
                }
            }
        }
    }

    private fun setUIColor(color: Int) {
        supportActionBar?.setBackgroundDrawable(ColorDrawable(color))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val hsv = floatArrayOf(0f, 0f, 0f)
            Color.colorToHSV(color, hsv)
            hsv[2] *= 0.8f
            window.statusBarColor = Color.HSVToColor(hsv)
        }
    }

    fun changeProfile(profile: ProfileEntry) {
        mainViewModel.setCurrentProfile(profile)
    }

    private fun forcePromptNewProfile() {
        val intent = Intent(this, ProfileEditorActivity::class.java)
        intent.putExtra(ProfileEditorActivity.FORCE_SELECTION_KEY, true)
        startActivityForResult(intent, PROFILE_EDITOR_CODE)
    }

    override fun onProfileDelete() {
        mainViewModel.deleteProfile(currentProfile!!)
    }

    override fun onEntryCancel() {}

    override fun onEntryConfirm(value: Float, hourOfDay: Int, minute: Int) {
        // Entries added using the floating action button always refer to today's date.
        val t = Calendar.getInstance().apply {
            set(Calendar.MINUTE, minute)
            set(Calendar.HOUR_OF_DAY, hourOfDay)
        }
        val entry = Entry(
            profile = currentProfile,
            value = value,
            date = t.time
        )
        mainViewModel.insertEntry(entry)
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PROFILE_EDITOR_CODE = 42
    }
}