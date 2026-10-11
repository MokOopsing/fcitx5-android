/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.clipboard.db

import android.content.Context
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.fcitx.fcitx5.android.data.clipboard.clipboardDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Every schema Room exported to app/schemas upgrades to the current one, its clips kept, with the migrations the app
 * opens the clipboard with: a version left without one fails here, not at the users' next start, which would keep
 * their clips in memory until a build that has it. Built as Room's MigrationTestHelper would, but from the source
 * tree: the helper reads the schemas from the test's assets, which :app's unit tests run without
 * (isIncludeAndroidResources is off).
 */
@RunWith(RobolectricTestRunner::class)
class ClipboardDatabaseMigrationTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    // each version's identity as it shipped, one line each, a new version's added as it ships: an entity changed
    // without a new version rewrites the current one's schema, which the upgrade below then passes, while every user's
    // file fails Room's identity check and keeps their clips in memory
    private val shipped = mapOf(
        1 to "6703837121d954933d8376d61e4d8291",
        2 to "fc902cf5d26aca7d0ac10be1fc3728f0",
        3 to "3046f390f998a6787882456a529ac924",
        4 to "b0fe6cdac09e0d7deaff17d8b45fe565",
    )

    @Test
    fun everyExportedSchemaIsTheOneItsVersionShippedWith() {
        assertEquals(shipped, schemas().mapValues { (_, schema) -> databaseIn(schema).getValue("identityHash").jsonPrimitive.content })
    }

    // what Room checks a user's file against as it opens it, made from the entities as this build compiled them: an
    // entity changed without a new version fails here too, with the schemas' export off
    @Test
    fun aFreshDatabaseIsTheNewestVersionAsItShipped() {
        val name = "fresh"
        context.deleteDatabase(name)
        val db = clipboardDatabase(context, name)
        try {
            val hash = db.openHelper.readableDatabase.query("SELECT identity_hash FROM room_master_table").use {
                it.moveToFirst()
                it.getString(0)
            }
            assertEquals(shipped.getValue(shipped.keys.max()), hash)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun everyExportedSchemaUpgradesToTheCurrentOneWithItsClips() = runTest {
        val schemas = schemas()
        // none left out between the first and the current, whose version would go untested
        assertEquals((1..schemas.size).toList(), schemas.keys.sorted())
        schemas.forEach { (version, schema) ->
            val name = "migrated-v$version"
            createAt(name, schema)
            val db = clipboardDatabase(context, name)
            try {
                val clip = db.clipboardDao().get(1)
                assertEquals("from $version", "kept", clip?.text)
                assertTrue("from $version", clip?.pinned == true)
            } finally {
                db.close()
                context.deleteDatabase(name)
            }
        }
    }

    // from the module dir, where Gradle runs the tests, or the repo root, where an IDE run may start
    private fun schemas(): Map<Int, File> =
        listOf("schemas", "app/schemas")
            .map { File(it, ClipboardDatabase::class.java.name) }
            .first { it.isDirectory }
            .listFiles { file -> file.extension == "json" }!!
            .associateBy { it.nameWithoutExtension.toInt() }

    // the database a schema file describes
    private fun databaseIn(schema: File) = Json.parseToJsonElement(schema.readText()).jsonObject.getValue("database").jsonObject

    // the database as a build of [schema]'s version left it, a pinned clip in it
    private fun createAt(name: String, schema: File) {
        val database = databaseIn(schema)
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            database.getValue("entities").jsonArray.map { it.jsonObject }.forEach { entity ->
                val table = entity.getValue("tableName").jsonPrimitive.content
                val indices = entity["indices"]?.jsonArray.orEmpty().map { it.jsonObject.getValue("createSql") }
                (listOf(entity.getValue("createSql")) + indices).forEach {
                    db.execSQL(it.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            database.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.execSQL("INSERT INTO ${ClipboardEntry.TABLE_NAME} (id, text, pinned) VALUES (1, 'kept', 1)")
            db.version = database.getValue("version").jsonPrimitive.int
        }
    }
}
