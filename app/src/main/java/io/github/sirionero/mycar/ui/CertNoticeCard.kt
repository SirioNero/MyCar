package io.github.sirionero.mycar.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sirionero.mycar.CertCatalog
import io.github.sirionero.mycar.CertProvider
import io.github.sirionero.mycar.CertRotation
import io.github.sirionero.mycar.CertRotationPolicy.Notice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What to do after the car turned down the certificate, or found it outside its date; see
 * [CertRotation]. Nothing when there's nothing to say.
 */
@Composable
internal fun CertNoticeCard(onOpenHelp: () -> Unit, onOpenSettings: () -> Unit) {
    val notice by CertRotation.notice.collectAsStateWithLifecycle()
    if (notice.kind == Notice.NONE) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val label = notice.cert?.let { CertCatalog.entry(it)?.label } ?: "the next certificate"

    /** Runs [work] off the main thread, with the buttons held until it's done. */
    fun act(work: () -> Unit) {
        if (busy) return
        busy = true
        problem = null
        scope.launch {
            val error = withContext(Dispatchers.IO) { runCatching(work).exceptionOrNull() }
            busy = false
            problem = error?.let { "Couldn't download the certificates. Check your internet connection, then try again." }
        }
    }

    val (title, text) = when (notice.kind) {
        Notice.SWITCHED -> "Your car turned down this certificate" to
            "myCar uses $label from now on. Connect to the car again: plug the cable back in, or connect wirelessly."
        Notice.NO_SPARE -> "Your car turned down this certificate" to
            "Get the spare certificate, then connect to the car again."
        Notice.EXHAUSTED -> "Your car turned down every certificate" to
            "myCar tried ${CertCatalog.entries.size}. Some cars made from around 2020 check phones more strictly, " +
            "and myCar can't connect to them yet."
        Notice.NO_CERT -> "myCar has no car certificate yet" to
            "Cars only talk to phones with a certificate they trust. Get one now, then connect to the car again."
        Notice.OWN_REFUSED -> "Your car turned down your certificate" to
            "To use the public ones instead, remove yours in Settings, under Certificate."
        Notice.CLOCK -> "Check the car's date" to
            "The car says the certificate isn't valid on its date. Set the car's clock, then connect again."
        Notice.NONE -> return
    }

    // The same amber as the Bluetooth card: something to do, not something broken.
    val dark = isSystemInDarkTheme()
    val container = if (dark) Color(0xFF4A3B00) else Color(0xFFFFE08A)
    val content = if (dark) Color(0xFFFFE08A) else Color(0xFF3A2E00)
    val filled = ButtonDefaults.buttonColors(containerColor = content, contentColor = container)
    Spacer(Modifier.height(16.dp))
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (notice.kind) {
                    Notice.NO_SPARE -> Button(onClick = {
                        act {
                            CertCatalog.downloadAll(context)
                            checkNotNull(CertRotation.useSpare(context)) { "no spare" }
                        }
                    }, enabled = !busy, colors = filled) { Text(if (busy) "Getting it…" else "Get the spare") }
                    Notice.NO_CERT -> Button(onClick = {
                        act {
                            CertProvider.downloadAndImport(context)
                            CertRotation.dismiss(context)
                        }
                    }, enabled = !busy, colors = filled) { Text(if (busy) "Getting certificates…" else "Get certificates") }
                    Notice.EXHAUSTED -> {
                        Button(onClick = onOpenHelp, colors = filled) { Text("Which cars work") }
                        TextButton(onClick = { act { CertRotation.startOver(context) } }, enabled = !busy) {
                            Text("Try again from the first", color = content)
                        }
                    }
                    Notice.OWN_REFUSED -> Button(onClick = onOpenSettings, colors = filled) { Text("Settings") }
                    else -> Unit
                }
                if (notice.kind != Notice.EXHAUSTED) {
                    TextButton(onClick = { CertRotation.dismiss(context) }, enabled = !busy) { Text("OK", color = content) }
                }
            }
        }
    }
}
