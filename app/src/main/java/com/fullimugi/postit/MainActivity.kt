@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.fullimugi.postit

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class Tab(val label: String) { Feed("Board"), Map("Map"), Lists("Lists"), Reminders("Reminders") }

private fun Tab.icon(): ImageVector = when (this) {
    Tab.Feed -> Icons.Filled.Home
    Tab.Map -> Icons.Filled.Share
    Tab.Lists -> Icons.AutoMirrored.Filled.List
    Tab.Reminders -> Icons.Filled.Notifications
}

class MainActivity : ComponentActivity() {
    private val tab = mutableStateOf(Tab.Feed)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readTab(intent)
        setContent { PostItTheme { App(tab) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readTab(intent)
    }

    private fun readTab(i: Intent?) {
        val name = i?.getStringExtra("tab") ?: return
        Tab.entries.firstOrNull { it.name == name }?.let { tab.value = it }
    }
}

// ---------------------------------------------------------------- app shell

@Composable
private fun App(tabState: MutableState<Tab>) {
    val ctx = LocalContext.current
    val db = remember { Store.db(ctx) }
    val version by Store.version.collectAsState()
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        resumeTick++
        Reminders.rescheduleAll(ctx)
    }
    val items = remember(version, resumeTick) { db.items() }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var draft by rememberSaveable { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val allTags = remember(items) {
        items.flatMap { it.tags }.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.map { it.key }
    }
    val tab = tabState.value

    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        resumeTick++
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val send = {
        val text = draft.trim()
        if (text.isNotEmpty()) {
            val reply = Capture.capture(ctx, text)
            draft = ""
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar("✓ $reply")
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Header(
                items = items,
                onHelp = { showHelp = true },
                onRules = { showRules = true },
                onExport = { exportCsv(ctx, items) },
            )
        },
    ) { pad ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .consumeWindowInsets(pad)
                .imePadding()
        ) {
            if (wide) {
                Box(Modifier.fillMaxHeight().padding(start = 12.dp, end = 4.dp), contentAlignment = Alignment.Center) {
                    PillNav(tab, { it.icon() }, vertical = true) { tabState.value = it }
                }
                if (tab == Tab.Feed) TagSidebar(allTags, tag) { tag = it }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        Tab.Feed -> FeedScreen(items, allTags, tag, { tag = it }, query, { query = it }, showChips = !wide)
                        Tab.Map -> MapScreen(
                            items,
                            onOpenTag = { t -> tag = t; query = ""; tabState.value = Tab.Feed },
                            onOpenTab = { t -> tabState.value = t },
                        )
                        Tab.Lists -> ListsScreen(items)
                        Tab.Reminders -> RemindersScreen(items, resumeTick)
                    }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter)) { data ->
                        Snackbar(
                            data,
                            shape = RoundedCornerShape(50),
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                        )
                    }
                }
                ChatComposer(
                    text = draft,
                    onText = { draft = it },
                    onSend = { send() },
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 8.dp),
                )
                if (!wide && !WindowInsets.isImeVisible) {
                    Box(Modifier.fillMaxWidth().padding(bottom = 10.dp, top = 2.dp), contentAlignment = Alignment.Center) {
                        PillNav(tab, { it.icon() }, vertical = false) { tabState.value = it }
                    }
                }
            }
        }
    }

    if (showHelp) HelpDialog { showHelp = false }
    if (showRules) RulesDialog { showRules = false }
}

@Composable
private fun Header(items: List<Item>, onHelp: () -> Unit, onRules: () -> Unit, onExport: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 10.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Post-it",
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 46.sp, lineHeight = 46.sp),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Squiggle(Tomato, Modifier.padding(start = 2.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                greeting(items),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RoundIcon(Icons.Filled.Info, "How it works", onHelp)
        Box {
            RoundIcon(Icons.Filled.MoreVert, "More") { menuOpen = true }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("List keywords") }, onClick = { menuOpen = false; onRules() })
                DropdownMenuItem(text = { Text("Export as CSV") }, onClick = { menuOpen = false; onExport() })
            }
        }
    }
}

private fun greeting(items: List<Item>): String {
    val h = LocalTime.now().hour
    val hi = when {
        h < 5 -> "Up late"
        h < 12 -> "Good morning"
        h < 17 -> "Good afternoon"
        h < 22 -> "Good evening"
        else -> "Up late"
    }
    val today = LocalDate.now()
    val n = items.count { it.createdAt.ldt().toLocalDate() == today }
    val tail = when (n) {
        0 -> "nothing caught yet today"
        1 -> "1 thought caught today"
        else -> "$n thoughts caught today"
    }
    return "$hi · $tail"
}

@Composable
private fun TagSidebar(tags: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    val dark = Look.dark
    LazyColumn(
        Modifier.width(184.dp).fillMaxHeight(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Tags", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        }
        item {
            TagPill("Everything", selected == null, MaterialTheme.colorScheme.surfaceContainerHighest) { onSelect(null) }
        }
        items(tags) { t ->
            TagPill("#$t", selected == t, Sticky.paper(t, dark)) { onSelect(if (selected == t) null else t) }
        }
    }
}

// ---------------------------------------------------------------- board (feed)

@Composable
private fun FeedScreen(
    items: List<Item>,
    allTags: List<String>,
    tag: String?,
    onTag: (String?) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    showChips: Boolean,
) {
    val dark = Look.dark
    val cs = MaterialTheme.colorScheme
    val q = query.trim().removePrefix("#")
    val shown = items.filter { item ->
        (tag == null || tag in item.tags) &&
            (q.isEmpty() || item.body.contains(q, ignoreCase = true) ||
                item.tags.any { it.contains(q, ignoreCase = true) } ||
                (item.listName?.contains(q, ignoreCase = true) ?: false))
    }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(158.dp),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
        verticalItemSpacing = 14.dp,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "search", span = StaggeredGridItemSpan.FullLine) {
            TextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(50)),
                placeholder = { Text("Search your brain…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceContainerHigh,
                    unfocusedContainerColor = cs.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Tomato,
                ),
            )
        }
        if (showChips && allTags.isNotEmpty()) {
            item(key = "chips", span = StaggeredGridItemSpan.FullLine) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { TagPill("Everything", tag == null, cs.surfaceContainerHighest) { onTag(null) } }
                    items(allTags) { t ->
                        TagPill("#$t", tag == t, Sticky.paper(t, dark)) { onTag(if (tag == t) null else t) }
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                if (items.isEmpty()) {
                    EmptyNote("Blank board!", "Try “app idea: plant tracker #ideas”, “watch step brothers” or “remind me at 5 to call mom”.")
                } else {
                    EmptyNote("Nothing here", "Try another search or tag.")
                }
            }
        }
        items(shown.size, key = { shown[it].id }) { i ->
            StickyItem(shown[i], Modifier.animateItem())
        }
    }
}

@Composable
private fun StickyItem(item: Item, modifier: Modifier) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    val dark = Look.dark
    val paper = Sticky.paper(Sticky.keyFor(item), dark)
    val ink = Sticky.ink(dark)
    StickyCard(
        paper, item.id, modifier,
        onClick = { if (item.listName != null) toggle(ctx, item) },
        onLongClick = { confirm = true },
    ) {
        val kicker = when {
            item.remindAt != null -> Fmt.whenText(item.remindAt) + (Repeat.label(item.repeat)?.let { " · $it" } ?: "")
            item.listName != null -> item.listName
            else -> null
        }
        if (kicker != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.remindAt != null) {
                    Icon(Icons.Filled.Notifications, null, tint = ink, modifier = Modifier.height(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(kicker, style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.75f))
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.Top) {
            if (item.listName != null) {
                CheckDot(item.checked, ink, paper) { toggle(ctx, item) }
                Spacer(Modifier.width(10.dp))
            }
            Text(
                item.body,
                style = NoteText,
                color = if (item.checked) ink.copy(alpha = 0.45f) else ink,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            )
        }
        Spacer(Modifier.height(10.dp))
        val foot = (item.tags.map { "#$it" } + Fmt.ago(item.createdAt)).joinToString("  ")
        Text(foot, style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.6f))
    }
    if (confirm) DeleteDialog(item.body, onDismiss = { confirm = false }) {
        deleteItem(ctx, item)
        confirm = false
    }
}

// ---------------------------------------------------------------- lists

@Composable
private fun ListsScreen(items: List<Item>) {
    val ctx = LocalContext.current
    val dark = Look.dark
    val lists = remember(items) {
        items.filter { it.listName != null }
            .groupBy { it.listName.orEmpty() }
            .toList()
            .sortedByDescending { (_, v) -> v.maxOf { it.createdAt } }
    }
    if (lists.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            EmptyNote("No lists yet", "Try “buy eggs, milk”, “watch step brothers” or “+books Dune”.")
        }
        return
    }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(290.dp),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
        verticalItemSpacing = 18.dp,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(lists.size, key = { lists[it].first }) { i ->
            val (name, entries) = lists[i]
            val done = entries.count { it.checked }
            val paper = Sticky.paper(name, dark)
            val ink = Sticky.ink(dark)
            StickyCard(paper, name.hashCode().toLong(), Modifier.animateItem()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = ink,
                        modifier = Modifier.weight(1f),
                    )
                    InkBadge("$done/${entries.size}", ink, paper)
                }
                Spacer(Modifier.height(6.dp))
                ChunkyProgress(if (entries.isEmpty()) 0f else done.toFloat() / entries.size, ink)
                Spacer(Modifier.height(8.dp))
                val ordered = entries.filter { !it.checked }.sortedBy { it.createdAt } +
                    entries.filter { it.checked }.sortedBy { it.createdAt }
                ordered.forEach { ListRow(it, ink, paper) }
                if (done > 0) {
                    TextButton(onClick = {
                        Store.db(ctx).clearChecked(name)
                        Store.changed(ctx)
                    }) { Text("Clear done", color = ink) }
                }
            }
        }
    }
}

@Composable
private fun ListRow(item: Item, ink: Color, paper: Color) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = { toggle(ctx, item) }, onLongClick = { confirm = true })
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckDot(item.checked, ink, paper) { toggle(ctx, item) }
        Spacer(Modifier.width(10.dp))
        Text(
            item.body,
            style = NoteText,
            color = if (item.checked) ink.copy(alpha = 0.45f) else ink,
            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
        )
    }
    if (confirm) DeleteDialog(item.body, onDismiss = { confirm = false }) {
        deleteItem(ctx, item)
        confirm = false
    }
}

// ---------------------------------------------------------------- reminders

private val clockF = DateTimeFormatter.ofPattern("h:mm")
private val ampmF = DateTimeFormatter.ofPattern("a")
private val dayF = DateTimeFormatter.ofPattern("EEE, MMM d")

private fun dayLabel(ms: Long): String {
    val d = ms.ldt().toLocalDate()
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(dayF)
    }
}

@Composable
private fun RemindersScreen(items: List<Item>, tick: Int) {
    val ctx = LocalContext.current
    val now = remember(items, tick) { System.currentTimeMillis() }
    val all = items.filter { it.remindAt != null }
    val upcoming = all.filter { !it.checked && ((it.remindAt ?: 0L) >= now || it.repeat != null) }
        .sortedBy { it.remindAt }
    val upcomingIds = upcoming.map { it.id }.toSet()
    val past = all.filter { it.id !in upcomingIds }.sortedByDescending { it.remindAt }

    val exactOk = remember(tick) {
        Build.VERSION.SDK_INT < 31 || (ctx.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: true)
    }
    val notifOk = remember(tick) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!notifOk) {
            item {
                Banner("Notifications are off, so reminders can't pop up.", "Turn on") {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    )
                }
            }
        }
        if (!exactOk) {
            item {
                Banner("Reminders may show up a few minutes late.", "Fix") {
                    ctx.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                    )
                }
            }
        }
        if (all.isEmpty()) {
            item { EmptyNote("Nothing to nag you about", "Try “remind me tomorrow at 9am to call the dentist” or “remind me every Monday at 8am to take out the trash”.") }
        }
        if (upcoming.isNotEmpty()) {
            item { SectionTitle("Coming up") }
            items(upcoming, key = { it.id }) { ReminderRow(it, isPast = false, Modifier.animateItem()) }
        }
        if (past.isNotEmpty()) {
            item { SectionTitle("Done and dusted") }
            items(past, key = { it.id }) { ReminderRow(it, isPast = true, Modifier.animateItem()) }
        }
    }
}

@Composable
private fun ReminderRow(item: Item, isPast: Boolean, modifier: Modifier) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    val dark = Look.dark
    val paper = Sticky.paper(Sticky.keyFor(item), dark)
    val ink = Sticky.ink(dark)
    val at = item.remindAt ?: 0L
    StickyCard(
        paper, item.id, modifier.alpha(if (isPast) 0.6f else 1f),
        tilt = !isPast,
        onLongClick = { confirm = true },
        onClick = {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(78.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        at.ldt().format(clockF),
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 34.sp),
                        color = ink,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(at.ldt().format(ampmF), style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.7f))
                }
                Text(dayLabel(at), style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.7f))
            }
            Box(Modifier.width(2.dp).height(46.dp).background(ink.copy(alpha = 0.18f)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.body,
                    style = NoteText,
                    color = ink,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                )
                Repeat.label(item.repeat)?.let {
                    Text("↻ $it", style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.7f))
                }
            }
            if (!isPast) {
                Spacer(Modifier.width(8.dp))
                CheckDot(false, ink, paper) { toggle(ctx, item) }
            }
        }
    }
    if (confirm) DeleteDialog(item.body, onDismiss = { confirm = false }) {
        deleteItem(ctx, item)
        confirm = false
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}

@Composable
private fun Banner(text: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClick) { Text(action, color = Tomato) }
    }
}

// ---------------------------------------------------------------- shared bits

private fun toggle(ctx: Context, item: Item) {
    val nowChecked = !item.checked
    Store.db(ctx).setChecked(item.id, nowChecked)
    val at = item.remindAt
    if (at != null) {
        if (nowChecked) Reminders.cancel(ctx, item.id)
        else if (at > System.currentTimeMillis()) Reminders.schedule(ctx, item.id, at)
    }
    Store.changed(ctx)
}

private fun deleteItem(ctx: Context, item: Item) {
    Reminders.cancel(ctx, item.id)
    Store.db(ctx).delete(item.id)
    Store.changed(ctx)
}

private fun exportCsv(ctx: Context, items: List<Item>) {
    fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    val csv = buildString {
        appendLine("created,body,tags,list,done,remind_at,repeat")
        items.forEach {
            appendLine(
                listOf(
                    Fmt.iso(it.createdAt), q(it.body), q(it.tags.joinToString(" ")), q(it.listName.orEmpty()),
                    it.checked.toString(), it.remindAt?.let { ms -> Fmt.iso(ms) }.orEmpty(), it.repeat.orEmpty(),
                ).joinToString(",")
            )
        }
    }
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Post-it export")
        .putExtra(Intent.EXTRA_TEXT, csv)
    ctx.startActivity(Intent.createChooser(send, "Export notes"))
}

@Composable
private fun DeleteDialog(body: String, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this?") },
        text = { Text(body, maxLines = 4) },
        confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    val rows = listOf(
        "#tag" to "Files a note under that tag. Use as many as you like.",
        "remind me at 5 to call mom" to "A reminder at 5 PM today (or tomorrow if 5 has passed).",
        "remind me tomorrow at 9am …" to "Works with today, tonight, tomorrow, weekdays, noon, “in 20 minutes”.",
        "remind me every Monday at 8am …" to "Repeats. Also: every day, every weekday, every Mon and Fri.",
        "watch step brothers" to "Adds “Step Brothers” to Movies. Also “I want to watch …”.",
        "read, binge, listen to, visit, cook" to "Go to Books, Shows, Music, Places and Recipes.",
        "buy eggs, milk" to "Adds two items to your Shopping list.",
        "todo renew passport" to "Adds to your To-do list.",
        "⋮ › List keywords" to "Add your own, like “try” → Restaurants.",
        "+movies Dune, Arrival" to "Adds to any list you name (here, Movies).",
        "add tape to my hardware list" to "Same thing, in plain words.",
        "Map tab" to "Every note links to its tags, lists and reminders. Pinch to zoom, tap a dot for details.",
        "Long-press" to "Deletes a note, list item or reminder.",
        "Share from WhatsApp" to "Share any message to “Save to Post-it” to file it.",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How it works") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { (k, v) ->
                    Column {
                        Text(k, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
}

@Composable
private fun RulesDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { Store.db(ctx) }
    var version by remember { mutableIntStateOf(0) }
    val rules = remember(version) { db.rules() }
    var keyword by remember { mutableStateOf("") }
    var list by remember { mutableStateOf("") }
    var titles by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("List keywords") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Start a message with a keyword and it goes on that list. “watch step brothers” → Movies.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                ) {
                    rules.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.keyword, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text("→ ${r.listName}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { db.deleteRule(r.id); version++ }) {
                                Icon(Icons.Filled.Close, "Remove ${r.keyword}")
                            }
                        }
                    }
                }
                HorizontalDivider()
                Text("Add a keyword", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = keyword, onValueChange = { keyword = it; error = null },
                    label = { Text("Keyword") }, placeholder = { Text("try") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = list, onValueChange = { list = it; error = null },
                    label = { Text("List") }, placeholder = { Text("Restaurants") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = titles, onCheckedChange = { titles = it })
                    Text("Capitalize like a title", style = MaterialTheme.typography.bodyMedium)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    keyword.isBlank() -> error = "Enter a keyword first."
                    list.isBlank() -> error = "Enter a list name first."
                    else -> {
                        db.addRule(keyword, list.trim().replaceFirstChar { it.uppercaseChar() }, titles)
                        keyword = ""; list = ""; version++
                    }
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
