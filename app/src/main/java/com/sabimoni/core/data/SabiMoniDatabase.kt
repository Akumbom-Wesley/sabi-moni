package com.sabimoni.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sabimoni.core.data.dao.CategoryDao
import com.sabimoni.core.data.dao.GroupContributionDao
import com.sabimoni.core.data.dao.GroupDao
import com.sabimoni.core.data.dao.MessageDao
import com.sabimoni.core.data.dao.ReminderDao
import com.sabimoni.core.data.dao.SavingsGoalDao
import com.sabimoni.core.data.dao.TransactionDao
import com.sabimoni.core.data.entity.CategoryEntity
import com.sabimoni.core.data.entity.GroupContributionEntity
import com.sabimoni.core.data.entity.GroupEntity
import com.sabimoni.core.data.entity.MessageEntity
import com.sabimoni.core.data.entity.ReminderEntity
import com.sabimoni.core.data.entity.SavingsGoalEntity
import com.sabimoni.core.data.entity.TransactionEntity

@Database(
    entities = [
        MessageEntity::class,
        CategoryEntity::class,
        GroupEntity::class,
        GroupContributionEntity::class,
        TransactionEntity::class,
        SavingsGoalEntity::class,
        ReminderEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SabiMoniDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun categoryDao(): CategoryDao
    abstract fun groupDao(): GroupDao
    abstract fun groupContributionDao(): GroupContributionDao
    abstract fun transactionDao(): TransactionDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        const val NAME = "sabimoni.db"
    }
}
