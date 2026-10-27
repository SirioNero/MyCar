package io.github.sirionero.mycar.xonos.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/** Il [Menu] come lo vuole Media3: ogni voce diventa un MediaItem, con titolo, tipo e copertina. */
class BrowseTree(
    private val menu: Menu,
    private val artwork: Artwork,
) {

    fun root(): MediaItem = mediaItem(menu.root())

    fun item(mediaId: String): MediaItem? = menu.node(mediaId)?.let(::mediaItem)

    /** Riempie titolo e copertina di un elemento chiesto dall'auto, se lo si conosce. */
    fun resolve(item: MediaItem): MediaItem {
        val node = menu.node(item.mediaId) ?: return item
        return item.buildUpon().setMediaMetadata(mediaItem(node).mediaMetadata).build()
    }

    suspend fun children(parentId: String): List<MediaItem> = menu.children(parentId).map(::mediaItem)

    suspend fun search(query: String): List<MediaItem> = menu.search(query).map(::mediaItem)

    /** Suona ciò che l'auto ha scelto: un elemento del menu, una ricerca a voce o la ripresa. */
    suspend fun play(item: MediaItem) = menu.play(item.mediaId, item.requestMetadata.searchQuery)

    private fun mediaItem(node: MenuNode): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(node.title)
            .setDisplayTitle(node.title)
            .setSubtitle(node.subtitle)
            .setArtworkUri(artwork.uriFor(node.image, LIST_ART) ?: artwork.iconUri(node.icon))
            .setIsBrowsable(node.browsable)
            .setIsPlayable(node.playable)
            .setMediaType(mediaTypeOf(node.type))
            .build()
        return MediaItem.Builder().setMediaId(node.id).setMediaMetadata(metadata).build()
    }

    private companion object {
        const val LIST_ART = 256

        fun mediaTypeOf(type: String) = when (type) {
            "track" -> MediaMetadata.MEDIA_TYPE_MUSIC
            "album" -> MediaMetadata.MEDIA_TYPE_ALBUM
            "artist" -> MediaMetadata.MEDIA_TYPE_ARTIST
            "playlist" -> MediaMetadata.MEDIA_TYPE_PLAYLIST
            "radio" -> MediaMetadata.MEDIA_TYPE_RADIO_STATION
            "podcast" -> MediaMetadata.MEDIA_TYPE_PODCAST
            "podcast_episode" -> MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE
            "audiobook" -> MediaMetadata.MEDIA_TYPE_AUDIO_BOOK
            "genre" -> MediaMetadata.MEDIA_TYPE_GENRE
            "folder" -> MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
            else -> MediaMetadata.MEDIA_TYPE_MIXED
        }
    }
}
