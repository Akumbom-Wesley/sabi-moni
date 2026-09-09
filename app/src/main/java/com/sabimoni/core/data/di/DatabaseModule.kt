package com.sabimoni.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sabimoni.core.data.SabiMoniDatabase
import com.sabimoni.core.data.dao.CategoryDao
import com.sabimoni.core.data.dao.GroupContributionDao
import com.sabimoni.core.data.dao.GroupDao
import com.sabimoni.core.data.dao.MessageDao
import com.sabimoni.core.data.dao.ReminderDao
import com.sabimoni.core.data.dao.SavingsGoalDao
import com.sabimoni.core.data.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val DEFAULT_CATEGORIES = listOf(
    "Transport",
    "Food",
    "Airtime/Data",
    "School",
    "Groups",
    "Savings",
    "Misc",
)

private object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        DEFAULT_CATEGORIES.forEachIndexed { index, name ->
            db.execSQL(
                "INSERT INTO categories (name, sortOrder, isArchived) VALUES (?, ?, 0)",
                arrayOf<Any>(name, index),
            )
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SabiMoniDatabase =
        Room.databaseBuilder(context, SabiMoniDatabase::class.java, SabiMoniDatabase.NAME)
            .addCallback(SeedCallback)
            .build()

    @Provides
    fun provideMessageDao(db: SabiMoniDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideCategoryDao(db: SabiMoniDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideGroupDao(db: SabiMoniDatabase): GroupDao = db.groupDao()

    @Provides
    fun provideGroupContributionDao(db: SabiMoniDatabase): GroupContributionDao =
        db.groupContributionDao()

    @Provides
    fun provideTransactionDao(db: SabiMoniDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideSavingsGoalDao(db: SabiMoniDatabase): SavingsGoalDao = db.savingsGoalDao()

    @Provides
    fun provideReminderDao(db: SabiMoniDatabase): ReminderDao = db.reminderDao()
}
