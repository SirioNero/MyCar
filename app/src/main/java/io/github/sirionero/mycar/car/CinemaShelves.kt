package io.github.sirionero.mycar.car

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sirionero.mycar.PermissionActivity
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.car.design.GlassTile
import io.github.sirionero.mycar.car.design.MyCarRadius
import io.github.sirionero.mycar.car.design.MyCarSpace
import io.github.sirionero.mycar.car.theme.GsButton
import io.github.sirionero.mycar.car.theme.GsIconButton
import io.github.sirionero.mycar.cinema.ChannelLogos
import io.github.sirionero.mycar.cinema.PhoneVideo
import io.github.sirionero.mycar.cinema.PhoneVideos
import io.github.sirionero.mycar.cinema.TvChannel
import io.github.sirionero.mycar.cinema.TvList
import io.github.sirionero.mycar.cinema.TvListState
import io.github.sirionero.mycar.cinema.TvLists
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

/*
 * Cinema's own shelves, beside the sites: the videos on the phone, and the TV channels of each
 * IPTV list added on the phone. Both play on Android's own player (CinemaPlayer).
 */

/** A Cinema tile for one of myCar's own shelves: a white glyph on its colour, as the sites' tiles are laid out. */
@Composable
internal fun ShelfTile(label: String, color: Color, @DrawableRes glyph: Int, iconSize: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(MyCarRadius.Card))
            .clickable(onClick = onClick)
            .padding(MyCarSpace.S),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MyCarSpace.S),
    ) {
        GlassTile(color, glyph, iconSize)
        Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The phone's videos, newest first, each with its picture and length; one opens in the player,
 * from where it was left. Without Android's permission, a button asks for it on the phone, and
 * the videos show as soon as it's given.
 */
@Composable
internal fun PhoneVideosShelf(onPlay: (CinemaMedia) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val navigator = LocalCarNavigator.current
    var allowed by remember { mutableStateOf(PhoneVideos.allowed(context)) }
    var videos by remember { mutableStateOf<List<PhoneVideo>?>(null) }
    val grid = rememberLazyGridState()
    // The answer on the phone comes while this shows: asked again every second until then.
    LaunchedEffect(allowed) {
        while (!allowed) {
            delay(1_000)
            allowed = PhoneVideos.allowed(context)
        }
        videos = PhoneVideos.list(context)
    }
    DisposableEffect(Unit) {
        navigator.backInterceptor = {
            onClose()
            true
        }
        onDispose { navigator.backInterceptor = null }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("On this phone", Modifier.padding(horizontal = MyCarSpace.L, vertical = MyCarSpace.S))
        val list = videos
        when {
            !allowed -> ShelfMessage(
                "To show the phone's videos here, myCar needs Android's permission for them.",
                button = "Ask on the phone",
            ) { PermissionActivity.ask(context, PhoneVideos.permissions) }
            list == null -> ShelfLoading()
            list.isEmpty() -> ShelfMessage(
                if (PhoneVideos.limited(context)) {
                    "No videos picked for myCar. On the phone, in myCar's settings under Cinema, choose which ones it may see."
                } else {
                    "No videos on this phone."
                },
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(VideoTileWidth),
                state = grid,
                contentPadding = PaddingValues(MyCarSpace.L),
                horizontalArrangement = Arrangement.spacedBy(MyCarSpace.M),
                verticalArrangement = Arrangement.spacedBy(MyCarSpace.M),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list, key = { it.id }) { video ->
                    VideoTile(video) { onPlay(CinemaMedia(video.title, video.uri, resumeKey = "video:${video.id}")) }
                }
            }
        }
    }
}

@Composable
private fun VideoTile(video: PhoneVideo, onClick: () -> Unit) {
    val context = LocalContext.current
    val picture by produceState<ImageBitmap?>(null, video.id) { value = PhoneVideos.thumbnail(context, video) }
    Column(
        Modifier
            .clip(RoundedCornerShape(MyCarRadius.Card))
            .clickable(onClick = onClick)
            .padding(MyCarSpace.XS),
        verticalArrangement = Arrangement.spacedBy(MyCarSpace.S),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(MyCarRadius.Card))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            val shown = picture
            if (shown != null) {
                Image(shown, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(painterResource(R.drawable.sym_movie), null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (video.durationMs > 0) {
                Text(
                    formatDuration(video.durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(MyCarSpace.S)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A TV list's channels: as they were last downloaded at once, a fresh download behind them. With
 * groups in the list (news, sport...), the groups first, then a group's channels.
 */
@Composable
internal fun TvShelf(list: TvList, onPlay: (CinemaMedia) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val navigator = LocalCarNavigator.current
    val states by TvLists.states.collectAsState()
    val state = states[list.url] ?: TvListState(loading = true)
    var group by rememberSaveable(list.url) { mutableStateOf<String?>(null) }
    // A channel by name, across the groups: lists run to thousands of channels.
    var query by rememberSaveable(list.url) { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val groups = remember(state.channels) { state.channels.mapNotNull { it.group }.distinct() }
    // Counted once, not again for every tile at every redraw.
    val counts = remember(state.channels) { state.channels.groupingBy { it.group }.eachCount() }
    val groupsGrid = rememberLazyGridState()
    // A group's channels from the top; the same group's place kept while one of them plays.
    val channelsGrid = rememberSaveable(group, saver = LazyGridState.Saver) { LazyGridState() }
    LaunchedEffect(list.url) {
        // Logos that didn't come last time (a tunnel, a slow server) get another chance.
        ChannelLogos.forgetFailures()
        TvLists.load(context, list)
    }
    DisposableEffect(Unit) {
        navigator.backInterceptor = {
            when {
                searching -> searching = false
                query.isNotEmpty() -> query = ""
                group != null -> group = null
                else -> onClose()
            }
            true
        }
        onDispose { navigator.backInterceptor = null }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(group?.takeIf { it != ALL } ?: list.name, Modifier.padding(horizontal = MyCarSpace.L, vertical = MyCarSpace.S)) {
            if (state.channels.size > SEARCH_FROM) {
                GsIconButton(Icons.Filled.Search, "Find a channel", { searching = !searching })
            }
            if (state.loading) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            } else {
                GsIconButton(Icons.Filled.Refresh, "Download the list again", { TvLists.load(context, list, force = true) })
            }
        }
        val problem = state.problem
        if (problem != null && state.channels.isNotEmpty()) {
            Text(
                "$problem Showing the channels from the last time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = MyCarSpace.L),
            )
        }
        val found = remember(state.channels, query) {
            if (query.isBlank()) null else state.channels.filter { it.name.contains(query.trim(), ignoreCase = true) }
        }
        // Between the header and the keyboard, never under it.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                found != null -> Column(Modifier.fillMaxSize()) {
                    Text(
                        "Channels with “${query.trim()}”: ${found.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = MyCarSpace.L),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(ChannelTileWidth),
                        contentPadding = PaddingValues(MyCarSpace.L),
                        horizontalArrangement = Arrangement.spacedBy(MyCarSpace.M),
                        verticalArrangement = Arrangement.spacedBy(MyCarSpace.M),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(found) { channel -> ChannelTile(channel) { onPlay(channel.media()) } }
                    }
                }
                state.channels.isEmpty() && state.loading -> ShelfLoading()
                state.channels.isEmpty() -> ShelfMessage(problem ?: "No channels in this list.", button = "Try again") {
                    TvLists.load(context, list, force = true)
                }
                groups.size > 1 && group == null -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(GroupTileWidth),
                    state = groupsGrid,
                    contentPadding = PaddingValues(MyCarSpace.L),
                    horizontalArrangement = Arrangement.spacedBy(MyCarSpace.M),
                    verticalArrangement = Arrangement.spacedBy(MyCarSpace.M),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = ALL) { GroupTile("All channels", state.channels.size) { group = ALL } }
                    items(groups, key = { it }) { name ->
                        GroupTile(name, counts[name] ?: 0) { group = name }
                    }
                }
                else -> {
                    val shown = remember(state.channels, group) {
                        if (group == null || group == ALL) state.channels else state.channels.filter { it.group == group }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(ChannelTileWidth),
                        state = channelsGrid,
                        contentPadding = PaddingValues(MyCarSpace.L),
                        horizontalArrangement = Arrangement.spacedBy(MyCarSpace.M),
                        verticalArrangement = Arrangement.spacedBy(MyCarSpace.M),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(shown) { channel -> ChannelTile(channel) { onPlay(channel.media()) } }
                    }
                }
            }
        }
        if (searching) {
            CarKeyboard(
                query,
                onTextChange = { query = it },
                onSubmit = { searching = false },
                onDismiss = { searching = false },
            )
        }
    }
}

/** A channel for the player: a film in the list (a file, not a stream) seeks and starts again where it was left. */
private fun TvChannel.media(): CinemaMedia {
    val file = url.substringBefore('?').substringAfterLast('.').lowercase() in FILE_TYPES
    return CinemaMedia(name, Uri.parse(url), headers, live = !file, resumeKey = if (file) "tv:$url" else null)
}

@Composable
private fun GroupTile(name: String, count: Int, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MyCarRadius.Card))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(MyCarSpace.L),
        verticalArrangement = Arrangement.spacedBy(MyCarSpace.XS),
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (count == 1) "1 channel" else "$count channels",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChannelTile(channel: TvChannel, onClick: () -> Unit) {
    val url = channel.logo
    val logo by produceState(url?.let { ChannelLogos.kept(it) }, url) {
        if (value == null && url != null) value = ChannelLogos.load(url)
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(MyCarRadius.Card))
            .clickable(onClick = onClick)
            .padding(MyCarSpace.XS),
        verticalArrangement = Arrangement.spacedBy(MyCarSpace.S),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(MyCarRadius.Card))
                .background(if (logo != null) LogoBackground else initialColor(channel.name)),
            contentAlignment = Alignment.Center,
        ) {
            val shown = logo
            if (shown != null) {
                Image(shown, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(MyCarSpace.M))
            } else {
                Text(channel.name.take(1).uppercase(), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(channel.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ShelfLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** A word in the middle of a shelf, and a button when there's something to do about it. */
@Composable
private fun ShelfMessage(text: String, button: String? = null, onButton: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(MyCarSpace.XL),
        verticalArrangement = Arrangement.spacedBy(MyCarSpace.L, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (button != null) GsButton(onClick = onButton) { Text(button) }
    }
}

// The remainder made positive, not the hash: the one hash with no positive value made it -2.
private fun initialColor(name: String) = ChannelColors[(name.hashCode() % ChannelColors.size).absoluteValue]

/** The group that shows every channel of a list. */
private const val ALL = "\u0000all"
/** Addresses of films rather than streams, in the lists that have both. */
private val FILE_TYPES = setOf("mp4", "mkv", "avi", "mov", "webm", "m4v")
private val VideoTileWidth = 260.dp
private val ChannelTileWidth = 168.dp
private val GroupTileWidth = 240.dp
/** Behind a logo: mid grey, where both dark and light logos still read. */
private val LogoBackground = Color(0xFF48484A)
private val ChannelColors = listOf(
    Color(0xFFD7263D), Color(0xFF6C63FF), Color(0xFF0F9D58), Color(0xFFF4511E), Color(0xFF3D8BFF), Color(0xFF8E24AA),
)

/** Lists longer than this have the search button: shorter ones fit a glance. */
private const val SEARCH_FROM = 24
