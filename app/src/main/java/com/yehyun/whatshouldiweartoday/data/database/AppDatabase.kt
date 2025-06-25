package com.yehyun.whatshouldiweartoday.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ClothingItem::class], version = 1)
abstract class AppDatabase : RoomDatabase() {

    abstract fun clothingDao(): ClothingDao

    //앱 전체에서 이 데이터베이스 인스턴스가 단 하나만 존재하도록 보장하는 역할
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "clothing_database" // 데이터베이스 파일 이름
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}