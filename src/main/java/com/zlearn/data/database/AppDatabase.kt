package com.zlearn.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [QuestionEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
}

