package org.phalanx.android.core.format

import android.content.Context
import android.os.Build
import android.util.LruCache
import android.view.textclassifier.TextClassification
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextLinks
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finds dates, phone numbers, addresses and the like in messages with Android's own text
 * classifier, the one behind smart text selection. Its small models ship with the system and run
 * on the phone: nothing is downloaded and nothing leaves the device.
 *
 * Finding is the light step and runs for each message shown, once: results are kept by text. What
 * to do with one ([actions]) is the heavier step, asked only when the user taps it.
 */
@Singleton
class TextEntities @Inject internal constructor(@ApplicationContext private val context: Context) {
    private val found = LruCache<String, List<EntitySpan>>(CACHE_SIZE)

    /** The result [spans] already has for [text], without asking the classifier; null if none yet. */
    fun cached(text: String): List<EntitySpan>? = found.get(text)

    /** Empty before Android 9, which has no entity detection to ask, or when nothing was found. */
    suspend fun spans(text: String): List<EntitySpan> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || text.isBlank()) return emptyList()
        // The classifier is a call into a system service: never on the main thread.
        return found.get(text) ?: withContext(Dispatchers.IO) { detect(text) }.also { found.put(text, it) }
    }

    /** The classifier's actions for [span] of [text], best first. */
    suspend fun actions(text: String, span: EntitySpan): List<EntityAction> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptyList()
        return withContext(Dispatchers.IO) { classify(text, span) }
    }

    /** Every entity in [text] that has something to do, for a message's menu. */
    suspend fun find(text: String): List<FoundEntity> = spans(text)
        .mapNotNull { span ->
            val actions = actions(text, span)
            if (actions.isEmpty()) null else FoundEntity(text.substring(span.start, span.end), span.kind, actions)
        }
        .distinctBy { it.text }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun detect(text: String): List<EntitySpan> {
        val classifier = classifier() ?: return emptyList()
        val clipped = text.take(classifier.maxGenerateLinksTextLength)
        val request = TextLinks.Request.Builder(clipped)
            .setEntityConfig(TextClassifier.EntityConfig.createWithExplicitEntityList(KINDS.keys.toList()))
            .build()
        return classifier.generateLinks(request).links.mapNotNull { link ->
            val kind = (0 until link.entityCount).firstNotNullOfOrNull { KINDS[link.getEntity(it)] }
            kind?.let { EntitySpan(link.start, link.end, it) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun classify(text: String, span: EntitySpan): List<EntityAction> {
        val classifier = classifier() ?: return emptyList()
        val request = TextClassification.Request.Builder(text, span.start, span.end).build()
        return classifier.classifyText(request).actions
            .filter { it.isEnabled }
            .take(MAX_ACTIONS)
            .map { EntityAction(it.title.toString(), it.actionIntent) }
    }

    private fun classifier(): TextClassifier? = context.getSystemService(TextClassificationManager::class.java)
        .textClassifier
        .takeIf { it != TextClassifier.NO_OP }

    private companion object {
        // Links are left out: Phalanx shows them already, with tracker stripping and previews.
        val KINDS = mapOf(
            TextClassifier.TYPE_DATE to EntityKind.Date,
            TextClassifier.TYPE_DATE_TIME to EntityKind.Date,
            TextClassifier.TYPE_PHONE to EntityKind.Phone,
            TextClassifier.TYPE_EMAIL to EntityKind.Email,
            TextClassifier.TYPE_ADDRESS to EntityKind.Address,
            TextClassifier.TYPE_FLIGHT_NUMBER to EntityKind.Flight
        )

        // A few screens of history: scrolling back never asks the classifier twice.
        const val CACHE_SIZE = 512

        // The first is the classifier's main suggestion; more than three crowds the sheet.
        const val MAX_ACTIONS = 3
    }
}
