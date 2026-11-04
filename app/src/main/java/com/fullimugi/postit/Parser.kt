package com.fullimugi.postit

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class ReminderSpec(
    val body: String,
    val at: LocalDateTime,
    val repeat: String?,
    /** True when no time was found and we fell back to "in 1 hour". */
    val defaulted: Boolean,
)

data class Parsed(
    val tags: List<String>,
    val body: String,
    val listName: String? = null,
    val listItems: List<String> = emptyList(),
    val reminder: ReminderSpec? = null,
)

/**
 * Turns a message into something organized.
 *
 *  - `#tag` anywhere files the note under that tag.
 *  - "remind me …" / "reminder …" makes a reminder (times, days, "every …").
 *  - "buy …", "todo …", "+listname …", "add … to my X list" add checklist items
 *    (commas split items).
 *  - Anything else is a plain note.
 */
object Parser {
    private val I = RegexOption.IGNORE_CASE
    private val IS = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

    private val tagRe = Regex("""(?<![\p{L}\p{N}_&/#])#([\p{L}\p{N}_-]+)""")
    private val remindRe = Regex("""\b(?:remind\s+me|reminder)\b:?""", I)

    private const val DAYS = "monday|tuesday|wednesday|thursday|friday|saturday|sunday"

    fun parse(raw: String, rules: List<ListRule> = emptyList(), now: LocalDateTime = LocalDateTime.now()): Parsed {
        val tags = tagRe.findAll(raw).map { it.groupValues[1].lowercase() }.distinct().toList()
        val text = tidy(tagRe.replace(raw, " "))
        val body = text.ifBlank { raw.trim() }

        if (remindRe.containsMatchIn(text)) {
            return Parsed(tags, body, reminder = parseReminder(remindRe.replace(text, " "), now))
        }
        val list = listMatch(text, rules)
        if (list != null && list.second.isNotEmpty()) {
            return Parsed(tags, body, listName = list.first, listItems = list.second)
        }
        return Parsed(tags, body)
    }

    // ---------- lists ----------

    private val intentRe = Regex(
        """^(?:i\s+)?(?:really\s+)?(?:want\s+to|wanna|need\s+to|have\s+to|gotta|got\s+to|should|must|remember\s+to|gonna|going\s+to)\s+""",
        I
    )
    private val mediaRe = Regex("""^(?:the\s+)?(?:movie|film|show|series|tv\s+show|book|novel|album|song|podcast|restaurant)\s+""", I)

    private fun listMatch(t: String, rules: List<ListRule>): Pair<String, List<String>>? {
        Regex("""^\+([\p{L}\p{N}_-]+)[:\s]+(.+)$""", IS).find(t)?.let {
            return pretty(it.groupValues[1]) to split(it.groupValues[2])
        }
        Regex("""^(?:add|put)\s+(.+?)\s+(?:to|on)\s+(?:my\s+|the\s+)?(.+?)\s+list[.!]?$""", IS).find(t)?.let {
            return pretty(it.groupValues[2]) to split(it.groupValues[1])
        }
        // "I want to watch step brothers" → "watch step brothers"
        val s = intentRe.replace(t, "")
        for (r in rules.sortedByDescending { it.keyword.length }) {
            val words = r.keyword.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
            if (words.isEmpty()) continue
            val kw = words.joinToString("""\s+""") { Regex.escape(it) }
            val m = Regex("""^$kw(?![\p{L}\p{N}])[:\s]+(.+)$""", IS).find(s) ?: continue
            val items = split(m.groupValues[1]).map { mediaRe.replace(it, "").trim() }.filter { it.isNotEmpty() }
            if (items.isEmpty()) continue
            return r.listName to (if (r.titleCase) items.map { titleCase(it) } else items.map { cap(it) })
        }
        return null
    }

    private val small = setOf("a", "an", "the", "and", "or", "of", "in", "on", "at", "to", "for", "by", "with", "vs")

    /** "step brothers" → "Step Brothers"; leaves words like "iPhone" alone. */
    private fun titleCase(s: String): String =
        s.split(" ").mapIndexed { i, w ->
            when {
                w.isEmpty() -> w
                w != w.lowercase() -> w
                i > 0 && w in small -> w
                else -> w.replaceFirstChar { it.uppercaseChar() }
            }
        }.joinToString(" ")

    private fun split(s: String): List<String> =
        s.split(Regex("""[,;\n]+"""))
            .map { it.trim().trimEnd('.', '!') }
            .filter { it.isNotEmpty() }
            .map { cap(it) }

    private fun pretty(name: String): String = cap(name.replace('_', ' '))

    // ---------- reminders ----------

    private fun parseReminder(input: String, now: LocalDateTime): ReminderSpec {
        var s = " $input "
        fun take(re: Regex): MatchResult? {
            val m = re.find(s) ?: return null
            s = s.replaceRange(m.range, " ")
            return m
        }

        var repeat: String? = null
        var date: LocalDate? = null
        var weekday: DayOfWeek? = null
        var time: LocalTime? = null
        var relative: Duration? = null
        var defaultHour = 9
        var found = false

        // "in 20 minutes", "in an hour", "in half an hour"
        if (take(Regex("""\bin\s+half\s+an?\s+hour\b""", I)) != null) {
            relative = Duration.ofMinutes(30)
        } else {
            take(Regex("""\bin\s+(\d+|an?|one)\s*(minutes?|mins?|hours?|hrs?|days?|weeks?)\b""", I))?.let { m ->
                val n = m.groupValues[1].toLongOrNull() ?: 1L
                val unit = m.groupValues[2].lowercase()
                relative = when {
                    unit.startsWith("m") -> Duration.ofMinutes(n)
                    unit.startsWith("h") -> Duration.ofHours(n)
                    unit.startsWith("d") -> Duration.ofDays(n)
                    else -> Duration.ofDays(7 * n)
                }
            }
        }

        // "every monday and friday", "every day", "every weekday", "daily"
        val dayList = """(?:$DAYS)s?(?:\s*(?:,\s*and|,|and|&)\s*(?:$DAYS)s?)*"""
        take(Regex("""\b(?:every|each)\s+(day|morning|night|evening|weekday|weekdays|weekend|$dayList)\b""", I))?.let { m ->
            found = true
            val what = m.groupValues[1].lowercase()
            repeat = when {
                what == "day" -> "DAILY"
                what == "morning" -> { defaultHour = 9; "DAILY" }
                what == "evening" -> { defaultHour = 18; "DAILY" }
                what == "night" -> { defaultHour = 21; "DAILY" }
                what.startsWith("weekday") -> "DOW:1,2,3,4,5"
                what == "weekend" -> "DOW:6,7"
                else -> {
                    val ds = Regex(DAYS).findAll(what).map { dow(it.value).value }.distinct().sorted()
                    "DOW:" + ds.joinToString(",")
                }
            }
        }
        if (repeat == null && take(Regex("""\bdaily\b""", I)) != null) {
            found = true
            repeat = "DAILY"
        }

        // Day words
        when {
            take(Regex("""\btonight\b""", I)) != null -> {
                found = true; date = now.toLocalDate(); defaultHour = 20
            }
            take(Regex("""\btoday\b""", I)) != null -> {
                found = true; date = now.toLocalDate()
            }
            take(Regex("""\b(?:tomorrow|tmrw|tmr)\b""", I)) != null -> {
                found = true; date = now.toLocalDate().plusDays(1)
            }
        }
        take(Regex("""\b(?:on\s+|next\s+|this\s+)?($DAYS)\b""", I))?.let { m ->
            found = true
            weekday = dow(m.groupValues[1].lowercase())
        }

        // Times
        take(Regex("""\b(?:at\s+)?(noon|midnight)\b""", I))?.let { m ->
            time = if (m.groupValues[1].equals("noon", true)) LocalTime.NOON else LocalTime.MIDNIGHT
        }
        if (time == null) {
            take(Regex("""(?:\bat\s+|@\s*|\b)(\d{1,2})(?::(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)(?![\p{L}])""", I))?.let { m ->
                val pm = m.groupValues[3].lowercase().startsWith("p")
                val h = m.groupValues[1].toInt()
                val min = m.groupValues[2].toIntOrNull() ?: 0
                if (h in 1..12 && min in 0..59) time = LocalTime.of(h % 12 + if (pm) 12 else 0, min)
            }
        }
        if (time == null) {
            take(Regex("""(?:\bat\s+|@\s*)(\d{1,2})(?::(\d{2}))?\b""", I))?.let { m ->
                time = guess(m.groupValues[1].toInt(), m.groupValues[2].toIntOrNull() ?: 0)
            }
        }
        if (time == null) {
            take(Regex("""\b(\d{1,2}):(\d{2})\b"""))?.let { m ->
                time = guess(m.groupValues[1].toInt(), m.groupValues[2].toInt())
            }
        }
        take(Regex("""\b(?:in\s+the\s+|this\s+)?(morning|afternoon|evening)\b""", I))?.let { m ->
            found = true
            defaultHour = when (m.groupValues[1].lowercase()) {
                "morning" -> 9
                "afternoon" -> 15
                else -> 18
            }
        }
        if (time != null) found = true

        val body = cleanBody(s)
        val rel = relative
        if (rel != null) return ReminderSpec(body, now.plus(rel).withNano(0), null, false)
        if (!found) return ReminderSpec(body, now.plusHours(1).withNano(0), null, true)

        val t = time ?: LocalTime.of(defaultHour, 0)
        val rep = repeat
        val wd = weekday
        val d = date
        var at: LocalDateTime
        when {
            rep != null -> at = Repeat.next(rep, LocalDateTime.of(now.toLocalDate(), t), now)
            wd != null -> {
                var day = now.toLocalDate()
                while (day.dayOfWeek != wd) day = day.plusDays(1)
                at = LocalDateTime.of(day, t)
                if (!at.isAfter(now)) at = at.plusWeeks(1)
            }
            d != null -> {
                at = LocalDateTime.of(d, t)
                if (!at.isAfter(now)) at = at.plusDays(1)
            }
            else -> {
                at = LocalDateTime.of(now.toLocalDate(), t)
                if (!at.isAfter(now)) at = at.plusDays(1)
            }
        }
        return ReminderSpec(body, at, rep, false)
    }

    /** "at 5" means 5 pm, "at 9" means 9 am. */
    private fun guess(h: Int, m: Int): LocalTime? {
        if (h > 23 || m > 59) return null
        val hour = if (h in 1..6) h + 12 else h
        return LocalTime.of(hour, m)
    }

    private fun dow(name: String): DayOfWeek = when (name.lowercase().removeSuffix("s")) {
        "monday" -> DayOfWeek.MONDAY
        "tuesday" -> DayOfWeek.TUESDAY
        "wednesday" -> DayOfWeek.WEDNESDAY
        "thursday" -> DayOfWeek.THURSDAY
        "friday" -> DayOfWeek.FRIDAY
        "saturday" -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }

    private fun cleanBody(s: String): String {
        var b = tidy(s)
        repeat(3) {
            b = b.replace(Regex("""^(?:to|that|about|on|at|me)\b\s*""", I), "")
                .replace(Regex("""\s+(?:at|on|to|in)$""", I), "")
                .trim(' ', ',', '.', ':', ';', '-')
        }
        return if (b.isBlank()) "Reminder" else cap(b)
    }

    private fun tidy(s: String): String =
        s.lines().joinToString("\n") { it.replace(Regex("""[ \t]+"""), " ").trim() }.trim()

    private fun cap(s: String): String = s.replaceFirstChar { it.uppercaseChar() }
}
