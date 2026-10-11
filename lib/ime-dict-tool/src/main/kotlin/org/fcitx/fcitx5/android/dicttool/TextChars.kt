/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.dicttool

/** What the corpus tools take for a Han character: one definition, as copies of it had drifted apart. */
object TextChars {
    /**
     * CJK Unified Ideographs and Extension A: the Han characters a Char holds. WebText, CommonCrawl
     * (MIN_HAN) and PageCleaner counted the first alone until they took this, NewWords both: Extension
     * A now counts toward their thresholds too, so the pages and lines they keep may differ from those
     * engine-data.sh's inputs were measured with.
     */
    fun isHan(c: Char) = c in '一'..'鿿' || c in '㐀'..'䶿'
}
