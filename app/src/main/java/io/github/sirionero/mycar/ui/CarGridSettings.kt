package io.github.sirionero.mycar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.car.AppGridLayout
import io.github.sirionero.mycar.car.CarSettings
import io.github.sirionero.mycar.car.HiddenApps
import io.github.sirionero.mycar.car.design.AppIcon
import io.github.sirionero.mycar.car.hiddenAsShown
import io.github.sirionero.mycar.car.launcherOrder
import io.github.sirionero.mycar.car.rememberAllEntries
import io.github.sirionero.mycar.car.rememberReplacedByWeb
import io.github.sirionero.mycar.car.rememberVehicleDataShown

/**
 * The car's app grid from the phone: pages or one list, as in the car's Settings, and which apps are
 * on it: all of them but the ones hidden, here or in the car (a long press on an app there, Hide from
 * the app grid). The same list the car's Settings bring back from, on the same key.
 */
@Composable
internal fun CarGridSection() {
    val layout by CarSettings.appGrid.collectAsState()
    val hidden by HiddenApps.ids.collectAsState()
    val order by CarSettings.appOrder.collectAsState()
    val entries = rememberAllEntries()
    val replaced = rememberReplacedByWeb()
    val vehicleData = rememberVehicleDataShown()
    // The grid's apps in its own order; a music service's app in place of its web player isn't one.
    val apps = launcherOrder(entries.filterNot(replaced), vehicleData, order).filter(HiddenApps::canHide)
    val off = hiddenAsShown(hidden, entries)
    val count = apps.count { it.componentId in off }
    var open by remember { mutableStateOf(false) }
    SettingsSection {
        PickRow(
            stringResource(R.string.car_settings_app_grid),
            listOf(
                Pick(AppGridLayout.PAGES, stringResource(R.string.car_settings_app_grid_pages)),
                Pick(AppGridLayout.LIST, stringResource(R.string.car_settings_app_grid_list)),
            ),
            layout,
        ) { CarSettings.setAppGrid(it) }
        SettingsDivider()
        SettingsRow(
            stringResource(R.string.settings_grid_apps),
            Modifier.clickable { open = true },
            subtitle = if (count == 0) stringResource(R.string.settings_grid_apps_all)
            else pluralStringResource(R.plurals.settings_grid_apps_hidden, count, count),
        )
    }
    if (!open) return
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.settings_grid_apps)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                apps.forEach { entry ->
                    val id = entry.componentId ?: return@forEach
                    val shown = id !in off
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(shown, role = Role.Checkbox) { if (it) HiddenApps.show(id, entries) else HiddenApps.hide(id) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Checkbox(checked = shown, onCheckedChange = null)
                        AppIcon(entry.icon, entry.builtIn?.id, 32.dp)
                        Text(entry.shownLabel, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(
                    stringResource(R.string.settings_grid_apps_note),
                    Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.common_done)) } },
    )
}
