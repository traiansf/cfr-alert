package ro.trenuri.app.ui.station

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import ro.trenuri.app.ui.common.ErrorState
import ro.trenuri.app.ui.common.LoadingState
import ro.trenuri.infofer.model.Station

@OptIn(ExperimentalMaterial3Api::class)
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

    LaunchedEffect(selected) {
        query = selected?.name ?: ""
    }

    Column(modifier = modifier) {
        ExposedDropdownMenuBox(
            expanded = expanded && suggestions.isNotEmpty(),
            onExpandedChange = { if (!it) expanded = false },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { v ->
                    query = v
                    vm.onQueryChange(v)
                    expanded = true
                },
                label = { Text(label) },
                trailingIcon = {
                    IconButton(onClick = requestLocation) {
                        Icon(Icons.Default.LocationOn, contentDescription = "Locație")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryEditable),
                singleLine = true,
            )
            DropdownMenu(
                expanded = expanded && suggestions.isNotEmpty(),
                onDismissRequest = { expanded = false },
            ) {
                suggestions.forEach { station ->
                    DropdownMenuItem(
                        text = { Text(station.name) },
                        onClick = {
                            query = station.name
                            expanded = false
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
            is NearbyUiState.Ready -> {
                n.stations.forEach { station ->
                    Text(
                        text = station.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                query = station.name
                                onPicked(station)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            is NearbyUiState.Error -> ErrorState(n.message)
        }
    }
}
