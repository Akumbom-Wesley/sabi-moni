package com.sabimoni.core.data.repository

import androidx.room.withTransaction
import com.sabimoni.core.ai.TransactionDraft
import com.sabimoni.core.data.SabiMoniDatabase
import com.sabimoni.core.data.dao.CategoryDao
import com.sabimoni.core.data.dao.GroupContributionDao
import com.sabimoni.core.data.dao.GroupDao
import com.sabimoni.core.data.dao.MessageDao
import com.sabimoni.core.data.dao.TransactionDao
import com.sabimoni.core.data.entity.Direction
import com.sabimoni.core.data.entity.MessageEntity
import com.sabimoni.core.data.entity.MessageSource
import com.sabimoni.core.data.entity.TransactionEntity
import com.sabimoni.core.data.model.Category
import com.sabimoni.core.data.model.DayTotals
import com.sabimoni.core.data.model.EditorOptions
import com.sabimoni.core.data.model.MoneyGroup
import com.sabimoni.core.money.Money
import com.sabimoni.core.parse.stripRestatedAmount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val database: SabiMoniDatabase,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val groupDao: GroupDao,
    private val contributionDao: GroupContributionDao,
    private val messageDao: MessageDao,
    // Depends on GroupRepository, not the reverse: GroupRepository reaches for
    // `transactionDao` directly, so there is no cycle.
    private val groupRepository: GroupRepository,
    private val clock: Clock,
) {

    /**
     * Persists what the parser understood and marks the message parsed, in one database
     * transaction. Splitting the two would let a crash leave transactions written against
     * a message still queued as pending, which the worker would then parse again —
     * duplicating every entry. See docs/adr/0017-parse-worker-and-failure-policy.md.
     *
     * A message that yields no entries is still PARSED, not FAILED: "happy birthday mum"
     * is a message with nothing to log, not an error.
     */
    suspend fun commitParse(message: MessageEntity, drafts: List<TransactionDraft>) {
        val now = Instant.now(clock)
        val today = LocalDate.now(clock)

        database.withTransaction {
            val entities = drafts
                // A zero or negative amount is not a thing that happened. The response
                // schema cannot express "positive", so it is enforced here.
                .filter { it.amountXaf > 0L }
                .map { draft -> draft.toEntity(message, today, now) }

            if (entities.isNotEmpty()) {
                transactionDao.insertAll(entities)
            }
            messageDao.markParsed(message.id, now)
        }
    }

    suspend fun markFailed(messageId: Long, reason: String) {
        messageDao.markFailed(messageId, reason)
    }

    /** Records a failed attempt while still intending to retry — see ADR-0021. */
    suspend fun noteAttemptFailure(messageId: Long, reason: String) {
        messageDao.noteAttemptFailure(messageId, reason)
    }

    /**
     * What the editor's pickers may offer: existing categories and existing groups.
     * Neither is ever created from the editor — ADR-0017 for categories, ADR-0025 for
     * groups.
     */
    fun observeEditorOptions(): Flow<EditorOptions> = combine(
        categoryDao.observeActive(),
        groupRepository.observeGroups(),
    ) { categories, groups ->
        EditorOptions(
            categories = categories.map { Category(id = it.id, name = it.name) },
            groups = groups,
        )
    }

    /**
     * The running total for one day (FR1.5), summed over every entry dated that day —
     * parser-produced, SMS-detected and manual alike.
     *
     * Summed in Kotlin off the single day query rather than by two SUM queries: one
     * subscription instead of two, and one day of one person's spending is a handful of
     * rows.
     */
    fun observeDayTotals(day: LocalDate): Flow<DayTotals> =
        transactionDao.observeForDay(day).map { rows ->
            DayTotals(
                income = rows.totalFor(Direction.INCOME),
                expense = rows.totalFor(Direction.EXPENSE),
            )
        }

    /** Income minus expense over everything logged — the headline on the capture screen. */
    fun observeBalance(): Flow<Money> = transactionDao.observeBalance().map(::Money)

    /** Form entry, the fallback for when tapping is faster than typing (FR1.6). */
    suspend fun addManualEntry(
        amount: Money,
        direction: Direction,
        categoryId: Long?,
        groupId: Long?,
        note: String?,
        occurredOn: LocalDate,
    ): Long = transactionDao.insert(
        TransactionEntity(
            // No message: this was not interpreted from anything, so there is nothing for
            // it to sit under in the thread.
            messageId = null,
            occurredOn = occurredOn,
            amountXaf = amount.xaf,
            direction = direction,
            categoryId = categoryId,
            // A hand-entered gift to a group, settling no announced obligation — so
            // `groupContributionId` stays null (ADR-0025).
            groupId = groupId,
            note = note?.takeIf(String::isNotBlank),
            autoDetected = false,
            createdAt = Instant.now(clock),
        ),
    )

    /**
     * Applies a tapped correction (FR1.4). Deliberately does not touch the message: the
     * raw text stays exactly as it was sent, and the message stays PARSED rather than
     * being re-interpreted — v1 corrections are taps, not dialogue (spec §6).
     */
    suspend fun correctEntry(
        id: Long,
        amount: Money,
        direction: Direction,
        categoryId: Long?,
        groupId: Long?,
        note: String?,
        occurredOn: LocalDate,
    ) {
        val existing = transactionDao.byId(id) ?: return

        // A transaction that settles an announced obligation takes its group from that
        // obligation, so the existing value wins here whatever the caller passed. Letting
        // a correction move it would leave `groupId` disagreeing with the contribution's
        // own group — the one thing ADR-0025's two references must never do.
        val effectiveGroupId =
            if (existing.groupContributionId != null) existing.groupId else groupId

        transactionDao.applyCorrection(
            id = id,
            amountXaf = amount.xaf,
            direction = direction,
            categoryId = categoryId,
            groupId = effectiveGroupId,
            note = note?.takeIf(String::isNotBlank),
            occurredOn = occurredOn,
        )
    }

    /**
     * Removes entries the parser should never have produced. The messages are left alone —
     * what was said is the audit record; only what we concluded from it is editable.
     *
     * Takes a selection rather than one id so a multi-entry delete is one statement: the
     * running total lands on its new value once instead of ticking down per entry.
     */
    suspend fun deleteEntries(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val idList = ids.toList()

        // Which obligations these payments were settling, read *before* the delete, while
        // the links still exist.
        val settled = contributionDao.contributionsSettledBy(idList)

        transactionDao.deleteByIds(idList)

        // An obligation still reading Paid after the payment that settled it has been
        // deleted is the owed view lying about what you owe. Only reverted once nothing
        // else still pays for it, since a contribution may be settled in instalments
        // (ADR-0026).
        settled.forEach { contributionId ->
            if (contributionDao.paymentCountFor(contributionId) == 0) {
                groupRepository.revertToPending(contributionId)
            }
        }
    }

    private suspend fun TransactionDraft.toEntity(
        message: MessageEntity,
        today: LocalDate,
        now: Instant,
    ) = TransactionEntity(
        messageId = message.id,
        occurredOn = occurredOn ?: today,
        amountXaf = amountXaf,
        direction = direction,
        // Only ever resolved against categories that already exist — the model is not
        // allowed to invent taxonomy the user would then have to maintain (ADR-0017).
        categoryId = category?.let { categoryDao.byNameIgnoreCase(it)?.id },
        // The group the parser matched, resolved on exactly the same terms: matched
        // against existing groups, never creating one. This is what ADR-0017 could not do
        // and ADR-0025 unblocked — "gave 5000 to choir" now lands on the choir record
        // instead of surviving as a word in the note.
        groupId = matchedGroup?.let { groupDao.byNameIgnoreCase(it)?.id },
        // The amount is a field, not prose. A note that restates it would contradict the
        // entry the moment the user corrects the amount — see ADR-0020.
        note = stripRestatedAmount(note, amountXaf),
        moneySource = moneySource,
        autoDetected = message.source == MessageSource.SMS,
        createdAt = now,
    )
}

private fun List<TransactionEntity>.totalFor(direction: Direction): Money =
    Money(filter { it.direction == direction }.sumOf { it.amountXaf })
