package com.mare5x.colorcalendar

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.provider.BaseColumns
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*


// https://developer.android.com/training/data-storage/sqlite#DefineContract
private object DatabaseContract {
    const val DB_NAME = "database.db"
    const val DB_VERSION = 1

    @SuppressLint("SimpleDateFormat")
    val DATE_FORMAT = SimpleDateFormat("YYYY-MM-DD HH:MM:SS.SSS")  // ISO8601

    object ProfileEntryDB : BaseColumns {
        const val TABLE_NAME = "profile"
        const val PROFILE_NAME = "name"
        const val MIN_COLOR = "min_color"
        const val MAX_COLOR = "max_color"

        const val DB_CREATE = """
            CREATE TABLE ${TABLE_NAME}(
                ${BaseColumns._ID} INTEGER PRIMARY KEY,
                $PROFILE_NAME TEXT NOT NULL,
                $MIN_COLOR INTEGER NOT NULL,
                $MAX_COLOR INTEGER NOT NULL
            );
        """
    }

    object EntryDB : BaseColumns {
        const val TABLE_NAME = "entry"
        const val DATE = "date"
        const val VALUE = "value"
        const val PROFILE_FK = "profile_id"

        const val DB_CREATE = """
            CREATE TABLE ${TABLE_NAME}(
                ${BaseColumns._ID} INTEGER PRIMARY KEY,
                $DATE TEXT,
                $VALUE REAL NOT NULL
                
                FOREIGN KEY (${PROFILE_FK}) REFERENCES ${ProfileEntryDB.TABLE_NAME} (${BaseColumns._ID})
            );
        """
    }
}


data class ProfileEntry(var id: Int = -1, var name: String = "null", var minColor: Int = 0, var maxColor: Int = 0)

data class Entry(var id: Int = -1, var profile: ProfileEntry, var date: Date, var value: Float)


class DatabaseHelper(ctx : Context) : SQLiteOpenHelper(ctx, DatabaseContract.DB_NAME, null, DatabaseContract.DB_VERSION) {
    private var writableDB: SQLiteDatabase? = null
    private var readableDB: SQLiteDatabase? = null

    private val TAG: String = DatabaseHelper::class.simpleName ?: "null"

    // TODO execute database commands in a coroutine
    init {
        Log.i(TAG, queryProfile(1).toString())
    }


    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(DatabaseContract.ProfileEntryDB.DB_CREATE + DatabaseContract.EntryDB.DB_CREATE)

        insertProfile(ProfileEntry(name = "default", minColor = Color.RED, maxColor = Color.GREEN))
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        val s = """
            DROP TABLE IF EXISTS ${DatabaseContract.ProfileEntryDB.TABLE_NAME};
            DROP TABLE IF EXISTS ${DatabaseContract.EntryDB.TABLE_NAME};
        """
        db.execSQL(s)
        onCreate(db)
    }

    fun insertProfile(profile: ProfileEntry): Long {
        val values = ContentValues().apply {
            put(DatabaseContract.ProfileEntryDB.PROFILE_NAME, profile.name)
            put(DatabaseContract.ProfileEntryDB.MIN_COLOR, profile.minColor)
            put(DatabaseContract.ProfileEntryDB.MAX_COLOR, profile.maxColor)
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

    fun insertEntry(entry: Entry): Long {
        if (entry.profile.id < 0) {
            throw Exception("Invalid profile id for $entry")
        }

        val dateStr = DatabaseContract.DATE_FORMAT.format(entry.date)
        val values = ContentValues().apply {
            put(DatabaseContract.EntryDB.DATE, dateStr)
            put(DatabaseContract.EntryDB.VALUE, entry.value)
            put(DatabaseContract.EntryDB.PROFILE_FK, entry.profile.id)
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

    fun queryProfile(id: Int): ProfileEntry {
        val profile = ProfileEntry()
        val _db = DatabaseContract.ProfileEntryDB

        val queryStr = """
            SELECT *
            FROM ${_db.TABLE_NAME}
            WHERE _id = $id
        """.trimIndent()

        var cursor: Cursor? = null
        try {
            if (readableDB == null) readableDB = readableDatabase
            cursor = readableDB!!.rawQuery(queryStr, null)
            cursor.moveToFirst()
            if (cursor.count == 0) {
                return ProfileEntry(id = -1)
            }

            profile.id = id
            profile.name = cursor.getString(cursor.getColumnIndex(_db.PROFILE_NAME))
            profile.minColor = cursor.getInt(cursor.getColumnIndex(_db.MIN_COLOR))
            profile.maxColor = cursor.getInt(cursor.getColumnIndex(_db.MAX_COLOR))
        } catch (e: Exception) {
            Log.e(TAG, "queryProfile: ", e)
        } finally {
            cursor?.close()
        }
        return profile
    }

    // Returns the difference between the latest and earliest stored date.
    fun getDateRange(profile: ProfileEntry): Int {
        return 0
    }

    fun getProfilesCount(): Long {
        if (readableDB == null) readableDB = readableDatabase
        return DatabaseUtils.queryNumEntries(readableDB, DatabaseContract.ProfileEntryDB.TABLE_NAME)
    }


}

