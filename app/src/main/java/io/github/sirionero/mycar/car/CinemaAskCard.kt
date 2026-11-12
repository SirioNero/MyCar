package io.github.sirionero.mycar.car

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.car.design.MyCarSpace
import io.github.sirionero.mycar.car.theme.GsButton
import io.github.sirionero.mycar.car.theme.GsTone
import kotlinx.coroutines.delay

/**
 * The driver's yes, on the car's screen, before Cinema opens what another app sent that could reach the
 * home network (see CinemaAsks): a page, whose scripts run in myCar's web view, or a video at a home
 * address. Any app on the phone can send one; only a tap on "Open" here - or the knob - opens it, and the
 * card says where it leads. "Not now", the car's Back or a tap beside the card drops it; one sent meanwhile
 * takes its place; unanswered, it goes when it's old news.
 */
@Composable
internal fun CinemaAskCard() {
    val asked by CinemaAsks.asked.collectAsState()
    val ask = asked?.takeIf { !it.agreed && CinemaAsks.fresh(it) } ?: return
    LaunchedEffect(ask) {
        delay(CinemaAsks.timeLeft(ask))
        CinemaAsks.dismiss(ask)
    }
    CardBackKey(CardBack::cinemaAsk) { CinemaAsks.dismiss(ask) }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = { CinemaAsks.dismiss(ask) },
        ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            // Taps on the card stay on it, off the scrim's "Not now".
            onClick = {},
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(MyCarSpace.L).widthIn(max = CARD_WIDTH).fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(MyCarSpace.XXL),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(if (ask is CinemaAsks.Page) R.string.open_with_confirm_page else R.string.open_with_confirm_home),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    CinemaAsks.placeOf(ask),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MyCarSpace.L)) {
                    GsButton(onClick = { CinemaAsks.dismiss(ask) }, tone = GsTone.Outline, modifier = Modifier.weight(1f).height(56.dp)) {
                        Text(stringResource(R.string.common_not_now), style = MaterialTheme.typography.titleMedium)
                    }
                    GsButton(onClick = { CinemaAsks.agree(ask) }, modifier = Modifier.weight(1f).height(56.dp)) {
                        Text(stringResource(R.string.common_open), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

/** As wide as a question and its two buttons need: on a wide screen, not across the whole of it. */
private val CARD_WIDTH = 600.dp
