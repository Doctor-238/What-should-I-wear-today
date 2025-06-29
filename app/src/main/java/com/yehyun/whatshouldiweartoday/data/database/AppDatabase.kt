package com.yehyun.whatshouldiweartoday.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Database(entities = [ClothingItem::class, SavedStyle::class, StyleItemCrossRef::class], version = 12, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun clothingDao(): ClothingDao
    abstract fun styleDao(): StyleDao

    // [오류 해결] clearAllData가 호출할, 모든 테이블을 삭제하는 함수
    override fun clearAllTables() {
        // 이 함수는 Room 라이브러리가 자동으로 구현해줍니다.
        // 우리는 이 함수를 호출하기만 하면 됩니다.
    }

    // [오류 해결] SettingsFragment에서 호출할 전체 데이터 삭제 함수
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