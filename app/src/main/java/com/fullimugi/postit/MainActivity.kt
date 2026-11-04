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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch

enum class Tab(val label: String) { Feed("Feed"), Lists("Lists"), Reminders("Reminders") }

private fun Tab.icon(): ImageVector = when (this) {
    Tab.Feed -> Icons.Filled.Home
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
    var showHelp by remember { mutableStateOf(false) }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post-it") },
                actions = {
                    IconButton(onClick = { exportCsv(ctx, items) }) { Icon(Icons.Filled.Share, "Export as CSV") }
                    IconButton(onClick = { showHelp = true }) { Icon(Icons.Filled.Info, "How it works") }
                },
            )
        },
        bottomBar = {
            if (!wide) {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tabState.value = t },
                            icon = { Icon(t.icon(), null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .consumeWindowInsets(pad)
                .imePadding()
        ) {
            if (wide) {
                NavigationRail {
                    Spacer(Modifier.height(8.dp))
                    Tab.entries.forEach { t ->
                        NavigationRailItem(
                            selected = tab == t,
                            onClick = { tabState.value = t },
                            icon = { Icon(t.icon(), null) },
                            label = { Text(t.label) },
                        )
                    }
                }
                if (tab == Tab.Feed) TagSidebar(allTags, tag) { tag = it }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        Tab.Feed -> FeedScreen(items, allTags, tag, { tag = it }, query, { query = it }, showChips = !wide)
                        Tab.Lists -> ListsScreen(items)
                        Tab.Reminders -> RemindersScreen(items, resumeTick)
                    }
                }
                Composer { text ->
                    val reply = Capture.capture(ctx, text)
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar(reply)
                    }
                }
            }
        }
    }

    if (showHelp) HelpDialog { showHelp = false }
}

@Composable
private fun Composer(onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val send = {
        if (text.isNotBlank()) {
            onSend(text.trim())
            text = ""
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message yourself…") },
            shape = RoundedCornerShape(24.dp),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(onSend = { send() }),
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(onClick = { send() }, enabled = text.isNotBlank()) {
            Icon(Icons.AutoMirrored.Filled.Send, "Send")
        }
    }
}

@Composable
private fun TagSidebar(tags: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    LazyColumn(Modifier.width(176.dp).fillMaxHeight().padding(vertical = 8.dp)) {
        item {
            Text(
                "Tags",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        item {
            NavigationDrawerItem(
                label = { Text("All") },
                selected = selected == null,
                onClick = { onSelect(null) },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        items(tags) { t ->
            NavigationDrawerItem(
                label = { Text("#$t") },
                selected = selected == t,
                onClick = { onSelect(t) },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
    VerticalDivider()
}

// ---------------------------------------------------------------- feed

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
    val q = query.trim().removePrefix("#")
    val shown = items.filter { item ->
        (tag == null || tag in item.tags) &&
            (q.isEmpty() || item.body.contains(q, ignoreCase = true) ||
                item.tags.any { it.contains(q, ignoreCase = true) } ||
                (item.listName?.contains(q, ignoreCase = true) ?: false))
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search notes") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
            )
        }
        if (showChips && allTags.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = tag == null, onClick = { onTag(null) }, label = { Text("All") }) }
                    items(allTags) { t ->
                        FilterChip(
                            selected = tag == t,
                            onClick = { onTag(if (tag == t) null else t) },
                            label = { Text("#$t") },
                        )
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                if (items.isEmpty()) Empty("Text yourself anything", "Try “app idea: plant tracker #ideas”, “buy eggs, milk” or “remind me tomorrow at 9 to call the dentist”.")
                else Empty("Nothing matches", "Try another search or tag.")
            }
        }
        items(shown, key = { it.id }) { ItemCard(it) }
    }
}

@Composable
private fun ItemCard(item: Item) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    Card(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (item.listName != null) toggle(ctx, item) },
                onLongClick = { confirm = true },
            )
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.listName != null) {
                Checkbox(checked = item.checked, onCheckedChange = { toggle(ctx, item) })
            } else if (item.remindAt != null) {
                Icon(
                    Icons.Filled.Notifications, null,
                    modifier = Modifier.padding(end = 12.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    item.body,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                )
                Text(
                    meta(item),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (confirm) DeleteDialog(item.body, onDismiss = { confirm = false }) {
        deleteItem(ctx, item)
        confirm = false
    }
}

private fun meta(item: Item): String = buildList {
    addAll(item.tags.map { "#$it" })
    item.listName?.let { add(it) }
    item.remindAt?.let { add(Fmt.whenText(it)) }
    Repeat.label(item.repeat)?.let { add(it) }
    add(Fmt.ago(item.createdAt))
}.joinToString(" · ")

// ---------------------------------------------------------------- lists

@Composable
private fun ListsScreen(items: List<Item>) {
    val ctx = LocalContext.current
    val lists = remember(items) {
        items.filter { it.listName != null }
            .groupBy { it.listName.orEmpty() }
            .toList()
            .sortedByDescending { (_, v) -> v.maxOf { it.createdAt } }
    }
    if (lists.isEmpty()) {
        Empty("No lists yet", "Try “buy eggs, milk”, “todo renew passport” or “+movies Dune, Arrival”.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(lists, key = { it.first }) { (name, entries) ->
            val done = entries.count { it.checked }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            "$done/${entries.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (done > 0) {
                            TextButton(onClick = {
                                Store.db(ctx).clearChecked(name)
                                Store.changed(ctx)
                            }) { Text("Clear done") }
                        } else {
                            Spacer(Modifier.width(12.dp))
                        }
                    }
                    LinearProgressIndicator(
                        progress = { if (entries.isEmpty()) 0f else done.toFloat() / entries.size },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    val ordered = entries.filter { !it.checked }.sortedBy { it.createdAt } +
                        entries.filter { it.checked }.sortedBy { it.createdAt }
                    ordered.forEach { ListRow(it) }
                }
            }
        }
    }
}

@Composable
private fun ListRow(item: Item) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { toggle(ctx, item) }, onLongClick = { confirm = true })
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.checked, onCheckedChange = { toggle(ctx, item) })
        Text(
            item.body,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
        )
    }
    if (confirm) DeleteDialog(item.body, onDismiss = { confirm = false }) {
        deleteItem(ctx, item)
        confirm = false
    }
}

// ---------------------------------------------------------------- reminders

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
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                Banner("Reminders may arrive a few minutes late.", "Fix") {
                    ctx.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                    )
                }
            }
        }
        if (all.isEmpty()) {
            item { Empty("No reminders yet", "Try “remind me tomorrow at 9am to call the dentist” or “remind me every Monday at 8am to take out the trash”.") }
        }
        if (upcoming.isNotEmpty()) {
            item { SectionTitle("Upcoming") }
            items(upcoming, key = { it.id }) { ReminderRow(it, isPast = false) }
        }
        if (past.isNotEmpty()) {
            item { SectionTitle("Done and past") }
            items(past, key = { it.id }) { ReminderRow(it, isPast = true) }
        }
    }
}

@Composable
private fun ReminderRow(item: Item, isPast: Boolean) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { confirm = true })
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.body,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    color = if (isPast) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                )
                val whenText = listOfNotNull(item.remindAt?.let { Fmt.whenText(it) }, Repeat.label(item.repeat))
                    .joinToString(" · ")
                Text(
                    whenText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isPast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            }
            if (!isPast) {
                IconButton(onClick = { toggle(ctx, item) }) { Icon(Icons.Filled.Check, "Mark done") }
            }
        }
    }
    if (confirm) DeleteDialog(item.body, onDismiss = { confirm = false }) {
        deleteItem(ctx, item)
        confirm = false
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
private fun Empty(title: String, hint: String) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun Banner(text: String, action: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClick) { Text(action) }
        }
    }
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
        "buy eggs, milk" to "Adds two items to your Shopping list.",
        "todo renew passport" to "Adds to your To-do list.",
        "+movies Dune, Arrival" to "Adds to any list you name (here, Movies).",
        "add tape to my hardware list" to "Same thing, in plain words.",
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
