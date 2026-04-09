package com.zlearn.di

import android.content.Context
import androidx.room.Room
import com.zlearn.data.database.AppDatabase
import com.zlearn.data.database.QuestionDao
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.Provides
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "app_db").build()

    @Provides
    fun provideQuestionDao(db: AppDatabase): QuestionDao = db.questionDao()
}
