package com.yehyun.whatshouldiweartoday.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ▼▼▼▼▼ 핵심 수정 부분 ▼▼▼▼▼
@Database(entities = [ClothingItem::class, SavedStyle::class, StyleItemCrossRef::class], version = 13, exportSchema = false)
// ▲▲▲▲▲ 핵심 수정 부분 ▲▲▲▲▲
abstract class AppDatabase : RoomDatabase() {

    abstract fun clothingDao(): ClothingDao
    abstract fun styleDao(): StyleDao

    override fun clearAllTables() {
        // 이 함수는 Room 라이브러리가 자동으로 구현해줍니다.
    }

    suspend fun clearAllData(context: Context) {
        withContext(Dispatchers.IO) {
            // 1. DB에서 모든 옷 아이템의 정보를 가져옵니다.
            val allItems = clothingDao().getAllItemsList()

            // 2. 각 아이템 정보에 있는 이미지 경로(imagePath)를 이용해 실제 파일을 삭제합니다.
            allItems.forEach { item ->
                try {
                    val file = File(item.imageUri)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    // 파일 삭제 중 오류가 발생할 경우를 대비한 예외 처리입니다.
                    e.printStackTrace()
                }
            }
            allItems.forEach { item ->
                try {
                    val file = File(item.processedImageUri)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    // 파일 삭제 중 오류가 발생할 경우를 대비한 예외 처리입니다.
                    e.printStackTrace()
                }

                // 3. 데이터베이스의 모든 테이블 데이터를 삭제합니다.
                clearAllTables()
            }
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