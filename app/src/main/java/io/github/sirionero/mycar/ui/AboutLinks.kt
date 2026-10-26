package io.github.sirionero.mycar.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext

/** Where myCar lives outside the app: support, the chat room, and the code. */
internal object MyCarLinks {
    /** A card payment page for giving once, any amount. Its row stays hidden while this is empty. */
    const val PAYMENT_LINK = ""
    /** myCar's Bitcoin address (native SegWit). */
    const val BITCOIN = "bc1qu8q2sc5kuccjj3zpzzfpj85xn7szp73nae9xlx"
    const val MATRIX = "https://matrix.to/#/!mf0_mgBd5KqKpc9FfX_wHp1jFCcwAtPrndIn_oGIyMk?via=matrix.org"
    const val SOURCE = "https://github.com/sirionero/mycar"
    const val LICENSE = "$SOURCE/blob/main/LICENSE"
    /** Gearslip, the project myCar is a modified version of. */
    const val UPSTREAM = "https://github.com/twinksoftware/gearslip"
    const val CARS = "$SOURCE/blob/main/CARS.md"

    fun open(context: Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Opens a Bitcoin wallet app on the address (a BIP 21 link); says so when none is installed. */
    fun openBitcoinWallet(context: Context) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("bitcoin:$BITCOIN")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            Toast.makeText(context, "No Bitcoin wallet app found. Copy the address instead.", Toast.LENGTH_LONG).show()
        }
    }

    fun copyBitcoin(context: Context) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Bitcoin address", BITCOIN))
        Toast.makeText(context, "Bitcoin address copied", Toast.LENGTH_SHORT).show()
    }
}

/** The ways to support myCar: Bitcoin, and card once it's set up. */
@Composable
internal fun DonateRows() {
    val context = LocalContext.current
    val arrow = @Composable {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    var bitcoinOpen by remember { mutableStateOf(false) }
    SettingsRow(
        "Bitcoin",
        Modifier.clickable { bitcoinOpen = true },
        subtitle = "Open your wallet or copy the address",
        icon = Icons.Filled.Favorite,
        trailing = arrow,
    )
    if (bitcoinOpen) BitcoinDialog { bitcoinOpen = false }
    if (MyCarLinks.PAYMENT_LINK.isNotEmpty()) {
        SettingsDivider()
        SettingsRow(
            "Give once",
            Modifier.clickable { MyCarLinks.open(context, MyCarLinks.PAYMENT_LINK) },
            subtitle = "Any amount, by card",
            icon = Icons.Filled.Favorite,
            trailing = arrow,
        )
    }
}

/** The Bitcoin address in full, with ways to pay it from this phone or another device. */
@Composable
internal fun BitcoinDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
        title = { Text("Support with Bitcoin") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Send any amount to this address from your Bitcoin wallet.")
                SelectionContainer {
                    Text(
                        MyCarLinks.BITCOIN,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { MyCarLinks.openBitcoinWallet(context) }, Modifier.fillMaxWidth()) {
                    Text("Open in wallet")
                }
                OutlinedButton(onClick = { MyCarLinks.copyBitcoin(context) }, Modifier.fillMaxWidth()) {
                    Text("Copy address")
                }
                TextButton(onClick = onDismiss, Modifier.fillMaxWidth()) {
                    Text("Close")
                }
            }
        },
    )
}

/** The About section's links: the chat room, the source code, the license, and credits. */
@Composable
internal fun AboutLinkRows() {
    val context = LocalContext.current
    val arrow = @Composable {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SettingsRow(
        "Gearslip chat on Matrix",
        Modifier.clickable { MyCarLinks.open(context, MyCarLinks.MATRIX) },
        subtitle = "The community of Gearslip, the project myCar is based on",
        icon = Icons.AutoMirrored.Filled.Send,
        trailing = arrow,
    )
    SettingsDivider()
    SettingsRow(
        "Source code",
        Modifier.clickable { MyCarLinks.open(context, MyCarLinks.SOURCE) },
        subtitle = "Read or change myCar's code under the AGPL",
        icon = Icons.Filled.Build,
        trailing = arrow,
    )
    SettingsDivider()
    SettingsRow(
        "Based on Gearslip",
        Modifier.clickable { MyCarLinks.open(context, MyCarLinks.UPSTREAM) },
        subtitle = "myCar is a modified version of Gearslip, © 2026 Twink Software LLC and contributors",
        icon = Icons.Filled.Info,
        trailing = arrow,
    )
    SettingsDivider()
    SettingsRow(
        "License",
        Modifier.clickable { MyCarLinks.open(context, MyCarLinks.LICENSE) },
        subtitle = "Free software under the GNU AGPL v3, with no warranty",
        icon = Icons.Filled.Info,
        trailing = arrow,
    )
    SettingsDivider()
    SettingsRow(
        "Voice credits",
        subtitle = "Speech recognition by sherpa-onnx (Apache 2.0), with Kroko ASR by Banafo " +
            "(CC BY-SA 4.0), and Parakeet and Canary by NVIDIA (CC BY 4.0)",
        icon = Icons.Filled.Favorite,
    )
}
