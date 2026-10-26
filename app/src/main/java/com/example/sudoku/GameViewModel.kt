package com.example.sudoku

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Level(val label: String, val clues: Int) { EASY("Facile", 40), MEDIUM("Medio", 32), HARD("Difficile", 26), EXPERT("Esperto", 22) }
enum class Status { PLAYING, WON, LOST }

class GameState(
    val level: Level, val sol: IntArray, val giv: IntArray, val cur: IntArray, val notes: IntArray,
    val errors: Int = 0, val seconds: Int = 0, val selected: Int = -1,
    val noteMode: Boolean = false, val status: Status = Status.PLAYING
) {
    fun copy(
        cur: IntArray = this.cur, notes: IntArray = this.notes, errors: Int = this.errors,
        seconds: Int = this.seconds, selected: Int = this.selected,
        noteMode: Boolean = this.noteMode, status: Status = this.status
    ) = GameState(level, sol, giv, cur, notes, errors, seconds, selected, noteMode, status)
}

class GameViewModel(app: Application) : AndroidViewModel(app) {
    var state by mutableStateOf<GameState?>(null); private set
    var paused = false
    private val history = ArrayDeque<Pair<IntArray, IntArray>>()
    private val prefs = app.getSharedPreferences("sudoku", 0)

    /** Salva la partita in corso (chiamata da MainActivity.onStop). A partita finita cancella il salvataggio. */
    fun save() {
        val s = state ?: return
        if (s.status != Status.PLAYING) { prefs.edit().clear().apply(); return }
        prefs.edit()
            .putString("level", s.level.name)
            .putString("sol", s.sol.joinToString(""))
            .putString("giv", s.giv.joinToString(""))
            .putString("cur", s.cur.joinToString(""))
            .putString("notes", s.notes.joinToString(","))
            .putInt("errors", s.errors).putInt("seconds", s.seconds)
            .apply()
    }

    private fun load(): Boolean {
        return try {
            fun grid(k: String) = prefs.getString(k, null)?.map { it - '0' }?.toIntArray()
            val sol = grid("sol") ?: return false
            val giv = grid("giv") ?: return false
            val cur = grid("cur") ?: return false
            val notes = prefs.getString("notes", null)?.split(",")?.map { it.toInt() }?.toIntArray() ?: return false
            if (sol.size != 81 || giv.size != 81 || cur.size != 81 || notes.size != 81) return false
            state = GameState(
                Level.valueOf(prefs.getString("level", "MEDIUM")!!), sol, giv, cur, notes,
                prefs.getInt("errors", 0), prefs.getInt("seconds", 0)
            )
            true
        } catch (e: Exception) { false }
    }

    init {
        if (!load()) newGame(Level.MEDIUM)
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val s = state
                if (s != null && s.status == Status.PLAYING && !paused) state = s.copy(seconds = s.seconds + 1)
            }
        }
    }

    fun newGame(level: Level) {
        state = null; history.clear()
        viewModelScope.launch {
            val (p, sol) = withContext(Dispatchers.Default) { SudokuEngine.generate(level.clues) }
            state = GameState(level, sol, p, p.clone(), IntArray(81))
        }
    }

    fun retry() {
        val s = state ?: return; history.clear()
        state = GameState(s.level, s.sol, s.giv, s.giv.clone(), IntArray(81))
    }

    fun select(i: Int) { state = state?.copy(selected = i) }
    fun toggleNote() { state = state?.let { it.copy(noteMode = !it.noteMode) } }

    private fun push(s: GameState) {
        history.addLast(s.cur.clone() to s.notes.clone())
        if (history.size > 150) history.removeFirst()
    }

    private fun clean(notes: IntArray, i: Int, n: Int) {
        val r = i / 9; val c = i % 9; val br = r - r % 3; val bc = c - c % 3; val bit = (1 shl n).inv()
        for (k in 0 until 9) { notes[r * 9 + k] = notes[r * 9 + k] and bit; notes[k * 9 + c] = notes[k * 9 + c] and bit }
        for (a in 0..2) for (b in 0..2) { val j = (br + a) * 9 + bc + b; notes[j] = notes[j] and bit }
    }

    /** Cella modificabile: non bloccata e non già corretta. */
    private fun editable(s: GameState) =
        s.status == Status.PLAYING && s.selected >= 0 && s.giv[s.selected] == 0 && s.cur[s.selected] != s.sol[s.selected]

    fun put(n: Int) {
        val s = state ?: return; if (!editable(s)) return
        val i = s.selected; val cur = s.cur.clone(); val notes = s.notes.clone()
        if (s.noteMode) {
            if (cur[i] != 0) return
            push(s); notes[i] = notes[i] xor (1 shl n); state = s.copy(notes = notes); return
        }
        push(s); cur[i] = n; notes[i] = 0
        state = if (n != s.sol[i]) {
            val e = s.errors + 1
            s.copy(cur = cur, notes = notes, errors = e, status = if (e >= 3) Status.LOST else Status.PLAYING)
        } else {
            clean(notes, i, n)
            s.copy(cur = cur, notes = notes, status = if (cur.contentEquals(s.sol)) Status.WON else Status.PLAYING)
        }
    }

    fun erase() {
        val s = state ?: return; if (!editable(s)) return
        val i = s.selected; if (s.cur[i] == 0 && s.notes[i] == 0) return
        push(s)
        val cur = s.cur.clone(); val notes = s.notes.clone(); cur[i] = 0; notes[i] = 0
        state = s.copy(cur = cur, notes = notes)
    }

    fun undo() {
        val s = state ?: return; if (s.status != Status.PLAYING || history.isEmpty()) return
        val (c, n) = history.removeLast(); state = s.copy(cur = c, notes = n) // gli errori non si annullano
    }

    fun hint() {
        val s = state ?: return; if (!editable(s)) return
        val i = s.selected; push(s)
        val cur = s.cur.clone(); val notes = s.notes.clone(); cur[i] = s.sol[i]; notes[i] = 0
        clean(notes, i, s.sol[i])
        state = s.copy(cur = cur, notes = notes, status = if (cur.contentEquals(s.sol)) Status.WON else Status.PLAYING)
    }
}
