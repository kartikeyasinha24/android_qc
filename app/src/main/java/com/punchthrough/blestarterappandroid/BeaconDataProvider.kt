package com.punchthrough.blestarterappandroid

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.BaseColumns

class BeaconDataProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.punchthrough.blestarterappandroid.provider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/beacon_data")
    }

    private lateinit var dbHelper: BeaconDataDbHelper

    override fun onCreate(): Boolean {
        dbHelper = BeaconDataDbHelper(context!!)
        return true
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? {
        return dbHelper.readableDatabase.query(
            BeaconDataContract.BeaconDataEntry.TABLE_NAME,
            projection, selection, selectionArgs, null, null, sortOrder
        )
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val id = dbHelper.writableDatabase.insert(BeaconDataContract.BeaconDataEntry.TABLE_NAME, null, values)
        return Uri.withAppendedPath(CONTENT_URI, id.toString())
    }

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        return dbHelper.writableDatabase.update(
            BeaconDataContract.BeaconDataEntry.TABLE_NAME, values, selection, selectionArgs
        )
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return dbHelper.writableDatabase.delete(
            BeaconDataContract.BeaconDataEntry.TABLE_NAME, selection, selectionArgs
        )
    }

    override fun getType(uri: Uri): String? {
        return null
    }
}