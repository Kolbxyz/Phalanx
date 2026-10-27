package org.phalanx.android.core.matrix

import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.data.model.Bridge
import org.phalanx.android.data.model.CustomEmote
import org.phalanx.android.data.model.MediaRef
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.di.ApplicationScope

/** Where a room's custom emoji come from: [key] names its network for [SeenEmoteStore]. */
internal class EmoteScope(val key: String, val roomId: String, val bridged: Boolean)

/** One open room's emoji, worked out once and again only after new ones were seen in it. */
internal class SessionEmotes(private val source: RoomEmotes, private val scope: EmoteScope) {
    @Volatile private var cached: List<CustomEmote>? = null

    suspend fun all(): List<CustomEmote> = cached ?: source.of(scope).also { cached = it }

    /** Shortcode to `mxc://` URL, what [org.phalanx.android.core.format.OutgoingMessage] sends them with. */
    suspend fun byShortcode(): Map<String, String> = all().associate { it.shortcode to it.url }

    // Each update brings the whole timeline: only emoji not looked at yet are worth the work.
    private val looked = HashSet<Pair<String, MediaRef>>()

    /** Called with each update of the timeline, one at a time. */
    suspend fun learn(entries: List<TimelineEntry>) {
        val fresh = source.markedIn(entries, ownOnly = scope.bridged).filter(looked::add)
        if (fresh.isNotEmpty() && source.learn(scope, fresh)) cached = null
    }
}

/**
 * The custom emoji that make sense in a room. A bridged room gets those the user sent through its
 * network, since only those turn back into the network's own emoji (Discord's in a Discord room,
 * and so on) and are surely theirs to use. A Matrix room gets the image packs, then those seen in
 * other Matrix rooms, which work for everyone. This room's come first.
 */
@Singleton
internal class RoomEmotes @Inject constructor(
    private val active: ActiveClient,
    private val api: ClientServerApi,
    private val seen: SeenEmoteStore,
    @ApplicationScope scope: CoroutineScope
) {
    // Packs change rarely; read once per room while signed in, and again after a failed read.
    private val roomPacks = ConcurrentHashMap<String, List<Pair<String, String>>>()
    @Volatile private var accountPacks: List<Pair<String, String>>? = null

    init {
        scope.launch {
            active.syncService.collect {
                roomPacks.clear()
                accountPacks = null
            }
        }
    }

    /** [bridge] is the network the room is bridged to, null for a Matrix room. */
    fun forRoom(ownUserId: String, roomId: String, bridge: Bridge?): SessionEmotes =
        SessionEmotes(this, EmoteScope("$ownUserId|${bridge?.name ?: MATRIX}", roomId, bridged = bridge != null))

    suspend fun of(scope: EmoteScope): List<CustomEmote> {
        val packs = if (scope.bridged) emptyList() else roomPack(scope.roomId) + accountPacks()
        val (here, elsewhere) = seen.of(scope.key).partition { it.roomId == scope.roomId }
        return (packs + (here + elsewhere).map { it.shortcode to it.url })
            .distinctBy { it.first }
            .mapNotNull { (shortcode, url) -> mediaRefOf(url)?.let { CustomEmote(shortcode, url, it) } }
    }

    /**
     * The emoji [entries] show, as shortcode and image. With [ownOnly], only the user's own: a
     * network says nothing of where an emoji comes from, so one someone else sent may be from a
     * server the user is not in, while one they sent is one the network let them use.
     */
    fun markedIn(entries: List<TimelineEntry>, ownOnly: Boolean): List<Pair<String, MediaRef>> = entries
        .filterIsInstance<TimelineEntry.Message>()
        .filter { it.isOwn || !ownOnly }
        .flatMap { message -> message.body.text()?.let(::emotesIn).orEmpty() }
        .distinct()

    /** Remembers [found] for [scope]; true when any was new. */
    suspend fun learn(scope: EmoteScope, found: List<Pair<String, MediaRef>>): Boolean {
        val urls = found.mapNotNull { (shortcode, ref) ->
            val url = try {
                ref.toSource().use { it.url() }
            } catch (_: ClientException) {
                return@mapNotNull null
            }
            (shortcode to url).takeIf { url.startsWith("mxc://") }
        }
        // Remembering them is a convenience: a failed write only leaves them out of the suggestions.
        return urls.isNotEmpty() && seen.add(scope.key, scope.roomId, urls).getOrDefault(false)
    }

    private suspend fun roomPack(roomId: String): List<Pair<String, String>> =
        roomPacks[roomId] ?: statePack(roomId, "")?.also { roomPacks[roomId] = it }.orEmpty()

    private suspend fun accountPacks(): List<Pair<String, String>> = accountPacks ?: readAccountPacks()

    /** The user's own pack and the packs they turned on everywhere; kept once all could be read. */
    private suspend fun readAccountPacks(): List<Pair<String, String>> {
        val client = active.client() ?: return emptyList()
        return try {
            val own = client.accountData(ImagePacks.USER_PACK)?.let(ImagePacks::emotesOf).orEmpty()
            val global = client.accountData(ImagePacks.GLOBAL_PACKS)?.let(ImagePacks::globalPacksOf).orEmpty()
            val shared = global.flatMap { (roomId, keys) -> keys.map { key -> statePack(roomId, key) } }
            (own + shared.filterNotNull().flatten()).also { if (null !in shared) accountPacks = it }
        } catch (_: ClientException) {
            emptyList()
        }
    }

    /** The pack in [roomId]'s state under [key]: empty when there is none, null when it couldn't be read. */
    private suspend fun statePack(roomId: String, key: String): List<Pair<String, String>>? = try {
        val path = "rooms/${encode(roomId)}/state/${ImagePacks.ROOM_PACK}/${encode(key)}"
        val response = api.call("GET", path)
        when {
            response.isSuccess -> ImagePacks.emotesOf(response.body)
            response.code == NOT_FOUND -> emptyList()
            else -> null
        }
    } catch (_: IOException) {
        null
    }

    private fun encode(segment: String) = URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")

    private fun MessageBody.text(): MessageBody.Text? = when (this) {
        is MessageBody.Text -> this
        is MessageBody.Image -> caption
        is MessageBody.Video -> caption
        is MessageBody.File -> caption
        else -> null
    }

    /** The emoji [text] shows, read back from the marks [HtmlSanitizer] left. */
    private fun emotesIn(text: MessageBody.Text): List<Pair<String, MediaRef>> {
        val html = text.html?.takeIf { text.emotes.isNotEmpty() } ?: return emptyList()
        return MARKED.findAll(html).mapNotNull { match ->
            val shortcode = match.groupValues[2].takeIf { ImagePacks.SHORTCODE.matches(it) } ?: return@mapNotNull null
            val ref = match.groupValues[1].toIntOrNull()?.let(text.emotes::getOrNull) ?: return@mapNotNull null
            shortcode to ref
        }.toList()
    }

    private companion object {
        const val MATRIX = "Matrix"
        const val NOT_FOUND = 404
        val MARKED = Regex("<annotation emote=\"(\\d+)\">:([^<:]+):</annotation>")
    }
}
