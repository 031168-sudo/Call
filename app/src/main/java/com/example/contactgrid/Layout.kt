package com.example.contactgrid

import androidx.compose.ui.geometry.Offset

const val COLS = 3
const val ROWS = 4
const val PER_PAGE = COLS * ROWS

/** Геометрия страницы в пикселях: одинакова для всех страниц. */
data class Grid(val cellW: Float, val cellH: Float, val pad: Float, val gap: Float) {
    val aspect: Float get() = cellW / cellH

    fun origin(pos: Int): Offset {
        val col = pos % COLS
        val row = pos / COLS
        return Offset(pad + col * (cellW + gap), pad + row * (cellH + gap))
    }

    /** Ячейка под точкой, или null, если точка мимо сетки. */
    fun cellAt(p: Offset): Int? {
        val col = ((p.x - pad) / (cellW + gap)).toInt()
        val row = ((p.y - pad) / (cellH + gap)).toInt()
        if (p.x < pad || p.y < pad || col !in 0 until COLS || row !in 0 until ROWS) return null
        return row * COLS + col
    }

    /** Ближайшая ячейка — для перетаскивания. */
    fun cellAtClamped(p: Offset): Int {
        val col = ((p.x - pad) / (cellW + gap)).toInt().coerceIn(0, COLS - 1)
        val row = ((p.y - pad) / (cellH + gap)).toInt().coerceIn(0, ROWS - 1)
        return row * COLS + col
    }

    companion object {
        fun of(width: Int, height: Int, pad: Float, gap: Float): Grid? {
            if (width <= 0 || height <= 0) return null
            val w = (width - 2 * pad - (COLS - 1) * gap) / COLS
            val h = (height - 2 * pad - (ROWS - 1) * gap) / ROWS
            return Grid(w, h, pad, gap)
        }
    }
}

fun pageCount(people: List<Person>): Int =
    ((people.maxOfOrNull { it.slot } ?: 0) / PER_PAGE + 1).coerceAtLeast(1)

fun firstFreeSlot(people: List<Person>): Int {
    val used = people.map { it.slot }.toHashSet()
    var s = 0
    while (s in used) s++
    return s
}

/**
 * Раскладка с учётом перетаскиваемой карточки, как на рабочем столе телефона:
 * свободное место — карточка встаёт туда; занятое — соседи сдвигаются к ближайшему
 * свободному месту на странице; страница заполнена — карточки меняются местами.
 */
fun arrange(people: List<Person>, dragId: String?, hover: Int?): Map<String, Int> {
    val res = people.associate { it.id to it.slot }.toMutableMap()
    if (dragId == null || hover == null) return res
    val origin = res.remove(dragId) ?: return res
    val page = hover / PER_PAGE
    val base = page * PER_PAGE
    val occ = arrayOfNulls<String>(PER_PAGE)
    res.forEach { (id, s) -> if (s / PER_PAGE == page) occ[s - base] = id }
    val h = hover - base
    if (occ[h] != null) {
        val after = (h + 1 until PER_PAGE).firstOrNull { occ[it] == null }
        val before = (h - 1 downTo 0).firstOrNull { occ[it] == null }
        when {
            after != null -> for (i in after downTo h + 1) occ[i] = occ[i - 1]
            before != null -> for (i in before until h) occ[i] = occ[i + 1]
            else -> res[occ[h]!!] = origin   // страница полна — обмен
        }
        occ[h] = null
        occ.forEachIndexed { i, id -> if (id != null) res[id] = base + i }
    }
    res[dragId] = hover
    return res
}

/** Убирает пустые страницы, сохраняя расположение карточек внутри страниц. */
fun normalize(people: List<Person>): List<Person> {
    val pages = people.map { it.slot / PER_PAGE }.distinct().sorted()
    val remap = pages.withIndex().associate { (newIdx, old) -> old to newIdx }
    return people.map { p ->
        val newPage = remap[p.slot / PER_PAGE] ?: 0
        p.copy(slot = newPage * PER_PAGE + p.slot % PER_PAGE)
    }
}
