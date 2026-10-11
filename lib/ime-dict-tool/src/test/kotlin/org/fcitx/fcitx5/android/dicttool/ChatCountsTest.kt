/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.dicttool

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatCountsTest {

    // 𠀀 is U+20000, of CJK Ext-B: two UTF-16 chars
    private val words = listOf("<unk>", "你", "好", "𠀀", "𠀀好")

    private val model = unigrams(words)

    private fun unigrams(vocabulary: List<String>) = ArpaModel.read(
        vocabulary.joinToString("", "\\data\\\nngram 1=${vocabulary.size}\n\n\\1-grams:\n", "\n\\end\\\n") { "-1.5\t$it\n" }.reader().buffered(),
        "test",
    )

    private fun id(word: String) = words.indexOf(word)

    private fun ChatCounts.bigram(v: Int, w: Int): Int {
        val i = bigrams.indexOf(NgramKey.of(v, w))
        return if (i >= 0) bigramCount[i] else 0
    }

    @Test
    fun aWordWithACharPastUffffIsCounted() {
        val counts = ChatCounts(model)
        counts.add("你𠀀好")
        // 你 𠀀好: two words likelier than three
        assertEquals(2L, counts.tokens)
        assertEquals(1, counts.bigram(counts.start, id("你")))
        assertEquals(1, counts.bigram(id("你"), id("𠀀好")))
    }

    @Test
    fun aCharPastUffffThatIsNoWordOrHalfOfOneSplitsTheText() {
        val counts = ChatCounts(model)
        // an emoji, and a surrogate without its other half
        counts.add("你😀好")
        counts.add("你\uD840好")
        assertEquals(4L, counts.tokens)
        assertEquals(0, counts.bigram(id("你"), id("好")))
        assertEquals(2, counts.bigram(counts.start, id("好")))
    }

    /** Thousands of words of consecutive code points, as CJK's are: each is found by its chars, however long its probe. */
    @Test
    fun everyWordOfAVocabularyOfThousandsIsFound() {
        // 50 chars from U+4E00 and every pair of them: 2,551 words in 4,096 slots, a probe wrapping round the end
        val chars = (0 until 50).map { Char(0x4E00 + it).toString() }
        val vocabulary = listOf("<unk>") + chars + chars.flatMap { a -> chars.map { b -> a + b } }
        val counts = ChatCounts(unigrams(vocabulary))
        // each added alone: a pair is one word, likelier than its two chars
        vocabulary.drop(1).forEach(counts::add)
        assertEquals(vocabulary.size - 1L, counts.tokens)
        for (id in 1 until vocabulary.size) assertEquals(vocabulary[id], 1, counts.bigram(counts.start, id))
    }
}
