package io.github.sirionero.mycar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sirionero.mycar.CertCatalog
import io.github.sirionero.mycar.CertProvider
import io.github.sirionero.mycar.CertRotation
import io.github.sirionero.mycar.CertRotationPolicy.Notice
import io.github.sirionero.mycar.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What to do after the car turned down the certificate, or found it outside its date; see
 * [CertRotation]. Nothing when there's nothing to say.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CertNoticeCard(onOpenHelp: () -> Unit, onOpenSettings: () -> Unit) {
    val notice by CertRotation.notice.collectAsStateWithLifecycle()
    if (notice.kind == Notice.NONE) return
    val context = LocalContext.current
    // Done meanwhile somewhere else - certificates got in the setup, a file imported in Settings:
    // the card asked for it still, after the setup's own card had gone.
    val resumed = rememberResumeCount()
    val gotOne = remember(notice, resumed) { notice.kind == Notice.NO_CERT && CertProvider.hasAnyCert(context) }
    if (gotOne) {
        LaunchedEffect(notice) { CertRotation.dismiss(context) }
        return
    }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val label = notice.cert?.let { CertCatalog.entry(it)?.label }
    // The one to get: the first of the catalog not on the phone yet.
    val spare = remember(notice) {
        val present = CertCatalog.present(context)
        CertCatalog.entries.firstOrNull { it.id !in present }?.label
    }

    val downloadFailed = stringResource(R.string.cert_notice_download_failed)

    /** Runs [work] off the main thread, with the buttons held until it's done. */
    fun act(work: () -> Unit) {
        if (busy) return
        busy = true
        problem = null
        scope.launch {
            val error = withContext(Dispatchers.IO) { runCatching(work).exceptionOrNull() }
            busy = false
            problem = error?.let { downloadFailed }
        }
    }

    val (title, text) = when (notice.kind) {
        Notice.SWITCHED -> stringResource(R.string.cert_notice_turned_down_title) to
            if (label != null) stringResource(R.string.cert_notice_switched_text, label) else stringResource(R.string.cert_notice_switched_next_text)
        Notice.NO_SPARE -> stringResource(R.string.cert_notice_turned_down_title) to
            if (spare != null) stringResource(R.string.cert_notice_no_spare_text, spare) else stringResource(R.string.cert_notice_no_spare_unnamed_text)
        Notice.EXHAUSTED -> stringResource(R.string.cert_notice_exhausted_title) to
            pluralStringResource(R.plurals.cert_notice_exhausted_text, CertCatalog.entries.size, CertCatalog.entries.size)
        Notice.NO_CERT -> stringResource(R.string.cert_notice_no_cert_title) to
            stringResource(R.string.cert_notice_no_cert_text)
        Notice.OWN_REFUSED -> stringResource(R.string.cert_notice_own_refused_title) to
            stringResource(R.string.cert_notice_own_refused_text)
        Notice.CLOCK -> stringResource(R.string.cert_notice_clock_title) to
            stringResource(R.string.cert_notice_clock_text)
        Notice.NONE -> return
    }

    // The same amber as the Bluetooth card: something to do, not something broken.
    val dark = phoneDark()
    val container = if (dark) Color(0xFF4A3B00) else Color(0xFFFFE08A)
    val content = if (dark) Color(0xFFFFE08A) else Color(0xFF3A2E00)
    val filled = ButtonDefaults.buttonColors(containerColor = content, contentColor = container)
    Spacer(Modifier.height(16.dp))
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
            // Side by side where they fit: "Try again from the first" beside a button doesn't on a narrow phone.
            FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (notice.kind) {
                    Notice.NO_SPARE -> Button(onClick = {
                        act {
                            CertCatalog.downloadAll(context)
                            checkNotNull(CertRotation.useSpare(context)) { "no spare" }
                        }
                    }, enabled = !busy, colors = filled) {
                        Text(stringResource(if (busy) R.string.cert_notice_getting_spare else R.string.cert_notice_get_spare))
                    }
                    Notice.NO_CERT -> Button(onClick = {
                        act {
                            CertProvider.downloadAndImport(context)
                            CertRotation.dismiss(context)
                        }
                    }, enabled = !busy, colors = filled) {
                        Text(stringResource(if (busy) R.string.cert_notice_getting_certificates else R.string.cert_notice_get_certificates))
                    }
                    Notice.EXHAUSTED -> {
                        Button(onClick = onOpenHelp, colors = filled) { Text(stringResource(R.string.help_which_cars)) }
                        TextButton(onClick = { act { CertRotation.startOver(context) } }, enabled = !busy) {
                            Text(stringResource(R.string.cert_notice_start_over), color = content)
                        }
                    }
                    Notice.OWN_REFUSED -> Button(onClick = onOpenSettings, colors = filled) { Text(stringResource(R.string.common_settings)) }
                    else -> Unit
                }
                if (notice.kind != Notice.EXHAUSTED) {
                    TextButton(onClick = { CertRotation.dismiss(context) }, enabled = !busy) { Text(stringResource(R.string.common_ok), color = content) }
                }
            }
        }
    }
}
