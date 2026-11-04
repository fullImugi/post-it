package com.fullimugi.postit

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay

/** The pop-up "messenger" the widget opens. Stays open so you can fire off several notes. */
class QuickCaptureActivity : ComponentActivity() {

    companion object {
        const val EXTRA_VOICE = "voice"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val voice = intent.getBooleanExtra(EXTRA_VOICE, false)
        val shared = if (intent.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
        setContent {
            PostItTheme {
                CaptureSheet(startVoice = voice, initial = shared.orEmpty(), onClose = { finish() }, onOpenApp = {
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                })
            }
        }
    }
}

@Composable
private fun CaptureSheet(startVoice: Boolean, initial: String, onClose: () -> Unit, onOpenApp: () -> Unit) {
    val ctx = LocalContext.current
    val version by Store.version.collectAsState()
    val recent = remember(version) { Store.db(ctx).messages(5).reversed() }
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }

    val send: (String) -> Unit = { t ->
        if (t.isNotBlank()) {
            Capture.capture(ctx, t)
            text = ""
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!said.isNullOrBlank()) send(said) else focus.requestFocus()
    }
    val startListening = {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a note")
        try {
            voiceLauncher.launch(i)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "Voice typing isn't available on this phone", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (startVoice) {
            startListening()
        } else {
            delay(150)
            focus.requestFocus()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 5.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("What's on your mind?", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                        Squiggle(Tomato)
                    }
                    TextButton(onClick = onOpenApp) { Text("Open app", color = Tomato) }
                }
                recent.forEach { m -> Bubble(m) }
                Spacer(Modifier.height(4.dp))
                ChatComposer(
                    text = text,
                    onText = { text = it },
                    onSend = { send(text) },
                    onMic = { startListening() },
                    focus = focus,
                )
            }
        }
    }
}

private val tagRe = Regex("""#([\p{L}\p{N}_-]+)""")

@Composable
fun Bubble(m: Message) {
    val dark = Look.dark
    val key = tagRe.find(m.text)?.groupValues?.get(1) ?: "m${m.id}"
    val paper = Sticky.paper(key, dark)
    val ink = Sticky.ink(dark)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(
            color = paper,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp),
            shadowElevation = 2.dp,
            modifier = Modifier.padding(start = 48.dp),
        ) {
            Text(
                m.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = NoteText,
                color = ink,
                maxLines = 4,
            )
        }
        Text(
            "✓ ${m.reply} · ${Fmt.ago(m.createdAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp, end = 6.dp),
        )
    }
}
