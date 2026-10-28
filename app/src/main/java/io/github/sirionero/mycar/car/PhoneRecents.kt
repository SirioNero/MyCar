package io.github.sirionero.mycar.car

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.telephony.PhoneNumberUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.PermissionActivity
import io.github.sirionero.mycar.car.design.MyCarPalette
import io.github.sirionero.mycar.car.theme.GsButton
import io.github.sirionero.mycar.car.theme.GsTone
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How a call in the phone's log went, as the car's Recents tells it. */
internal enum class RecentKind(val label: String, val arrow: String) {
    INCOMING("Incoming", "↙"),
    OUTGOING("Outgoing", "↗"),
    MISSED("Missed", "↙"),
    DECLINED("Declined", "↙"),
}

/**
 * A line of the car's Recents: the newest of [count] calls in a row with the same number. [name]
 * is the one the phone's log keeps for it, null for a number not in the contacts; [number] is
 * empty for a private one, which can't be called back.
 */
internal data class RecentCall(val name: String?, val number: String, val kind: RecentKind, val at: Long, val count: Int)

internal fun hasCallLog(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

/**
 * The phone's latest calls, newest first, off its call log; voicemail and blocked ones left out.
 * [grouped]: calls in a row with the same number as one line, as the Recents tab shows them.
 */
internal fun loadRecents(context: Context, grouped: Boolean = true): List<RecentCall> {
    if (!hasCallLog(context)) return emptyList()
    val out = mutableListOf<RecentCall>()
    // "limit": the call log's own way to give only the latest rows - an old phone has thousands.
    val uri = CallLog.Calls.CONTENT_URI.buildUpon().appendQueryParameter("limit", READ_ROWS.toString()).build()
    val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE)
    runCatching {
        context.contentResolver.query(uri, projection, null, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
            val numberAt = c.getColumnIndex(CallLog.Calls.NUMBER)
            val nameAt = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val typeAt = c.getColumnIndex(CallLog.Calls.TYPE)
            val dateAt = c.getColumnIndex(CallLog.Calls.DATE)
            var read = 0
            while (out.size < SHOWN && read++ < READ_ROWS && c.moveToNext()) {
                val kind = when (c.getInt(typeAt)) {
                    CallLog.Calls.INCOMING_TYPE, CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> RecentKind.INCOMING
                    CallLog.Calls.OUTGOING_TYPE -> RecentKind.OUTGOING
                    CallLog.Calls.MISSED_TYPE -> RecentKind.MISSED
                    CallLog.Calls.REJECTED_TYPE -> RecentKind.DECLINED
                    else -> continue
                }
                val number = c.getString(numberAt).orEmpty()
                val name = c.getString(nameAt)?.takeIf { it.isNotBlank() }
                val last = out.lastOrNull()
                if (grouped && last != null && number.isNotBlank() && PhoneNumberUtils.compare(last.number, number)) {
                    out[out.lastIndex] = last.copy(count = last.count + 1, name = last.name ?: name)
                } else {
                    out += RecentCall(name, number, kind, c.getLong(dateAt), 1)
                }
            }
        }
    }.onFailure { MyCarLog.w("calls: couldn't read the call log: ${it.message}") }
    return out
}

/**
 * The Phone's Recents tab: the latest calls, a missed one in red, a tap asks "Call …?" over the
 * whole screen as a number on a page does (CallPrompt). Read again when the log changes, so a
 * call just made or missed is at the top on the way back.
 */
@Composable
internal fun RecentsList(context: Context) {
    var allowed by remember { mutableStateOf(hasCallLog(context)) }
    if (!allowed) {
        CallLogMissing(context) { allowed = true }
        return
    }
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                version++
            }
        }
        runCatching { context.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer) }
        onDispose { runCatching { context.contentResolver.unregisterContentObserver(observer) } }
    }
    // Null until read the first time; after that the list stays up while it's read again.
    val loaded by produceState<List<RecentCall>?>(null, context, version) {
        value = withContext(Dispatchers.IO) { loadRecents(context) }
    }
    val calls = loaded
    when {
        calls == null -> Note("Loading recent calls…")
        calls.isEmpty() -> Note("No recent calls.")
        else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(calls) { call ->
                RecentRow(call, onClick = call.number.takeIf { it.isNotBlank() }?.let { number -> { CallPrompt.ask(call.name ?: number, number) } })
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CallLogMissing(context: Context, onGranted: () -> Unit) {
    // Android's question shows on the phone; the answer is picked up without coming back in.
    LaunchedEffect(Unit) {
        while (!hasCallLog(context)) delay(1_000)
        onGranted()
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Note("Recent calls come from the phone's call log. Allow it on the phone when you're parked.")
        GsButton(tone = GsTone.Tonal, onClick = {
            PermissionActivity.ask(context, arrayOf(Manifest.permission.READ_CALL_LOG))
        }) { Text("Ask on the phone") }
    }
}

@Composable
private fun RecentRow(call: RecentCall, onClick: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val missed = call.kind == RecentKind.MISSED
    val title = (call.name ?: call.number.ifBlank { "Private number" }) + if (call.count > 1) "  (${call.count})" else ""
    // The number too when there's a name: the same person can call from two.
    val detail = listOfNotNull(
        "${call.kind.arrow} ${call.kind.label}",
        whenLabel(call.at),
        call.number.takeIf { call.name != null && it.isNotBlank() },
    ).joinToString("  ·  ")
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val mark = if (missed) MyCarPalette.Negative else scheme.onPrimaryContainer
            Box(
                Modifier
                    .size(40.dp)
                    .background(if (missed) MyCarPalette.Negative.copy(alpha = 0.18f) else scheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val initial = call.name?.firstOrNull { it.isLetterOrDigit() }?.uppercase()
                if (initial != null) {
                    Text(initial, style = MaterialTheme.typography.titleMedium, color = mark, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Filled.Call, contentDescription = null, Modifier.size(20.dp), tint = mark)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (missed) MyCarPalette.Negative else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onClick != null) Icon(Icons.Filled.Call, contentDescription = "Call", tint = scheme.primary)
        }
    }
}

/** Today a time, then "Yesterday", the day of the week, a date: as the phone's own log says it. */
private fun whenLabel(at: Long, now: Long = System.currentTimeMillis()): String {
    val zone = ZoneId.systemDefault()
    val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(at).atZone(zone).toLocalDate(), Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
    fun pattern(skeleton: String) =
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton), Locale.getDefault()).format(Date(at))
    return when {
        days <= 0L -> DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at))
        days == 1L -> "Yesterday"
        days < 7L -> pattern("EEEE")
        else -> pattern("dMMM")
    }
}

/** Lines shown, and at most how many rows of the log are read to fill them. */
private const val SHOWN = 60
private const val READ_ROWS = 300
