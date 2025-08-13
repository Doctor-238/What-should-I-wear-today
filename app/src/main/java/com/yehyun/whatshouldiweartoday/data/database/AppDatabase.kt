package com.yehyun.whatshouldiweartoday.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File


@Database(entities = [ClothingItem::class, SavedStyle::class, StyleItemCrossRef::class], version = 13, exportSchema = false)

abstract class AppDatabase : RoomDatabase() {

    abstract fun clothingDao(): ClothingDao
    abstract fun styleDao(): StyleDao

    override fun clearAllTables() {
    }

    suspend fun clearAllData(context: Context) {
        withContext(Dispatchers.IO) {

            val allItems = clothingDao().getAllItemsLists()

            allItems.forEach { item ->
                try {
                    val file = File(item.imageUri)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
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
                    e.printStackTrace()
                }

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