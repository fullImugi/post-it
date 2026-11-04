package com.fullimugi.postit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class NodeKind { Tag, List, Reminders, Inbox, Note }

class GNode(
    val id: String,
    val kind: NodeKind,
    val label: String,
    val item: Item?,
    var x: Float,
    var y: Float,
    /** Color key, shared with the sticky notes on the board. */
    val key: String?,
) {
    var vx = 0f
    var vy = 0f
    var degree = 0
    val isHub get() = kind != NodeKind.Note
    val radius: Float
        get() = if (isHub) min(12f + 3.2f * sqrt(degree.toFloat()), 40f) else 6f
}

class GraphModel(val nodes: List<GNode>, val edges: List<IntArray>, val added: Int) {
    val neighbors: List<Set<Int>> = run {
        val n = List(nodes.size) { HashSet<Int>() }
        edges.forEach { (a, b) -> n[a].add(b); n[b].add(a) }
        n
    }
}

/** Keeps node positions while the app is running, so the map doesn't reshuffle between visits. */
private object MapMemory {
    val nodes = HashMap<String, GNode>()
    var scale = 1f
    var pan = Offset.Zero
}

private val rnd = java.util.Random()

private fun buildGraph(items: List<Item>, old: Map<String, GNode>): GraphModel {
    val nodes = ArrayList<GNode>()
    val index = HashMap<String, Int>()
    val edges = ArrayList<IntArray>()
    var added = 0

    fun node(id: String, kind: NodeKind, label: String, key: String?, item: Item? = null, near: GNode? = null): Int {
        index[id]?.let { return it }
        val prev = old[id]
        val n = if (prev != null) {
            GNode(id, kind, label, item, prev.x, prev.y, key).also { it.vx = prev.vx; it.vy = prev.vy }
        } else {
            added++
            val spread = if (near == null) 400f else 50f
            GNode(
                id, kind, label, item,
                (near?.x ?: 0f) + (rnd.nextFloat() - 0.5f) * spread,
                (near?.y ?: 0f) + (rnd.nextFloat() - 0.5f) * spread,
                key,
            )
        }
        nodes += n
        index[id] = nodes.size - 1
        return nodes.size - 1
    }

    for (it in items.take(400)) {
        val hubs = ArrayList<Int>()
        it.tags.forEach { t -> hubs += node("t:$t", NodeKind.Tag, "#$t", t) }
        it.listName?.let { l -> hubs += node("l:${l.lowercase()}", NodeKind.List, l, l) }
        if (it.remindAt != null) hubs += node("r", NodeKind.Reminders, "Reminders", "reminders")
        if (hubs.isEmpty()) hubs += node("i", NodeKind.Inbox, "Untagged", null)
        val label = if (it.body.length > 28) it.body.take(26).trimEnd() + "…" else it.body
        val ni = node("n:${it.id}", NodeKind.Note, label, Sticky.keyFor(it), it, near = nodes[hubs.first()])
        hubs.forEach { h ->
            edges += intArrayOf(ni, h)
            nodes[h].degree++
            nodes[ni].degree++
        }
    }
    return GraphModel(nodes, edges, added)
}

/** One tick of a simple force layout: nodes push apart, links pull together, everything drifts to the middle. */
private fun step(g: GraphModel, alpha: Float) {
    val ns = g.nodes
    val n = ns.size
    for (i in 0 until n) {
        val a = ns[i]
        for (j in i + 1 until n) {
            val b = ns[j]
            var dx = a.x - b.x
            var dy = a.y - b.y
            var d2 = dx * dx + dy * dy
            if (d2 < 0.01f) {
                dx = rnd.nextFloat() - 0.5f; dy = rnd.nextFloat() - 0.5f; d2 = 0.5f
            }
            if (d2 > 640_000f) continue // too far apart to matter
            val k = when {
                a.isHub && b.isHub -> 60_000f
                a.isHub || b.isHub -> 9_000f
                else -> 2_500f
            }
            val d = sqrt(d2)
            val f = k / max(d2, 100f)
            val fx = dx / d * f
            val fy = dy / d * f
            a.vx += fx; a.vy += fy
            b.vx -= fx; b.vy -= fy
        }
    }
    for (e in g.edges) {
        val a = ns[e[0]]
        val b = ns[e[1]]
        val dx = b.x - a.x
        val dy = b.y - a.y
        val d = max(sqrt(dx * dx + dy * dy), 0.1f)
        val rest = 40f + b.radius * 2f
        val f = 0.04f * (d - rest)
        val fx = dx / d * f
        val fy = dy / d * f
        a.vx += fx; a.vy += fy
        b.vx -= fx * 0.3f; b.vy -= fy * 0.3f // hubs are "heavier"
    }
    for (a in ns) {
        a.vx -= a.x * 0.004f
        a.vy -= a.y * 0.004f
        a.vx = (a.vx * alpha).coerceIn(-40f, 40f)
        a.vy = (a.vy * alpha).coerceIn(-40f, 40f)
        a.x += a.vx
        a.y += a.vy
        a.vx *= 0.6f
        a.vy *= 0.6f
    }
}

@Composable
fun MapScreen(items: List<Item>, onOpenTag: (String) -> Unit, onOpenTab: (Tab) -> Unit) {
    var graph by remember { mutableStateOf(GraphModel(emptyList(), emptyList(), 0)) }
    var frame by remember { mutableIntStateOf(0) }
    var scale by remember { mutableFloatStateOf(MapMemory.scale) }
    var pan by remember { mutableStateOf(MapMemory.pan) }
    var selected by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(items) {
        val firstTime = MapMemory.nodes.isEmpty()
        val g = buildGraph(items, MapMemory.nodes)
        MapMemory.nodes.clear()
        g.nodes.forEach { MapMemory.nodes[it.id] = it }
        if (firstTime) repeat(120) { step(g, 0.9f) } // settle before the first frame
        graph = g
        if (selected != null && g.nodes.none { it.id == selected }) selected = null
        var alpha = when {
            firstTime -> 0.6f
            g.added > 0 -> 0.9f
            else -> 0.3f
        }
        while (alpha > 0.02f) {
            withFrameNanos { }
            step(g, alpha)
            alpha *= 0.985f
            frame++
        }
    }

    val cs = MaterialTheme.colorScheme
    val dark = Look.dark
    fun boldOf(n: GNode): Color = if (n.kind == NodeKind.Inbox) cs.outline else Sticky.bold(n.key)
    fun paperOf(n: GNode): Color = if (n.kind == NodeKind.Inbox) cs.surfaceContainerHighest else Sticky.paper(n.key, dark)

    val measurer = rememberTextMeasurer()
    val hubStyle = TextStyle(color = cs.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = Fredoka)
    val noteStyle = TextStyle(color = cs.onSurfaceVariant, fontSize = 13.sp, fontFamily = Hand)
    val labels = remember(cs.onSurface) { HashMap<String, TextLayoutResult>() }

    Box(Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Your map grows as you write", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Each note becomes a dot, linked to its #tags, lists and reminders.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, panChange, zoom, _ ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val newScale = (scale * zoom).coerceIn(0.25f, 5f)
                        val world = (centroid - center - pan) / scale
                        pan = centroid - center - world * newScale + panChange
                        scale = newScale
                        MapMemory.scale = scale
                        MapMemory.pan = pan
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val w = (tap - center - pan) / scale
                        var best: GNode? = null
                        var bestD = Float.MAX_VALUE
                        for (n in graph.nodes) {
                            val dx = n.x - w.x
                            val dy = n.y - w.y
                            val d = sqrt(dx * dx + dy * dy)
                            val hit = n.radius + 14f / scale
                            if (d < hit && d < bestD) {
                                best = n; bestD = d
                            }
                        }
                        selected = best?.id
                    }
                }
        ) {
            frame // redraw on every simulation tick
            val g = graph
            val center = Offset(size.width / 2f, size.height / 2f) + pan
            fun screen(n: GNode) = Offset(center.x + n.x * scale, center.y + n.y * scale)

            val selIdx = selected?.let { id -> g.nodes.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
            val focus: Set<Int>? = selIdx?.let { g.neighbors[it] + it }

            // dotted paper
            val gap = 28f * scale.coerceIn(0.6f, 2.5f)
            val dotC = cs.outline.copy(alpha = 0.25f)
            val ox = ((center.x % gap) + gap) % gap
            val oy = ((center.y % gap) + gap) % gap
            var gx = ox
            while (gx < size.width) {
                var gy = oy
                while (gy < size.height) {
                    drawCircle(dotC, 1.6f, Offset(gx, gy))
                    gy += gap
                }
                gx += gap
            }

            for (e in g.edges) {
                val dim = focus != null && !(e[0] in focus && e[1] in focus)
                val hub = g.nodes[e[1]]
                val a = screen(g.nodes[e[0]])
                val b = screen(hub)
                // a gentle curve instead of a ruler-straight line
                val mx = (a.x + b.x) / 2f
                val my = (a.y + b.y) / 2f
                val bend = 0.12f
                val cx = mx - (b.y - a.y) * bend
                val cy = my + (b.x - a.x) * bend
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(a.x, a.y)
                    quadraticBezierTo(cx, cy, b.x, b.y)
                }
                drawPath(
                    path,
                    color = boldOf(hub).copy(alpha = if (dim) 0.06f else 0.4f),
                    style = Stroke(width = if (!dim && focus != null) 3.5f else 2f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
            g.nodes.forEachIndexed { i, n ->
                val dim = focus != null && i !in focus
                val p = screen(n)
                val r = n.radius * scale.coerceIn(0.6f, 2f)
                val faded = n.item?.checked == true
                val a = when {
                    dim -> 0.15f
                    faded -> 0.4f
                    else -> 1f
                }
                if (n.isHub) {
                    drawCircle(boldOf(n).copy(alpha = 0.18f * a), r + 8f, p)
                    drawCircle(paperOf(n).copy(alpha = a), r, p)
                    drawCircle(boldOf(n).copy(alpha = a), r, p, style = Stroke(width = 4f))
                } else {
                    drawCircle(cs.background.copy(alpha = a), r + 2.5f, p)
                    drawCircle(boldOf(n).copy(alpha = a), r, p)
                }
                if (i == selIdx) drawCircle(Tomato, r + 7f, p, style = Stroke(width = 3.5f))
            }
            g.nodes.forEachIndexed { i, n ->
                val inFocus = focus != null && i in focus
                val show = n.isHub || scale >= 1.4f || inFocus
                if (!show || (focus != null && !inFocus && !n.isHub)) return@forEachIndexed
                val layout = labels.getOrPut(n.id + "|" + n.label) {
                    measurer.measure(n.label, if (n.isHub) hubStyle else noteStyle)
                }
                val p = screen(n)
                val r = n.radius * scale.coerceIn(0.6f, 2f)
                drawText(
                    layout,
                    topLeft = Offset(p.x - layout.size.width / 2f, p.y + r + 3f),
                    alpha = if (focus != null && !inFocus) 0.3f else 1f,
                )
            }
        }

        Text(
            "Pinch to zoom · tap a bubble",
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        )
        TextButton(
            onClick = {
                scale = 1f; pan = Offset.Zero; selected = null
                MapMemory.scale = 1f; MapMemory.pan = Offset.Zero
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
        ) { Text("Recenter", color = Tomato) }

        val sel = selected?.let { id -> graph.nodes.firstOrNull { it.id == id } }
        if (sel != null) {
            DetailCard(
                node = sel,
                connected = graph.nodes.indexOf(sel).let { idx ->
                    if (idx < 0) emptyList() else graph.neighbors[idx].map { graph.nodes[it] }
                },
                onOpenTag = onOpenTag,
                onOpenTab = onOpenTab,
                onClose = { selected = null },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun DetailCard(
    node: GNode,
    connected: List<GNode>,
    onOpenTag: (String) -> Unit,
    onOpenTab: (Tab) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val dark = Look.dark
    val paper = if (node.kind == NodeKind.Inbox) MaterialTheme.colorScheme.surfaceContainerHigh else Sticky.paper(node.key, dark)
    val ink = Sticky.ink(dark)
    StickyCard(paper, node.id.hashCode().toLong(), modifier.padding(14.dp).fillMaxWidth().widthIn(max = 560.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val item = node.item
            if (item != null) {
                Text(item.body, style = NoteText.copy(fontSize = 18.sp), color = ink)
                val links = connected.joinToString(" · ") { it.label }
                Text(
                    listOf(links, Fmt.ago(item.createdAt)).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = ink.copy(alpha = 0.7f),
                )
            } else {
                Text(
                    "${node.label} · ${connected.size}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ink,
                )
                Column(
                    Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    connected.sortedByDescending { it.item?.createdAt ?: 0L }.forEach { n ->
                        val ni = n.item ?: return@forEach
                        Text(
                            (if (ni.checked) "✓ " else "• ") + ni.body,
                            style = NoteText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (ni.checked) ink.copy(alpha = 0.45f) else ink,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when (node.kind) {
                    NodeKind.Tag -> InkButton("Show on board", ink, paper) { onOpenTag(node.label.removePrefix("#")) }
                    NodeKind.List -> InkButton("Open lists", ink, paper) { onOpenTab(Tab.Lists) }
                    NodeKind.Reminders -> InkButton("Open reminders", ink, paper) { onOpenTab(Tab.Reminders) }
                    else -> {}
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Close", color = ink) }
            }
        }
    }
}

@Composable
private fun InkButton(label: String, ink: Color, paper: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(ink)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = paper)
    }
}
