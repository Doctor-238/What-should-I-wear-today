package com.yehyun.whatshouldiweartoday.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE clothing_items ADD COLUMN fitMinHeight REAL")
        db.execSQL("ALTER TABLE clothing_items ADD COLUMN fitMaxHeight REAL")
        db.execSQL("ALTER TABLE clothing_items ADD COLUMN fitMinWeight REAL")
        db.execSQL("ALTER TABLE clothing_items ADD COLUMN fitMaxWeight REAL")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE clothing_items ADD COLUMN purpose TEXT NOT NULL DEFAULT ''")
    }
}

val ALL_MIGRATIONS = arrayOf(
    MIGRATION_13_14,
    MIGRATION_14_15
)
