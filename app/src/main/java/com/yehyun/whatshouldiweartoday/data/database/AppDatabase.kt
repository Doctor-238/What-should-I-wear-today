package com.yehyun.whatshouldiweartoday.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// [수정] 데이터베이스 버전을 1에서 2로 올립니다.
@Database(entities = [ClothingItem::class], version = 2)
abstract class AppDatabase : RoomDatabase() {

    abstract fun clothingDao(): ClothingDao

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
                    // [추가] 앞으로 설계도가 또 바뀌면, 기존 데이터베이스를 지우고 새로 만들도록 설정합니다.
                    // 이렇게 하면 개발 중에 이 오류가 다시 발생하는 것을 막을 수 있습니다.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
