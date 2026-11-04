@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.fullimugi.postit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/** A sticky note: paper color, a slight tilt, a strip of tape on top and a folded corner. */
@Composable
fun StickyCard(
    color: Color,
    seed: Long,
    modifier: Modifier = Modifier,
    tilt: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val angle = if (tilt) (Math.floorMod(seed * 7919L, 7L).toInt() - 3) * 0.55f else 0f
    val shape = CutCornerShape(bottomEnd = 18.dp)
    val dark = Look.dark
    val fold = Color.Black.copy(alpha = if (dark) 0.28f else 0.10f)
    val tape = Color.White.copy(alpha = if (dark) 0.16f else 0.55f)
    val clickMod = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
    } else Modifier

    Box(modifier.graphicsLayer { rotationZ = angle }.padding(top = 6.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(if (dark) 2.dp else 4.dp, shape, ambientColor = Color(0x55000000), spotColor = Color(0x33000000))
                .clip(shape)
                .background(color)
                .then(clickMod)
                .drawWithContent {
                    drawContent()
                    val c = 18.dp.toPx()
                    val p = Path().apply {
                        moveTo(size.width - c, size.height)
                        lineTo(size.width, size.height - c)
                        lineTo(size.width - c, size.height - c)
                        close()
                    }
                    drawPath(p, fold)
                }
                .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 14.dp),
            content = content,
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-4).dp)
                .graphicsLayer { rotationZ = -angle * 3f - 2f }
                .size(width = 46.dp, height = 14.dp)
                .background(tape, RoundedCornerShape(2.dp))
        )
    }
}

/** A round, hand-drawn-feeling checkbox. */
@Composable
fun CheckDot(checked: Boolean, ink: Color, paper: Color, onClick: () -> Unit) {
    val bg by animateColorAsState(if (checked) ink else Color.Transparent, label = "check")
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, ink, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Filled.Check, null, tint = paper, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun ChunkyProgress(fraction: Float, ink: Color) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "progress")
    Box(
        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(ink.copy(alpha = 0.14f))
    ) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(50)).background(ink))
    }
}

/** A little hand-drawn squiggle, used under the title. */
@Composable
fun Squiggle(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.width(92.dp).height(10.dp)) {
        val p = Path()
        val waves = 5
        val step = size.width / 60f
        for (i in 0..60) {
            val x = i * step
            val y = size.height / 2f + sin(i / 60f * waves * 2 * Math.PI).toFloat() * size.height / 3f
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun TagPill(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val ink = Sticky.ink(Look.dark)
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.secondary else color, label = "pill")
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSecondary else ink,
        )
    }
}

@Composable
fun InkBadge(text: String, ink: Color, paper: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(ink).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = paper)
    }
}

@Composable
fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(4.dp)
            .size(42.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}

/** Floating ink-colored nav pill. Horizontal when folded, vertical when unfolded. */
@Composable
fun PillNav(current: Tab, icon: (Tab) -> ImageVector, vertical: Boolean, onSelect: (Tab) -> Unit) {
    val onInk = MaterialTheme.colorScheme.onSecondary
    Surface(
        color = MaterialTheme.colorScheme.secondary,
        shape = RoundedCornerShape(50),
        shadowElevation = 10.dp,
    ) {
        val items: @Composable () -> Unit = {
            Tab.entries.forEach { t ->
                val selected = t == current
                val bg by animateColorAsState(if (selected) Tomato else Color.Transparent, label = "nav")
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(bg)
                        .clickable { onSelect(t) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .animateContentSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        icon(t), t.label,
                        tint = if (selected) Color.White else onInk.copy(alpha = 0.75f),
                        modifier = Modifier.size(22.dp),
                    )
                    if (selected && !vertical) {
                        Spacer(Modifier.width(6.dp))
                        Text(t.label, style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
            }
        }
        if (vertical) {
            Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { items() }
        } else {
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) { items() }
        }
    }
}

/** The "message yourself" bar shared by the app and the pop-up. */
@Composable
fun ChatComposer(
    text: String,
    onText: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    onMic: (() -> Unit)? = null,
    focus: FocusRequester? = null,
) {
    val cs = MaterialTheme.colorScheme
    val canSend = text.isNotBlank()
    val sendBg by animateColorAsState(if (canSend) Tomato else cs.surfaceContainerHighest, label = "send")
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = text,
            onValueChange = onText,
            modifier = Modifier
                .weight(1f)
                .shadow(3.dp, RoundedCornerShape(28.dp))
                .then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
            placeholder = { Text("Text yourself anything…", style = NoteText, color = cs.onSurfaceVariant) },
            textStyle = NoteText.copy(color = cs.onSurface),
            shape = RoundedCornerShape(28.dp),
            maxLines = 4,
            trailingIcon = if (onMic != null) {
                {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onMic),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_mic), "Voice note", tint = Tomato, modifier = Modifier.size(24.dp))
                    }
                }
            } else null,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = cs.surfaceContainerHigh,
                unfocusedContainerColor = cs.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Tomato,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(54.dp)
                .shadow(if (canSend) 6.dp else 0.dp, CircleShape)
                .clip(CircleShape)
                .background(sendBg)
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send, "Send",
                tint = if (canSend) Color.White else cs.onSurfaceVariant,
                modifier = Modifier.size(24.dp).alpha(if (canSend) 1f else 0.6f),
            )
        }
    }
}

/** Empty states are a tilted sticky note with a hint. */
@Composable
fun EmptyNote(title: String, hint: String) {
    val dark = Look.dark
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        StickyCard(Sticky.paper(null, dark), seed = 3, modifier = Modifier.width(280.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Sticky.ink(dark))
            Spacer(Modifier.height(4.dp))
            Text(hint, style = NoteText, color = Sticky.ink(dark).copy(alpha = 0.8f))
        }
    }
}

