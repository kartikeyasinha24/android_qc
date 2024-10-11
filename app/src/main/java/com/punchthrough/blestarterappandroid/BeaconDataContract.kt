package com.punchthrough.blestarterappandroid

import android.provider.BaseColumns

object BeaconDataContract {
    object BeaconDataEntry : BaseColumns {
        const val TABLE_NAME = "beacon_data"
        const val COLUMN_UUID = "uuid"
        const val COLUMN_DATA = "data"
    }
}