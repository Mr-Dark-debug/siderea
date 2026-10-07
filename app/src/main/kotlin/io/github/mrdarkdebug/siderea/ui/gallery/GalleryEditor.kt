package io.github.mrdarkdebug.siderea.ui.gallery

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mrdarkdebug.siderea.core.ui.components.ChipButton
import io.github.mrdarkdebug.siderea.core.ui.components.IconTarget
import io.github.mrdarkdebug.siderea.core.ui.components.PillButton
import io.github.mrdarkdebug.siderea.core.ui.components.PillStyle
import io.github.mrdarkdebug.siderea.core.ui.components.SideriaTopBar
import io.github.mrdarkdebug.siderea.core.ui.theme.Siderea
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class EditTool { CROP, LIGHT, COLOR }

@Composable
// Decoder, provider and storage failures all become a retryable error while retaining the original.
@Suppress("TooGenericExceptionCaught")
fun GalleryEditor(
    photo: GalleryPhoto,
    repository: GalleryRepository,
    onBack: () -> Unit,
    onSaved: (GalleryPhoto) -> Unit,
) {
    var edit by remember(photo.uri) { mutableStateOf(PhotoEdit()) }
    var tool by remember { mutableStateOf(EditTool.CROP) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val original by produceState<Pair<Bitmap?, Boolean>>(null to false, photo.uri) {
        value = repository.image(photo, full = true) to true
    }
    val preview by produceState<Bitmap?>(null, original.first, edit) {
        delay(PREVIEW_DEBOUNCE_MS)
        original.first?.let { source ->
            value = withContext(Dispatchers.Default) { PhotoRenderer.render(source, edit) }
        }
    }
    val back = {
        if (!saving) {
            if (edit == PhotoEdit()) onBack() else discard = true
        }
    }
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SideriaTopBar(
            "Edit photo",
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navigationDescription = "Cancel editing",
            onNavigationClick = back,
            actions = { ChipButton("Reset", { if (!saving) edit = PhotoEdit() }, description = "Reset edits") },
        )
        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            preview?.let { bitmap ->
                Image(
                    bitmap.asImageBitmap(),
                    "Edited photo preview",
                    Modifier.fillMaxSize().pointerInput(tool, saving) {
                        if (tool == EditTool.CROP && !saving) {
                            detectDragGestures { change, movement ->
                                change.consume()
                                edit =
                                    edit.copy(
                                        positionX = (edit.positionX - movement.x / size.width * 2).coerceIn(-1f, 1f),
                                        positionY = (edit.positionY - movement.y / size.height * 2).coerceIn(-1f, 1f),
                                    )
                            }
                        }
                    },
                )
            } ?: if (!original.second) CircularProgressIndicator() else Text("This format cannot be previewed.")
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(
                    max = 300.dp,
                ).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditTool.entries.forEach { option ->
                    ChipButton(
                        option.name.lowercase().replaceFirstChar { it.uppercase() },
                        { if (!saving) tool = option },
                        selected = tool == option,
                    )
                }
            }
            when (tool) {
                EditTool.CROP -> {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTarget(Icons.Default.RotateRight, "Rotate photo", {
                            if (!saving) {
                                edit =
                                    edit.copy(turns = (edit.turns + 1) % 4)
                            }
                        })
                        IconTarget(Icons.Default.Flip, "Mirror photo", {
                            if (!saving) {
                                edit =
                                    edit.copy(mirror = !edit.mirror)
                            }
                        })
                        CropAspect.entries.forEach { aspect ->
                            ChipButton(aspect.label, {
                                if (!saving) {
                                    edit =
                                        edit.copy(crop = aspect, positionX = 0f, positionY = 0f)
                                }
                            }, selected = edit.crop == aspect)
                        }
                    }
                    EditSlider("Crop zoom · Drag photo to position", edit.zoom, 1f..3f, !saving) {
                        edit =
                            edit.copy(zoom = it)
                    }
                }

                EditTool.LIGHT -> {
                    EditSlider(
                        "Brightness",
                        edit.brightness,
                        -0.5f..0.5f,
                        !saving,
                    ) { edit = edit.copy(brightness = it) }
                    EditSlider("Contrast", edit.contrast, 0.5f..1.5f, !saving) { edit = edit.copy(contrast = it) }
                }

                EditTool.COLOR -> {
                    EditSlider("Saturation", edit.saturation, 0f..2f, !saving) { edit = edit.copy(saturation = it) }
                    EditSlider("Warmth", edit.warmth, -1f..1f, !saving) { edit = edit.copy(warmth = it) }
                }
            }
            error?.let { Text(it, color = Siderea.palette.accent) }
        }
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PillButton(
                if (saving) "Saving…" else "Save copy",
                {
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            onSaved(repository.saveCopy(photo, edit))
                        } catch (
                            e: CancellationException,
                        ) {
                            throw e
                        } catch (
                            e: Exception,
                        ) {
                            error = e.message ?: "Couldn't save. Try again."
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = !saving && original.first != null,
                style = PillStyle.Filled,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Original kept · JPEG copy up to 8 MP",
                style = Siderea.text.caption,
                color = Siderea.palette.onSurfaceMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    if (discard) {
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text("Discard edits?") },
            confirmButton = { TextButton(onClick = onBack) { Text("Discard") } },
            dismissButton = {
                TextButton(onClick = { discard = false }) { Text("Keep editing") }
            },
            containerColor = Siderea.palette.surface,
        )
    }
}

@Composable
private fun EditSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    Text(label, style = Siderea.text.caption, color = Siderea.palette.onSurfaceMuted)
    Slider(
        value,
        onChange,
        enabled = enabled,
        valueRange = range,
        modifier =
            Modifier.fillMaxWidth().semantics {
                contentDescription =
                    label
            },
    )
}

private const val PREVIEW_DEBOUNCE_MS = 60L
