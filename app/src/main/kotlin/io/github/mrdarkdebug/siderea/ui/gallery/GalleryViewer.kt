package io.github.mrdarkdebug.siderea.ui.gallery

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.mrdarkdebug.siderea.core.ui.components.IconTarget
import io.github.mrdarkdebug.siderea.core.ui.components.KeyValueRow
import io.github.mrdarkdebug.siderea.core.ui.components.PillButton
import io.github.mrdarkdebug.siderea.core.ui.components.SideriaTopBar
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryViewer(
    photos: List<GalleryPhoto>,
    initialIndex: Int,
    repository: GalleryRepository,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    onEdit: (GalleryPhoto) -> Unit,
    onDelete: (GalleryPhoto) -> Unit,
    busy: Boolean = false,
) {
    BackHandler(onBack = onBack)
    val pager =
        rememberPagerState(initialPage = initialIndex.coerceIn(0, photos.lastIndex), pageCount = { photos.size })
    val photo = photos.getOrNull(pager.currentPage) ?: photos.last()
    val context = LocalContext.current
    var info by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SideriaTopBar(
            "${pager.currentPage.coerceAtMost(photos.lastIndex) + 1} / ${photos.size}",
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navigationDescription = "Back to gallery",
            onNavigationClick = onBack,
            actions = {
                IconTarget(Icons.Default.Info, "Photo details", { info = true })
            },
        )
        HorizontalPager(pager, modifier = Modifier.weight(1f), key = { photos[it].uri }) { index ->
            ZoomablePhoto(photos[index], repository)
        }
        Text(
            Instant.ofEpochMilli(photo.capturedAt).atZone(ZoneId.systemDefault()).format(TIME_FORMAT),
            color = Siderea.palette.onSurfaceMuted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillButton("Edit", { onEdit(photo) }, enabled = photo.editable && !busy, modifier = Modifier.weight(1f))
            PillButton(
                "Share",
                { photoIntent(context, photo, share = true) },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
            PillButton("Delete", { onDelete(photo) }, enabled = !busy, modifier = Modifier.weight(1f))
        }
    }
    if (info) {
        val metadata by produceState<Map<String, String>>(emptyMap(), photo.uri) { value = repository.details(photo) }
        ModalBottomSheet(onDismissRequest = { info = false }, containerColor = Siderea.palette.surface) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Photo details", style = MaterialTheme.typography.titleLarge)
                Text(photo.name, style = MaterialTheme.typography.bodyMedium, color = Siderea.palette.onSurfaceMuted)
                KeyValueRow(
                    "Taken",
                    Instant.ofEpochMilli(photo.capturedAt).atZone(ZoneId.systemDefault()).format(TIME_FORMAT),
                )
                KeyValueRow("Format", photo.name.substringAfterLast('.').uppercase(Locale.ROOT))
                KeyValueRow(
                    "Size",
                    android.text.format.Formatter
                        .formatShortFileSize(context, photo.bytes),
                )
                if (photo.width > 0) KeyValueRow("Dimensions", "${photo.width} × ${photo.height}")
                metadata.forEach { (key, value) -> KeyValueRow(key, value) }
                photo.sessionId?.let { id ->
                    PillButton("Open session", {
                        info = false
                        onOpenSession(id)
                    })
                }
                PillButton(
                    "Open in another app",
                    { photoIntent(context, photo, share = false) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.getDefault())
private const val MAX_ZOOM = 5f

@Composable
private fun ZoomablePhoto(
    photo: GalleryPhoto,
    repository: GalleryRepository,
) {
    val context = LocalContext.current
    val result by produceState<Pair<Bitmap?, Boolean>>(null to false, photo.uri) {
        value = repository.image(photo, full = true) to true
    }
    val bitmap = result.first
    var zoom by remember(photo.uri) { mutableFloatStateOf(1f) }
    var pan by remember(photo.uri) { mutableStateOf(Offset.Zero) }
    val transform =
        rememberTransformableState { scale, movement, _ ->
            zoom = (zoom * scale).coerceIn(1f, MAX_ZOOM)
            pan = if (zoom == 1f) Offset.Zero else pan + movement
        }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap!!.asImageBitmap(),
                "${photo.name}. Pinch to zoom.",
                modifier =
                    Modifier
                        .fillMaxSize()
                        .transformable(transform, canPan = { zoom > 1f })
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x.coerceIn(-size.width * (zoom - 1f) / 2, size.width * (zoom - 1f) / 2)
                            translationY = pan.y.coerceIn(-size.height * (zoom - 1f) / 2, size.height * (zoom - 1f) / 2)
                        },
            )
            if (zoom > 1f) {
                PillButton(
                    "Reset zoom",
                    {
                        zoom = 1f
                        pan = Offset.Zero
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
        } else if (!result.second) {
            CircularProgressIndicator(color = Siderea.palette.accent)
        } else {
            // RAW decoders vary by device. Keep the original accessible even when no preview is available.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Preview unavailable", color = Siderea.palette.onSurfaceMuted)
                PillButton("Open original", { photoIntent(context, photo, share = false) })
            }
        }
    }
}

internal fun photoIntent(
    context: Context,
    photo: GalleryPhoto,
    share: Boolean,
) {
    val uri = Uri.parse(photo.uri)
    val intent =
        Intent(if (share) Intent.ACTION_SEND else Intent.ACTION_VIEW).apply {
            if (share) {
                type = photo.mime
                putExtra(Intent.EXTRA_STREAM, uri)
            } else {
                setDataAndType(uri, photo.mime)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("Photo", uri)
        }
    try {
        context.startActivity(if (share) Intent.createChooser(intent, "Share photo") else intent)
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this image format.", Toast.LENGTH_LONG).show()
    }
}

internal fun sharePhotos(
    context: Context,
    photos: List<GalleryPhoto>,
) {
    if (photos.isEmpty()) return
    if (photos.size == 1) return photoIntent(context, photos.single(), share = true)
    val uris = ArrayList(photos.map { Uri.parse(it.uri) })
    val intent =
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData =
                android.content.ClipData.newRawUri("Photos", uris.first()).apply {
                    uris.drop(1).forEach { addItem(android.content.ClipData.Item(it)) }
                }
        }
    try {
        context.startActivity(Intent.createChooser(intent, "Share photos"))
    } catch (
        _: android.content.ActivityNotFoundException,
    ) {
        Toast.makeText(context, "No app can share these images.", Toast.LENGTH_LONG).show()
    }
}
