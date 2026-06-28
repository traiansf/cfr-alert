package ro.trenuri.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ro.trenuri.infofer.model.StopStatus
import ro.trenuri.infofer.model.TrainBranch
import ro.trenuri.infofer.model.TrainItinerary
import ro.trenuri.infofer.model.TrainStop

private val Green = Color(0xFF1B5E20)
private val Red = Color(0xFFB71C1C)

@Composable
fun TrainDetailScreen(viewModel: TrainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var number by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                label = { Text("Număr tren") },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { viewModel.search(number) }) { Text("Caută") }
        }

        when (val s = state) {
            TrainUiState.Idle -> Text("Introdu un număr de tren.")
            TrainUiState.Loading -> CircularProgressIndicator()
            TrainUiState.Empty -> Text("Trenul nu a fost găsit sau nu are date.")
            is TrainUiState.Error -> Text(s.message, color = Red)
            is TrainUiState.Success -> TrainItineraryView(s.itinerary)
        }
    }
}

@Composable
private fun TrainItineraryView(itinerary: TrainItinerary) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "${itinerary.category} ${itinerary.trainNumber}",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        itinerary.branches.forEach { branch ->
            item { BranchHeader(branch) }
            items(branch.stops) { stop -> StopRow(stop) }
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
private fun StopRow(stop: TrainStop) {
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
            modifier = Modifier.weight(1f),
        )
    }
}
