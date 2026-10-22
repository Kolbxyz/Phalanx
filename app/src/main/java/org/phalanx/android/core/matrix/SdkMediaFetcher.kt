package org.phalanx.android.core.matrix

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.IOException
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.core.media.firstFrame
import org.phalanx.android.data.model.MediaRequest

/**
 * Loads media through the SDK, which decrypts encrypted-room media and keeps its own encrypted
 * on-disk cache. That is why the image loader has no disk cache of its own: it would store
 * decrypted copies in the clear.
 */
internal class SdkMediaFetcher(
    private val request: MediaRequest,
    private val options: Options,
    private val active: ActiveClient
) : Fetcher {
    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val client = active.client() ?: throw IOException("Signed out")
        val bytes = try {
            request.ref.toSource().use { source ->
                val size = request.thumbnailPx?.toULong()
                if (size != null && !request.videoFrame) {
                    client.getMediaThumbnail(source, size, size)
                } else {
                    client.getMediaContent(source)
                }
            }
        } catch (e: ClientException) {
            // Coil reports IOExceptions as a failed request, which shows the error state.
            throw IOException("Media unavailable", e)
        }
        if (request.videoFrame) {
            return@withContext ImageFetchResult(
                image = firstFrame(bytes, request.thumbnailPx).asImage(),
                isSampled = request.thumbnailPx != null,
                dataSource = DataSource.NETWORK
            )
        }
        SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), options.fileSystem),
            mimeType = null,
            dataSource = DataSource.NETWORK
        )
    }

    class Factory(private val active: ActiveClient) : Fetcher.Factory<MediaRequest> {
        override fun create(data: MediaRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            SdkMediaFetcher(data, options, active)
    }
}

/** Lets the memory cache recognise the same media at the same size. */
internal object MediaRequestKeyer : Keyer<MediaRequest> {
    override fun key(data: MediaRequest, options: Options): String {
        val frame = if (data.videoFrame) "#frame" else ""
        return "${data.ref.serialized}@${data.thumbnailPx}$frame"
    }
}
