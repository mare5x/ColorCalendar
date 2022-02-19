package com.mare5x.colorcalendar

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import android.util.Log
import androidx.core.database.getIntOrNull
import java.util.*


// https://developer.android.com/training/data-storage/sqlite#DefineContract
object DatabaseContract {
    const val DB_NAME = "database.db"
    const val DB_VERSION = 4

    object ProfileEntryDB : BaseColumns {
        const val ID = BaseColumns._ID
        const val TABLE_NAME = "profile"
        const val PROFILE_NAME = "name"
        const val MIN_COLOR = "min_color"
        const val MAX_COLOR = "max_color"
        const val PREF_COLOR = "pref_color"
        const val BANNER_COLOR = "banner_color"
        const val CREATION_DATE = "creation_date"
        const val PROFILE_TYPE = "profile_type"
        const val PROFILE_FLAGS = "flags"

        const val DB_CREATE = """
            CREATE TABLE ${TABLE_NAME}(
                $ID INTEGER PRIMARY KEY,
                $PROFILE_NAME TEXT NOT NULL,
                $MIN_COLOR INTEGER NOT NULL,
                $MAX_COLOR INTEGER NOT NULL,
                $PREF_COLOR INTEGER NOT NULL,
                $BANNER_COLOR INTEGER,
                $CREATION_DATE INTEGER NOT NULL,
                $PROFILE_TYPE INTEGER NOT NULL,
                $PROFILE_FLAGS INTEGER 
            );
        """
    }

    object EntryDB : BaseColumns {
        const val ID = BaseColumns._ID
        const val TABLE_NAME = "entry"
        const val DATE = "_date"
        const val VALUE = "value"
        const val PROFILE_FK = "profile_id"
        const val COLOR = "color"
        const val FLAGS = "flags"

        const val DB_CREATE = """
            CREATE TABLE $TABLE_NAME(
                $ID INTEGER PRIMARY KEY,
                $PROFILE_FK INTEGER NOT NULL,
                $DATE INTEGER NOT NULL,
                $VALUE REAL NOT NULL,
                $COLOR INTEGER,
                $FLAGS INTEGER,
                
                FOREIGN KEY (${PROFILE_FK}) REFERENCES ${ProfileEntryDB.TABLE_NAME} ($ID)
            );
        """
    }
}


enum class ProfileType(val value: Int) {
    TWO_COLOR_CIRCLE(0),  // 0 must be default for old database compatibility
    FREE_COLOR(1);
//    ONE_COLOR_SHADE(2),
//    BOOLEAN_COLOR(3);

    companion object {
        fun fromInt(value: Int) = values().first { value == it.value }
    }
}

interface IntFlag {
    val value: Int
}

enum class ProfileFlag(override val value: Int) : IntFlag {
    CUSTOM_BANNER(1 shl 0),
    CIRCLE_LONG(1 shl 1);  // Short or long circle for circle profile type
}

enum class EntryFlag(override val value: Int) : IntFlag {
    IS_SELECTED(1 shl 0)
}

infix fun Int.hasFlag(flag: IntFlag): Boolean = this and flag.value > 0
infix fun Int.hasFlagNot(flag: IntFlag) = !(this hasFlag flag)
fun Int.setFlag0(flag: IntFlag): Int = this and flag.value.inv()
fun Int.setFlag1(flag: IntFlag): Int = this or flag.value
fun Int.setFlag(flag: IntFlag, b: Boolean): Int = if (b) this.setFlag1(flag) else this.setFlag0(flag)


data class ProfileEntry(
    var id: Long = -1,
    var name: String = "",
    var minColor: Int = 0,
    var maxColor: Int = 0,
    var prefColor: Int = 0,
    var bannerColor: Int? = null,
    var creationDate: Date = Date(),
    var type: ProfileType = ProfileType.TWO_COLOR_CIRCLE,
    var flags: Int = 0  // Extra flags, as Int because I don't know what I'll need ...
) {
    fun contentValues(): ContentValues {
        val profileDB = DatabaseContract.ProfileEntryDB
        return ContentValues().apply {
            put(profileDB.PROFILE_NAME, name)
            put(profileDB.MIN_COLOR, minColor)
            put(profileDB.MAX_COLOR, maxColor)
            put(profileDB.PREF_COLOR, prefColor)
            put(profileDB.BANNER_COLOR, bannerColor)
            put(profileDB.CREATION_DATE, creationDate.time)
            put(profileDB.PROFILE_TYPE, type.value)
            put(profileDB.PROFILE_FLAGS, flags)
        }
    }
}

data class Entry(
    var id: Long = -1,
    var profile: ProfileEntry? = null,
    var date: Date = Date(),
    var value: Float = 0f,  // Progress bar value
    var color: Int? = null,  // Not null if selected as a 'free' color by the user otherwise the color is determined from the min/max colors
    var flags: Int = 0
) : Comparable<Entry> {

    // Order Entries by date
    override fun compareTo(other: Entry): Int {
        return compareValuesBy(this, other, { it.date }, { it.id }, { it.value })
    }

    fun contentValues(): ContentValues {
        val entryDB = DatabaseContract.EntryDB
        return ContentValues().apply {
            put(entryDB.DATE, date.time)
            put(entryDB.VALUE, value)
            put(entryDB.PROFILE_FK, profile!!.id)
            put(entryDB.COLOR, color)
            put(entryDB.FLAGS, flags)
        }
    }
}


fun isValidDatabaseFile(file: String): Boolean {
    return try {
        val db = SQLiteDatabase.openDatabase(file, null, SQLiteDatabase.OPEN_READONLY)
        val valid = (DatabaseUtils.queryNumEntries(db, DatabaseContract.ProfileEntryDB.TABLE_NAME) > 0 &&
                DatabaseUtils.queryNumEntries(db, DatabaseContract.ProfileEntryDB.TABLE_NAME) >= 0)
        db.close()
        valid
    } catch (e: Exception) {
        false
    }
}


fun readProfileEntry(cursor: Cursor) : ProfileEntry {
    val profileDB = DatabaseContract.ProfileEntryDB
    val profile = ProfileEntry()
    with (cursor) {
        profile.id = getLong(getColumnIndexOrThrow(profileDB.ID))
        profile.name = getString(getColumnIndexOrThrow(profileDB.PROFILE_NAME))
        profile.minColor = getInt(getColumnIndexOrThrow(profileDB.MIN_COLOR))
        profile.maxColor = getInt(getColumnIndexOrThrow(profileDB.MAX_COLOR))
        profile.prefColor = getInt(getColumnIndexOrThrow(profileDB.PREF_COLOR))
        profile.bannerColor = getIntOrNull(getColumnIndexOrThrow(profileDB.BANNER_COLOR))
        profile.creationDate = Date(getLong(getColumnIndexOrThrow(profileDB.CREATION_DATE)))
        profile.type = ProfileType.fromInt(getInt(getColumnIndexOrThrow(profileDB.PROFILE_TYPE)))
        profile.flags = getIntOrNull(getColumnIndexOrThrow(profileDB.PROFILE_FLAGS)) ?: 0
    }
    return profile
}

fun readEntry(cursor: Cursor, profile: ProfileEntry? = null): Entry {
    val entryDB = DatabaseContract.EntryDB
    val entry = Entry(
        id = cursor.getLong(cursor.getColumnIndexOrThrow(entryDB.ID)),
        profile = profile,
        date = Date(cursor.getLong(cursor.getColumnIndexOrThrow(entryDB.DATE))),
        value = cursor.getFloat(cursor.getColumnIndexOrThrow(entryDB.VALUE)),
        color = cursor.getIntOrNull(cursor.getColumnIndexOrThrow(entryDB.COLOR)),
        flags = cursor.getIntOrNull(cursor.getColumnIndexOrThrow(entryDB.FLAGS)) ?: 0
    )
    return entry
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
        val profile = readProfileEntry(cursor)
        cursor.moveToNext()
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
        val entry = readEntry(cursor, profile)
        cursor.moveToNext()
        entry
    }
    cursor.close()
    return res
}


// Get the closest entry to the given date from a profile.
fun queryClosestEntry(db: SQLiteDatabase, profile: ProfileEntry, date: Date) : Entry {
    val entryDB = DatabaseContract.EntryDB

    val queryStr = """
        SELECT *
        FROM ${entryDB.TABLE_NAME}
        WHERE ${entryDB.PROFILE_FK} = ${profile.id}
            AND ${entryDB.DATE} < ${date.time}
        ORDER BY ${entryDB.DATE} DESC
        LIMIT 1
    """.trimIndent()

    val cursor = db.rawQuery(queryStr, null)
    cursor.moveToFirst()
    val entry = if (cursor.count > 0) {
        readEntry(cursor, profile)
    } else {
        Entry()
    }
    cursor.close()
    return entry
}


class DatabaseHelper : SQLiteOpenHelper {
    constructor(ctx : Context) : super(ctx, DatabaseContract.DB_NAME, null, DatabaseContract.DB_VERSION)
    constructor(ctx : Context?, name: String) : super(ctx, name, null, DatabaseContract.DB_VERSION)
    // TODO execute database commands in a coroutine

    override fun onCreate(db: SQLiteDatabase) {
        // "Multiple statements separated by semicolons are not supported."
        db.execSQL(DatabaseContract.ProfileEntryDB.DB_CREATE)
        db.execSQL(DatabaseContract.EntryDB.DB_CREATE)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        super.onDowngrade(db, oldVersion, newVersion)
        // TODO?
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        val profileDB = DatabaseContract.ProfileEntryDB
        val entryDB = DatabaseContract.EntryDB
        var version = oldVersion
        if (version == 1 && version < newVersion) {
            db.execSQL("""
                ALTER TABLE ${profileDB.TABLE_NAME}
                ADD COLUMN ${profileDB.BANNER_COLOR} INTEGER 
            """.trimIndent())
            db.execSQL("""
                ALTER TABLE ${profileDB.TABLE_NAME}
                ADD COLUMN ${profileDB.PROFILE_TYPE} INTEGER NOT NULL DEFAULT 0
            """.trimIndent())
            db.execSQL("""
                ALTER TABLE ${profileDB.TABLE_NAME}
                ADD COLUMN ${profileDB.PROFILE_FLAGS} INTEGER 
            """.trimIndent())

            version += 1
        }
        if (version == 2 && version < newVersion) {
            db.execSQL("""
                ALTER TABLE ${entryDB.TABLE_NAME}
                ADD COLUMN ${entryDB.COLOR} INTEGER 
            """.trimIndent())

            version += 1
        }
        if (version == 3 && version < newVersion) {
            db.execSQL("""
                ALTER TABLE ${entryDB.TABLE_NAME}
                ADD COLUMN ${entryDB.FLAGS} INTEGER
            """.trimIndent())
        }
    }

    fun insertProfile(profile: ProfileEntry): Long {
        val profileDB = DatabaseContract.ProfileEntryDB
        try {
            return writableDatabase.insertOrThrow(profileDB.TABLE_NAME, null, profile.contentValues())
        } catch (e: Exception) {
            Log.e(TAG, "insertProfile: ", e)
        }
        return -1
    }

    fun updateProfile(profile: ProfileEntry): Long {
        val profileDB = DatabaseContract.ProfileEntryDB
        writableDatabase.update(profileDB.TABLE_NAME, profile.contentValues(), "${profileDB.ID} = ${profile.id}", null)
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
        try {
            return writableDatabase.insertOrThrow(entryDB.TABLE_NAME, null, entry.contentValues())
        } catch (e: Exception) {
            Log.e(TAG, "insertEntry: ", e)
        }
        return -1
    }

    fun queryProfile(id: Long): ProfileEntry {
        val profileDB = DatabaseContract.ProfileEntryDB

        val queryStr = """
            SELECT *
            FROM ${profileDB.TABLE_NAME}
            WHERE _id = $id
        """.trimIndent()

        val cursor = writableDatabase.rawQuery(queryStr, null)
        cursor.moveToFirst()
        val profile = if (cursor.count == 0) ProfileEntry()
                      else readProfileEntry(cursor)
        cursor.close()
        return profile
    }

    fun queryAllProfiles(): Array<ProfileEntry> {
        return queryAllProfiles(writableDatabase)
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
                writableDatabase.insertOrThrow(entryDB.TABLE_NAME, null, entry.contentValues())
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
            val profile = readProfileEntry(cursor)
            cursor.moveToNext()
            profile
        }
        cursor.close()
        return res
    }

    fun queryClosestEntry(profile: ProfileEntry, date: Date) =
        queryClosestEntry(writableDatabase, profile, date)

    companion object {
        val TAG = DatabaseHelper::class.simpleName ?: "null"
    }
}

