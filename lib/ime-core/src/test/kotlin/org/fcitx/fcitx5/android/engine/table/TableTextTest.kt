/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.table

import org.fcitx.fcitx5.android.engine.data.CodeTable
import org.fcitx.fcitx5.android.engine.data.CodeTableReader
import org.fcitx.fcitx5.android.engine.data.SourceException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer

class TableTextTest {

    private fun read(text: String) = CodeTableReader().apply { read(text.reader().buffered(), "table") }

    private fun CodeTableReader.table() = CodeTable.load(ByteBuffer.wrap(builder.build().toByteArray()))

    private fun CodeTable.texts(code: String) = exactRange(code).map { text(it) }

    @Test
    fun theTablesOwnWordsAllStayOrderedByUseAndOnlyAnAddedRareWordGoes() {
        val reader = read(
            "键码=abcdefgh\n码长=4\n[组词规则]\ne2=p11+p12+p21+p22\n[数据]\n" +
                "ab 工\nab 共\nab 公\ncd 作\nabcd 攻错\nabcd 工作\nabcd 宫作\n",
        )
        // 共作 and 公作 are abcd by the rules, as the table's own are
        val score = mapOf("工作" to -3f, "攻错" to -5f, "共作" to -3.5f, "公作" to -4.5f)
        val added = TableText.addWords(reader, listOf("共作", "公作"), { score[it] ?: Float.NEGATIVE_INFINITY }) { false }
        // 攻错 a hundredth of 工作 and 宫作 never seen: the table's, they stay; 公作, added, a
        // thirtieth, goes
        assertEquals(listOf("工作", "共作", "攻错", "宫作"), reader.table().texts("abcd"))
        assertEquals(0, added.fresh)
        assertEquals(1, added.others)
        assertEquals(1, added.dropped)
        assertEquals(listOf("工", "共", "公"), reader.table().texts("ab"))
    }

    @Test
    fun aTableIsBuiltWithItsPhrasesCodedAndFailsWhereTheEngineCannotUseIt() {
        val reader = read("键码=abcd\n码长=4\n[组词规则]\ne2=p11+p12+p21+p22\n[数据]\nab 工\ncd 作\n[词组]\n工作\n工无\n")
        val table = CodeTable.load(ByteBuffer.wrap(TableText.build(reader, "table")))
        // 无 has no code, so 工无 has none
        assertEquals(listOf("工作"), table.texts("abcd"))
        assertEquals(3, table.size)
        assertEquals(1, CodeTable.load(ByteBuffer.wrap(TableText.build(read("键码=a\n码长=4\n[数据]\na 工\n"), "table"))).size)
        val e = assertThrows(SourceException::class.java) { TableText.build(read("键码=a\n[数据]\na 工\n"), "table") }
        assertEquals(0, e.line)
        assertThrows(SourceException::class.java) {
            TableText.build(read("键码=a\n码长=4\n[组词规则]\ne2=p11+\n[数据]\na 工\n[词组]\n工工\n"), "table")
        }
    }
}
