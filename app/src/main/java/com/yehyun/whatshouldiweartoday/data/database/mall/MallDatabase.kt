package com.yehyun.whatshouldiweartoday.data.database.mall

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MallItem::class], version = 1, exportSchema = false)
abstract class MallDatabase : RoomDatabase() {

    abstract fun mallDao(): MallDao

    companion object {
        @Volatile
        private var INSTANCE: MallDatabase? = null

        fun getDatabase(context: Context): MallDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MallDatabase::class.java,
                    "mall_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
