package com.mare5x.colorcalendar

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.observe
import com.google.android.material.floatingactionbutton.FloatingActionButton


class MainActivity : AppCompatActivity(), ColorPickerDialogFragment.ColorPickerListener, ProfileEditorDialogFragment.ProfileEditorListener {

    private lateinit var db: DatabaseHelper
    private val gridViewModel: ColorGridViewModel by viewModels { ColorGridViewModelFactory(db) }
    private val profilesViewModel: ProfilesViewModel by viewModels { ProfilesViewModelFactory(db) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        db = DatabaseHelper(this)
        gridViewModel.setProfile(1)

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { view ->
            val dialog = ColorPickerDialogFragment()
            dialog.show(supportFragmentManager, "colorPicker")
        }

        profilesViewModel.getProfiles().observe(this) { profiles ->
            if (profiles.isNotEmpty())
                gridViewModel.setProfile(profiles.last())
        }

        gridViewModel.getProfile().observe(this) { profile ->
            setUIColor(profile.prefColor)
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
        gridViewModel.insertEntry(Entry(value = value))
    }

    override fun onColorCancel(value: Float) { }

    override fun onProfileConfirm(name: String, minColor: Int, maxColor: Int, prefColor: Int) {
        profilesViewModel.insertProfile(ProfileEntry(
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
        setUIColor(gridViewModel.getProfile().value!!.prefColor)
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}