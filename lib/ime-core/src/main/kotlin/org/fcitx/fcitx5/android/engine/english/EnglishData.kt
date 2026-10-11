/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.english

import org.fcitx.fcitx5.android.engine.data.BitPacked
import org.fcitx.fcitx5.android.engine.data.DataFile
import org.fcitx.fcitx5.android.engine.data.DataFormatException
import org.fcitx.fcitx5.android.engine.data.NgramModel
import org.fcitx.fcitx5.android.engine.data.NgramTables
import org.fcitx.fcitx5.android.engine.data.PinyinData.Section
import org.fcitx.fcitx5.android.engine.data.StringTable
import org.fcitx.fcitx5.android.engine.data.Vocabulary
import org.fcitx.fcitx5.android.engine.data.WordIndex
import org.fcitx.fcitx5.android.engine.data.ensureFormat
import java.nio.ByteBuffer

/**
 * The compiled English data: the words as they are written (I, London, don't) and a trigram model
 * over them, in one [DataFile]. Words are also in the order of their [key]s, the letters typed for
 * them, for finding a word by its keys, or by keys near them. Written by [EnglishDataBuilder].
 */
class EnglishData private constructor(file: DataFile) {

    val meta: Map<String, String> = file.meta(Section.META)

    val vocabulary = Vocabulary(StringTable(file.bitPacked(Section.VOCAB_OFFSETS), file.section(Section.VOCAB_CHARS)))
    val model = NgramModel(file, meta.int(META_LM_VOCABULARY), meta.int(META_UNKNOWN))

    /** The sentence start, `<s>`: the context of a sentence's first word. */
    val start: Int = meta.int(META_START)

    /** The model's unknown word, `<unk>`: typed for none. */
    val unknown: Int = meta.int(META_UNKNOWN)

    private val keyOrder: BitPacked = file.bitPacked(KEY_ORDER)

    init {
        ensureFormat(model.vocabularySize == vocabulary.size) { "the model has ${model.vocabularySize} words of ${vocabulary.size}" }
        ensureFormat(start in 0 until vocabulary.size) { "start $start is no word" }
        keyOrder.checkBelow("key order", vocabulary.size)
    }

    /** How many words are in key order: all but the model's own (`<s>`, `<unk>`); none if the order is not. */
    val keyed: Int get() = keys.size

    /** The word at [rank] in key order. */
    fun keyedWord(rank: Int): Int = keyOrder[rank]

    // made when first asked for, as WordIndex is: some hundreds of milliseconds on a phone. Checked
    // here, not on load (unverified there, as the app's): keys out of order would send the search
    // past a key's end, and a file that broken offers no word rather than fail on a key
    private val keys: Array<String> by lazy {
        val keys = Array(keyOrder.size) { key(vocabulary.word(keyOrder[it])) }
        if ((1 until keys.size).all { keys[it - 1] <= keys[it] }) keys else emptyArray()
    }

    /** The key of the word at [rank] in key order. */
    fun keyAt(rank: Int): String = keys[rank]

    private val index: WordIndex by lazy { WordIndex(vocabulary, vocabulary.size) }

    /** The id of the word written [text], exactly, or [NgramModel.NO_WORD]. */
    fun id(text: CharSequence): Int = index.find(text)

    fun word(id: Int): String = vocabulary.word(id)

    /** Builds what is made when first asked for, off the thread that types. */
    fun warmUp() {
        keys
        index
    }

    companion object {
        /** Layout of the sections; bump on any change to it. */
        const val VERSION = 1

        internal const val META_LM_VOCABULARY = "lm.vocabulary"
        internal const val META_UNKNOWN = "lm.unknown"
        internal const val META_START = "lm.start"

        // past the pinyin data's numbers, whose model sections this file shares
        internal const val KEY_ORDER = 30

        /** What is typed for [word]: its letters, lower case, without apostrophes (dont for don't). */
        fun key(word: String): String {
            if (word.none { it == '\'' || it.isUpperCase() }) return word
            val b = StringBuilder(word.length)
            for (c in word) if (c != '\'') b.append(c.lowercaseChar())
            return b.toString()
        }

        /** @throws DataFormatException if [buffer] is not English data this build can read */
        fun load(buffer: ByteBuffer, verify: Boolean = true): EnglishData =
            EnglishData(DataFile.open(buffer, DataFile.KIND_ENGLISH, VERSION, verify))

        private fun Map<String, String>.int(key: String): Int =
            get(key)?.toIntOrNull() ?: throw DataFormatException("meta $key missing")
    }
}

/**
 * Compiles an ARPA-style backoff model of English words into the layout [EnglishData] reads. Runs on
 * the build host (lib/ime-dict-tool) and in tests. The words are the unigrams, in the order added;
 * [UNKNOWN] and [EnglishText.START] must be among them.
 */
class EnglishDataBuilder {
    private val ids = HashMap<String, Int>()
    private val words = ArrayList<String>()
    private val tables = NgramTables { words[it] }

    fun unigram(word: String, prob: Float, backoff: Float) = apply {
        finite(prob, backoff) { word }
        require(ids.put(word, words.size) == null) { "unigram \"$word\" added twice" }
        words += word
        tables.unigram(prob, backoff)
    }

    fun bigram(prev: String, word: String, prob: Float, backoff: Float) = apply {
        finite(prob, backoff) { "$prev $word" }
        tables.bigram(id(prev), id(word), prob, backoff)
    }

    fun trigram(prev2: String, prev: String, word: String, prob: Float) = apply {
        finite(prob, 0f) { "$prev2 $prev $word" }
        tables.trigram(id(prev2), id(prev), id(word), prob)
    }

    private inline fun finite(prob: Float, backoff: Float, ngram: () -> String) =
        require(prob.isFinite() && backoff.isFinite()) { "\"${ngram()}\": $prob $backoff" }

    private fun id(word: String) = requireNotNull(ids[word]) { "\"$word\" is not a unigram" }

    fun build(meta: Map<String, String> = emptyMap()): DataFile.Writer {
        val unknown = id(UNKNOWN)
        val start = id(EnglishText.START)
        val out = DataFile.Writer(DataFile.KIND_ENGLISH, EnglishData.VERSION)
        out.addMeta(
            Section.META,
            meta + mapOf(
                EnglishData.META_LM_VOCABULARY to words.size.toString(),
                EnglishData.META_UNKNOWN to unknown.toString(),
                EnglishData.META_START to start.toString(),
            ),
        )
        StringTable.write(out, Section.VOCAB_OFFSETS, Section.VOCAB_CHARS, words)
        tables.write(out, words.size)
        // the words typed, by key, the likelier of one key first; the model's own are typed by none
        val typed = words.indices.filter { it != unknown && it != start }
        val keys = typed.associateWith { EnglishData.key(words[it]) }
        val order = typed.sortedWith(compareBy<Int> { keys.getValue(it) }.thenByDescending { tables.unigramProb(it) }.thenBy { it })
        out.add(EnglishData.KEY_ORDER, BitPacked.encode(order.size) { order[it] })
        return out
    }

    companion object {
        const val UNKNOWN = "<unk>"
    }
}
