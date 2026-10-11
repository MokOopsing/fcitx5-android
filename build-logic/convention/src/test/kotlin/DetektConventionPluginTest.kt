/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DetektConventionPluginTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun everySourceSetsJavaAndKotlinDirectoriesAreChecked() {
        val module = folder.root
        for (dir in listOf("main/java", "debug/java", "androidTestVoice/java", "testFixtures/kotlin", "main/res", "main/cpp")) {
            module.resolve("src/$dir").mkdirs()
        }
        module.resolve("src/README.md").writeText("not a source set")
        assertEquals(
            listOf("androidTestVoice/java", "debug/java", "main/java", "testFixtures/kotlin").map { module.resolve("src/$it") },
            kotlinSourceDirs(module),
        )
    }

    @Test
    fun aModuleWithoutSourcesHasNone() {
        assertTrue(kotlinSourceDirs(folder.root).isEmpty())
    }
}
