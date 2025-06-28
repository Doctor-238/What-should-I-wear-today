package com.yehyun.whatshouldiweartoday.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// [수정] 데이터베이스 버전을 6에서 7로 올립니다.
@Database(entities = [ClothingItem::class, SavedStyle::class, StyleItemCrossRef::class], version = 7)
abstract class AppDatabase : RoomDatabase() {

    abstract fun clothingDao(): ClothingDao
    abstract fun styleDao(): StyleDao // [추가] 스타일 DAO를 위한 추상 함수
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
