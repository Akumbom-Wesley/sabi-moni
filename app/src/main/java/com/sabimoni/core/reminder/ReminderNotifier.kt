package com.sabimoni.core.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sabimoni.R
import com.sabimoni.core.data.model.Contribution
import com.sabimoni.core.money.format
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the contribution reminder (FR4.3) with the cost of missing it in the body
 * (FR4.4).
 *
 * The penalty is the whole point of the notification. "Choir contribution due in 3 days"
 * is information; "5 000 FCFA due Friday · missing it costs 2 000 FCFA" is a reason to
 * act, and stopping penalty loss is what this pillar of the app exists for.
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /**
     * Returns false when the notification could not be posted because the user has not
     * granted the permission, so the caller can decide whether that is worth surfacing.
     */
    fun notifyDue(contribution: Contribution, today: LocalDate): Boolean {
        if (!canPost()) return false

        ensureChannel()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("${contribution.groupName} · ${contribution.amount.format()}")
            .setContentText(body(contribution, today))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body(contribution, today)))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()

        // One notification id per contribution, so a second reminder for the same
        // obligation replaces the first rather than stacking duplicates.
        NotificationManagerCompat.from(context)
            .notify(contribution.id.toNotificationId(), notification)
        return true
    }

    fun cancel(contributionId: Long) {
        NotificationManagerCompat.from(context).cancel(contributionId.toNotificationId())
    }

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Before the deadline the penalty is a warning; after it, it is a bill. The overdue
     * wording says what is owed *now* rather than repeating the original amount, because
     * that is the number the user has to find (FR4.4, ADR-0029).
     */
    private fun body(contribution: Contribution, today: LocalDate): String {
        val days = contribution.daysUntilDue(today)
        val fine = contribution.fineIncurred(today)

        if (days < 0L) {
            val late = "overdue by ${-days} ${plural(-days, "day")}"
            return if (fine == null) {
                late
            } else {
                "$late · fine of ${fine.format()} applies · " +
                    "${contribution.owedOn(today).format()} to settle"
            }
        }

        val timing = when (days) {
            0L -> "due today"
            1L -> "due tomorrow"
            else -> "due in $days days"
        }
        val warning = contribution.penalty
            ?.takeIf { !it.isZero }
            ?.let { " · missing it costs ${it.format()}" }
            .orEmpty()
        return timing + warning
    }

    private fun plural(count: Long, word: String) = if (count == 1L) word else "${word}s"

    private fun openApp(): PendingIntent {
        val intent = Intent(context, MAIN_ACTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Created on each post rather than at startup: creating a channel that already exists
     * is a no-op, and this keeps the app from declaring a notification channel it may
     * never use.
     */
    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Contribution reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Warns you before a group contribution is due."
            },
        )
    }

    private companion object {
        const val CHANNEL_ID = "contribution-reminders"
        val MAIN_ACTIVITY = com.sabimoni.MainActivity::class.java

        /** Ids are row ids, so an Int cast is safe long before it is a real concern. */
        fun Long.toNotificationId(): Int = (this % Int.MAX_VALUE).toInt()
    }
}
