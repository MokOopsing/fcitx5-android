/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.user

/**
 * What the user commits for what they type, where the engine did not offer it first: `ee` for
 * 嗯嗯. The decoder reads `ee` as e e (呃呃), and 嗯嗯 (en en) only as two syllables each the start
 * of a longer one, a penalty each that learning the word never makes up: put together from 嗯
 * and 嗯 once, it stayed far down. A habit is offered first for its keys instead, however the
 * decoder reads them.
 *
 * Each text of a key counts its commits: [learn] adds one to what was committed (or [WHOLE], see
 * there), unless the engine offers it first on its own, and halves the others of the key, passed
 * over. The text
 * counted most is the habit, from [MIN_COUNT] on. Keys are as typed and per input method
 * ([scope]): `ee` in 双拼 is other syllables. At most [limit] are kept, those counted least going
 * first. What the session teaches it is its to say (PinyinSession: text put together from pieces).
 */
class KeyHabits(private val limit: Int = DEFAULT_LIMIT) {
    init {
        require(limit > 0) { "limit $limit" }
    }

    /** Hears of each count as it changes, to keep it: see [UserStore]. Zero is gone. */
    fun interface Journal {
        fun changed(keys: String, text: String, count: Float)
    }

    var journal: Journal? = null

    // scoped keys, to the texts committed for them and their counts
    private val habits = HashMap<String, HashMap<String, Float>>()

    /** The habits of one input method: its keys apart from another's. */
    inner class Scope internal constructor(private val name: String) {
        private fun key(keys: String) = name + SCOPE_END + keys

        /** The text to offer first for [keys], or null if there is none. */
        fun habit(keys: String): String? = this@KeyHabits.habit(key(keys))

        /**
         * [text] committed for [keys], where the engine would offer [own] first without habits:
         * counted, by [weight], unless it is that, which needs no habit; the key's others, passed
         * over, halved.
         */
        fun learn(keys: String, text: String, own: String?, weight: Float = 1f) {
            if (keys.length <= MAX_KEY) this@KeyHabits.learn(key(keys), text, own, weight)
        }

        /** Forgets every habit of [text], of any input method. */
        fun forget(text: String) = this@KeyHabits.forget(text)
    }

    fun scope(name: String): Scope {
        require(SCOPE_END !in name) { "scope $name" }
        return Scope(name)
    }

    private fun habit(key: String): String? =
        habits[key]?.maxByOrNull { it.value }?.takeIf { it.value >= MIN_COUNT }?.key

    private fun learn(key: String, text: String, own: String?, weight: Float) {
        if (text.isEmpty() || text.length > MAX_TEXT) return
        // the others committed for these keys before, passed over now
        habits[key]?.filterKeys { it != text }?.forEach { (other, count) -> set(key, other, count / 2) }
        if (text == own) return
        set(key, text, (habits[key]?.get(text) ?: 0f) + weight)
        trim(key, text)
    }

    /** A count as the log has it, not learned again: zero is none. */
    fun restore(scopedKeys: String, text: String, count: Float) {
        if (count < DROP) habits[scopedKeys]?.let { texts ->
            if (texts.remove(text) != null) size--
            if (texts.isEmpty()) habits.remove(scopedKeys)
        } else if (habits.getOrPut(scopedKeys) { HashMap() }.put(text, count) == null) {
            size++
        }
    }

    /** Forgets every habit of [text], a word the user forgot or blocked. */
    fun forget(text: String) = forgetAll(setOf(text))

    /** [forget] for each of [texts], in one pass. */
    fun forgetAll(texts: Set<String>) {
        if (texts.isEmpty()) return
        val gone = habits.flatMap { (key, kept) -> kept.keys.filter { it in texts }.map { key to it } }
        gone.forEach { (key, text) -> set(key, text, 0f) }
    }

    /** Each count kept, its keys scoped as [restore] takes them. */
    fun forEach(visit: (scopedKeys: String, text: String, count: Float) -> Unit) {
        for ((key, texts) in habits) for ((text, c) in texts) visit(key, text, c)
    }

    // counted as they change: summed, it was a pass over every key at each learn at the limit
    var size: Int = 0
        private set

    private fun set(key: String, text: String, count: Float) {
        val kept = if (count < DROP) 0f else count
        restore(key, text, kept)
        journal?.changed(key, text, kept)
    }

    // the least counted go when there are too many, but not the one just learned
    private fun trim(key: String, text: String) {
        val over = size - limit
        if (over <= 0) return
        if (over > 1) {
            // a log kept with a higher limit, at its first learn: all of them sorted, once
            val all = ArrayList<Triple<String, String, Float>>(size)
            for ((k, texts) in habits) for ((t, c) in texts) if (k != key || t != text) all += Triple(k, t, c)
            all.sortedWith { a, b -> compare(a.third, a.first, a.second, b) }.take(over).forEach { (k, t, _) -> set(k, t, 0f) }
            return
        }
        // one over, as each learn at the limit is: the least of them, a habit copied out only if less than those before
        var least: Triple<String, String, Float>? = null
        for ((k, texts) in habits) for ((t, c) in texts) {
            if (k == key && t == text) continue
            if (least == null || compare(c, k, t, least) < 0) least = Triple(k, t, c)
        }
        least?.let { (k, t, _) -> set(k, t, 0f) }
    }

    // by count, then keys, then text: the same ones go whatever order the map keeps them in
    private fun compare(count: Float, key: String, text: String, other: Triple<String, String, Float>): Int = when {
        count != other.third -> count.compareTo(other.third)
        key != other.first -> key.compareTo(other.first)
        else -> text.compareTo(other.second)
    }

    companion object {
        const val DEFAULT_LIMIT = 5000

        /**
         * What a word of two characters or more picked whole for keys of 简拼 counts, not put
         * together: twice is a habit. The user model's lift never put such a word first where the
         * engine reads its keys at a penalty another reading does not pay: 啧啧啧 for zzz, three
         * initials, below ZZZ, letters at no cost; 那个 for ng below 嗯. Once is not, as one pick
         * may be a slip. Keys spelt out are the user model's, which weighs what came before: a
         * habit of 实施 for shishi would put it before 事实 everywhere.
         */
        const val WHOLE = 0.5f

        /**
         * What committing [text] counts for its keys: one where it was [putTogether] from pieces,
         * what learning its words does not bring up (嗯嗯 for ee, read en en only at a penalty a
         * syllable), or where a habit was offered; [WHOLE] for a word picked whole for keys that
         * are [abbreviated]; nothing for a character, the user model's alone: a habit would put
         * it first whatever came before, as 泥 for ni every time.
         */
        fun weight(putTogether: Boolean, text: String, abbreviated: Boolean): Float = when {
            putTogether -> 1f
            abbreviated && text.codePointCount(0, text.length) > 1 -> WHOLE
            else -> 0f
        }

        /** Committed once is a habit: what the user had to look for, they want first next time. */
        const val MIN_COUNT = 1f

        // passed over twice, a habit of one is gone
        private const val DROP = 0.3f
        private const val MAX_KEY = 64
        private const val MAX_TEXT = 32
        private const val SCOPE_END = '\u0000'
    }
}
