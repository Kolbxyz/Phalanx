package org.phalanx.android.core.matrix

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.MediaSource
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.core.format.OpenGraph
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.core.preview.PageFetcher
import org.phalanx.android.data.model.LinkPreview
import org.phalanx.android.data.model.LinkPreviewSource
import org.phalanx.android.data.repository.LinkPreviewRepository

/**
 * Link previews, built on this phone from the page itself or asked of the homeserver, as the user
 * chose. Kept in memory for the session, so scrolling back over a link fetches nothing again.
 */
@Singleton
internal class RustLinkPreviewRepository @Inject constructor(
    private val active: ActiveClient,
    private val settings: SettingsStore,
    private val pages: PageFetcher
) : LinkPreviewRepository {
    private val lock = Mutex()

    // By source, image choice and URL; a page without a preview is kept too, as null. Under [lock].
    private val cache = object : LinkedHashMap<String, LinkPreview?>(CACHE_SIZE, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LinkPreview?>?) = size > CACHE_SIZE
    }

    override suspend fun preview(url: String, withImage: Boolean): Result<LinkPreview?> {
        val source = settings.current().linkPreviews
        val key = "$source $withImage $url"
        lock.withLock { if (cache.containsKey(key)) return Result.success(cache[key]) }
        val result = withContext(Dispatchers.IO) {
            when (source) {
                LinkPreviewSource.Off -> Result.success(null)
                LinkPreviewSource.Phone -> fromPhone(url, withImage)
                LinkPreviewSource.Homeserver -> fromHomeserver(url, withImage)
            }
        }
        result.onSuccess { preview -> lock.withLock { cache[key] = preview } }
        return result
    }

    private fun fromPhone(url: String, withImage: Boolean): Result<LinkPreview?> = try {
        val graph = OpenGraph.fromHtml(pages.page(url), url)
        Result.success(graph?.toPreview(url, graph.image?.takeIf { withImage }?.let(::phoneImage)))
    } catch (e: IOException) {
        Result.failure(e)
    }

    // A picture that fails to load leaves the card without one; the page's words still stand.
    private fun phoneImage(url: String): ByteArray? = try {
        pages.image(url)
    } catch (_: IOException) {
        null
    }

    private suspend fun fromHomeserver(url: String, withImage: Boolean): Result<LinkPreview?> {
        val client = active.client() ?: return Result.failure(IOException("Signed out"))
        return try {
            val graph = client.getUrlPreview(url, null)?.let(OpenGraph::fromJson)
            Result.success(graph?.toPreview(url, graph.image?.takeIf { withImage }?.let { client.thumbnail(it) }))
        } catch (e: ClientException) {
            Result.failure(e)
        }
    }

    private suspend fun Client.thumbnail(mxc: String): ByteArray? = try {
        MediaSource.fromUrl(mxc).use { getMediaThumbnail(it, IMAGE_PX.toULong(), IMAGE_PX.toULong()) }
    } catch (_: ClientException) {
        null
    }

    private fun OpenGraph.toPreview(url: String, image: ByteArray?) =
        LinkPreview(url, title, description, siteName, image)

    private companion object {
        const val CACHE_SIZE = 64
        const val LOAD_FACTOR = 0.75f
        const val IMAGE_PX = 640
    }
}
