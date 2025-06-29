package com.yehyun.whatshouldiweartoday.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// [핵심 수정] 데이터베이스 버전을 10에서 11로 올립니다.
@Database(entities = [ClothingItem::class, SavedStyle::class, StyleItemCrossRef::class], version = 12)
abstract class AppDatabase : RoomDatabase() {

    abstract fun clothingDao(): ClothingDao
    abstract fun styleDao(): StyleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "clothing_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}