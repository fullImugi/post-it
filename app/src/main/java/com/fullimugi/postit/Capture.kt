package com.fullimugi.postit

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** Takes one message, files it, and returns the short reply shown under it. */
object Capture {
    fun capture(ctx: Context, raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return ""
        val db = Store.db(ctx)
        val p = Parser.parse(text, db.rules())
        val now = System.currentTimeMillis()
        val tagText = p.tags.joinToString(" ") { "#$it" }

        val reminder = p.reminder
        val listName = p.listName
        val reply = when {
            reminder != null -> {
                val at = reminder.at.millis()
                val id = db.insertItem(reminder.body, p.tags, null, at, reminder.repeat, now)
                Reminders.schedule(ctx, id, at)
                buildString {
                    append("Reminder set · ")
                    append(Fmt.whenText(at))
                    Repeat.label(reminder.repeat)?.let { append(" · ").append(it) }
                    if (reminder.defaulted) append(" (no time found, so in 1 hour)")
                }
            }
            listName != null -> {
                val name = db.canonicalList(listName)
                p.listItems.forEach { db.insertItem(it, p.tags, name, null, null, now) }
                val n = p.listItems.size
                "Added $n to $name · ${db.openCount(name)} open"
            }
            else -> {
                db.insertItem(p.body, p.tags, null, null, null, now)
                if (tagText.isEmpty()) "Saved" else "Saved to $tagText"
            }
        }
        db.insertMessage(text, reply, now)
        Store.changed(ctx)
        return reply
    }
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(Reminders.CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH)
        )
    }
}
