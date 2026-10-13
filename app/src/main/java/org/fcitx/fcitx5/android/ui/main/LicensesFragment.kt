/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.License
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import org.fcitx.fcitx5.android.utils.addPreference
import org.fcitx.fcitx5.android.utils.openUrl

class LicensesFragment : PaddingPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val res = resources
        lifecycleScope.launch {
            // a few hundred libraries' entries: read and parsed off the main thread
            val libraries = withContext(Dispatchers.IO) {
                val jsonString = res.openRawResource(R.raw.aboutlibraries)
                    .bufferedReader()
                    .use { it.readText() }
                Libs.Builder()
                    .withJson(jsonString)
                    .build()
                    .libraries
                    .sortedBy {
                        if (it.tag == "native") it.uniqueId.uppercase() else it.uniqueId.lowercase()
                    }
            }
            preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
                libraries.forEach {
                    addPreference(
                        title = "${it.uniqueId}:${it.artifactVersion}",
                        summary = it.licenses.joinToString { l -> l.spdxId ?: l.name }
                    ) {
                        showLicenseDialog(it.uniqueId, it.licenses)
                    }
                }
            }
        }
    }

    private fun showLicenseDialog(uniqueId: String, licenses: Set<License>): Boolean {
        when (licenses.size) {
            0 -> {}
            1 -> showLicenseContent(licenses.first())
            else -> {
                val licenseArray = licenses.toTypedArray()
                val licenseNames = licenseArray.map { it.spdxId ?: it.name }.toTypedArray()
                AlertDialog.Builder(requireContext())
                    .setTitle(uniqueId)
                    .setItems(licenseNames) { _, idx ->
                        showLicenseContent(licenseArray[idx])
                    }
                    .setPositiveButton(android.R.string.cancel, null)
                    .show()
            }
        }
        return true
    }

    private fun showLicenseContent(license: License) {
        license.url?.takeIf { it.isNotBlank() }?.let { requireContext().openUrl(it) }
    }
}
