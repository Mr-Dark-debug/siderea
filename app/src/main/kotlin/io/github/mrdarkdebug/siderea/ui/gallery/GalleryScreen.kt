package io.github.mrdarkdebug.siderea.ui.gallery

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mrdarkdebug.siderea.core.ui.components.ChipButton
import io.github.mrdarkdebug.siderea.core.ui.components.IconTarget
import io.github.mrdarkdebug.siderea.core.ui.components.PillButton
import io.github.mrdarkdebug.siderea.core.ui.components.PillStyle
import io.github.mrdarkdebug.siderea.core.ui.components.SideriaTopBar
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import io.github.mrdarkdebug.siderea.core.ui.theme.SideriaShapes
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var calendar by rememberSaveable { mutableStateOf(false) }
    var dayString by rememberSaveable { mutableStateOf<String?>(null) }
    var monthString by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var viewing by remember { mutableStateOf<List<GalleryPhoto>?>(null) }
    var initialIndex by remember { mutableIntStateOf(0) }
    val days = remember(state.photos) { galleryDays(state.photos) }
    val day = dayString?.let(LocalDate::parse)
    val visible = remember(state.photos, day) { state.photos.filter { day == null || it.date() == day } }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    if (viewing != null) {
        GalleryViewer(
            viewing.orEmpty(),
            initialIndex,
            viewModel.repository,
            onBack = { viewing = null },
            onOpenSession = onOpenSession,
        )
        return
    }
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SideriaTopBar(
            "Gallery",
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navigationDescription = "Back to camera",
            onNavigationClick = onBack,
            actions = {
                IconTarget(Icons.Default.Refresh, "Refresh gallery", viewModel::refresh)
                IconTarget(Icons.Default.CalendarMonth, "Show calendar", { calendar = !calendar })
            },
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (day == null) "Your moments" else day.format(DAY_FORMAT),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Siderea.palette.onBackground,
                )
                Text(
                    "${visible.size} images",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Siderea.palette.onSurfaceMuted,
                )
            }
            if (day != null) ChipButton("All dates", { dayString = null })
        }
        if (calendar) {
            GalleryCalendar(
                YearMonth.parse(monthString),
                days.keys,
                day,
                onMonth = { monthString = it.toString() },
                onDate = { dayString = it.toString() },
            )
        }
        when {
            state.error != null -> {
                GalleryMessage(state.error.orEmpty(), "Try again", viewModel::refresh)
            }

            state.loading && state.photos.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Siderea.palette.accent)
                }
            }

            visible.isEmpty() -> {
                GalleryMessage(
                    if (day == null) "Your photos will appear here" else "No photos on this date",
                    if (day == null) "Take a photo" else "Show all photos",
                    if (day == null) {
                        onBack
                    } else {
                        { dayString = null }
                    },
                )
            }

            else -> {
                LazyVerticalGrid(
                    GridCells.Adaptive(100.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    var offset = 0
                    galleryDays(visible).forEach { (date, photos) ->
                        val groupOffset = offset
                        item(key = "date-$date", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                dateHeading(date),
                                style = MaterialTheme.typography.titleMedium,
                                color = Siderea.palette.onBackground,
                                modifier = Modifier.padding(top = 20.dp, bottom = 10.dp, start = 4.dp),
                            )
                        }
                        itemsIndexed(photos, key = { _, photo -> photo.uri }) { index, photo ->
                            GalleryThumbnail(photo, viewModel.repository) {
                                initialIndex = groupOffset + index
                                viewing = visible
                            }
                        }
                        offset += photos.size
                    }
                }
            }
        }
    }
}

private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

private fun dateHeading(date: LocalDate): String =
    when (date) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> date.format(DAY_FORMAT)
    }

@Composable
private fun GalleryMessage(
    message: String,
    action: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.PhotoLibrary, null, tint = Siderea.palette.onSurfaceMuted, modifier = Modifier.size(48.dp))
        Text(message, color = Siderea.palette.onSurfaceMuted, modifier = Modifier.padding(vertical = 20.dp))
        PillButton(action, onClick, style = PillStyle.Outlined)
    }
}

@Composable
private fun GalleryThumbnail(
    photo: GalleryPhoto,
    repository: GalleryRepository,
    onClick: () -> Unit,
) {
    val bitmap by produceState<Bitmap?>(null, photo.uri) { value = repository.image(photo) }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(SideriaShapes.small)
            .background(Siderea.palette.surfaceRaised)
            .semantics { contentDescription = "${photo.name}, ${photo.date()}" }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        if (bitmap != null) {
            Image(
                bitmap!!.asImageBitmap(),
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Default.PhotoLibrary,
                null,
                tint = Siderea.palette.onSurfaceMuted,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (photo.mime != "image/jpeg") {
            Text(
                photo.name.substringAfterLast('.').uppercase(Locale.ROOT),
                style = Siderea.text.caption,
                color = Siderea.palette.onBackground,
                modifier = Modifier.align(Alignment.BottomStart).background(Siderea.palette.background).padding(6.dp),
            )
        }
    }
}

@Composable
private fun GalleryCalendar(
    month: YearMonth,
    dates: Set<LocalDate>,
    selectedDay: LocalDate?,
    onMonth: (YearMonth) -> Unit,
    onDate: (LocalDate) -> Unit,
) {
    BoxWithConstraints(Modifier.padding(horizontal = 16.dp)) {
        Column(Modifier.horizontalScroll(rememberScrollState()).width(maxOf(maxWidth, 336.dp))) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconTarget(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    "Previous month",
                    { onMonth(month.minusMonths(1)) },
                )
                Text(
                    month.format(MONTH_FORMAT),
                    color = Siderea.palette.onBackground,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconTarget(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month", { onMonth(month.plusMonths(1)) })
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(it, color = Siderea.palette.onSurfaceMuted, style = Siderea.text.caption)
                    }
                }
            }
            calendarCells(month).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    (week + List(7 - week.size) { null }).forEach { date ->
                        Column(
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(SideriaShapes.small)
                                .background(
                                    if (date != null && date == selectedDay) {
                                        Siderea.palette.accentContainer
                                    } else {
                                        Siderea.palette.background
                                    },
                                ).semantics {
                                    if (date != null) {
                                        contentDescription =
                                            "$date${if (date in dates) ", has photos" else ", no photos"}"
                                        selected = date == selectedDay
                                    }
                                }.then(
                                    if (date !=
                                        null
                                    ) {
                                        Modifier.clickable(role = Role.Button) { onDate(date) }
                                    } else {
                                        Modifier
                                    },
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            if (date != null) {
                                Text(date.dayOfMonth.toString(), color = Siderea.palette.onBackground)
                                Box(
                                    Modifier
                                        .size(4.dp)
                                        .clip(SideriaShapes.pill)
                                        .background(
                                            if (date in
                                                dates
                                            ) {
                                                Siderea.palette.accent
                                            } else {
                                                Siderea.palette.background
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
