/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

import java.io.File
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.HexFormat

/** A file's SHA-256 in lowercase hex: what a download is pinned to, and what the data descriptor lists. */
internal fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    DigestInputStream(file.inputStream(), digest).use { it.copyTo(OutputStream.nullOutputStream()) }
    return hex(digest.digest())
}

internal fun sha256Of(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

internal fun hex(bytes: ByteArray): String = HexFormat.of().formatHex(bytes)
