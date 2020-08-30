package com.mare5x.colorcalendar

import ProfileFragmentAdapter
import ProfileSpinnerAdapter
import ProfilesViewModel
import ProfilesViewModelFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
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


class MainViewModel(private val db: DatabaseHelper) : ViewModel() {
    private val currentProfile = MutableLiveData<ProfileEntry>()
    private val insertedProfile = MutableLiveData<ProfileEntry>()
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

    fun getInsertedEntry() = insertedEntry
    fun insertEntry(entry: Entry) {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = currentProfile.value!!
            entry.profile = profile
            entry.date = Date()
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


class MainActivity : AppCompatActivity(), ColorPickerDialogFragment.ColorPickerListener, ProfileEditorDialogFragment.ProfileEditorListener {

    private lateinit var db: DatabaseHelper
    private lateinit var viewPager: ViewPager2

    private val mainViewModel: MainViewModel by viewModels { MainViewModelFactory(db) }
    private val profilesViewModel: ProfilesViewModel by viewModels { ProfilesViewModelFactory(db) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        db = DatabaseHelper(this)

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { view ->
            val dialog = ColorPickerDialogFragment()
            dialog.show(supportFragmentManager, "colorPicker")
        }

        // Set up a spinner instead of the title in the app bar.
        // The spinner is used to select the currently displayed profile.
        val actionBar = supportActionBar
        actionBar?.setCustomView(R.layout.toolbar_profile_spinner)
        actionBar?.setDisplayShowCustomEnabled(true)
        actionBar?.setDisplayShowTitleEnabled(false)

        val profileSpinner = findViewById<Spinner>(R.id.profileSpinner)

        val profileSpinnerAdapter = ProfileSpinnerAdapter(actionBar?.themedContext ?: this)
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
                changeProfile(profilesViewModel.getProfile(position))
            }
        })

        mainViewModel.getInsertedProfile().observe(this) { profile ->
            profilesViewModel.addProfile(profile)
            profileSpinnerAdapter.add(profile)
            changeProfile(profile)
        }

        profilesViewModel.getProfiles().observe(this) { profiles ->
            if (profiles.isNotEmpty()) {
                profileSpinnerAdapter.clear()
                profileSpinnerAdapter.addAll(profiles)
                profileSpinnerAdapter.notifyDataSetChanged()

                profileFragmentAdapter.profiles = profiles
                profileFragmentAdapter.notifyDataSetChanged()
            }
        }

        mainViewModel.getCurrentProfile().observe(this) { profile ->
            onProfileChanged(profile)

            profileSpinner.setSelection(profileSpinnerAdapter.getPosition(profile))
            viewPager.setCurrentItem(profileFragmentAdapter.profiles.indexOf(profile))
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
            R.id.action_profile -> {
                val dialog = ProfileEditorDialogFragment()
                dialog.show(supportFragmentManager, "profileEditor")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onColorConfirm(value: Float) {
        mainViewModel.insertEntry(Entry(value = value))
    }

    override fun onColorCancel(value: Float) { }

    override fun onProfileConfirm(name: String, minColor: Int, maxColor: Int, prefColor: Int) {
        mainViewModel.insertProfile(ProfileEntry(
            name = name,
            minColor = minColor,
            maxColor = maxColor,
            prefColor = prefColor
        ))
    }

    override fun onProfileColorChanged(color: Int) = setUIColor(color)

    fun setUIColor(color: Int) {
        supportActionBar?.setBackgroundDrawable(ColorDrawable(color))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val hsv = floatArrayOf(0f, 0f, 0f)
            Color.colorToHSV(color, hsv)
            hsv[2] *= 0.8f
            window.statusBarColor = Color.HSVToColor(hsv)
        }
    }

    override fun onProfileDismiss() {
        // Restore color
        setUIColor(mainViewModel.getCurrentProfile().value!!.prefColor)
    }

    fun changeProfile(profile: ProfileEntry) {
        mainViewModel.setCurrentProfile(profile)
    }

    fun onProfileChanged(profile: ProfileEntry) {
        setUIColor(profile.prefColor)
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}