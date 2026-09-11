package com.sabimoni.core.data

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Renames the stored `GroupType.CONTRIBUTION` to `CHURCH` (ADR-0027).
 *
 * Hand-written rather than an `@AutoMigration` because nothing about the *schema* changes
 * — `type` was and remains a TEXT column. What changes is the data inside it, which Room
 * cannot infer from two identical schemas.
 *
 * Without this, `GroupType.valueOf("CONTRIBUTION")` throws the moment an existing group is
 * read, which is every time the app opens.
 */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("UPDATE `money_groups` SET `type` = 'CHURCH' WHERE `type` = 'CONTRIBUTION'")
    }
}

/**
 * Drops `money_groups.type` (ADR-0028).
 *
 * Declared rather than inferred: Room refuses to guess that a vanished column was meant to
 * go, because the alternative reading — someone renamed it — would silently discard data.
 * `@DeleteColumn` is how you say you meant it, and Room then generates the table
 * recreation (SQLite cannot `DROP COLUMN` at this project's minimum API level).
 */
@DeleteColumn(tableName = "money_groups", columnName = "type")
class DropGroupType : AutoMigrationSpec
