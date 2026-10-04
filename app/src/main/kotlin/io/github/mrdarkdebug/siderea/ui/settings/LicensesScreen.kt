package io.github.mrdarkdebug.siderea.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import io.github.mrdarkdebug.siderea.R
import io.github.mrdarkdebug.siderea.core.ui.components.SideriaTopBar

/** Licenses of everything Siderea ships, generated at build time from the resolved dependencies. */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val libraries by produceLibraries(R.raw.aboutlibraries)
    Column(Modifier.fillMaxSize()) {
        SideriaTopBar(
            title = stringResource(R.string.licenses_title),
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navigationDescription = stringResource(R.string.action_back),
            onNavigationClick = onBack,
        )
        LibrariesContainer(libraries = libraries, modifier = Modifier.fillMaxSize())
    }
}
