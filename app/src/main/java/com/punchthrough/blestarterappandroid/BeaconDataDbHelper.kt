package com.punchthrough.blestarterappandroid

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns

private const val SQL_CREATE_ENTRIES =
    "CREATE TABLE ${BeaconDataContract.BeaconDataEntry.TABLE_NAME} (" +
            "${BaseColumns._ID} INTEGER PRIMARY KEY," +
            "${BeaconDataContract.BeaconDataEntry.COLUMN_UUID} TEXT," +
            "${BeaconDataContract.BeaconDataEntry.COLUMN_DATA} TEXT)"

private const val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS ${BeaconDataContract.BeaconDataEntry.TABLE_NAME}"

class BeaconDataDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "BeaconData.db"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL(SQL_DELETE_ENTRIES)
        onCreate(db)
    }
}