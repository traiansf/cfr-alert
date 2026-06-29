package ro.trenuri.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.app.ui.train.TrainDetailScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    TrainDetailScreen(
                        viewModel = koinViewModel(),
                        today = {
                            val now = Clock.System.now()
                                .toLocalDateTime(TimeZone.currentSystemDefault())
                            AppDate(now.year, now.monthNumber, now.dayOfMonth)
                        },
                        onStationClick = {},
                    )
                }
            }
        }
    }
}
