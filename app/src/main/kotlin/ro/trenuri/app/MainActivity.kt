package ro.trenuri.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import org.koin.androidx.compose.koinViewModel
import ro.trenuri.app.ui.TrainDetailScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    TrainDetailScreen(viewModel = koinViewModel())
                }
            }
        }
    }
}
