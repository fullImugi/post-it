package com.fullimugi.postit

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/**
 * The home-screen "messenger": your recent messages as chat bubbles with the app's
 * reply under each, and a message bar that opens the quick-capture pop-up.
 */
class NoteWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        update(ctx, mgr, ids)
    }

    companion object {
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, NoteWidget::class.java))
            if (ids.isNotEmpty()) update(ctx, mgr, ids)
        }

        @Suppress("DEPRECATION")
        private fun update(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            ids.forEach { mgr.updateAppWidget(it, build(ctx, it)) }
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.list)
        }

        @Suppress("DEPRECATION")
        private fun build(ctx: Context, widgetId: Int): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget)

            val svc = Intent(ctx, WidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
            rv.setRemoteAdapter(R.id.list, svc)
            rv.setEmptyView(R.id.list, R.id.empty)

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val openApp = PendingIntent.getActivity(
                ctx, 10,
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                flags
            )
            rv.setOnClickPendingIntent(R.id.header, openApp)

            val openReminders = PendingIntent.getActivity(
                ctx, 11,
                Intent(ctx, MainActivity::class.java)
                    .putExtra("tab", Tab.Reminders.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                flags
            )
            rv.setOnClickPendingIntent(R.id.next, openReminders)

            val type = PendingIntent.getActivity(
                ctx, 12,
                Intent(ctx, QuickCaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                flags
            )
            rv.setOnClickPendingIntent(R.id.compose, type)

            val voice = PendingIntent.getActivity(
                ctx, 13,
                Intent(ctx, QuickCaptureActivity::class.java)
                    .putExtra(QuickCaptureActivity.EXTRA_VOICE, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                flags
            )
            rv.setOnClickPendingIntent(R.id.mic, voice)

            // Tapping a bubble opens the app.
            val template = PendingIntent.getActivity(
                ctx, 14,
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            rv.setPendingIntentTemplate(R.id.list, template)

            val next = Store.db(ctx).nextReminder(System.currentTimeMillis())
            val nextAt = next?.remindAt
            if (next != null && nextAt != null) {
                rv.setTextViewText(R.id.next, "Next: ${next.body} · ${Fmt.whenText(nextAt)}")
                rv.setViewVisibility(R.id.next, View.VISIBLE)
            } else {
                rv.setViewVisibility(R.id.next, View.INVISIBLE)
            }
            return rv
        }
    }
}

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val ctx: Context) : RemoteViewsFactory {
        private val tagRe = Regex("""#([\p{L}\p{N}_-]+)""")
        private val bubbles = intArrayOf(
            R.drawable.bubble_0, R.drawable.bubble_1, R.drawable.bubble_2,
            R.drawable.bubble_3, R.drawable.bubble_4, R.drawable.bubble_5,
        )
        private var messages: List<Message> = emptyList()

        override fun onCreate() {}
        override fun onDestroy() {}

        override fun onDataSetChanged() {
            // Oldest to newest, so the newest sits right above the message bar.
            messages = Store.db(ctx).messages(20).reversed()
        }

        override fun getCount(): Int = messages.size

        override fun getViewAt(position: Int): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_item)
            val m = messages.getOrNull(position) ?: return rv
            rv.setTextViewText(R.id.text, m.text)
            val key = tagRe.find(m.text)?.groupValues?.get(1) ?: "m${m.id}"
            rv.setInt(R.id.text, "setBackgroundResource", bubbles[Math.floorMod(key.lowercase().hashCode(), bubbles.size)])
            rv.setTextViewText(R.id.meta, "✓ ${m.reply} · ${Fmt.ago(m.createdAt)}")
            rv.setOnClickFillInIntent(R.id.row, Intent())
            return rv
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long = messages.getOrNull(position)?.id ?: position.toLong()
        override fun hasStableIds(): Boolean = true
    }
}
