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
    version = 5,
    exportSchema = true,
    /**
     * Both of these recreate a table and copy every row, because SQLite can neither add a
     * foreign key nor drop a column with `ALTER TABLE` at this project's minimum API
     * level. Room derives that SQL from the exported schemas; hand-writing a twelve-column
     * recreation against a database holding real money would be the riskier option.
     *
     * - **v1 → v2** adds `transactions.groupId` (ADR-0025).
     * - **v4 → v5** adds the three recurrence columns to `money_groups` (ADR-0029). A pure
     *   addition with no foreign key, so this one is a plain `ALTER TABLE`.
     * - **v3 → v4** drops `money_groups.type` (ADR-0028), declared via [DropGroupType]
     *   because Room will not assume a vanished column was meant to go.
     *
     * **v2 → v3** is the odd one out and lives in `Migrations.kt`: it renamed a stored enum
     * value (ADR-0027), which is a data change Room cannot infer from two structurally
     * identical schemas. It is now vestigial — v4 deletes the column it wrote to — but it
     * has to stay, because a database exists in the world at version 3.
     */
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 3, to = 4, spec = DropGroupType::class),
        AutoMigration(from = 4, to = 5),
    ],
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
