/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.table

import org.fcitx.fcitx5.android.engine.data.CodeTable
import org.fcitx.fcitx5.android.engine.session.Action.Key
import org.fcitx.fcitx5.android.engine.session.Session
import org.fcitx.fcitx5.android.engine.session.Snapshot
import java.nio.ByteBuffer

/**
 * A table of [entries] ("code text") as the table tests build them: 五笔's keys, 码长 4 and its
 * 组词规则 for two, three and four characters or more, with [header] lines added or replaced.
 */
internal fun dictionary(vararg entries: String, header: Map<String, String> = emptyMap()) = CodeTable.Builder()
    .header("键码", "abcdefghijklmnopqrstuvwxy")
    .header("码长", "4")
    .apply { header.forEach { (k, v) -> header(k, v) } }
    .rule("e2", "p11+p12+p21+p22")
    .rule("e3", "p11+p21+p31+p32")
    .rule("a4", "p11+p21+p31+n11")
    .apply { entries.forEach { e -> e.split(' ').let { entry(it[0], it[1]) } } }
    .build()
    .toByteArray()
    .let { TableDictionary(CodeTable.load(ByteBuffer.wrap(it))) }

/** Types [keys], returning every snapshot's commit joined and the last snapshot. */
internal fun Session.type(keys: String): Pair<String, Snapshot> {
    var commit = ""
    var last: Snapshot? = null
    for (c in keys) last = apply(Key(c)).also { commit += it.commit }
    return commit to last!!
}
