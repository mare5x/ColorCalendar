package com.mare5x.colorcalendar

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit

fun calcDayDifference(d1: Date, d2: Date): Int {
    // Remove time information to calculate only the difference in days ...
    val t1 = Calendar.getInstance().apply {
        time = d1
        set(Calendar.SECOND, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val t2 = Calendar.getInstance().apply {
        time = d2
        set(Calendar.SECOND, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return TimeUnit.DAYS.convert(t2 - t1, TimeUnit.MILLISECONDS).toInt()
}

fun getProfileDayAge(profile: ProfileEntry) = calcDayDifference(profile.creationDate, Date()) + 1

typealias EntryList = Array<SortedSet<Entry>>

class EntriesViewModel(private val db: DatabaseHelper) : ViewModel() {
    private val entriesByDayData = MutableLiveData<EntryList>()
    private val profileData = MutableLiveData<ProfileEntry>()
    private val dayChanged = MutableLiveData<Int>()

    fun getEntriesByDay() = entriesByDayData
    fun getProfile() = profileData
    fun getDayChanged() = dayChanged

    fun setProfile(profile: ProfileEntry) {
        profileData.value = profile
        fetchGridEntries(profile)
    }

    fun setProfile(profileId: Long) {
        setProfile(db.queryProfile(profileId))
    }

    private fun fetchGridEntries(profile: ProfileEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = db.queryAllEntries(profile)

            val dayEntries = EntryList(getProfileDayAge(profile)) { sortedSetOf() }
            entries.forEach {
                if (it.date != null) {
                    val day = calcDayDifference(profile.creationDate, it.date!!)
                    dayEntries[day].add(it)
                }
            }

            entriesByDayData.postValue(dayEntries)
        }
    }

    fun setDayEntries(dayPosition: Int, entries: Collection<Entry>) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteDayEntries(profileData.value!!, dayPosition)
            db.insertEntries(entries)

            val dayEntries = entriesByDayData.value!![dayPosition]
            dayEntries.clear()
            dayEntries.addAll(entries)
            dayChanged.postValue(dayPosition)
        }
    }
}

class ColorGridViewModelFactory(private val db: DatabaseHelper) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return EntriesViewModel(db) as T
    }
}

class ColorRectAdapter(var profile: ProfileEntry) :
        RecyclerView.Adapter<ColorRectAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val rect: ColorRectButton

        var clickListener: (position: Int) -> Unit = { }

        init {
            v.setOnClickListener { clickListener(adapterPosition) }
            rect = v.findViewById(R.id.colorRect)
        }
    }

    // Refers to the list in the view model.
    var dayEntries: EntryList = emptyArray()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var clickListener: (position: Int) -> Unit = { }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // TODO layout inflation is the bottleneck when first populating the grid ...
        // The problem is mitigated by displaying fewer columns
        val v = LayoutInflater.from(parent.context).inflate(R.layout.color_grid_item, parent, false)
        return ViewHolder(v).also { h -> h.clickListener = clickListener }
    }

    override fun getItemCount(): Int = dayEntries.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Use 'position' as the number of days since profile creation date.
        val entry = if (dayEntries[position].size > 0) dayEntries[position].last() else null
        holder.rect.color = if (entry != null)
            calcGradientColor(profile.minColor, profile.maxColor, entry.value)
            else Color.GRAY
    }

    companion object {
        private const val TAG = "ColorRectAdapter"
    }
}

class ColorGrid : RecyclerView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    init {
        layoutManager = GridLayoutManager(context, 14)
        setHasFixedSize(true)  // Optimization
    }
}