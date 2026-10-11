/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

// Compiles the engine's data files from their text sources (libime's dictionary, language model
// and code tables). A host-side tool: it never ships in the APK; the formats themselves, and
// the code that reads them on the device, live in :lib:ime-core.
//
//   ./gradlew :lib:ime-dict-tool:run --args="pinyin -o <out> --lm <lm.arpa> <dict.txt>..."
//   ./gradlew :lib:ime-dict-tool:run --args="table -o <out> <table.txt>"
//   ./gradlew :lib:ime-dict-tool:run --args="mix -o <out.arpa> --lm <lm.arpa> <chat.jsonl.gz | fineweb.parquet>..."
//   ./gradlew :lib:ime-dict-tool:run --args="check <pinyin data> <lm.arpa>"
//
// A release's data (a CommonCrawl crawl's Chinese pages, cleaned, the chat-like of them mixed in as
// FineWeb-2's are, and the new words of them all) is engine-data.sh's to make, a job of an hour for
// a rented machine.
//
// The app build runs it through EngineDataPlugin (build-logic): `./gradlew :app:compileEngineData`
// downloads libime's sources and the model engine-data.sh mixed (a release's) once, and puts
// engine/pinyin.data into the app's assets.
plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    // coverage reported only (`koverHtmlReport`, uploaded by CI), with no floor yet
    alias(libs.plugins.kover)
}

application {
    mainClass.set("org.fcitx.fcitx5.android.dicttool.MainKt")
    // mix holds two language models and the chat's counts in memory: 7 GB resident over two
    // FineWeb-2 shards, as measured (engine-data.sh sets its own)
    applicationDefaultJvmArgs = listOf("-Xmx6g")
}

tasks.named<JavaExec>("run") {
    // relative paths in --args resolve against where gradle was started, not this module
    workingDir = rootProject.projectDir
}

dependencies {
    implementation(project(":lib:ime-core"))
    implementation(libs.duckdb.jdbc)
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    // MainTest compiles the lists the app build passes: an edit to one alone runs it again
    inputs.files(rootProject.files("lexicon/readings.tsv", "lexicon/misreadings.tsv", "lexicon/remove.tsv"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
    testLogging {
        events("failed")
    }
}
