package org.phalanx.android.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.phalanx.android.data.model.AppSettings
import org.phalanx.android.data.model.TextSize
import org.phalanx.android.data.model.ThemeMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App preferences in a DataStore file. Nothing here is secret, so it is not encrypted; it is still
 * excluded from backup and device transfer with the rest of the app's data.
 */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val store: DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(FILE_NAME) }

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    /** The settings as they are now, for one-off reads outside the UI. */
    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[THEME_MODE] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[DYNAMIC_COLOR] = enabled }

    suspend fun setAmoledBlack(enabled: Boolean) = store.edit { it[AMOLED_BLACK] = enabled }

    suspend fun setReduceMotion(enabled: Boolean) = store.edit { it[REDUCE_MOTION] = enabled }

    suspend fun setMessageTextSize(size: TextSize) = store.edit { it[TEXT_SIZE] = size.name }

    suspend fun setEnterToSend(enabled: Boolean) = store.edit { it[ENTER_TO_SEND] = enabled }

    suspend fun setMarkdown(enabled: Boolean) = store.edit { it[MARKDOWN] = enabled }

    suspend fun setFullQualityImages(enabled: Boolean) = store.edit { it[FULL_QUALITY_IMAGES] = enabled }

    suspend fun setAutoLoadMedia(enabled: Boolean) = store.edit { it[AUTO_LOAD_MEDIA] = enabled }

    suspend fun setPublicReadReceipts(enabled: Boolean) = store.edit { it[PUBLIC_READ_RECEIPTS] = enabled }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            // An unknown name (from a newer build, say) falls back instead of crashing.
            themeMode = ThemeMode.entries.firstOrNull { it.name == this[THEME_MODE] } ?: defaults.themeMode,
            dynamicColor = this[DYNAMIC_COLOR] ?: defaults.dynamicColor,
            amoledBlack = this[AMOLED_BLACK] ?: defaults.amoledBlack,
            reduceMotion = this[REDUCE_MOTION] ?: defaults.reduceMotion,
            messageTextSize = TextSize.entries.firstOrNull { it.name == this[TEXT_SIZE] } ?: defaults.messageTextSize,
            enterToSend = this[ENTER_TO_SEND] ?: defaults.enterToSend,
            markdown = this[MARKDOWN] ?: defaults.markdown,
            fullQualityImages = this[FULL_QUALITY_IMAGES] ?: defaults.fullQualityImages,
            autoLoadMedia = this[AUTO_LOAD_MEDIA] ?: defaults.autoLoadMedia,
            publicReadReceipts = this[PUBLIC_READ_RECEIPTS] ?: defaults.publicReadReceipts
        )
    }

    private companion object {
        const val FILE_NAME = "settings"
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val PUBLIC_READ_RECEIPTS = booleanPreferencesKey("public_read_receipts")
        val TEXT_SIZE = stringPreferencesKey("message_text_size")
        val ENTER_TO_SEND = booleanPreferencesKey("enter_to_send")
        val MARKDOWN = booleanPreferencesKey("markdown")
        val FULL_QUALITY_IMAGES = booleanPreferencesKey("full_quality_images")
        val AUTO_LOAD_MEDIA = booleanPreferencesKey("auto_load_media")
    }
}
