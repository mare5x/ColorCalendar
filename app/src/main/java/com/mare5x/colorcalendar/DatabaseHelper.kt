package com.mare5x.colorcalendar

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*


// https://developer.android.com/training/data-storage/sqlite#DefineContract
private object DatabaseContract {
    const val DB_NAME = "database.db"
    const val DB_VERSION = 1

    // TODO store dates as longs
    @SuppressLint("SimpleDateFormat")
    val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")  // ISO8601

    object ProfileEntryDB : BaseColumns {
        const val ID = BaseColumns._ID
        const val TABLE_NAME = "profile"
        const val PROFILE_NAME = "name"
        const val MIN_COLOR = "min_color"
        const val MAX_COLOR = "max_color"
        const val PREF_COLOR = "pref_color"
        const val CREATION_DATE = "creation_date"

        const val DB_CREATE = """
            CREATE TABLE ${TABLE_NAME}(
                $ID INTEGER PRIMARY KEY,
                $PROFILE_NAME TEXT NOT NULL,
                $MIN_COLOR INTEGER NOT NULL,
                $MAX_COLOR INTEGER NOT NULL,
                $PREF_COLOR INTEGER NOT NULL,
                $CREATION_DATE TEXT NOT NULL
            );
        """
    }

    object EntryDB : BaseColumns {
        const val ID = BaseColumns._ID
        const val TABLE_NAME = "entry"
        const val DATE = "_date"
        const val VALUE = "value"
        const val PROFILE_FK = "profile_id"

        const val DB_CREATE = """
            CREATE TABLE $TABLE_NAME(
                $ID INTEGER PRIMARY KEY,
                $PROFILE_FK INTEGER NOT NULL,
                $DATE TEXT,
                $VALUE REAL NOT NULL,
                
                FOREIGN KEY (${PROFILE_FK}) REFERENCES ${ProfileEntryDB.TABLE_NAME} ($ID)
            );
        """
    }
}


data class ProfileEntry(
    var id: Long = -1,
    var name: String = "null",
    var minColor: Int = 0,
    var maxColor: Int = 0,
    var prefColor: Int = 0,
    var creationDate: Date = Date()
)

data class Entry(
    var id: Long = -1,
    var profile: ProfileEntry? = null,
    var date: Date? = null,
    var value: Float = 0f
) : Comparable<Entry> {

    // Order Entries by date
    override fun compareTo(other: Entry): Int {
        return compareValuesBy(this, other, { it.date }, { it.id }, { it.value })
    }
}


class DatabaseHelper(ctx : Context) : SQLiteOpenHelper(ctx, DatabaseContract.DB_NAME, null, DatabaseContract.DB_VERSION) {
    private var writableDB: SQLiteDatabase? = null
    private var readableDB: SQLiteDatabase? = null

    // TODO execute database commands in a coroutine

    override fun onCreate(db: SQLiteDatabase) {
        // "Multiple statements separated by semicolons are not supported."
        db.execSQL(DatabaseContract.ProfileEntryDB.DB_CREATE)
        db.execSQL(DatabaseContract.EntryDB.DB_CREATE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS ${DatabaseContract.ProfileEntryDB.TABLE_NAME};")
        db.execSQL("DROP TABLE IF EXISTS ${DatabaseContract.EntryDB.TABLE_NAME};")
        onCreate(db)
    }

    fun insertProfile(profile: ProfileEntry): Long {
        val profileDB = DatabaseContract.ProfileEntryDB
        val values = ContentValues().apply {
            put(profileDB.PROFILE_NAME, profile.name)
            put(profileDB.MIN_COLOR, profile.minColor)
            put(profileDB.MAX_COLOR, profile.maxColor)
            put(profileDB.PREF_COLOR, profile.prefColor)
            put(profileDB.CREATION_DATE, DatabaseContract.DATE_FORMAT.format(profile.creationDate))
        }

        try {
            if (writableDB == null) {
                writableDB = writableDatabase
            }
            return writableDB!!.insert(DatabaseContract.ProfileEntryDB.TABLE_NAME, null, values)
        } catch (e: Exception) {
            Log.e(TAG, "insertProfile: ", e)
        }
        return -1
    }

    fun updateProfile(profile: ProfileEntry): Long {
        val profileDB = DatabaseContract.ProfileEntryDB
        val values = ContentValues().apply {
            put(profileDB.PROFILE_NAME, profile.name)
            put(profileDB.MIN_COLOR, profile.minColor)
            put(profileDB.MAX_COLOR, profile.maxColor)
            put(profileDB.PREF_COLOR, profile.prefColor)
            put(profileDB.CREATION_DATE, DatabaseContract.DATE_FORMAT.format(profile.creationDate))
        }
        if (writableDB == null) writableDB = writableDatabase
        writableDB!!.update(profileDB.TABLE_NAME, values, "${profileDB.ID} = ${profile.id}", null)
        return profile.id
    }

    fun deleteProfile(profile: ProfileEntry) {
        if (writableDB == null) writableDB = writableDatabase
        writableDB!!.delete(
            DatabaseContract.EntryDB.TABLE_NAME,
            "${DatabaseContract.EntryDB.PROFILE_FK} = ${profile.id}",
            null)
        writableDB!!.delete(
            DatabaseContract.ProfileEntryDB.TABLE_NAME,
            "${DatabaseContract.ProfileEntryDB.ID} = ${profile.id}",
            null
        )
    }

    fun insertEntry(entry: Entry): Long {
        if (entry.profile == null || entry.profile!!.id < 0) {
            throw Exception("Invalid profile id for $entry")
        }

        val dateStr = DatabaseContract.DATE_FORMAT.format(entry.date!!)
        val values = ContentValues().apply {
            put(DatabaseContract.EntryDB.DATE, dateStr)
            put(DatabaseContract.EntryDB.VALUE, entry.value)
            put(DatabaseContract.EntryDB.PROFILE_FK, entry.profile!!.id)
        }

        try {
            if (writableDB == null) {
                writableDB = writableDatabase
            }
            return writableDB!!.insert(DatabaseContract.EntryDB.TABLE_NAME, null, values)
        } catch (e: Exception) {
            Log.e(TAG, "insertEntry: ", e)
        }
        return -1
    }

    fun queryProfile(id: Long): ProfileEntry {
        val profile = ProfileEntry()
        val profileDB = DatabaseContract.ProfileEntryDB

        val queryStr = """
            SELECT *
            FROM ${profileDB.TABLE_NAME}
            WHERE _id = $id
        """.trimIndent()

        var cursor: Cursor? = null
        try {
            if (readableDB == null) readableDB = readableDatabase
            cursor = readableDB!!.rawQuery(queryStr, null)
            cursor.moveToFirst()
            if (cursor.count == 0) {
                return ProfileEntry()
            }

            with(cursor) {
                profile.id = id
                profile.name = getString(getColumnIndex(profileDB.PROFILE_NAME))
                profile.minColor = getInt(getColumnIndex(profileDB.MIN_COLOR))
                profile.maxColor = getInt(getColumnIndex(profileDB.MAX_COLOR))
                profile.prefColor = getInt(getColumnIndex(profileDB.PREF_COLOR))
                profile.creationDate = DatabaseContract.DATE_FORMAT.parse(
                    getString(getColumnIndex(profileDB.CREATION_DATE)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "queryProfile: ", e)
        } finally {
            cursor?.close()
        }
        return profile
    }

    fun queryAllProfiles(): Array<ProfileEntry> {
        val profileDB = DatabaseContract.ProfileEntryDB
        val queryStr = """
            SELECT *
            FROM ${profileDB.TABLE_NAME}
        """.trimIndent()

        if (readableDB == null) readableDB = readableDatabase
        val cursor = readableDB!!.rawQuery(queryStr, null)
        cursor.moveToFirst()
        val res = Array(cursor.count) {
            val profile = ProfileEntry()
            with(cursor) {
                profile.id = getLong(getColumnIndex(profileDB.ID))
                profile.name = getString(getColumnIndex(profileDB.PROFILE_NAME))
                profile.minColor = getInt(getColumnIndex(profileDB.MIN_COLOR))
                profile.maxColor = getInt(getColumnIndex(profileDB.MAX_COLOR))
                profile.prefColor = getInt(getColumnIndex(profileDB.PREF_COLOR))
                profile.creationDate = DatabaseContract.DATE_FORMAT.parse(getString(getColumnIndex(profileDB.CREATION_DATE)))
                moveToNext()
            }
            profile
        }
        cursor.close()
        return res
    }

    fun queryEntry(id: Long): Entry {
        val entry = Entry()
        val entryDB = DatabaseContract.EntryDB

        val queryStr = """
            SELECT *
            FROM ${entryDB.TABLE_NAME}
            WHERE _id = $id
        """.trimIndent()

        var cursor: Cursor? = null
        try {
            if (readableDB == null) readableDB = readableDatabase
            cursor = readableDB!!.rawQuery(queryStr, null)
            cursor.moveToFirst()
            if (cursor.count == 0) {
                return Entry()
            }

            val profileFk = cursor.getLong(cursor.getColumnIndex(entryDB.PROFILE_FK))
            val dateStr = cursor.getString(cursor.getColumnIndex(entryDB.DATE))
            entry.id = id
            entry.profile = queryProfile(profileFk)
            entry.date = DatabaseContract.DATE_FORMAT.parse(dateStr)
            entry.value = cursor.getFloat(cursor.getColumnIndex(entryDB.VALUE))
        } catch (e: Exception) {
            Log.e(TAG, "queryEntry: ", e)
        } finally {
            cursor?.close()
        }
        return entry
    }

    fun queryEntry(profile: ProfileEntry, date: Date) : Entry? {
        val entryDB = DatabaseContract.EntryDB
        val queryStr = """
            SELECT *
            FROM ${entryDB.TABLE_NAME}
            WHERE ${entryDB.PROFILE_FK} = ${profile.id} AND
                date(${entryDB.DATE}) = date('${DatabaseContract.DATE_FORMAT.format(date)}')
        """.trimIndent()

        var cursor: Cursor? = null
        var entry: Entry? = null
        try {
            if (readableDB == null) readableDB = readableDatabase
            cursor = readableDB!!.rawQuery(queryStr, null)
            cursor.moveToFirst()
            if (cursor.count > 0) {
                val dateStr = cursor.getString(cursor.getColumnIndex(entryDB.DATE))
                entry = Entry(
                    id = cursor.getLong(cursor.getColumnIndex(entryDB.ID)),
                    profile = profile,
                    date = DatabaseContract.DATE_FORMAT.parse(dateStr),
                    value = cursor.getFloat(cursor.getColumnIndex(entryDB.VALUE))
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "queryEntry: ", e)
        } finally {
            cursor?.close()
        }
        return entry
    }

    fun queryAllEntries(profile: ProfileEntry) : Array<Entry> {
        val entryDB = DatabaseContract.EntryDB
        val queryStr = """
            SELECT *
            FROM ${entryDB.TABLE_NAME}
            WHERE ${entryDB.PROFILE_FK} = ${profile.id}
        """.trimIndent()

        if (readableDB == null) readableDB = readableDatabase
        val cursor = readableDB!!.rawQuery(queryStr, null)

        cursor.moveToFirst()
        val res = Array(cursor.count) {
            val dateStr = cursor.getString(cursor.getColumnIndex(entryDB.DATE))
            val entry = Entry(
                id = cursor.getLong(cursor.getColumnIndex(entryDB.ID)),
                profile = profile,
                date = DatabaseContract.DATE_FORMAT.parse(dateStr),
                value = cursor.getFloat(cursor.getColumnIndex(entryDB.VALUE))
            )
            cursor.moveToNext()
            entry
        }
        cursor.close()
        return res
    }

    fun deleteDayEntries(profile: ProfileEntry, dayPosition: Int): Int {
        val entryDB = DatabaseContract.EntryDB
        val dateStr = DatabaseContract.DATE_FORMAT.format(profile.creationDate)
        val whereStr = """
                ${entryDB.PROFILE_FK} = ${profile.id} 
                AND
                CAST((julianday(${entryDB.DATE}) - julianday(DATE("${dateStr}"))) AS INTEGER) = ${dayPosition}
            """.trimIndent()
        // NOTE 'floor' isn't supported ... Casting only works for non-negative integers, which
        // means profile creation date MUST be earlier than entry date.
        // TODO check timezone issues?

        if (writableDB == null) writableDB = writableDatabase
        return writableDB!!.delete(entryDB.TABLE_NAME, whereStr, null)
    }

    fun insertEntries(entries: Collection<Entry>) {
        // TODO bulk
        entries.forEach { entry ->
            insertEntry(entry)
        }
    }

    fun getProfilesCount(): Long {
        if (readableDB == null) readableDB = readableDatabase
        return DatabaseUtils.queryNumEntries(readableDB, DatabaseContract.ProfileEntryDB.TABLE_NAME)
    }

    companion object {
        val TAG = DatabaseHelper::class.simpleName ?: "null"
    }
}

