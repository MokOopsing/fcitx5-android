/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */

import org.gradle.api.JavaVersion

object Versions {

    val java = JavaVersion.VERSION_11
    const val compileSdk = 36
    const val minSdk = 23
    const val targetSdk = 36

    const val defaultCMake = "3.31.6"
    const val defaultNDK = "28.0.13004108"
    const val defaultBuildTools = "36.1.0"

    // NOTE: increase this value to bump version code
    const val baseVersionCode = 11
    const val baseVersionName = "0.1.3"

    private val abiIds = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 3, "x86_64" to 4)
    val supportedABIs = abiIds.keys
    const val fallbackABI = "arm64-v8a"

    fun calculateVersionCode(abi: String = fallbackABI): Int {
        val abiId = abiIds[abi] ?: 0
        return baseVersionCode * 10 + abiId
    }
}
