/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.text.inSpans
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.data.pinyin.ImportedDictionaries.Unread
import org.fcitx.fcitx5.android.data.pinyin.PinyinDictManager
import org.fcitx.fcitx5.android.data.pinyin.dict.PinyinDictionary
import org.fcitx.fcitx5.android.data.pinyin.dict.TextDictionary
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import org.fcitx.fcitx5.android.ui.common.withLoadingDialog
import org.fcitx.fcitx5.android.ui.main.MainViewModel
import org.fcitx.fcitx5.android.ui.main.MainViewModel.ButtonMode
import org.fcitx.fcitx5.android.ui.main.launchOutlivingPage
import org.fcitx.fcitx5.android.utils.Const
import org.fcitx.fcitx5.android.utils.addPreference
import org.fcitx.fcitx5.android.utils.appContext
import org.fcitx.fcitx5.android.utils.importErrorDialog
import org.fcitx.fcitx5.android.utils.lazyRoute
import org.fcitx.fcitx5.android.utils.navigateWithAnim
import org.fcitx.fcitx5.android.utils.openUrl
import org.fcitx.fcitx5.android.utils.queryFileName
import org.fcitx.fcitx5.android.utils.toast
import org.fcitx.fcitx5.android.utils.withLowercaseExtension
import timber.log.Timber
import java.io.InputStream
import kotlin.coroutines.CoroutineContext

/**
 * The three layers of the dictionary, as they rank: the user dictionary, the new words, the
 * base. A dictionary the user imports is merged into one of them, as asked when it is imported;
 * those merged into a layer rank alike, and can be turned off, moved or deleted.
 */
class PinyinDictionaryFragment : PaddingPreferenceFragment() {

    private val args by lazyRoute<SettingsRoute.PinyinDict>()

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var launcher: ActivityResultLauncher<String>

    /** the file opened with the app asked about: the view, made anew back from My words, has no saved state to tell */
    private var asked = false

    /** whether this view told of the dictionaries put aside unread: the list is built again after each change */
    private var toldUnread = false

    private class Listed(val dictionary: TextDictionary, val intoNew: Boolean, val words: Int)

    private enum class Layer(@StringRes val title: Int, @StringRes val hint: Int) {
        USER(R.string.layer_user, R.string.import_into_user),
        NEW(R.string.layer_new, R.string.import_into_new),
        BASE(R.string.layer_base, R.string.import_into_base),
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcher = registerForActivityResult(ActivityResultContracts.GetContent()) { it?.let(::askLayer) }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.toolbarButton.value = ButtonMode.NONE
        toldUnread = false
        rebuild()
        // a file opened with the app, once (the home page passes none, as "")
        if (savedInstanceState == null && !asked) args.uri?.takeIf { it.isNotEmpty() }?.let { askLayer(Uri.parse(it)) }
        asked = true
    }

    /** the list being read: one asked for later replaces it, not to be overwritten by it */
    private var listing: Job? = null

    private fun rebuild() {
        if (view == null) return
        listing?.cancel()
        val tell = !toldUnread
        listing = viewLifecycleOwner.lifecycleScope.launch {
            // after a change the page this one replaced (a rotation) made: its list was built again on that page, gone
            changes.awaitMade()
            val listed = withContext(Dispatchers.IO) {
                val dictionaries = PinyinDictManager.listDictionaries()
                val intoNew = PinyinDictManager.intoNew()
                dictionaries.zip(wordCounts.of(dictionaries)) { dictionary, words ->
                    Listed(dictionary, dictionary.name in intoNew, words)
                }
            }
            // read after the list: listing it runs the migration when the engine has not yet
            val unread = if (tell) withContext(Dispatchers.IO) { PinyinDictManager.unread() } else emptyList()
            show(listed)
            if (tell) {
                toldUnread = true
                tellUnread(unread)
            }
        }
    }

    /** Tells of [unread], the dictionaries the migration put aside unread; its OK forgets them. */
    private fun tellUnread(unread: List<Unread>) {
        val message = unreadNotice(unread, getString(R.string.dict_unread_message), getString(R.string.dict_unread_import_again)) {
            val reason = when (it.reason) {
                Unread.Reason.TOO_LARGE -> R.string.dict_unread_too_large
                Unread.Reason.DAMAGED -> R.string.dict_unread_damaged
                Unread.Reason.NO_WORDS -> R.string.dict_unread_no_words
                Unread.Reason.UNKNOWN -> R.string.dict_unread_unknown
            }
            getString(R.string.dict_unread_line, it.fileName, getString(reason))
        } ?: return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dict_unread_title)
            .setMessage(message)
            // only OK forgets them: one sent away by Back would be told again
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // not forgotten, they are told again the next time the page shows
                lifecycleScope.launch(Dispatchers.IO) { PinyinDictManager.forgetUnread(unread) }
            }
            .show()
    }

    private fun show(listed: List<Listed>) {
        val ctx = context ?: return
        val screen = preferenceScreen.apply { removeAll() }
        val user = category(R.string.layer_user)
        user.addPreference(R.string.my_words, R.string.layer_user_summary) {
            navigateWithAnim(SettingsRoute.UserWords)
        }
        val new = category(R.string.layer_new)
        new.addPreference(builtIn(R.string.builtin_new_title, R.string.builtin_new_summary))
        listed.filter { it.intoNew }.forEach { new.addPreference(row(it)) }
        val base = category(R.string.layer_base)
        base.addPreference(builtIn(R.string.builtin_base_title, R.string.builtin_base_summary))
        listed.filter { !it.intoNew }.forEach { base.addPreference(row(it)) }
        val files = category(R.string.import_section)
        files.addPreference(R.string.import_dict, R.string.import_dict_summary) { launcher.launch("*/*") }
        // no network permission: the browser downloads a pack, importing it is as for any file
        files.addPreference(R.string.download_word_packs) { ctx.openUrl(Const.wordPacksUrl) }
        screen.addPreference(Preference(ctx).apply {
            setSummary(R.string.words_page_hint)
            isSelectable = false
            isIconSpaceReserved = false
        })
    }

    private fun category(title: Int) = PreferenceCategory(requireContext()).apply {
        setTitle(title)
        isIconSpaceReserved = false
        preferenceScreen.addPreference(this)
    }

    private fun builtIn(title: Int, summary: Int) = Preference(requireContext()).apply {
        setTitle(title)
        setSummary(summary)
        isSelectable = false
        isIconSpaceReserved = false
    }

    private fun row(item: Listed) = Preference(requireContext()).apply {
        val dictionary = item.dictionary
        title = dictionary.name
        val state = getString(if (dictionary.isEnabled) R.string.dict_on else R.string.dict_off)
        summary = getString(R.string.dict_summary, state, resources.getQuantityString(R.plurals.word_count, item.words, item.words))
        isIconSpaceReserved = false
        setOnPreferenceClickListener { act(item); true }
    }

    private fun act(item: Listed) {
        val dictionary = item.dictionary
        val actions = listOf(
            (if (dictionary.isEnabled) R.string.dict_turn_off else R.string.dict_turn_on) to {
                change {
                    runCatching { check(if (dictionary.isEnabled) dictionary.disable() else dictionary.enable()) { "cannot rename ${dictionary.file.name}" } }
                }
            },
            (if (item.intoNew) R.string.dict_move_to_base else R.string.dict_move_to_new) to {
                change { PinyinDictManager.setIntoNew(dictionary.name, !item.intoNew) }
            },
            R.string.delete to { confirmDelete(dictionary) },
        )
        AlertDialog.Builder(requireContext())
            .setTitle(dictionary.name)
            .setItems(actions.map { getString(it.first) }.toTypedArray()) { _, which -> actions[which].second() }
            .show()
    }

    /**
     * Makes [edit], a change to the dictionaries' files, off the main thread and after the one
     * before it; lists them again then, and tells of one that failed.
     */
    private fun change(edit: suspend () -> Result<Unit>) = changes.change(edit, ::rebuild)

    private fun confirmDelete(dictionary: TextDictionary) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.dict_delete_confirm, dictionary.name))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                change { runCatching { check(PinyinDictManager.delete(dictionary)) { "cannot delete ${dictionary.file.name}" } } }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Which layer a file is merged into: the base unless asked otherwise, the safest of the three;
     * for a pack replacing one, the layer that one is in.
     */
    private fun askLayer(uri: Uri) {
        val ctx = requireContext()
        lifecycleScope.withLoadingDialog(ctx) {
            try {
                val incoming = inspect(ctx, uri) ?: return@withLoadingDialog
                // a pack replacing one stays where that one is when the import is taken as offered
                val offered = withContext(Dispatchers.IO) {
                    if (offersNewLayer(ManagedDictionaries.actionFor(incoming), PinyinDictManager.isIntoNew(incoming.name))) Layer.NEW else Layer.BASE
                }
                showLayers(ctx, uri, incoming, offered)
            } catch (e: CancellationException) {
                // the page left: nothing to show the error on
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // a provider's error comes back through the binder as any kind (a SecurityException for
                // a file the picker gave that it will not open after all): shown, as import() does
                ctx.importErrorDialog(e)
            }
        }
    }

    /** The file at [uri] to import; null for one without a name, or not a dictionary, which is told. */
    private suspend fun inspect(ctx: Context, uri: Uri): Incoming? {
        val cr = ctx.contentResolver
        val fileName = withContext(Dispatchers.IO) { cr.queryFileName(uri) } ?: return null
        val type = PinyinDictionary.Type.fromFileName(withLowercaseExtension(fileName))
        if (type == null) {
            ctx.importErrorDialog(R.string.invalid_dict)
            return null
        }
        return withContext(Dispatchers.IO) {
            val text = type == PinyinDictionary.Type.Text || type == PinyinDictionary.Type.Words
            Incoming(fileName, text && cr.openInputStreamOrThrow(uri).use(PackImport::isPack))
        }
    }

    /** Asks which layer [incoming] goes into, [offered] picked to begin with, and imports it there. */
    private fun showLayers(ctx: Context, uri: Uri, incoming: Incoming, offered: Layer) {
        val layers = listOf(Layer.BASE, Layer.NEW, Layer.USER)
        val items = layers.map { layer ->
            SpannableStringBuilder()
                .inSpans(StyleSpan(Typeface.BOLD)) { append(getString(layer.title)) }
                .append("\n")
                .inSpans(RelativeSizeSpan(SMALL)) { append(getString(layer.hint)) }
                // a gap under each: three of two lines each read as one block without
                .inSpans(RelativeSizeSpan(GAP)) { append("\n") }
        }.toTypedArray<CharSequence>()
        var picked = layers.indexOf(offered)
        AlertDialog.Builder(ctx)
            .setTitle(R.string.import_into_title)
            .setSingleChoiceItems(items, picked) { _, which -> picked = which }
            .setPositiveButton(R.string.import_dict) { _, _ -> import(uri, incoming, layers[picked]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun import(uri: Uri, incoming: Incoming, layer: Layer) {
        val ctx = requireContext()
        val cr = ctx.contentResolver
        lifecycleScope.withLoadingDialog(ctx, R.string.importing) {
            try {
                if (layer == Layer.USER) {
                    importIntoUser(uri, incoming.fileName)
                    return@withLoadingDialog
                }
                val imported = withContext(Dispatchers.IO) {
                    ManagedDictionaries.importFile(incoming, layer == Layer.NEW) { cr.openInputStreamOrThrow(uri) }
                }
                if (!imported) {
                    ctx.importErrorDialog(R.string.dict_already_exists)
                    return@withLoadingDialog
                }
                viewModel.fcitx.runOnReady { reloadEngine() }
                rebuild()
            } catch (e: CancellationException) {
                // the page left: nothing to show the error on
                throw e
            } catch (e: Exception) {
                ctx.importErrorDialog(e)
            }
        }
    }

    private suspend fun importIntoUser(uri: Uri, fileName: String) {
        val ctx = requireContext()
        val fcitx = viewModel.fcitx
        // the lines go to the engine as they are read, not held all at once as a list first
        val imported = withContext(Dispatchers.IO) {
            PinyinDictManager.readWords(ctx.contentResolver.openInputStreamOrThrow(uri), fileName) { lines ->
                fcitx.runOnReady { importUserWords(lines) }
            }
        }
        AlertDialog.Builder(ctx)
            .setTitle(R.string.import_dict)
            .setMessage(getString(R.string.import_words_done, imported.added, imported.blocked, imported.unread))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onStop() {
        changes.left()?.let { reload ->
            val fcitx = viewModel.fcitx
            // the connection checked now, in onStop, while the page has it; read again once the last change is made
            fcitx.lifecycleScope.launch(Dispatchers.Main.immediate) { fcitx.runOnReady(reload) }
        }
        super.onStop()
    }

    companion object {
        private const val SMALL = 0.85f
        private const val GAP = 0.6f

        // the process's: a page made anew reads again only the dictionaries that changed
        private val wordCounts = WordCounts { PinyinDictManager.wordCount(it) }

        // the process's too: a page made anew (a rotation) makes its changes after those of the one it replaces
        private val changes = DictionaryChanges({ launchOutlivingPage("dictionaries", it) }) {
            Timber.w(it, "dictionaries")
            // the page may be gone by now
            appContext.toast(R.string.dict_change_failed)
        }
    }
}

/** A file to import: a word pack or not, told by its first line whatever the file is called, as the import tells one. */
internal class Incoming(val fileName: String, val pack: Boolean) {
    /** what it is kept as, and replaces one of if a pack */
    val name = if (pack) PackImport.name(fileName) else PinyinDictionary.nameOf(fileName)

    val keptAs get() = if (pack) TextDictionary.fileName(name, type = PinyinDictionary.Type.Words) else fileName
}

/** Where a file the user imports goes: the dictionaries [PinyinDictManager] keeps, or a test's. */
internal interface ImportTarget {
    /** The type of the dictionary of [name] there, if there is one. */
    fun typeOf(name: String): PinyinDictionary.Type?

    fun import(stream: InputStream, fileName: String, intoNew: Boolean)

    fun setIntoNew(name: String, intoNew: Boolean)

    fun replacePack(name: String, text: String)
}

/** What importing [incoming] does, with what is there of its name. */
internal fun ImportTarget.actionFor(incoming: Incoming) = PackImport.action(incoming.pack, typeOf(incoming.name))

/**
 * Imports [incoming], as [open] reads it, into the new words ([intoNew]) or the base; false, with
 * nothing read or changed, for one refused.
 */
internal fun ImportTarget.importFile(incoming: Incoming, intoNew: Boolean, open: () -> InputStream): Boolean {
    // a pack of the same name is its next one (each month's official pack is youmo-new.words): it replaces
    when (actionFor(incoming)) {
        PackImport.Action.REFUSE -> return false
        PackImport.Action.IMPORT -> open().use { import(it, incoming.keptAs, intoNew) }
        PackImport.Action.REPLACE -> {
            val text = open().bufferedReader().use { it.readText() }
            // the layer first: a pack that then fails to replace is the last one, as asked
            setIntoNew(incoming.name, intoNew)
            replacePack(incoming.name, text)
        }
    }
    return true
}

private object ManagedDictionaries : ImportTarget {
    override fun typeOf(name: String) = PinyinDictManager.listDictionaries().firstOrNull { it.name == name }?.type

    override fun import(stream: InputStream, fileName: String, intoNew: Boolean) {
        PinyinDictManager.importFromInputStream(stream, fileName, intoNew).getOrThrow()
    }

    override fun setIntoNew(name: String, intoNew: Boolean) {
        PinyinDictManager.setIntoNew(name, intoNew).getOrThrow()
    }

    override fun replacePack(name: String, text: String) {
        PinyinDictManager.importPack(name, text).getOrThrow()
    }
}

/**
 * How many words each dictionary has, as [count] reads them, read again only for one whose file
 * changed since: the list, built again after each change, read every dictionary whole each time.
 */
internal class WordCounts(private val count: (TextDictionary) -> Int) {
    private class Counted(val length: Long, val modified: Long, val words: Int)

    // by name and type: turning one off or on renames its file, its words as they were
    private val counted = HashMap<String, Counted>()

    /** The words of each of [dictionaries]; one no longer among them is forgotten, counted anew should it return. */
    @Synchronized
    fun of(dictionaries: List<TextDictionary>): List<Int> {
        counted.keys.retainAll(dictionaries.mapTo(HashSet()) { keyOf(it) })
        return dictionaries.map { dictionary ->
            val length = dictionary.file.length()
            val modified = dictionary.file.lastModified()
            counted[keyOf(dictionary)]?.takeIf { it.length == length && it.modified == modified }?.words
                ?: count(dictionary).also { counted[keyOf(dictionary)] = Counted(length, modified, it) }
        }
    }

    private fun keyOf(dictionary: TextDictionary) = TextDictionary.fileName(dictionary.name, type = dictionary.type)
}

/**
 * The page's changes to the dictionaries' files, each made after the one before it, [start]ed on
 * a scope that outlives the page: on the page's own, one still waiting as the page was left was
 * cancelled, and the engine read the dictionaries again without it. Each is made on [io];
 * [onFailure] is told of one that failed. Used on the main thread.
 */
internal class DictionaryChanges(
    private val start: (suspend () -> Unit) -> Job,
    private val io: CoroutineContext = Dispatchers.IO,
    private val onFailure: (Throwable) -> Unit,
) {
    // a dictionary turned on or off, moved or deleted: the engine reads them again on leaving
    private var changed = false

    // the last: the next waits for it, and so do the engine reading them again and a page listing them
    private var last: Job? = null

    /** Makes [edit] once the change before it is made, then [then] (the list built again). */
    fun change(edit: suspend () -> Result<Unit>, then: () -> Unit) {
        // now: the page may be left before the change is made, and the engine waits for it then
        changed = true
        val before = last
        last = start {
            before?.join()
            withContext(io) { edit() }.onFailure(onFailure)
            then()
        }
    }

    /** Returns once the changes started so far are made: what a page lists then has them all. */
    suspend fun awaitMade() {
        last?.join()
    }

    /**
     * As the page is left: what reads the dictionaries again once the last change is made, if
     * one was made since the page was last left; null if none was.
     */
    fun left(): (suspend FcitxAPI.() -> Unit)? {
        if (!changed) return null
        changed = false
        val made = last
        val reload: suspend FcitxAPI.() -> Unit = {
            made?.join()
            reloadEngine()
        }
        return reload
    }
}

/**
 * Whether importing a file that does [action] offers the new words rather than the base: a pack
 * replacing one merged into the new words ([intoNew]) stays there (each month's youmo-new.words).
 */
internal fun offersNewLayer(action: PackImport.Action, intoNew: Boolean) = action == PackImport.Action.REPLACE && intoNew

/**
 * The notice of [unread], the dictionaries the migration put aside unread: [intro], a line each
 * as [line] words it, and [outro]; none, and no dialog, without one.
 */
internal fun unreadNotice(unread: List<Unread>, intro: String, outro: String, line: (Unread) -> String): String? =
    if (unread.isEmpty()) null else (listOf(intro) + unread.map(line) + outro).joinToString("\n")
