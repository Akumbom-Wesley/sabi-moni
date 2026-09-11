package com.sabimoni.core.data

import androidx.room.AutoMigration
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
    version = 2,
    exportSchema = true,
    /**
     * v2 adds `transactions.groupId` (ADR-0025). SQLite cannot add a foreign key with
     * `ALTER TABLE`, so the table has to be recreated and the rows copied — which is
     * exactly the SQL Room derives from the two exported schemas. Hand-writing that
     * recreation would be the riskier option against a database holding real money.
     */
    autoMigrations = [AutoMigration(from = 1, to = 2)],
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
