package org.phalanx.android.core.ai.translation

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow
import org.phalanx.android.core.ai.LocalSummarizer
import org.phalanx.android.core.ai.MAX_TRANSLATED_CHARS

/**
 * Translates a message into [Locale]'s language: with Bergamot when exactly one installed pair
 * translates into it, near instant; otherwise with the language model, which reads any language.
 * Bergamot cannot tell which language a message is in, and the phone's own detector is a system
 * service (Google's on Pixel phones), so a second pair into the same language leaves the choice
 * to the language model.
 */
@Singleton
class Translator @Inject internal constructor(
    private val store: TranslationModelStore,
    private val bergamot: BergamotTranslator,
    private val model: LocalSummarizer
) {
    /** The pairs installed, so translation can be offered without the language model. */
    val pairs: StateFlow<List<LanguagePair>> = store.pairs

    suspend fun translate(text: String, into: Locale, onText: (String) -> Unit): Result<String> {
        val pair = store.pairs.value.filter { it.to == into.language }.singleOrNull()
        return if (pair != null && BergamotNative.isAvailable) {
            bergamot.translate(pair, text.take(MAX_TRANSLATED_CHARS)).onSuccess(onText)
        } else {
            // The model is asked for a language by its English name, which it knows best.
            model.translate(text, into.getDisplayLanguage(Locale.ENGLISH), onText)
        }
    }

    /**
     * [text] translated with [pair] alone, for auto-translate: null when it does not read as the
     * pair's source language or the pair is not installed, so nothing slower is ever started.
     */
    suspend fun translateWith(pair: LanguagePair, text: String): Result<String>? {
        val usable = BergamotNative.isAvailable && pair in store.pairs.value && readsAs(text, pair.from, pair.to)
        return if (usable) bergamot.translate(pair, text.take(MAX_TRANSLATED_CHARS)) else null
    }
}
