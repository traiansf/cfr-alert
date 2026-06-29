package ro.trenuri.app.ui.station

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import ro.trenuri.app.ui.common.ErrorState
import ro.trenuri.app.ui.common.LoadingState
import ro.trenuri.infofer.model.Station

@Composable
fun StationPickerField(
    label: String,
    onPicked: (Station) -> Unit,
    modifier: Modifier = Modifier,
    selected: Station? = null,
    vm: StationPickerViewModel = koinViewModel(key = label),
) {
    val context = LocalContext.current
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val nearby by vm.nearby.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf(selected?.name ?: "") }
    var expanded by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf(false) }

    // Opportunistic: if permission is already granted, seed distance ordering for typed
    // suggestions without prompting. Never prompt here — only read if already granted.
    LaunchedEffect(Unit) {
        if (isLocationGranted(context)) {
            readLastKnownLocation(context)?.let { (lat, lon) ->
                vm.setLocation(lat, lon)
            }
        }
    }

    // Triggered only by explicit user action (GPS button tap).
    val requestLocation = rememberLocationRequester(
        onLocation = { lat, lon ->
            locationError = false
            vm.loadNearby(lat, lon)
        },
        onDenied = { locationError = true },
    )

    // selected drives the visible text (keeps swap/fill behaviour intact)
    LaunchedEffect(selected) {
        query = selected?.name ?: ""
    }

    val nearbyReady = nearby as? NearbyUiState.Ready

    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = { v ->
                    query = v
                    vm.onQueryChange(v)
                    // Typing dismisses nearby so the two dropdowns never overlap
                    if (v.isNotEmpty()) vm.clearNearby()
                    expanded = true
                },
                label = { Text(label) },
                trailingIcon = {
                    IconButton(onClick = requestLocation) {
                        Icon(Icons.Default.LocationOn, contentDescription = "Locație")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Typeahead suggestions — PopupProperties(focusable = false) keeps the IME
            // and focus on the text field while the list updates live.
            DropdownMenu(
                expanded = expanded && suggestions.isNotEmpty(),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = false),
            ) {
                suggestions.forEach { station ->
                    DropdownMenuItem(
                        text = { Text(station.name) },
                        onClick = {
                            query = station.name
                            expanded = false
                            vm.onQueryChange("")
                            vm.clearNearby()
                            onPicked(station)
                        },
                    )
                }
            }

            // GPS nearby results — same visual treatment as typeahead, also non-focusable.
            // Disappears on selection or when the user starts typing.
            DropdownMenu(
                expanded = nearbyReady != null,
                onDismissRequest = { vm.clearNearby() },
                properties = PopupProperties(focusable = false),
            ) {
                nearbyReady?.stations?.forEach { station ->
                    DropdownMenuItem(
                        text = { Text(station.name) },
                        onClick = {
                            query = station.name
                            expanded = false
                            vm.onQueryChange("")
                            vm.clearNearby()
                            onPicked(station)
                        },
                    )
                }
            }
        }

        if (locationError) {
            Text(
                text = "Nu am putut obține locația.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        when (val n = nearby) {
            NearbyUiState.Idle -> Unit
            NearbyUiState.Loading -> LoadingState()
            is NearbyUiState.Ready -> Unit  // rendered as dropdown inside Box above
            is NearbyUiState.Error -> ErrorState(n.message)
        }
    }
}
