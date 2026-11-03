package org.phalanx.android.core.format

import android.content.Context
import android.os.Build
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
 * Finds dates, phone numbers, addresses and the like in a message with Android's own text
 * classifier, the one that suggests "Call" when text is selected. Its small models ship with the
 * system and run on the phone: nothing is downloaded and nothing leaves the device. Asked only when
 * the user opens a message's menu, never for every message shown.
 */
@Singleton
class TextEntities @Inject internal constructor(@ApplicationContext private val context: Context) {
    /** Empty before Android 9, which has no entity detection to ask, or when nothing was found. */
    suspend fun find(text: String): List<FoundEntity> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || text.isBlank()) return emptyList()
        // The classifier is a call into a system service: never on the main thread.
        return withContext(Dispatchers.IO) { classify(text) }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun classify(text: String): List<FoundEntity> {
        val classifier = context.getSystemService(TextClassificationManager::class.java).textClassifier
        if (classifier == TextClassifier.NO_OP) return emptyList()
        val clipped = text.take(classifier.maxGenerateLinksTextLength)
        val request = TextLinks.Request.Builder(clipped)
            .setEntityConfig(TextClassifier.EntityConfig.createWithExplicitEntityList(KINDS.keys.toList()))
            .build()
        return classifier.generateLinks(request).links
            .mapNotNull { link -> found(classifier, clipped, link) }
            .distinctBy { it.text }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun found(classifier: TextClassifier, text: String, link: TextLinks.TextLink): FoundEntity? {
        val kind = (0 until link.entityCount).firstNotNullOfOrNull { KINDS[link.getEntity(it)] } ?: return null
        val classification = classifier.classifyText(
            TextClassification.Request.Builder(text, link.start, link.end).build()
        )
        val actions = classification.actions
            .filter { it.isEnabled }
            .take(MAX_ACTIONS)
            .map { EntityAction(it.title.toString(), it.actionIntent) }
        return if (actions.isEmpty()) null else FoundEntity(text.substring(link.start, link.end), kind, actions)
    }

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

        // The first is the classifier's main suggestion; more than two crowds the menu.
        const val MAX_ACTIONS = 2
    }
}
