/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.core.data

/**
 * The [FileAction]s that turn [old] into [shipped], in the order [DataManager.sync] runs them: by
 * [FileAction.ordinal], highest first, so what was at a path goes before what replaces it.
 */
fun DataHierarchy.plan(old: DataDescriptor, shipped: DataDescriptor): List<FileAction> =
    diff(old, shipped).sortedByDescending { it.ordinal }
