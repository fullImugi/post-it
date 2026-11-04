package com.fullimugi.postit

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** One organized thing: a note, a list item (listName set) or a reminder (remindAt set). */
data class Item(
    val id: Long,
    val body: String,
    val tags: List<String>,
    val listName: String?,
    val checked: Boolean,
    val remindAt: Long?,
    val repeat: String?,
    val createdAt: Long,
)

/** One message you sent, plus the short "receipt" the app replied with. */
data class Message(val id: Long, val text: String, val reply: String, val createdAt: Long)

/** "watch …" → Movies. [titleCase] turns "step brothers" into "Step Brothers". */
data class ListRule(val id: Long, val keyword: String, val listName: String, val titleCase: Boolean)

class Db(ctx: Context) : SQLiteOpenHelper(ctx, "postit.db", null, 2) {

    companion object {
        val DEFAULT_RULES = listOf(
            Triple("watch", "Movies", true),
            Triple("binge", "Shows", true),
            Triple("read", "Books", true),
            Triple("listen to", "Music", true),
            Triple("visit", "Places", true),
            Triple("go to", "Places", true),
            Triple("cook", "Recipes", true),
            Triple("buy", "Shopping", false),
            Triple("pick up", "Shopping", false),
            Triple("grab", "Shopping", false),
            Triple("todo", "To-do", false),
            Triple("to-do", "To-do", false),
            Triple("to do", "To-do", false),
            Triple("task", "To-do", false),
        )
    }

    private fun createRules(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS rules(id INTEGER PRIMARY KEY AUTOINCREMENT, keyword TEXT NOT NULL, " +
                "list_name TEXT NOT NULL, title_case INTEGER NOT NULL DEFAULT 0)"
        )
        DEFAULT_RULES.forEach { (k, l, t) ->
            val v = ContentValues()
            v.put("keyword", k)
            v.put("list_name", l)
            v.put("title_case", if (t) 1 else 0)
            db.insert("rules", null, v)
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        createRules(db)
        db.execSQL(
            "CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT, body TEXT NOT NULL, " +
                "tags TEXT NOT NULL DEFAULT '', list_name TEXT, checked INTEGER NOT NULL DEFAULT 0, " +
                "remind_at INTEGER, repeat TEXT, created_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, " +
                "reply TEXT NOT NULL, created_at INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createRules(db)
    }

    fun rules(): List<ListRule> =
        readableDatabase.rawQuery("SELECT * FROM rules ORDER BY list_name COLLATE NOCASE, keyword", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        ListRule(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            keyword = c.getString(c.getColumnIndexOrThrow("keyword")),
                            listName = c.getString(c.getColumnIndexOrThrow("list_name")),
                            titleCase = c.getInt(c.getColumnIndexOrThrow("title_case")) == 1,
                        )
                    )
                }
            }
        }

    fun addRule(keyword: String, listName: String, titleCase: Boolean) {
        val k = keyword.trim().lowercase()
        writableDatabase.delete("rules", "keyword = ?", arrayOf(k))
        val v = ContentValues()
        v.put("keyword", k)
        v.put("list_name", listName.trim())
        v.put("title_case", if (titleCase) 1 else 0)
        writableDatabase.insert("rules", null, v)
    }

    fun deleteRule(id: Long) {
        writableDatabase.delete("rules", "id = ?", arrayOf(id.toString()))
    }

    fun insertItem(
        body: String,
        tags: List<String>,
        listName: String?,
        remindAt: Long?,
        repeat: String?,
        createdAt: Long,
    ): Long {
        val v = ContentValues()
        v.put("body", body)
        v.put("tags", tags.joinToString(","))
        if (listName != null) v.put("list_name", listName) else v.putNull("list_name")
        if (remindAt != null) v.put("remind_at", remindAt) else v.putNull("remind_at")
        if (repeat != null) v.put("repeat", repeat) else v.putNull("repeat")
        v.put("created_at", createdAt)
        return writableDatabase.insert("items", null, v)
    }

    fun insertMessage(text: String, reply: String, createdAt: Long) {
        val v = ContentValues()
        v.put("text", text)
        v.put("reply", reply)
        v.put("created_at", createdAt)
        writableDatabase.insert("messages", null, v)
    }

    fun items(): List<Item> =
        query("SELECT * FROM items ORDER BY created_at DESC, id DESC")

    fun item(id: Long): Item? =
        query("SELECT * FROM items WHERE id = ?", id.toString()).firstOrNull()

    fun nextReminder(now: Long): Item? =
        query(
            "SELECT * FROM items WHERE remind_at >= ? AND checked = 0 ORDER BY remind_at LIMIT 1",
            now.toString()
        ).firstOrNull()

    /** Newest first. */
    fun messages(limit: Int): List<Message> =
        readableDatabase.rawQuery(
            "SELECT * FROM messages ORDER BY created_at DESC, id DESC LIMIT $limit", null
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Message(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            text = c.getString(c.getColumnIndexOrThrow("text")),
                            reply = c.getString(c.getColumnIndexOrThrow("reply")),
                            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                        )
                    )
                }
            }
        }

    /** Reuse the spelling of an existing list so "groceries" and "Groceries" stay one list. */
    fun canonicalList(name: String): String =
        readableDatabase.rawQuery(
            "SELECT list_name FROM items WHERE list_name = ? COLLATE NOCASE LIMIT 1", arrayOf(name)
        ).use { c -> if (c.moveToFirst()) c.getString(0) else name }

    fun openCount(listName: String): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM items WHERE list_name = ? AND checked = 0", arrayOf(listName)
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun setChecked(id: Long, checked: Boolean) {
        val v = ContentValues()
        v.put("checked", if (checked) 1 else 0)
        writableDatabase.update("items", v, "id = ?", arrayOf(id.toString()))
    }

    fun setRemindAt(id: Long, at: Long) {
        val v = ContentValues()
        v.put("remind_at", at)
        writableDatabase.update("items", v, "id = ?", arrayOf(id.toString()))
    }

    fun delete(id: Long) {
        writableDatabase.delete("items", "id = ?", arrayOf(id.toString()))
    }

    fun clearChecked(listName: String) {
        writableDatabase.delete("items", "list_name = ? AND checked = 1", arrayOf(listName))
    }

    private fun query(sql: String, vararg args: String): List<Item> =
        readableDatabase.rawQuery(sql, args).use { c -> buildList { while (c.moveToNext()) add(read(c)) } }

    private fun read(c: Cursor): Item {
        val remindIdx = c.getColumnIndexOrThrow("remind_at")
        val listIdx = c.getColumnIndexOrThrow("list_name")
        val repeatIdx = c.getColumnIndexOrThrow("repeat")
        return Item(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            body = c.getString(c.getColumnIndexOrThrow("body")),
            tags = c.getString(c.getColumnIndexOrThrow("tags")).split(',').filter { it.isNotBlank() },
            listName = if (c.isNull(listIdx)) null else c.getString(listIdx),
            checked = c.getInt(c.getColumnIndexOrThrow("checked")) == 1,
            remindAt = if (c.isNull(remindIdx)) null else c.getLong(remindIdx),
            repeat = if (c.isNull(repeatIdx)) null else c.getString(repeatIdx),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
        )
    }
}

object Store {
    @Volatile
    private var instance: Db? = null

    /** Bumped on every change so the UI reloads. */
    val version = MutableStateFlow(0)

    fun db(ctx: Context): Db =
        instance ?: synchronized(this) {
            instance ?: Db(ctx.applicationContext).also { instance = it }
        }

    fun changed(ctx: Context) {
        version.update { it + 1 }
        NoteWidget.refresh(ctx.applicationContext)
    }
}
