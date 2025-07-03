package com.yehyun.whatshouldiweartoday.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ▼▼▼▼▼ 핵심 수정 부분 ▼▼▼▼▼
@Database(entities = [ClothingItem::class, SavedStyle::class, StyleItemCrossRef::class], version = 13, exportSchema = false)
// ▲▲▲▲▲ 핵심 수정 부분 ▲▲▲▲▲
abstract class AppDatabase : RoomDatabase() {

    abstract fun clothingDao(): ClothingDao
    abstract fun styleDao(): StyleDao

    override fun clearAllTables() {
        // 이 함수는 Room 라이브러리가 자동으로 구현해줍니다.
    }

    suspend fun clearAllData() {
        withContext(Dispatchers.IO) {
            clearAllTables()
        }
    }


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