package ro.trenuri.app.ui.train

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import ro.trenuri.app.ui.DelayBanner
import ro.trenuri.app.ui.TrainUiState
import ro.trenuri.app.ui.TrainViewModel
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.app.ui.common.DatePickerField
import ro.trenuri.app.ui.delayBannerOf
import ro.trenuri.app.ui.history.QueryHistoryStore
import ro.trenuri.app.ui.history.TrainQuery
import ro.trenuri.infofer.model.Station
import ro.trenuri.infofer.model.StopStatus
import ro.trenuri.infofer.model.TrainBranch
import ro.trenuri.infofer.model.TrainItinerary
import ro.trenuri.infofer.model.TrainStop

private val Green = Color(0xFF1B5E20)
private val Red = Color(0xFFB71C1C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainDetailScreen(
    viewModel: TrainViewModel,
    date: AppDate,
    onDateChange: (AppDate) -> Unit,
    onStationClick: (Station) -> Unit,
    historyStore: QueryHistoryStore<TrainQuery> = koinInject(qualifier = named("history_tren")),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadedNumber by viewModel.loadedNumber.collectAsStateWithLifecycle()
    var number by remember { mutableStateOf(viewModel.loadedNumber.value) }
    var recentItems by remember { mutableStateOf(historyStore.recent()) }
    LaunchedEffect(loadedNumber) { if (loadedNumber.isNotBlank()) number = loadedNumber }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DatePickerField(
            date = date,
            onDateChange = onDateChange,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                label = { Text("Număr tren") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Button(onClick = {
                val trimmed = number.trim()
                viewModel.load(trimmed, date)
                if (trimmed.isNotBlank()) {
                    historyStore.add(TrainQuery(trimmed))
                    recentItems = historyStore.recent()
                }
            }) { Text("Caută") }
        }

        if (recentItems.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Recente", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    recentItems.forEach { entry ->
                        AssistChip(
                            onClick = { number = entry.number },
                            label = { Text(entry.number) },
                        )
                    }
                }
            }
        }

        when (val s = state) {
            TrainUiState.Idle -> Text("Introdu un număr de tren.")
            TrainUiState.Loading -> CircularProgressIndicator()
            TrainUiState.Empty -> Text("Trenul nu a fost găsit sau nu are date.")
            is TrainUiState.Error -> Text(s.message, color = Red)
            is TrainUiState.Success -> TrainItineraryView(s.itinerary, onStationClick)
        }
    }
}

@Composable
private fun TrainItineraryView(
    itinerary: TrainItinerary,
    onStationClick: (Station) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "${itinerary.category} ${itinerary.trainNumber}",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        itinerary.branches.forEach { branch ->
            item { BranchHeader(branch) }
            items(branch.stops) { stop -> StopRow(stop, onStationClick) }
        }
    }
}

@Composable
private fun BranchHeader(branch: TrainBranch) {
    Column {
        Text("${branch.from} – ${branch.to}", fontWeight = FontWeight.SemiBold)
        when (val banner = delayBannerOf(branch.delay)) {
            DelayBanner.NoLiveData -> Text("Fără date live")
            DelayBanner.OnTime -> Text("La timp", color = Green)
            is DelayBanner.Delayed -> {
                val reported = banner.reportedAt?.let { " · raportat la $it" } ?: ""
                Text("${banner.minutes} min întârziere$reported", color = Red)
            }
        }
    }
}

@Composable
private fun StopRow(stop: TrainStop, onStationClick: (Station) -> Unit) {
    val times = listOfNotNull(stop.arrival, stop.departure).joinToString(" / ")
    val details = buildList {
        stop.track?.let { add("linia $it") }
        stop.km?.let { add("km $it") }
    }.joinToString("  ")
    val statusColor = when (stop.status) {
        StopStatus.ON_TIME -> Green
        StopStatus.DELAYED -> Red
        StopStatus.UNKNOWN -> Color.Unspecified
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(times, color = statusColor)
        Text(
            text = stop.station.name + if (details.isNotEmpty()) "  $details" else "",
            modifier = if (stop.station.slug.isNotBlank()) {
                Modifier.weight(1f).clickable { onStationClick(stop.station) }
            } else {
                Modifier.weight(1f)
            },
        )
    }
}
