package org.phalanx.android.core.ai.translation

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.phalanx.android.core.ai.HashedCopy
import org.phalanx.android.core.ai.ModelRejectedException
import org.phalanx.android.core.ai.ModelTransfer
import org.phalanx.android.core.ai.TransferRunner
import org.phalanx.android.core.ai.copyHashed
import org.phalanx.android.core.ai.downloadHashed
import org.phalanx.android.di.ApplicationScope

/**
 * The translation model files the user downloaded or imported, one of [KNOWN_PAIR_FILES] each, kept
 * in the app's private storage under their hash: the two directions of a pair may share a file,
 * which is then kept once and counts for both. Which file it is comes from its hash alone, so the
 * user picks a pair's files in any order.
 */
@Singleton
class TranslationModelStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope
) {
    private val dir = File(context.filesDir, DIR_NAME)
    private val partial = File(dir, "$DIR_NAME.part")
    private val runner = TransferRunner(scope)
    private val mutablePairs = MutableStateFlow(read())

    /** The pairs whose files are all here. */
    val pairs: StateFlow<List<LanguagePair>> = mutablePairs.asStateFlow()

    /** The download or import under way, or how the last one ended. */
    val transfer: StateFlow<ModelTransfer> = runner.state

    /** Every pair this build can translate with, installed or not. */
    val available: List<LanguagePair> = KNOWN_PAIR_FILES.map { it.pair }.distinct()

    /** Downloads the files of [pair] it does not have yet, one after the other. */
    fun download(pair: LanguagePair) = runner.start { onProgress ->
        val missing = KNOWN_PAIR_FILES.filter { it.pair == pair }.distinctBy { it.sha256 }
            .filter { !fileOf(it).isFile }
        missing.forEachIndexed { index, file ->
            keep(listOf(file)) { job ->
                downloadHashed(file.url, partial, file.maxBytes, job) { onProgress((index + it) / missing.size) }
            }
        }
    }

    /** Copies [uris] in one after the other, a pair's files in any order; stops at the first refused. */
    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        runner.start { onProgress ->
            val limit = KNOWN_PAIR_FILES.maxOf { it.maxBytes }
            uris.forEachIndexed { index, uri ->
                keep(KNOWN_PAIR_FILES) { job ->
                    copyHashed(context, uri, partial, limit, job) { onProgress((index + it) / uris.size) }
                }
            }
        }
    }

    fun cancel() = runner.cancel()

    /** How much [pair] downloads, its shared files counted once. */
    fun sizeOf(pair: LanguagePair): Long =
        KNOWN_PAIR_FILES.filter { it.pair == pair }.distinctBy { it.sha256 }.sumOf { it.maxBytes }

    /** Deletes [pair]'s files, keeping any that another installed pair shares. */
    suspend fun remove(pair: LanguagePair) = withContext(Dispatchers.IO) {
        val kept = KNOWN_PAIR_FILES.filter { it.pair != pair && it.pair in mutablePairs.value }
            .map { it.sha256 }.toSet()
        KNOWN_PAIR_FILES.filter { it.pair == pair && it.sha256 !in kept }.forEach { fileOf(it).delete() }
        mutablePairs.value = read()
    }

    internal fun filesOf(pair: LanguagePair): PairFiles? = pairFilesOf(
        KNOWN_PAIR_FILES.filter { it.pair == pair && fileOf(it).isFile }.associate { it.role to fileOf(it).path }
    )

    // Keeps the copy only if it is one of [accepted]; any other, or a partial one, is deleted.
    private suspend fun keep(accepted: List<KnownPairFile>, copy: (Job) -> HashedCopy) =
        withContext(Dispatchers.IO) {
            try {
                dir.mkdirs()
                val copied = copy(coroutineContext.job)
                val known = accepted.firstOrNull { copied.size <= it.maxBytes && it.sha256 == copied.sha256 }
                    ?: throw ModelRejectedException()
                if (!partial.renameTo(fileOf(known))) throw IOException("Could not keep the file")
                mutablePairs.value = read()
            } finally {
                partial.delete()
            }
        }

    private fun fileOf(known: KnownPairFile) = File(dir, known.sha256)

    // Only this class writes the files, after checking their hashes.
    private fun read(): List<LanguagePair> = KNOWN_PAIR_FILES.map { it.pair }.distinct().filter { filesOf(it) != null }

    private companion object {
        const val DIR_NAME = "mt"
    }
}
