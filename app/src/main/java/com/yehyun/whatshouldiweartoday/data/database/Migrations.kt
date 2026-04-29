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

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE clothing_items ADD COLUMN fitMinWaist REAL")
        db.execSQL("ALTER TABLE clothing_items ADD COLUMN fitMaxWaist REAL")
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE clothing_items ADD COLUMN purchaseSource TEXT")
    }
}

val ALL_MIGRATIONS = arrayOf(
    MIGRATION_13_14,
    MIGRATION_14_15,
    MIGRATION_15_16,
    MIGRATION_16_17
)
