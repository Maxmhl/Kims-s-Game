package de.max.gedaechtnis

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import de.max.gedaechtnis.core.AppRoot
import de.max.gedaechtnis.core.ForegroundState
import de.max.gedaechtnis.core.GedaechtnisTheme
import de.max.gedaechtnis.kim.engine.Balancing
import de.max.gedaechtnis.kim.ui.KimViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val kimVm: KimViewModel by viewModels()
    @Inject lateinit var foreground: ForegroundState
    @Inject lateinit var balancing: Balancing

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { GedaechtnisTheme { AppRoot(kimVm, balancing) } }
    }

    override fun onStart() {
        super.onStart()
        foreground.set(true)
        kimVm.onAppForeground()
    }

    override fun onStop() {
        kimVm.onAppBackground()
        foreground.set(false)
        super.onStop()
    }
}
