package io.github.sirionero.mycar.ui

import android.content.Intent
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Words
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by MyCarLog.version.collectAsStateWithLifecycle()
    val lines = remember(version) { MyCarLog.snapshot() }

    var pastOpen by remember { mutableStateOf(false) }
    if (pastOpen) PastLogsDialog(onDismiss = { pastOpen = false })

    val listState = rememberLazyListState()
    val atEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 1
        }
    }
    // Live view: stay pinned to the newest line, but let go the moment the user scrolls up to
    // read something, and pick back up when they return to the bottom.
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to atEnd }.collect { (scrolling, end) ->
            if (scrolling) follow = end
        }
    }
    LaunchedEffect(lines.size, version) {
        if (follow && lines.isNotEmpty()) listState.scrollToItem(lines.size - 1)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logs_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                            // A full phone (the file can't be written) must not close myCar, and the car's session with it.
                            runCatching {
                            val uri = withContext(Dispatchers.IO) { MyCarLog.shareableFile(context) }
                            shareLog(context, uri)
                            }.onFailure { MyCarLog.w("logs: couldn't share the log: ${it.message}") }
                        }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        contentPadding = LOG_BUTTON_PADDING,
                    ) { Text(stringResource(R.string.logs_share)) }
                    OutlinedButton(
                        onClick = { pastOpen = true },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        contentPadding = LOG_BUTTON_PADDING,
                    ) { Text(stringResource(R.string.logs_earlier)) }
                    OutlinedButton(
                        onClick = { MyCarLog.clearLive() },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        contentPadding = LOG_BUTTON_PADDING,
                    ) { Text(stringResource(R.string.logs_clear)) }
                }
            }
        },
        floatingActionButton = {
            if (!follow && lines.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        follow = true
                        scope.launch { listState.scrollToItem(lines.size - 1) }
                    },
                ) { Text(stringResource(R.string.logs_jump_to_latest)) }
            }
        },
    ) { padding ->
        if (lines.isEmpty()) {
            Text(
                stringResource(R.string.logs_empty),
                modifier = Modifier.padding(padding).padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    itemsIndexed(lines) { _, line ->
                        Text(
                            line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = colorFor(line),
                        )
                    }
                }
            }
        }
    }
}

private fun shareLog(context: android.content.Context, uri: android.net.Uri) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, Words.get(R.string.logs_share_subject))
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, Words.get(R.string.logs_share_chooser)))
}

/**
 * Logs from earlier launches. Opening the app after a crash starts a new log, so the one that
 * saw the crash is only reachable here.
 */
@Composable
private fun PastLogsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logs by androidx.compose.runtime.produceState<List<MyCarLog.PastLog>?>(null) {
        value = withContext(Dispatchers.IO) { MyCarLog.pastLogs(context) }
    }
    val format = remember { java.text.SimpleDateFormat("EEE d MMM, h:mm a", java.util.Locale.getDefault()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.logs_earlier_title)) },
        text = {
            val list = logs
            when {
                list == null -> Text(stringResource(R.string.common_loading))
                list.isEmpty() -> Text(stringResource(R.string.logs_earlier_empty))
                else -> LazyColumn {
                    itemsIndexed(list) { _, log ->
                        androidx.compose.foundation.layout.Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        // As Share: a full phone must not close myCar.
                                        runCatching {
                                            val uri = withContext(Dispatchers.IO) { MyCarLog.shareableFile(context, log.file) }
                                            shareLog(context, uri)
                                        }.onFailure { MyCarLog.w("logs: couldn't share the earlier log: ${it.message}") }
                                    }
                                }
                                .padding(vertical = 12.dp),
                        ) {
                            Text(log.started?.let(format::format) ?: log.file.name, style = MaterialTheme.typography.titleMedium)
                            val detail = listOfNotNull(
                                stringResource(R.string.logs_crashed).takeIf { log.crashed },
                                log.car,
                                stringResource(R.string.logs_size_kb, (log.file.length() + 1023) / 1024),
                            ).joinToString(" · ")
                            Text(
                                detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (log.crashed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}

/** Lines look like "HH:mm:ss.SSS L message"; the level letter sits at index 13. */
@Composable
private fun colorFor(line: String): Color = when (line.getOrNull(13)) {
    'E' -> MaterialTheme.colorScheme.error
    'W' -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurface
}

/** Three side by side on a narrow phone: the buttons' usual 24 dp each side broke "Earlier" in two. */
private val LOG_BUTTON_PADDING = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
