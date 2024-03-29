package com.mare5x.colorcalendar

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.math.MathUtils.clamp
import androidx.core.view.MenuCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mare5x.colorcalendar.SettingsActivity.Companion.SETTINGS_CHANGED
import com.mare5x.colorcalendar.SettingsActivity.Companion.SETTINGS_CODE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import kotlin.math.abs
import kotlin.math.max

/** https://medium.com/androiddevelopers/livedata-with-snackbar-navigation-and-other-events-the-singleliveevent-case-ac2622673150
 * Used as a wrapper for data that is exposed via a LiveData that represents an event.
 * */
open class Event<out T>(private val content: T) {

    var hasBeenHandled = false

    /**
     * Returns the content and prevents its use again.
     */
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    /**
     * Returns the content, even if it's already been handled.
     */
    fun peekContent(): T = content
}


class MainViewModel(val db: DatabaseHelper) : ViewModel() {
    private val currentProfile = MutableLiveData<ProfileEntry>()
    private val insertedProfile = MutableLiveData<ProfileEntry>()
    private val updatedProfile = MutableLiveData<ProfileEntry>()
    private val deletedProfile = MutableLiveData<ProfileEntry>()
    private val insertedEntry = MutableLiveData<Entry>()

    // Single shot event to handle communication between ColorGridFragment and
    // the main activity.
    private val showScrollFabEvent = MutableLiveData<Event<Boolean>>()
    private val scrollFabClickedEvent = MutableLiveData<Event<Long>>()

    private val settingsChanged = MutableLiveData<Array<String>>()

    fun getCurrentProfile() = currentProfile
    fun setCurrentProfile(profile: ProfileEntry) {
        currentProfile.postValue(profile)
    }

    fun getInsertedProfile() = insertedProfile
    fun insertProfile(profile: ProfileEntry) {
        insertedProfile.postValue(profile)
    }
    fun getUpdatedProfile() = updatedProfile
    fun updateProfile(profile: ProfileEntry) {
        updatedProfile.postValue(profile)
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

    fun getShowScrollFabEvent() = showScrollFabEvent
    fun setShowScrollFab(show: Boolean) {
        showScrollFabEvent.value = Event(show)
    }
    fun getScrollFabClickedEvent() = scrollFabClickedEvent
    fun scrollFabClicked() {
        scrollFabClickedEvent.value = Event(currentProfile.value?.id ?: -1L)
    }

    fun getSettingsChanged() = settingsChanged
    fun setSettingsChanged(settings: Array<String>) {
        settingsChanged.postValue(settings)
    }
}

class MainViewModelFactory(private val db: DatabaseHelper) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(db) as T
    }
}


class MainActivity : AppCompatActivity(),
    EntryEditorDialog.EntryEditorListener,
    ProfileDeleteDialog.ProfileDeleteListener
{
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
                if (position < profilesViewModel.getSize()) {
                    changeProfile(profilesViewModel.getProfile(position))
                }

                mainViewModel.setShowScrollFab(false)
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

        findViewById<FloatingActionButton>(R.id.entry_fab).setOnClickListener {
            val e = profilesViewModel.getClosestEntry(currentProfile!!, Date())
            val dialog = EntryEditorDialog.create(currentProfile!!, if (e.id < 0) null else e)
            dialog.show(supportFragmentManager, "entryEditor")
        }
        val scrollFab = findViewById<FloatingActionButton>(R.id.scroll_fab)
        scrollFab.setOnClickListener {
            mainViewModel.scrollFabClicked()
        }
        mainViewModel.getShowScrollFabEvent().observe(this) { showEvent ->
            showEvent.getContentIfNotHandled()?.let { show ->
                if (show) scrollFab.show()
                else scrollFab.hide()
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
            updateUIColor()

            val position = profilesViewModel.getPosition(profile)
            profileSpinner.setSelection(position)
            viewPager.currentItem = position
        }
    }

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        MenuCompat.setGroupDividerEnabled(menu, true)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                db.close()  // commit (possible import/export)
                val intent = Intent(this, SettingsActivity::class.java)
                startActivityForResult(intent, SETTINGS_CODE)
                true
            }
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
        when (requestCode) {
            PROFILE_EDITOR_CODE -> {
                if (resultCode == RESULT_OK) {
                    val bundle = data?.extras
                    if (bundle != null) {
                        val profileId = bundle.getLong(ProfileEditorActivity.PROFILE_ID_KEY)
                        lifecycleScope.launch(Dispatchers.IO) {
                            val profile = db.queryProfile(profileId)
                            when (bundle.getString(ProfileEditorActivity.PROFILE_EDIT_MSG_KEY)) {
                                "created" -> mainViewModel.insertProfile(profile)
                                "updated" -> mainViewModel.updateProfile(profile)
                            }
                        }
                    }
                }
            }
            SETTINGS_CODE -> {
                if (resultCode == RESULT_OK) {
                    data?.extras?.getStringArray(SETTINGS_CHANGED)?.let {
                        mainViewModel.setSettingsChanged(it)
                    }
                }
            }
        }
    }

    private fun updateUIColor() {
        currentProfile?.let {
            if (it.flags hasFlag ProfileFlag.CUSTOM_BANNER) {
                setUIColor(it.bannerColor ?: it.prefColor)
            } else {
                setUIColor(it.prefColor)
            }
        }
    }

    private fun setUIColor(color: Int) {
        supportActionBar?.setBackgroundDrawable(ColorDrawable(color))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = dimColor(color, 0.8f)
        }
    }

    fun changeProfile(profile: ProfileEntry) {
        mainViewModel.setCurrentProfile(profile)
    }

    private fun forcePromptNewProfile() {
        // The profile editor will 'restart' the app...
        val intent = Intent(this, ProfileEditorActivity::class.java)
        intent.putExtra(ProfileEditorActivity.FORCE_SELECTION_KEY, true)
        startActivity(intent)
    }

    override fun onProfileDelete() {
        mainViewModel.deleteProfile(currentProfile!!)
    }

    override fun onEntryConfirm(value: Float, hourOfDay: Int, minute: Int) {
        // Entries added using the floating action button always refer to today's date.
        val t = Calendar.getInstance().apply {
            set(Calendar.MINUTE, minute)
            set(Calendar.HOUR_OF_DAY, hourOfDay)
        }
        val entry = Entry(
            profile = currentProfile,
            value = value,
            date = t.time,
            flags = EntryFlag.IS_SELECTED.value
        )
        mainViewModel.insertEntry(entry)
    }

    override fun onEntryConfirm(color: Int, hourOfDay: Int, minute: Int) {
        // Entries added using the floating action button always refer to today's date.
        val t = Calendar.getInstance().apply {
            set(Calendar.MINUTE, minute)
            set(Calendar.HOUR_OF_DAY, hourOfDay)
        }
        val entry = Entry(
            profile = currentProfile,
            value = 1f,
            date = t.time,
            color = color,
            flags = EntryFlag.IS_SELECTED.value
        )
        mainViewModel.insertEntry(entry)
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PROFILE_EDITOR_CODE = 42
    }
}


// https://stackoverflow.com/questions/46370836/android-movable-draggable-floating-action-button-fab
// Use a movable floating action button for the add entry button because the button can overlap
// the grid with no way to access the grid items.
class MovableFloatingActionButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FloatingActionButton(context, attrs, defStyleAttr), View.OnTouchListener {

    init {
        setOnTouchListener(this)
        isClickable = true
    }

    private var downX = 0f
    private var downY = 0f
    private var dx = 0f
    private var dy = 0f

    // Work around, so that animations still work when dragging and to get rid of double clicking.
    private var clickListener: OnClickListener? = null

    override fun setOnClickListener(listener: OnClickListener?) {
        // super.setOnClickListener(listener)
        clickListener = listener
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dx = view.x - downX
                dy = view.y - downY
                super.onTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                val parent: View = view.parent as View
                var newX = event.rawX + dx
                var newY = event.rawY + dy
                newX = clamp(newX, 0f, (parent.width - view.width).toFloat())
                newY = clamp(newY, parent.height * 0.5f, (parent.height - view.height).toFloat())
                view.animate().x(newX).y(newY).setDuration(0).start()
                true
            }
            MotionEvent.ACTION_UP -> {
                // Use super. touch events to get the ripple effect.
                // val clickListenerCopy = clickListener
                // setOnClickListener(null)
                super.onTouchEvent(event)
                // setOnClickListener(clickListenerCopy)

                if (abs(event.rawX - downX) < CLICK_DRAG_TOLERANCE && abs(event.rawY - downY) < CLICK_DRAG_TOLERANCE) {
                    // performClick() would double click with default setOnClickListener
                    clickListener?.onClick(this)
                    true
                } else {
                    true
                }
            }
            else -> {
                super.onTouchEvent(event)
            }
        }
    }

    companion object {
        const val CLICK_DRAG_TOLERANCE = 10f
    }
}