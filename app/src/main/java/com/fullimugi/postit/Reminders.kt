package com.fullimugi.postit

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

fun LocalDateTime.millis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun Long.ldt(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

object Fmt {
    private val timeF = DateTimeFormatter.ofPattern("h:mm a")
    private val fullF = DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a")
    private val dayF = DateTimeFormatter.ofPattern("MMM d")

    fun whenText(ms: Long): String {
        val d = ms.ldt()
        val today = LocalDate.now()
        return when (d.toLocalDate()) {
            today -> "Today at " + d.format(timeF)
            today.plusDays(1) -> "Tomorrow at " + d.format(timeF)
            else -> d.format(fullF)
        }
    }

    fun ago(ms: Long): String {
        val d = ms.ldt()
        val today = LocalDate.now()
        return when (d.toLocalDate()) {
            today -> d.format(timeF)
            today.minusDays(1) -> "Yesterday"
            else -> d.format(dayF)
        }
    }

    fun iso(ms: Long): String = ms.ldt().withNano(0).toString()
}

/** Repeat rules are stored as "DAILY" or "DOW:1,5" (ISO weekdays, Monday = 1). */
object Repeat {
    fun days(r: String): Set<Int>? =
        if (r.startsWith("DOW:")) r.removePrefix("DOW:").split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
        else null

    /** First time on or after [from] (same clock time) that is after [now] and matches the rule. */
    fun next(r: String, from: LocalDateTime, now: LocalDateTime): LocalDateTime {
        val ds = days(r)
        var a = from
        var guard = 0
        while ((!a.isAfter(now) || (ds != null && ds.isNotEmpty() && a.dayOfWeek.value !in ds)) && guard++ < 5000) {
            a = a.plusDays(1)
        }
        return a
    }

    fun label(r: String?): String? {
        if (r == null) return null
        if (r == "DAILY") return "Every day"
        val ds = days(r)?.sorted() ?: return null
        return when (ds) {
            listOf(1, 2, 3, 4, 5) -> "Every weekday"
            listOf(6, 7) -> "Every weekend"
            else -> "Every " + ds.joinToString(", ") {
                DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }
        }
    }
}

object Reminders {
    const val CHANNEL = "reminders"
    const val ACTION_FIRE = "com.fullimugi.postit.FIRE"
    const val ACTION_DONE = "com.fullimugi.postit.DONE"
    const val ACTION_SNOOZE = "com.fullimugi.postit.SNOOZE"

    fun schedule(ctx: Context, id: Long, at: Long, snooze: Boolean = false) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(ctx, id, snooze)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun cancel(ctx: Context, id: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(alarmIntent(ctx, id, false))
        am.cancel(alarmIntent(ctx, id, true))
        ctx.getSystemService(NotificationManager::class.java)?.cancel(id.toInt())
    }

    /** Re-arm every pending reminder (after a reboot, an update, or exact-alarm permission changes). */
    fun rescheduleAll(ctx: Context) {
        val db = Store.db(ctx)
        val now = System.currentTimeMillis()
        var changed = false
        db.items().filter { it.remindAt != null && !it.checked }.forEach { item ->
            var at = item.remindAt ?: return@forEach
            val rep = item.repeat
            if (at < now && rep != null) {
                at = Repeat.next(rep, at.ldt(), LocalDateTime.now()).millis()
                db.setRemindAt(item.id, at)
                changed = true
            }
            if (at >= now) schedule(ctx, item.id, at)
        }
        if (changed) Store.changed(ctx)
    }

    private fun alarmIntent(ctx: Context, id: Long, snooze: Boolean): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra("id", id)
            .putExtra("snooze", snooze)
        val code = if (snooze) id.toInt() + 1_000_000 else id.toInt()
        return PendingIntent.getBroadcast(
            ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun notify(ctx: Context, item: Item) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(
            ctx, item.id.toInt(),
            Intent(ctx, MainActivity::class.java)
                .putExtra("tab", Tab.Reminders.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags
        )
        val done = PendingIntent.getBroadcast(
            ctx, item.id.toInt() + 2_000_000,
            Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_DONE).putExtra("id", item.id),
            flags
        )
        val snooze = PendingIntent.getBroadcast(
            ctx, item.id.toInt() + 3_000_000,
            Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_SNOOZE).putExtra("id", item.id),
            flags
        )
        val noIcon: Icon? = null
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(item.body)
            .setContentText(Repeat.label(item.repeat) ?: "Reminder")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(noIcon, if (item.repeat != null) "Stop" else "Done", done).build())
            .addAction(Notification.Action.Builder(noIcon, "Snooze 10 min", snooze).build())
            .build()
        nm.notify(item.id.toInt(), n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1L)
        if (id < 0) return
        val db = Store.db(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        when (intent.action) {
            Reminders.ACTION_DONE -> {
                nm?.cancel(id.toInt())
                db.setChecked(id, true)
                Reminders.cancel(ctx, id)
                Store.changed(ctx)
            }
            Reminders.ACTION_SNOOZE -> {
                nm?.cancel(id.toInt())
                Reminders.schedule(ctx, id, System.currentTimeMillis() + 10 * 60_000L, snooze = true)
            }
            else -> {
                val item = db.item(id) ?: return
                if (item.checked) return
                Reminders.notify(ctx, item)
                val rep = item.repeat
                if (!intent.getBooleanExtra("snooze", false) && rep != null) {
                    val from = (item.remindAt ?: System.currentTimeMillis()).ldt()
                    val next = Repeat.next(rep, from, LocalDateTime.now()).millis()
                    db.setRemindAt(id, next)
                    Reminders.schedule(ctx, id, next)
                }
                Store.changed(ctx)
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Reminders.rescheduleAll(ctx)
        }
    }
}
