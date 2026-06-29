package ro.trenuri.app.ui.itinerary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.app.ui.common.DatePickerField
import ro.trenuri.app.ui.common.EmptyState
import ro.trenuri.app.ui.common.ErrorState
import ro.trenuri.app.ui.common.LoadingState
import ro.trenuri.app.ui.history.QueryHistoryStore
import ro.trenuri.app.ui.history.RouteQuery
import ro.trenuri.app.ui.station.StationPickerField
import ro.trenuri.infofer.model.ItineraryLeg
import ro.trenuri.infofer.model.ItineraryOption
import ro.trenuri.infofer.model.Station

@Composable
fun ItinerarySearchScreen(
    vm: ItineraryViewModel,
    date: AppDate,
    onDateChange: (AppDate) -> Unit,
    onTrainClick: (String) -> Unit,
    historyStore: QueryHistoryStore<RouteQuery> = koinInject(qualifier = named("history_rute")),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val loadedFrom by vm.loadedFrom.collectAsStateWithLifecycle()
    val loadedTo by vm.loadedTo.collectAsStateWithLifecycle()
    var from by remember { mutableStateOf(vm.loadedFrom.value) }
    var to by remember { mutableStateOf(vm.loadedTo.value) }
    var recentItems by remember { mutableStateOf(historyStore.recent()) }
    LaunchedEffect(loadedFrom) { loadedFrom?.let { from = it } }
    LaunchedEffect(loadedTo) { loadedTo?.let { to = it } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            StationPickerField(
                label = "De la",
                onPicked = { from = it },
                modifier = Modifier.padding(top = 16.dp),
                selected = from,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StationPickerField(
                    label = "Până la",
                    onPicked = { to = it },
                    modifier = Modifier.weight(1f),
                    selected = to,
                )
                TextButton(onClick = {
                    val temp = from
                    from = to
                    to = temp
                }) {
                    Text("⇄")
                }
            }
        }
        item {
            DatePickerField(
                date = date,
                onDateChange = onDateChange,
            )
        }
        item {
            Button(
                onClick = {
                    val f = from
                    val t = to
                    if (f != null && t != null) {
                        vm.search(f, t, date)
                        historyStore.add(RouteQuery(f, t))
                        recentItems = historyStore.recent()
                    }
                },
                enabled = from != null && to != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Caută")
            }
        }

        if (recentItems.isNotEmpty()) {
            item {
                Text("Recente", style = MaterialTheme.typography.labelSmall)
            }
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    recentItems.forEach { entry ->
                        AssistChip(
                            onClick = {
                                from = entry.from
                                to = entry.to
                            },
                            label = { Text("${entry.from.name} → ${entry.to.name}") },
                        )
                    }
                }
            }
        }

        when (val s = state) {
            ItineraryUiState.Idle ->
                item { EmptyState("Alege stațiile de plecare și sosire.") }
            ItineraryUiState.Loading ->
                item { LoadingState() }
            ItineraryUiState.Empty ->
                item { EmptyState("Nu există rute disponibile.") }
            is ItineraryUiState.Error ->
                item { ErrorState(s.message) }
            is ItineraryUiState.Success ->
                items(s.options) { option ->
                    ItineraryOptionCard(option, onTrainClick)
                }
        }

        item { /* bottom padding */ }
    }
}

@Composable
private fun ItineraryOptionCard(
    option: ItineraryOption,
    onTrainClick: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "${option.departureTime} → ${option.arrivalTime}",
                style = MaterialTheme.typography.titleMedium,
            )
            val durationText = option.durationMinutes?.let { m -> "${m / 60}h ${m % 60}m" }
            val changesText = if (option.changes == 0) "direct" else "${option.changes} schimbări"
            val infoText = listOfNotNull(durationText, changesText).joinToString("  ·  ")
            if (infoText.isNotEmpty()) {
                Text(infoText, style = MaterialTheme.typography.bodySmall)
            }

            if (option.legs.isNotEmpty()) {
                HorizontalDivider()
                option.legs.forEach { leg -> LegRow(leg, onTrainClick) }
            }
        }
    }
}

@Composable
private fun LegRow(leg: ItineraryLeg, onTrainClick: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = leg.category.name,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(
            text = leg.trainNumber,
            modifier = Modifier.clickable { onTrainClick(leg.trainNumber) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "${leg.departureStation} ${leg.departureTime} → ${leg.arrivalStation} ${leg.arrivalTime}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}
