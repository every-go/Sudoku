package com.example.sudoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    background = Color.White,
                    surface = Color.White,
                    onSurface = Color.Black,
                    primary = Color(0xFF1A4FBF),          // blu più scuro per i numeri tuoi
                    surfaceVariant = Color(0xFFE3E8F0)    // sfondo dei tasti del tastierino
                )
            ) {
                Surface(Modifier.fillMaxSize()) { SudokuScreen(vm) }
            }
        }
    }
    override fun onStart() { super.onStart(); vm.paused = false }
    override fun onStop() { super.onStop(); vm.paused = true; vm.save() }
}

@Composable
fun SudokuScreen(vm: GameViewModel) {
    val s = vm.state
    var showNew by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Sudoku", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Button(onClick = { showNew = true }) { Text("Nuova partita") }
        }
        if (s == null) {
            Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) { CircularProgressIndicator() }
        } else {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(s.level.label)
                Text("Errori ${s.errors}/3", color = if (s.errors > 0) MaterialTheme.colorScheme.error else Color.Unspecified)
                Text("%02d:%02d".format(s.seconds / 60, s.seconds % 60))
            }
            Board(s) { vm.select(it) }
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                Tool("Annulla", false, Modifier.weight(1f)) { vm.undo() }
                Tool("Cancella", false, Modifier.weight(1f)) { vm.erase() }
                Tool("Note", s.noteMode, Modifier.weight(1f)) { vm.toggleNote() }
                Tool("Aiuto", false, Modifier.weight(1f)) { vm.hint() }
            }
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(5.dp)) {
                for (n in 1..9) {
                    val left = 9 - s.cur.indices.count { s.cur[it] == n && s.sol[it] == n }
                    Box(
                        Modifier.weight(1f).aspectRatio(0.7f).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = left > 0) { vm.put(n) },
                        Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$n", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary.copy(if (left > 0) 1f else .25f))
                            Text(if (left > 0) "$left" else "", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }

    if (showNew) LevelDialog("Nuova partita", "Scegli la difficoltà.", { showNew = false }, null) { showNew = false; vm.newGame(it) }
    if (s != null && s.status == Status.WON)
        LevelDialog("Completato", "Tempo %02d:%02d, ${s.errors} errori.".format(s.seconds / 60, s.seconds % 60), null, null) { vm.newGame(it) }
    if (s != null && s.status == Status.LOST)
        LevelDialog("Partita persa", "Hai fatto 3 errori.", null, { vm.retry() }) { vm.newGame(it) }
}

@Composable
fun LevelDialog(title: String, msg: String, onDismiss: (() -> Unit)?, onRetry: (() -> Unit)?, onPick: (Level) -> Unit) {
    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        title = { Text(title) },
        text = {
            Column {
                Text(msg)
                if (onRetry != null) TextButton(onClick = onRetry) { Text("Riprova questo schema") }
                Level.values().forEach { l -> TextButton(onClick = { onPick(l) }) { Text(l.label) } }
            }
        },
        confirmButton = {},
        dismissButton = { if (onDismiss != null) TextButton(onClick = onDismiss) { Text("Continua a giocare") } }
    )
}

@Composable
fun Tool(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (on) Button(onClick, modifier, contentPadding = PaddingValues(0.dp)) { Text(label, fontSize = 13.sp) }
    else OutlinedButton(onClick, modifier, contentPadding = PaddingValues(0.dp)) { Text(label, fontSize = 13.sp) }
}

@Composable
fun Board(s: GameState, onSelect: (Int) -> Unit) {
    val line = MaterialTheme.colorScheme.onSurface
    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
        Column(Modifier.fillMaxSize()) {
            for (r in 0..8) Row(Modifier.weight(1f).fillMaxWidth()) {
                for (c in 0..8) Cell(s, r * 9 + c, Modifier.weight(1f).fillMaxHeight()) { onSelect(r * 9 + c) }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width / 9
            for (k in 0..9) {
                val thick = k % 3 == 0
                val t = if (thick) 3.dp.toPx() else 0.5.dp.toPx()
                val col = line.copy(alpha = if (thick) 1f else .5f)
                drawLine(col, Offset(k * w, 0f), Offset(k * w, size.height), t)
                drawLine(col, Offset(0f, k * w), Offset(size.width, k * w), t)
            }
        }
    }
}

@Composable
fun Cell(s: GameState, i: Int, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val v = s.cur[i]; val sel = s.selected
    val given = s.giv[i] != 0
    val wrong = v != 0 && !given && v != s.sol[i]
    val peer = sel >= 0 && (i / 9 == sel / 9 || i % 9 == sel % 9 || (i / 27 == sel / 27 && (i % 9) / 3 == (sel % 9) / 3))
    val same = sel >= 0 && s.cur[sel] != 0 && v == s.cur[sel] && !wrong
    val bg = when {
        i == sel -> cs.primary.copy(.5f)      // era .35f
        wrong -> cs.error.copy(.2f)
        same -> cs.primary.copy(.3f)          // era .2f
        peer -> cs.onSurface.copy(.12f)       // era .07f
        else -> Color.Transparent
    }
    Box(modifier.background(bg).clickable(onClick = onClick), Alignment.Center) {
        if (v != 0) {
            Text(
                "$v", fontSize = 26.sp,
                fontWeight = if (given) FontWeight.Bold else FontWeight.Normal,
                color = when { wrong -> cs.error; given -> cs.onSurface; else -> cs.primary }
            )
        } else if (s.notes[i] != 0) {
            Column {
                for (r in 0..2) Row {
                    for (c in 0..2) {
                        val n = r * 3 + c + 1
                        Text(
                            if (((s.notes[i] shr n) and 1) == 1) "$n" else " ",
                            fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.width(10.dp)
                        )
                    }
                }
            }
        }
    }
}
