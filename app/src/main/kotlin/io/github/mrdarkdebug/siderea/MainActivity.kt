package io.github.mrdarkdebug.siderea

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaTheme
import io.github.mrdarkdebug.siderea.ui.navigation.SideriaNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The UI is always dark, so system bar icons are always light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val current = settings
            if (current == null) {
                // First settings read is milliseconds away; show plain black rather than the wrong theme.
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black))
            } else {
                SideriaTheme(redMode = current.redMode, hapticsEnabled = current.hapticsEnabled) {
                    Box(Modifier.fillMaxSize().background(Siderea.palette.background)) {
                        SideriaNavHost()
                    }
                }
            }
        }
    }
}
