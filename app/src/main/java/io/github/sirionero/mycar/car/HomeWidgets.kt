package io.github.sirionero.mycar.car

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sirionero.mycar.car.design.MyCarMotion
import io.github.sirionero.mycar.car.design.MyCarPalette
import io.github.sirionero.mycar.car.design.MyCarRadius
import io.github.sirionero.mycar.car.design.MyCarSpace
import io.github.sirionero.mycar.car.design.glassPanel
import io.github.sirionero.mycar.car.design.rememberWallpaper
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The weather on a page of Home: now, big, then as much of the next hours and the coming days as
 * the tile has room for. A tap opens the whole forecast.
 */
@Composable
internal fun WeatherWidget(onHold: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val navigator = LocalCarNavigator.current
    val state by Weather.state.collectAsState()
    // A page stays up all drive; [Weather.refresh] only goes out once its reading is half an hour old.
    LaunchedEffect(Unit) {
        while (true) {
            Weather.refresh(context)
            delay(WIDGET_REFRESH_MS)
        }
    }

    WidgetCard(onClick = navigator::weather, onHold = onHold, modifier = modifier) {
        when (val s = state) {
            WeatherState.Loading -> WidgetNote("Getting the weather…")
            is WeatherState.Unavailable -> WidgetNote(s.reason)
            is WeatherState.Ready -> BoxWithConstraints(Modifier.fillMaxSize().padding(MyCarSpace.L)) {
                val w = s.data
                val today = w.days.firstOrNull()
                val hours = (maxWidth / HOUR_WIDTH).toInt().coerceIn(3, 8)
                // Each further part only where the ones above it leave room for it.
                val showHours = maxHeight >= NOW_AND_HOURS
                val days = if (showHours) ((maxHeight - NOW_AND_HOURS) / (DAY_ROW + MyCarSpace.M)).toInt().coerceIn(0, 4) else 0
                Column(verticalArrangement = Arrangement.spacedBy(MyCarSpace.M)) {
                    Text(
                        w.place,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyCarSpace.M)) {
                        WeatherIcon(w.code, w.isDay, size = 56.dp)
                        Text("${w.temp}°", fontSize = 48.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Text(
                        listOfNotNull(Weather.describe(w.code), today?.let { "H ${it.high}°  L ${it.low}°" }).joinToString("  ·  "),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (showHours) {
                        Row(Modifier.fillMaxWidth().padding(top = MyCarSpace.S)) {
                            w.hours.take(hours).forEach { h ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(h.time)).replace(":00", ""),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                    WeatherIcon(h.code, h.isDay, size = 28.dp, modifier = Modifier.padding(vertical = MyCarSpace.XS))
                                    Text("${h.temp}°", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    w.days.drop(1).take(days).forEach { d ->
                        Row(
                            Modifier.fillMaxWidth().height(DAY_ROW),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MyCarSpace.M),
                        ) {
                            Text(
                                SimpleDateFormat("EEE", Locale.getDefault()).format(Date(d.date)),
                                Modifier.width(56.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            WeatherIcon(d.code, true, size = 24.dp)
                            Text(
                                "${d.low}° / ${d.high}°",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * What's coming up in the phone's calendar, on a page of Home: today's date, then as many of the
 * next events as fit, from the same week the dashboard shows. A tap opens the dashboard.
 */
@Composable
internal fun AgendaWidget(onHold: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val navigator = LocalCarNavigator.current
    fun allowedNow() = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    var allowed by remember { mutableStateOf(allowedNow()) }
    fun todayNow(): String {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
    }
    // The day too: the page stays up all drive, and a drive past midnight kept yesterday's date.
    var today by remember { mutableStateOf(todayNow()) }
    // Read again now and then, as the page stays up all drive and the day moves on under it - or
    // the calendar is allowed on the phone meanwhile.
    val events by produceState(cachedAgenda) {
        while (true) {
            allowed = allowedNow()
            today = todayNow()
            value = withContext(Dispatchers.IO) { fetchAgenda(context) }.also { cachedAgenda = it }
            delay(WIDGET_REFRESH_MS)
        }
    }

    WidgetCard(onClick = navigator::dashboard, onHold = onHold, modifier = modifier) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(MyCarSpace.L)) {
            val fit = ((maxHeight - 64.dp) / EVENT_ROW).toInt().coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(MyCarSpace.S)) {
                Text(
                    today.replaceFirstChar { it.titlecase(Locale.getDefault()) },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("Coming up", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val list = events
                when {
                    !allowed -> WidgetText("Allow the calendar for myCar on the phone to see what's coming up.")
                    list == null -> WidgetText("Loading calendar…")
                    list.isEmpty() -> WidgetText("Nothing in the next week.")
                    else -> list.take(fit).forEach { EventLine(it) }
                }
            }
        }
    }
}

/** One event: a bar of colour, then when and what, on two lines so a long title still reads. */
@Composable
private fun EventLine(event: AgendaEvent) {
    Row(Modifier.fillMaxWidth().height(EVENT_ROW - MyCarSpace.S), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .padding(vertical = MyCarSpace.XS)
                .clip(RoundedCornerShape(2.dp))
                .background(MyCarPalette.Accent),
        )
        Column(Modifier.padding(start = MyCarSpace.M)) {
            Text(
                eventWhen(event),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                event.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WidgetText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BoxScope.WidgetNote(text: String) {
    Text(
        text,
        Modifier.align(Alignment.Center).padding(MyCarSpace.XL),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/**
 * A widget's card: frosted glass over the wallpaper, or a plain surface without one. Tapped it
 * opens the widget's own screen, held it changes the page, and it gives a touch under the finger.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WidgetCard(
    onClick: (() -> Unit)?,
    onHold: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(MyCarRadius.Card)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) CARD_PRESSED_SCALE else 1f,
        tween(MyCarMotion.PressMs, easing = MyCarMotion.Decelerate),
        label = "widgetPress",
    )
    val glass = rememberWallpaper() != null
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .then(if (glass) Modifier.glassPanel(shape, surface.copy(alpha = 0.62f)) else Modifier.background(surface))
            .combinedClickable(
                interactionSource = source,
                indication = LocalIndication.current,
                onLongClick = onHold,
                onClick = onClick ?: {},
            ),
        content = content,
    )
}

/** How often a widget looks again: the weather only goes out when its reading is old. */
private const val WIDGET_REFRESH_MS = 5 * 60 * 1000L

/** A widget card gives less than an icon does under the finger: it's much bigger. */
private const val CARD_PRESSED_SCALE = 0.98f

private val HOUR_WIDTH = 64.dp

/** The weather now and the row of hours under it, gaps included. */
private val NOW_AND_HOURS = 232.dp
private val DAY_ROW = 32.dp
private val EVENT_ROW = 56.dp
