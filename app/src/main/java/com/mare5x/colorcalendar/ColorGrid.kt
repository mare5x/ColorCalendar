package com.mare5x.colorcalendar

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

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

fun getProfileDayAge(profile: ProfileEntry) = calcDayDifference(profile.creationDate, Date())

typealias EntryList = Array<MutableList<Entry>>

class ColorGridViewModel(profile: ProfileEntry, private val db: DatabaseHelper) : ViewModel() {
    private val entriesByDay = MutableLiveData<EntryList>()

    init {
        fetchGridEntries(profile)
    }

    fun getEntriesByDay() = entriesByDay

    private fun fetchGridEntries(profile: ProfileEntry) {
        viewModelScope.launch {
            val entries = db.queryAllEntries(profile)

            val dayEntries = EntryList(getProfileDayAge(profile)) { mutableListOf() }
            entries.forEach {
                if (it.date != null) {
                    val day = calcDayDifference(profile.creationDate, it.date!!)
                    dayEntries[day].add(it)
                }
            }

            entriesByDay.postValue(dayEntries)
        }
    }
}

class ColorGridViewModelFactory(
    private val profile: ProfileEntry, private val db: DatabaseHelper
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return ColorGridViewModel(profile, db) as T
    }
}

class ColorRectAdapter(profile: ProfileEntry) :
        RecyclerView.Adapter<ColorRectAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val rect: ColorRect

        init {
            v.setOnClickListener { Log.i(TAG, "$adapterPosition clicked") }
            rect = v.findViewById(R.id.colorRect)
        }
    }

    var dayEntries: EntryList = Array(getProfileDayAge(profile)) { mutableListOf<Entry>() }
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.color_grid_item, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int = dayEntries.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Use 'position' as the number of days since profile creation date.
        val entry = if (dayEntries[position].size > 0) dayEntries[position][0] else null
        holder.rect.color = if (entry != null) Color.rgb((255 * entry.value).roundToInt(), 0, 0) else Color.GRAY
    }

    companion object {
        private const val TAG = "ColorRectAdapter"
    }
}

class ColorGrid : RecyclerView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    init {
        layoutManager = GridLayoutManager(context, 7)

    }
}