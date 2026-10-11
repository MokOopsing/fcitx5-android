/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.utils

import android.system.ErrnoException
import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * DataManager removes what it syncs, symlinks included, before putting a new one in its place. On
 * a device: Robolectric's lstat follows a symlink and never fails, so it cannot tell a dangling one.
 */
class FileUtilTest {

    private val dir: File =
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.resolve("FileUtilTest")

    @Before
    fun setUp() {
        dir.deleteRecursively()
        dir.mkdirs()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun assertGone(file: File) {
        assertThrows(file.path, ErrnoException::class.java) { Os.lstat(file.path) }
    }

    @Test
    fun aSymlinkWhoseTargetIsGoneIsRemovedSoANewOneCanTakeItsPlace() {
        val link = dir.resolve("link")
        Os.symlink(dir.resolve("missing").path, link.path)
        assertFalse("exists() follows the link", link.exists())
        assertTrue(FileUtil.removeFile(link).isSuccess)
        assertGone(link)
        assertTrue(FileUtil.symlink(dir.resolve("a"), link).isSuccess)
    }

    @Test
    fun aDirectoryHoldingASymlinkWhoseTargetIsGoneIsRemoved() {
        val sub = dir.resolve("sub").apply { mkdirs() }
        Os.symlink(dir.resolve("missing").path, sub.resolve("link").path)
        assertTrue(FileUtil.removeFile(sub).isSuccess)
        assertGone(sub)
    }

    @Test
    fun aSymlinkToADirectoryIsRemovedAndTheDirectoryKept() {
        val target = dir.resolve("target").apply { mkdirs() }
        target.resolve("f").writeText("f")
        val link = dir.resolve("link")
        Os.symlink(target.path, link.path)
        assertTrue(FileUtil.removeFile(link).isSuccess)
        assertGone(link)
        assertEquals("f", target.resolve("f").readText())
    }

    @Test
    fun aDirectoryHoldingASymlinkToADirectoryKeepsTheTarget() {
        val target = dir.resolve("target").apply { mkdirs() }
        target.resolve("f").writeText("f")
        val sub = dir.resolve("sub").apply { mkdirs() }
        Os.symlink(target.path, sub.resolve("link").path)
        assertTrue(FileUtil.removeFile(sub).isSuccess)
        assertGone(sub)
        // the link deleted, not entered: what it points to is another's
        assertEquals("f", target.resolve("f").readText())
    }

    @Test
    fun nothingThereIsAlreadyRemoved() {
        assertTrue(FileUtil.removeFile(dir.resolve("none")).isSuccess)
    }
}
