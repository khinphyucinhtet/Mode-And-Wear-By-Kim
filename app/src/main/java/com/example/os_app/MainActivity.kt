
package com.example.os_app

import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.os_app.screens.BluetoothScreen
import com.example.os_app.screens.HomeScreen
import com.example.os_app.screens.ReceiptsScreen
import com.example.os_app.screens.SplashScreen
import com.example.os_app.ui.theme.OS_AppTheme

private enum class AppScreen {
    SPLASH,
    HOME,
    BLUETOOTH,
    RECEIPTS
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            OS_AppTheme {
                var screen by rememberSaveable { mutableStateOf(AppScreen.SPLASH) }

                when (screen) {
                    AppScreen.SPLASH -> SplashScreen(onSplashFinished = { screen = AppScreen.HOME })
                    AppScreen.HOME -> HomeScreen(
                        onBluetoothClick = { screen = AppScreen.BLUETOOTH },
                        onReceiptsClick = { screen = AppScreen.RECEIPTS }
                    )
                    AppScreen.BLUETOOTH -> BluetoothScreen(onBackClick = { screen = AppScreen.HOME })
                    AppScreen.RECEIPTS -> ReceiptsScreen(onBackClick = { screen = AppScreen.HOME })
                }
            }
        }
    }
}
