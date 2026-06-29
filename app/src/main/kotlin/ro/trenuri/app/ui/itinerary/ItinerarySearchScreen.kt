package ro.trenuri.app.ui.itinerary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import ro.trenuri.app.ui.DelayBanner
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.app.ui.common.DatePickerField
import ro.trenuri.app.ui.common.EmptyState
import ro.trenuri.app.ui.common.ErrorState
import ro.trenuri.app.ui.common.LoadingState
import ro.trenuri.app.ui.common.Today
import ro.trenuri.app.ui.delayBannerOf
import ro.trenuri.app.ui.history.QueryHistoryStore
import ro.trenuri.app.ui.history.RouteQuery
import ro.trenuri.app.ui.station.StationPickerField
import ro.trenuri.infofer.model.ItineraryLeg
import ro.trenuri.infofer.model.ItineraryOption

/** Returns the day-separator label: "Azi", "Mâine", or the formatted date. */
private fun dayLabel(date: AppDate, todayDate: AppDate): String = when (date) {
    todayDate -> "Azi"
    todayDate.nextDay() -> "Mâine"
    else -> date.format()
}

/** Number of day-sections to eagerly auto-load when content doesn't fill the viewport. */
private const val EAGER_AUTO_LOAD_DAYS = 2

@Composable
fun ItinerarySearchScreen(
    vm: ItineraryViewModel,
    date: AppDate,
    onDateChange: (AppDate) -> Unit,
    onTrainClick: (String) -> Unit,
    historyStore: QueryHistoryStore<RouteQuery> = koinInject(qualifier = named("history_rute")),
    todayProvider: Today = koinInject(qualifier = named("today")),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val loadedFrom by vm.loadedFrom.collectAsStateWithLifecycle()
    val loadedTo by vm.loadedTo.collectAsStateWithLifecycle()
    var from by remember { mutableStateOf(vm.loadedFrom.value) }
    var to by remember { mutableStateOf(vm.loadedTo.value) }
    var recentItems by remember { mutableStateOf(historyStore.recent()) }
    LaunchedEffect(loadedFrom) { loadedFrom?.let { from = it } }
    LaunchedEffect(loadedTo) { loadedTo?.let { to = it } }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val listState = rememberLazyListState()

    // ── scroll-to-bottom lazy load trigger (day 3+) ───────────────────────────
    val nearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            val total = info.totalItemsCount
            visible.isNotEmpty() && total > 0 && visible.last().index >= total - 3
        }
    }

    // ── eager auto-fill trigger (days 1-2, when content doesn't fill viewport) ─
    val contentDoesNotFillViewport by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) return@derivedStateOf false
            val lastVisible = visible.last().index
            val total = info.totalItemsCount
            val contentHeight = visible.sumOf { it.size }
            val viewportHeight = info.viewportEndOffset - info.viewportStartOffset
            lastVisible == total - 1 && contentHeight <= viewportHeight
        }
    }

    // Lazy load: trigger when near the bottom (scroll-driven, all days beyond cap)
    LaunchedEffect(nearBottom) {
        val s = state as? ItineraryUiState.Success ?: return@LaunchedEffect
        if (nearBottom && s.canLoadMore && !s.loadingMore) {
            vm.loadMore()
        }
    }

    // Eager auto-fill: when sections < EAGER_AUTO_LOAD_DAYS and content doesn't fill screen
    LaunchedEffect(state, contentDoesNotFillViewport) {
        val s = state as? ItineraryUiState.Success ?: return@LaunchedEffect
        if (
            s.canLoadMore &&
            !s.loadingMore &&
            s.sections.size < EAGER_AUTO_LOAD_DAYS &&
            contentDoesNotFillViewport
        ) {
            vm.loadMore()
        }
    }

    val todayDate = remember { todayProvider() }

    LazyColumn(
        state = listState,
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
                    keyboardController?.hide()
                    focusManager.clearFocus()
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
            is ItineraryUiState.Success -> {
                s.sections.forEach { day ->
                    // Day separator header
                    item(key = "header_${day.date.year}_${day.date.month}_${day.date.day}") {
                        Text(
                            text = dayLabel(day.date, todayDate),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    if (day.options.isEmpty()) {
                        item(key = "empty_${day.date.year}_${day.date.month}_${day.date.day}") {
                            Text(
                                text = "Nicio rută disponibilă.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = day.options,
                            // Index disambiguates options that share departure+arrival time on the
                            // same day (otherwise duplicate LazyColumn keys crash on debug builds).
                            key = { index, _ -> "${day.date.year}_${day.date.month}_${day.date.day}_$index" },
                        ) { _, option ->
                            ItineraryOptionCard(option, onTrainClick)
                        }
                    }
                }

                // Load-more sentinel / spinner
                item(key = "load_more_sentinel") {
                    when {
                        s.loadingMore ->
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        !s.canLoadMore -> { /* nothing — reached cap */ }
                        else -> { /* nothing — lazy scroll triggers loadMore */ }
                    }
                }
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
        Column(
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
    Column {
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
        when (val banner = delayBannerOf(leg.delay)) {
            is DelayBanner.NoLiveData -> Text(
                text = "fără date live",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is DelayBanner.OnTime -> Text(
                text = "la timp",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF2E7D32),
            )
            is DelayBanner.Delayed -> Text(
                text = buildString {
                    append("${banner.minutes} min întârziere")
                    if (banner.reportedAt != null) append(" · raportat la ${banner.reportedAt}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
