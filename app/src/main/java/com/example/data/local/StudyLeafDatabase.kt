package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DocumentEntity::class,
        AnnotationEntity::class,
        ConversionHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StudyLeafDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun conversionHistoryDao(): ConversionHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: StudyLeafDatabase? = null

        fun getInstance(context: Context): StudyLeafDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StudyLeafDatabase::class.java,
                    "studyleaf_local_vault.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
