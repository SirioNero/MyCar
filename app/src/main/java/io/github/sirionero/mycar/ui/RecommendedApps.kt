package io.github.sirionero.mycar.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Words

/** A free app that runs on myCar's car screen, and where to get it. */
private class RecommendedApp(val name: String, val packageName: String, val url: String, @StringRes val about: Int)

private val recommended = listOf(
    R.string.recommended_section_maps to listOf(
        RecommendedApp("Organic Maps", "app.organicmaps", "https://github.com/organicmaps/organicmaps", R.string.recommended_about_organic_maps),
        RecommendedApp("Vela Maps", "app.vela", "https://github.com/PimpinPumpkin/Vela", R.string.recommended_about_vela),
    ),
    R.string.recommended_section_music to listOf(
        RecommendedApp("ViVi Music", "com.vivi.vivimusic", "https://github.com/vivizzz007/vivi-music", R.string.recommended_about_youtube_music),
        RecommendedApp("Metrolist", "com.metrolist.music", "https://github.com/MetrolistGroup/Metrolist", R.string.recommended_about_youtube_music),
    ),
    R.string.recommended_section_podcasts to listOf(
        RecommendedApp("AntennaPod", "de.danoeh.antennapod", "https://github.com/AntennaPod/AntennaPod", R.string.recommended_about_antennapod),
    ),
)

/** Opened from Settings: the same list the setup guide ends with. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendedAppsScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recommended_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.recommended_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            RecommendedAppsList()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Hidden for now: off, neither Settings nor the setup guide shows the list. The code stays. */
internal const val SHOW_RECOMMENDED_APPS = false

/** The list's introduction, in myCar's language (the setup guide shows it too). */
internal val RECOMMENDED_INTRO: String get() = Words.get(R.string.recommended_intro)

/** The same green as the launcher's "works" check. */
private val INSTALLED_GREEN = Color(0xFF43A047)

/** The apps by section, each with a GitHub button. Installed ones say so in green. */
@Composable
internal fun RecommendedAppsList(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        for ((section, apps) in recommended) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                for (app in apps) AppRow(app) { openPage(context, app.url) }
            }
        }
    }
}

@Composable
private fun AppRow(app: RecommendedApp, onOpen: () -> Unit) {
    val context = LocalContext.current
    val icon = remember(app.packageName) { installedIcon(context, app.packageName) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(icon.toBitmap(96, 96).asImageBitmap(), contentDescription = null, Modifier.fillMaxSize())
            } else {
                Text(app.name.take(1), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(app.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(app.about), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (icon != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = INSTALLED_GREEN, modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(R.string.recommended_installed),
                        style = MaterialTheme.typography.labelLarge,
                        color = INSTALLED_GREEN,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
        OutlinedButton(onClick = onOpen) { Text("GitHub") }
    }
}

private fun installedIcon(context: Context, packageName: String): Drawable? =
    runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()

private fun openPage(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
