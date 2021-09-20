package com.mare5x.colorcalendar

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import android.util.Log
import java.util.*


// https://developer.android.com/training/data-storage/sqlite#DefineContract
object DatabaseContract {
    const val DB_NAME = "database.db"
    const val DB_VERSION = 1

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
                $CREATION_DATE INTEGER NOT NULL
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
                $DATE INTEGER NOT NULL,
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
    var date: Date = Date(),
    var value: Float = 0f
) : Comparable<Entry> {

    // Order Entries by date
    override fun compareTo(other: Entry): Int {
        return compareValuesBy(this, other, { it.date }, { it.id }, { it.value })
    }
}


fun isValidDatabaseFile(file: String): Boolean {
    try {
        val db = SQLiteDatabase.openDatabase(file, null, SQLiteDatabase.OPEN_READONLY)
        val valid = (DatabaseUtils.queryNumEntries(db, DatabaseContract.ProfileEntryDB.TABLE_NAME) > 0 &&
            DatabaseUtils.queryNumEntries(db, DatabaseContract.ProfileEntryDB.TABLE_NAME) >= 0)
        db.close()
        return valid
    } catch (e: Exception) {
        return false
    }
}


fun queryAllProfiles(db: SQLiteDatabase): Array<ProfileEntry> {
    val profileDB = DatabaseContract.ProfileEntryDB
    val queryStr = """
            SELECT *
            FROM ${profileDB.TABLE_NAME}
        """.trimIndent()

    val cursor = db.rawQuery(queryStr, null)
    cursor.moveToFirst()
    val res = Array(cursor.count) {
        val profile = ProfileEntry()
        with(cursor) {
            profile.id = getLong(getColumnIndex(profileDB.ID))
            profile.name = getString(getColumnIndex(profileDB.PROFILE_NAME))
            profile.minColor = getInt(getColumnIndex(profileDB.MIN_COLOR))
            profile.maxColor = getInt(getColumnIndex(profileDB.MAX_COLOR))
            profile.prefColor = getInt(getColumnIndex(profileDB.PREF_COLOR))
            profile.creationDate = Date(getLong(getColumnIndex(profileDB.CREATION_DATE)))
            moveToNext()
        }
        profile
    }
    cursor.close()
    return res
}


fun queryAllEntries(db: SQLiteDatabase, profile: ProfileEntry) : Array<Entry> {
    val entryDB = DatabaseContract.EntryDB
    val queryStr = """
            SELECT *
            FROM ${entryDB.TABLE_NAME}
            WHERE ${entryDB.PROFILE_FK} = ${profile.id}
        """.trimIndent()

    val cursor = db.rawQuery(queryStr, null)
    cursor.moveToFirst()
    val res = Array(cursor.count) {
        val entry = Entry(
            id = cursor.getLong(cursor.getColumnIndex(entryDB.ID)),
            profile = profile,
            date = Date(cursor.getLong(cursor.getColumnIndex(entryDB.DATE))),
            value = cursor.getFloat(cursor.getColumnIndex(entryDB.VALUE))
        )
        cursor.moveToNext()
        entry
    }
    cursor.close()
    return res
}


class DatabaseHelper(ctx : Context) : SQLiteOpenHelper(ctx, DatabaseContract.DB_NAME, null, DatabaseContract.DB_VERSION) {
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
            put(profileDB.CREATION_DATE, profile.creationDate.time)
        }

        try {
            return writableDatabase.insertOrThrow(profileDB.TABLE_NAME, null, values)
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
            put(profileDB.CREATION_DATE, profile.creationDate.time)
        }
        writableDatabase.update(profileDB.TABLE_NAME, values, "${profileDB.ID} = ${profile.id}", null)
        return profile.id
    }

    fun deleteProfile(profile: ProfileEntry) {
        writableDatabase.delete(
            DatabaseContract.EntryDB.TABLE_NAME,
            "${DatabaseContract.EntryDB.PROFILE_FK} = ${profile.id}",
            null)
        writableDatabase.delete(
            DatabaseContract.ProfileEntryDB.TABLE_NAME,
            "${DatabaseContract.ProfileEntryDB.ID} = ${profile.id}",
            null
        )
    }

    fun insertEntry(entry: Entry): Long {
        if (entry.profile == null || entry.profile!!.id < 0) {
            throw Exception("Invalid profile id for $entry")
        }
        val entryDB = DatabaseContract.EntryDB

        val values = ContentValues().apply {
            put(entryDB.DATE, entry.date.time)
            put(entryDB.VALUE, entry.value)
            put(entryDB.PROFILE_FK, entry.profile!!.id)
        }

        try {
            return writableDatabase.insertOrThrow(entryDB.TABLE_NAME, null, values)
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
            cursor = writableDatabase.rawQuery(queryStr, null)
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
                profile.creationDate = Date(getLong(getColumnIndex(profileDB.CREATION_DATE)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "queryProfile: ", e)
        } finally {
            cursor?.close()
        }
        return profile
    }

    fun queryAllProfiles(): Array<ProfileEntry> {
        return queryAllProfiles(writableDatabase)
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
            cursor = writableDatabase.rawQuery(queryStr, null)
            cursor.moveToFirst()
            if (cursor.count == 0) {
                return Entry()
            }

            val profileFk = cursor.getLong(cursor.getColumnIndex(entryDB.PROFILE_FK))
            entry.id = id
            entry.profile = queryProfile(profileFk)
            entry.date = Date(cursor.getLong(cursor.getColumnIndex(entryDB.DATE)))
            entry.value = cursor.getFloat(cursor.getColumnIndex(entryDB.VALUE))
        } catch (e: Exception) {
            Log.e(TAG, "queryEntry: ", e)
        } finally {
            cursor?.close()
        }
        return entry
    }

    fun queryAllEntries(profile: ProfileEntry) : Array<Entry> {
        return queryAllEntries(writableDatabase, profile)
    }

    fun deleteDayEntries(profile: ProfileEntry, dayPosition: Int): Int {
        val entryDB = DatabaseContract.EntryDB
        val whereStr = """
                ${entryDB.PROFILE_FK} = ${profile.id} 
                AND
                CAST((julianday(${entryDB.DATE} / 1000, 'unixepoch') - julianday(DATE("${profile.creationDate.time}" / 1000, 'unixepoch'))) AS INTEGER) = ${dayPosition}
            """.trimIndent()
        // NOTE 'floor' isn't supported ... Casting only works for non-negative integers, which
        // means profile creation date MUST be earlier than entry date.
        // TODO check timezone issues?

        return writableDatabase.delete(entryDB.TABLE_NAME, whereStr, null)
    }

    fun insertEntries(entries: Collection<Entry>) {
        // Bulk insert entries

        val entryDB = DatabaseContract.EntryDB

        writableDatabase.beginTransaction()
        try {
            entries.forEach { entry ->
                if (entry.profile == null || entry.profile!!.id < 0) {
                    throw Exception("Invalid profile id for $entry")
                }
                val values = ContentValues().apply {
                    put(entryDB.DATE, entry.date.time)
                    put(entryDB.VALUE, entry.value)
                    put(entryDB.PROFILE_FK, entry.profile!!.id)
                }
                writableDatabase.insertOrThrow(entryDB.TABLE_NAME, null, values)
            }
            writableDatabase.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(TAG, "insertEntry: ", e)
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun queryProfileName(name: String): Array<ProfileEntry> {
        val profileDB = DatabaseContract.ProfileEntryDB
        val queryStr = """
            SELECT *
            FROM ${profileDB.TABLE_NAME}
            WHERE ${profileDB.PROFILE_NAME} = "$name"
        """.trimIndent()

        val cursor = writableDatabase.rawQuery(queryStr, null)
        cursor.moveToFirst()
        val res = Array(cursor.count) {
            val profile = ProfileEntry()
            with(cursor) {
                profile.id = getLong(getColumnIndex(profileDB.ID))
                profile.name = getString(getColumnIndex(profileDB.PROFILE_NAME))
                profile.minColor = getInt(getColumnIndex(profileDB.MIN_COLOR))
                profile.maxColor = getInt(getColumnIndex(profileDB.MAX_COLOR))
                profile.prefColor = getInt(getColumnIndex(profileDB.PREF_COLOR))
                profile.creationDate = Date(getLong(getColumnIndex(profileDB.CREATION_DATE)))
                moveToNext()
            }
            profile
        }
        cursor.close()
        return res
    }

    companion object {
        val TAG = DatabaseHelper::class.simpleName ?: "null"
    }
}

