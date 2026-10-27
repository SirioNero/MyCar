package io.github.sirionero.mycar.xonos.playback

import android.content.Context
import android.content.res.Resources
import io.github.sirionero.mycar.R

/** Le scritte del menu dalle risorse dell'app: in inglese, come tutto lo schermo di myCar in auto. */
class ResourceLabels(context: Context) : MenuLabels {

    private val resources: Resources = context.resources

    override fun category(id: String) = resources.getString(CATEGORIES.getValue(id))

    override fun kind(type: String) = resources.getString(KINDS.getValue(type))

    override fun results(count: Int) = resources.getQuantityString(R.plurals.xonos_results, count, count)

    override fun count(type: String, count: Int) = resources.getQuantityString(COUNTS.getValue(type), count, count)

    override val topTracks: String get() = resources.getString(R.string.xonos_top_tracks)

    override val allTracks: String get() = resources.getString(R.string.xonos_all_tracks)

    private companion object {
        val CATEGORIES = mapOf(
            "recent" to R.string.xonos_cat_recent,
            "foryou" to R.string.xonos_cat_foryou,
            "playlists" to R.string.xonos_cat_playlists,
            "albums" to R.string.xonos_cat_albums,
            "artists" to R.string.xonos_cat_artists,
            "tracks" to R.string.xonos_cat_tracks,
            "favorites" to R.string.xonos_cat_favorites,
            "genres" to R.string.xonos_cat_genres,
            "radios" to R.string.xonos_cat_radios,
            "podcasts" to R.string.xonos_cat_podcasts,
            "audiobooks" to R.string.xonos_cat_audiobooks,
            "browse" to R.string.xonos_cat_browse,
        )

        /** I tipi della ricerca si chiamano come le voci del menu. */
        val KINDS = mapOf(
            "artists" to R.string.xonos_cat_artists,
            "tracks" to R.string.xonos_cat_tracks,
            "albums" to R.string.xonos_cat_albums,
            "playlists" to R.string.xonos_cat_playlists,
            "podcasts" to R.string.xonos_cat_podcasts,
            "audiobooks" to R.string.xonos_cat_audiobooks,
            "radio" to R.string.xonos_cat_radios,
            "genres" to R.string.xonos_cat_genres,
        )

        val COUNTS = mapOf(
            "tracks" to R.plurals.xonos_count_tracks,
            "albums" to R.plurals.xonos_count_albums,
            "artists" to R.plurals.xonos_count_artists,
            "playlists" to R.plurals.xonos_count_playlists,
            "radio" to R.plurals.xonos_count_radio,
            "podcasts" to R.plurals.xonos_count_podcasts,
            "audiobooks" to R.plurals.xonos_count_audiobooks,
            "genres" to R.plurals.xonos_count_genres,
        )
    }
}
