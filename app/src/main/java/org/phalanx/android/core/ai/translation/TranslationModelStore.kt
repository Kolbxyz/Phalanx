package org.phalanx.android.core.ai.translation

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.phalanx.android.core.ai.ModelRejectedException
import org.phalanx.android.core.ai.copyHashed

/**
 * The translation model files the user imported, one of [KNOWN_PAIR_FILES] each, kept in the app's
 * private storage under their published names. Which file it is comes from its hash alone, so the
 * user picks a pair's files in any order. Nothing is downloaded.
 */
@Singleton
class TranslationModelStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir = File(context.filesDir, DIR_NAME)
    private val mutablePairs = MutableStateFlow(read())

    /** The pairs whose files are all here. */
    val pairs: StateFlow<List<LanguagePair>> = mutablePairs.asStateFlow()

    /** Copies [uri] in; [onProgress] gets the fraction done. The result is the file's published name. */
    suspend fun import(uri: Uri, onProgress: (Float) -> Unit): Result<String> = withContext(Dispatchers.IO) {
        val partial = File(dir, "$DIR_NAME.part")
        try {
            dir.mkdirs()
            val limit = KNOWN_PAIR_FILES.maxOfOrNull { it.maxBytes } ?: 0L
            val copy = copyHashed(context, uri, partial, limit, coroutineContext.job, onProgress)
            val known = KNOWN_PAIR_FILES.firstOrNull { copy.size <= it.maxBytes && it.sha256 == copy.sha256 }
                ?: throw ModelRejectedException()
            if (!partial.renameTo(File(dir, known.name))) throw IOException("Could not keep the file")
            mutablePairs.value = read()
            Result.success(known.name)
        } catch (e: ModelRejectedException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        } finally {
            partial.delete()
        }
    }

    /** Deletes [pair]'s files, keeping a vocabulary another installed pair shares. */
    suspend fun remove(pair: LanguagePair) = withContext(Dispatchers.IO) {
        val kept = KNOWN_PAIR_FILES.filter { it.pair != pair && it.pair in mutablePairs.value }.map { it.name }.toSet()
        KNOWN_PAIR_FILES.filter { it.pair == pair && it.name !in kept }.forEach { File(dir, it.name).delete() }
        mutablePairs.value = read()
    }

    internal fun filesOf(pair: LanguagePair): PairFiles? = pairFilesOf(
        KNOWN_PAIR_FILES.filter { it.pair == pair && File(dir, it.name).isFile }
            .associate { it.role to File(dir, it.name).path }
    )

    // Only this class writes the files, after checking their hashes.
    private fun read(): List<LanguagePair> = KNOWN_PAIR_FILES.map { it.pair }.distinct().filter { filesOf(it) != null }

    private companion object {
        const val DIR_NAME = "mt"
    }
}
