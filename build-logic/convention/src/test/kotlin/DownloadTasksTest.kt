/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadTasksTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun download(out: File, hash: String) =
        ProjectBuilder.builder().withProjectDir(folder.newFolder()).build()
            .tasks.register("download", DownloadTask::class.java).get().apply {
                // nothing listens there: a fetch fails at once, without the network
                url.set("http://127.0.0.1:1/engine-data.tar.zst")
                sha256.set(hash)
                outputFile.set(out)
            }

    // what CI's cache of the downloads and an offline build put there is used as it is
    @Test
    fun aFileThereWithItsSha256IsNotFetchedAgain() {
        val out = folder.newFile("engine-data.tar.zst").apply { writeText("downloaded before") }
        download(out, sha256Of(out)).download()
        assertEquals("downloaded before", out.readText())
        assertFalse(File(out.path + ".part").exists())
    }

    @Test
    fun aFileThereWithAnotherSha256IsFetchedAgain() {
        val out = folder.newFile("engine-data.tar.zst").apply { writeText("cut short") }
        val task = download(out, sha256Of("the whole archive".toByteArray()))
        val failed = assertThrows(IllegalStateException::class.java) { task.download() }
        assertTrue(failed.message!!.startsWith("cannot download http://127.0.0.1:1/"))
        assertEquals("cut short", out.readText())
    }

    @Test
    fun aMissingCmakeOfCmakeDirNamesNoSdkPackage() {
        val sdk = noCmake(File("/sdk/cmake/3.31.6/bin/cmake"), fromCmakeDir = false)
        assertTrue(sdk, sdk.contains("sdkmanager \"cmake;3.31.6\""))
        val local = noCmake(File("/opt/cmake/bin/cmake"), fromCmakeDir = true)
        assertFalse(local, local.contains("sdkmanager"))
        assertTrue(local, local.contains("cmake.dir"))
    }
}
