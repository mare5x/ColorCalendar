package com.mare5x.colorcalendar

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.math.MathUtils.clamp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.math.roundToInt


fun calcDayDifference(d1: Date, d2: Date): Int {
    // Remove time information to calculate only the difference in days ...
    val t1 = Calendar.getInstance().apply {
        time = d1
        set(Calendar.SECOND, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.HOUR_OF_DAY, 6)  // Stupid workaround because of historical time zone offsets (+-1 errors)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val t2 = Calendar.getInstance().apply {
        time = d2
        set(Calendar.SECOND, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return TimeUnit.DAYS.convert(t2 - t1, TimeUnit.MILLISECONDS).toInt()
}

fun getProfileDayAge(profile: ProfileEntry) = calcDayDifference(profile.creationDate, Date()) + 1

typealias EntryList = MutableList<SortedSet<Entry>>

class EntriesViewModel(private val db: DatabaseHelper) : ViewModel() {
    private val entriesByDayData = MutableLiveData<EntryList>()
    private val profileData = MutableLiveData<ProfileEntry>()
    private val dayChanged = MutableLiveData<Int>()

    fun getEntriesByDay() = entriesByDayData
    fun getProfile() = profileData
    fun getDayChanged() = dayChanged

    fun setProfile(profile: ProfileEntry) {
        profileData.value = profile
    }

    fun initProfile(profileId: Long) {
        val profile = db.queryProfile(profileId)
        profileData.value = profile
        fetchGridEntries(profile)
    }

    private fun fetchGridEntries(profile: ProfileEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = db.queryAllEntries(profile)

            val size = getProfileDayAge(profile)
            // Slightly larger capacity for eventual resizing.
            val dayEntries = ArrayList<SortedSet<Entry>>(size + 7)
            repeat(size) { dayEntries.add(sortedSetOf()) }

            entries.forEach {
                val day = calcDayDifference(profile.creationDate, it.date)
                if (day >= 0 && day < size)
                    dayEntries[day].add(it)
            }

            entriesByDayData.postValue(dayEntries)
        }
    }

    fun ensureEntriesSize(newSize: Int) {
        val entries = entriesByDayData.value
        if (entries != null && newSize > entries.size) {
            repeat(newSize - entries.size) { entries.add(sortedSetOf()) }
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
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return EntriesViewModel(db) as T
    }
}

class ColorRectAdapter(var profile: ProfileEntry) :
        RecyclerView.Adapter<ColorRectAdapter.ViewHolder>() {

    inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val rect: ColorRect

        init {
            v.setOnClickListener {
                clickListener(dayPosition(adapterPosition))
            }
            rect = v.findViewById(R.id.colorRect)
        }

        fun bind(position: Int) {
            val day = dayPosition(position)
            val entry = if (dayEntries[day].size > 0) dayEntries[day].last() else null
            rect.setColor(
                if (entry != null) calcGradientColor(profile.minColor, profile.maxColor, entry.value, profile.flags)
                else Color.GRAY
            )
            rect.drawBorder = (day == dayEntries.size - 1)
            if (rect.drawBorder) {
                val hsv = FloatArray(3)
                Color.colorToHSV(profile.prefColor, hsv)
                hsv[1] *= 0.5f
                hsv[2] *= 0.75f
                rect.setBorderColor(Color.HSVToColor(hsv))
            }
        }
    }

    // Show day entries sorted ascending or descending by date.
    private var orderDesc = true

    // Refers to the list in the view model.
    var dayEntries: EntryList = mutableListOf()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var clickListener: (position: Int) -> Unit = { }

    // Adapter positions must be transformed using dayPosition to get the correct
    // entry index. Day positions must likewise be transformed by this function to get
    // the adapter positions.
    private fun dayPosition(position: Int): Int {
        return if (orderDesc) (dayEntries.size - position - 1) else position
    }

    fun notifyDayChanged(dayPosition: Int) {
        notifyItemChanged(dayPosition(dayPosition))
    }

    fun addDayEntry(entry: Entry) {
        val position = calcDayDifference(profile.creationDate, entry.date)
        if (dayEntries.isNotEmpty() && position < dayEntries.size) {
            dayEntries[position].add(entry)
        }
        notifyDayChanged(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // TODO layout inflation is the bottleneck when first populating the grid ...
        // The problem is mitigated by displaying fewer columns
        val v = LayoutInflater.from(parent.context).inflate(R.layout.color_grid_item, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int = dayEntries.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(position)
    }

    companion object {
        private const val TAG = "ColorRectAdapter"
    }
}

class ColorGrid : RecyclerView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private val gridLayoutManager: GridLayoutManager = GridLayoutManager(context, 7)

    init {
        layoutManager = gridLayoutManager
        setHasFixedSize(true)  // Optimization
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // Set the number of grid columns, so that each item is 'itemSize' pixels large.
        val itemSize = resources.getDimension(R.dimen.color_item_size) * 0.75
        val marginSize = resources.getDimension(R.dimen.grid_item_margin)
        val cols = w / (itemSize + 2.0 * marginSize)
        gridLayoutManager.spanCount = clamp(cols.roundToInt(), 2, 14)
    }
}

class ColorGridFragment : Fragment() {
    private lateinit var grid: ColorGrid
    private lateinit var adapter: ColorRectAdapter

    // Structure:
    // Data is stored in a database. The view model fetches data from the database in
    // a separate coroutine. The view model's live data is observed for changes, which notifies
    // the grid adapter. The adapter is given the data and creates the views for
    // grid (RecyclerView).
    // The view model is scoped to the fragment's lifecycle.
    private lateinit var db: DatabaseHelper
    private val gridModel: EntriesViewModel by viewModels { ColorGridViewModelFactory(db) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.color_grid_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainModel: MainViewModel by activityViewModels()
        db = mainModel.db

        // Fragment created using companion 'create' function with bundle arguments (profile id).
        val profileId = requireArguments().getLong(PROFILE_ID_KEY)
        gridModel.initProfile(profileId)

        adapter = ColorRectAdapter(gridModel.getProfile().value!!)
        grid = view.findViewById(R.id.colorGrid)
        grid.adapter = adapter

        // Note: it was simpler to put the fab in the root activity instead of here because
        // the view pager was intercepting certain touch events ...
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (grid.computeVerticalScrollOffset() > grid.computeVerticalScrollExtent()) {
                    mainModel.setShowScrollFab(true)
                } else {
                    mainModel.setShowScrollFab(false)
                }
            }
        })

        mainModel.getScrollFabClickedEvent().observe(viewLifecycleOwner) { clickEvent ->
            // The event gets sent to all observers (also pages other than the current one in the
            // view pager). For that reason we must peek the event's profile id before marking it handled.
            Log.i(TAG, "onViewCreated: ${clickEvent.peekContent()} | $profileId ${clickEvent.hasBeenHandled}")
            clickEvent.peekContent().let { id ->
                if (profileId == id && !clickEvent.hasBeenHandled) {
                    clickEvent.hasBeenHandled = true
                    scrollToTop()
                }
            }
        }

        mainModel.getInsertedEntry().observe(viewLifecycleOwner) { entry ->
            if (entry.profile!!.id == profileId && entry.id != -1L) {
                ensureEntriesSize()
                adapter.addDayEntry(entry)
            }
        }

        mainModel.getUpdatedProfile().observe(viewLifecycleOwner) { profile ->
            if (profile.id == profileId) {
                gridModel.initProfile(profileId)
                adapter.profile = profile
                adapter.notifyDataSetChanged()
            }
        }

        gridModel.getEntriesByDay().observe(viewLifecycleOwner) { entries ->
            adapter.dayEntries = entries
        }
        gridModel.getProfile().observe(viewLifecycleOwner) { profile ->
            adapter.profile = profile
        }
        gridModel.getDayChanged().observe(viewLifecycleOwner) { dayPosition ->
            adapter.notifyDayChanged(dayPosition)
        }

        adapter.clickListener = { day ->
            val dialog = EntryViewerDialog.create(adapter.profile, day)
            dialog.show(childFragmentManager, "entryViewer")
        }
    }

    override fun onResume() {
        super.onResume()

        // If the app lives past midnight, the day list must be enlarged.
        ensureEntriesSize()
    }

    private fun ensureEntriesSize() {
        val profile = gridModel.getProfile().value
        if (profile != null) {
            val oldSize = adapter.dayEntries.size
            val newSize = getProfileDayAge(profile)
            if (newSize > oldSize) {
                gridModel.ensureEntriesSize(newSize)
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun scrollToTop() {
        // Smooth scrolling takes too long if the list is long ...
        // grid.smoothScrollToPosition(0)
        grid.scrollToPosition(0)
    }

    companion object {
        const val TAG = "ColorGridFragment"

        const val PROFILE_ID_KEY = "PROFILE_ID"

        fun create(profile: ProfileEntry): ColorGridFragment {
            val fragment = ColorGridFragment()
            fragment.arguments = Bundle().apply { putLong(PROFILE_ID_KEY, profile.id) }
            return fragment
        }
    }
}
