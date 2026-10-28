package io.github.sirionero.mycar.car

import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.car.design.AppIcon
import io.github.sirionero.mycar.car.design.MyCarCarTheme
import io.github.sirionero.mycar.car.design.MyCarPalette
import io.github.sirionero.mycar.car.design.MyCarRadius
import io.github.sirionero.mycar.car.design.glassPanel
import io.github.sirionero.mycar.car.theme.GsColors
import io.github.sirionero.mycar.car.theme.GsIconButton
import io.github.sirionero.mycar.media.MediaArt
import io.github.sirionero.mycar.media.NowPlaying

/**
 * The player docked beside the map. The cover fills the whole column; the controls sit over its
 * lower part on a pane of frosted glass - the same cover, blurred, under a dark tint - and a tap
 * anywhere else on the cover opens the full media screen. Drawn in the dark scheme whatever the
 * app theme, since it all sits on a picture. With no cover, the column takes the app's colour and
 * shows its icon instead.
 */
@Composable
internal fun GlassDockedPlayer(now: NowPlaying, modifier: Modifier) {
    val navigator = LocalCarNavigator.current
    val media = CarServices.media
    val context = LocalContext.current
    val art by produceState(now.art, now.art, now.artUri) { value = now.art ?: MediaArt.load(context, now.artUri) }
    val cover = remember(art) { art?.asImageBitmap() }
    val ground = rememberArtColor(art, MyCarPalette.DarkSurface2)
    val mediaApp by CarServices.mediaApp.collectAsState()
    val appIcon = remember(mediaApp) { mediaApp?.let { findEntry(it.component.flattenToString())?.icon } }
    // Where the glass pane is, so the blurred copy of the cover shows only there.
    var pane by remember { mutableStateOf(Rect.Zero) }
    val paneShape = RoundedCornerShape(MyCarRadius.Sheet)
    val paneCorner = with(LocalDensity.current) { MyCarRadius.Sheet.toPx() }

    MyCarCarTheme(dark = true) {
        // See-through buttons: glass, not solid discs, behind the controls on the pane.
        val scheme = MaterialTheme.colorScheme.copy(
            surfaceContainerHighest = Color.White.copy(alpha = 0.16f),
            onSurfaceVariant = Color.White.copy(alpha = 0.92f),
        )
        // White glyphs whatever the car's theme: the pane is dark glass over the cover. The focus
        // ring kept, which MaterialTheme would swap for its plain ripple.
        val ring = LocalIndication.current
        MaterialTheme(colorScheme = scheme) { CompositionLocalProvider(LocalContentColor provides Color.White, LocalIndication provides ring) {
            Box(modifier.clip(RoundedCornerShape(MyCarRadius.Card)).background(ground)) {
                if (cover != null) {
                    Image(cover, null, Modifier.fillMaxSize().clickable { navigator.media() }, contentScale = ContentScale.Crop)
                    Image(
                        cover,
                        null,
                        Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                if (pane.isEmpty) return@drawWithContent
                                clipPath(Path().apply { addRoundRect(RoundRect(pane, CornerRadius(paneCorner))) }) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                            .blur(GLASS_BLUR, BlurredEdgeTreatment.Rectangle),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().clickable { navigator.media() }.padding(bottom = NO_COVER_LIFT),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(appIcon, null, NO_COVER_ICON)
                    }
                }
                // Dark glass, so it shows on a white cover as well as a black one.
                GsIconButton(
                    Icons.Filled.Close, "Close the player", navigator::closeSidePanel,
                    Modifier.align(Alignment.TopEnd).padding(10.dp),
                    colors = GsColors(Color.Black.copy(alpha = 0.4f), Color.White),
                    size = 44.dp, iconSize = 22.dp, shape = CircleShape,
                )
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(10.dp)
                        .onGloballyPositioned { pane = it.boundsInParent() }
                        .glassPanel(paneShape)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        now.title,
                        style = MaterialTheme.typography.titleMedium.copy(shadow = TEXT_SHADOW),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        now.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(shadow = TEXT_SHADOW),
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (now.durationMs > 0) {
                        PlayerProgress(now, media, Modifier.fillMaxWidth().padding(vertical = 2.dp), showTimes = false)
                    } else {
                        Spacer(Modifier.height(8.dp))
                    }
                    // A narrow column (Large or Extra large on a small screen): the buttons shrink to
                    // fit, rather than "Next" getting squeezed and Lyrics falling off the end.
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val skip = min(48.dp, maxWidth / 3.3f)
                        val mode = min(44.dp, maxWidth / 4)
                        Column {
                            TransportRow(now, media, skip = skip, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            PlayModeRow(now, media, size = mode, modifier = Modifier.fillMaxWidth()) {
                                PlainButton(navigator::showSideLyrics, "Lyrics", mode) {
                                    Icon(painterResource(R.drawable.sym_lyrics), null, Modifier.size(mode * 0.52f))
                                }
                            }
                        }
                    }
                }
            }
        } }
    }
}

private val GLASS_BLUR = 28.dp
private val NO_COVER_ICON = 96.dp
/** Keeps the app's icon centred in the part of the column the glass pane leaves. */
private val NO_COVER_LIFT = 180.dp
private val TEXT_SHADOW = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), blurRadius = 6f)
