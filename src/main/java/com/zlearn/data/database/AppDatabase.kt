package com.zlearn.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.zlearn.data.model.User

@Database(entities = [QuestionEntity::class, User::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun userDao(): UserDao
}
