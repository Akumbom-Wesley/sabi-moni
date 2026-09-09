package com.sabimoni.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "messages", indices = [Index("status"), Index("sentAt")])
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val rawText: String,
    val source: MessageSource,
    val sentAt: Instant,
    val status: ParseStatus,
    val parsedAt: Instant? = null,
    val failureReason: String? = null,
)

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
)

// Named `money_groups` rather than `groups`, which SQLite reserves for window frames.
@Entity(tableName = "money_groups", indices = [Index(value = ["name"], unique = true)])
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: GroupType = GroupType.CONTRIBUTION,
    val penaltyXaf: Long? = null,
    val reminderLeadDays: Int = 2,
    val isArchived: Boolean = false,
)

@Entity(
    tableName = "group_contributions",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId"), Index("dueDate"), Index("status")],
)
data class GroupContributionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val groupId: Long,
    val amountXaf: Long,
    val dueDate: LocalDate,
    val status: ContributionStatus = ContributionStatus.PENDING,
    val paidDate: LocalDate? = null,
    val note: String? = null,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = GroupContributionEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupContributionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("messageId"),
        Index("categoryId"),
        Index("groupContributionId"),
        Index("occurredOn"),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val messageId: Long? = null,
    val occurredOn: LocalDate,
    val amountXaf: Long,
    val direction: Direction,
    val categoryId: Long? = null,
    val note: String? = null,
    val moneySource: MoneySource = MoneySource.UNKNOWN,
    val groupContributionId: Long? = null,
    val autoDetected: Boolean = false,
    val createdAt: Instant,
)

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val targetXaf: Long,
    val currentXaf: Long = 0L,
    val targetDate: LocalDate? = null,
    val createdAt: Instant,
    val isArchived: Boolean = false,
)

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = GroupContributionEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupContributionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupContributionId"), Index("remindAt")],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val groupContributionId: Long,
    val remindAt: Instant,
    val firedAt: Instant? = null,
)
