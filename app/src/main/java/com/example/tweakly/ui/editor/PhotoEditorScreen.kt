package com.example.tweakly.ui.editor

import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    mediaUri: String,
    mediaName: String,
    onBack: () -> Unit,
    vm: PhotoEditorViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    var showTextDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var currentPath by remember { mutableStateOf<List<Pair<Float,Float>>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize(1, 1)) }

    LaunchedEffect(mediaUri) { vm.loadImage(ctx, mediaUri) }

    LaunchedEffect(state.savedUri) {
        if (state.savedUri != null) { vm.clearSaved(); onBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактор") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.Close, "Закрыть") } },
                actions = {
                    IconButton({ vm.undo() }, enabled = state.undoStack.isNotEmpty()) {
                        Icon(Icons.Default.Undo, "Отменить")
                    }
                    IconButton({ vm.autoCorrect() }) { Icon(Icons.Default.AutoFixHigh, "Авто") }
                    IconButton({ vm.save(ctx, mediaName) }, enabled = !state.isSaving) {
                        if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Save, "Сохранить")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // ── Canvas ────────────────────────────────────────────────────────
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)
                .onSizeChanged { canvasSize = it }) {
                state.displayBitmap?.let { bmp ->
                    val drawMod = if (state.isDrawMode) {
                        Modifier.pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentPath = listOf(offset.x / canvasSize.width.toFloat() to offset.y / canvasSize.height.toFloat())
                                },
                                onDrag = { change, _ ->
                                    currentPath = currentPath + (change.position.x / canvasSize.width.toFloat() to change.position.y / canvasSize.height.toFloat())
                                },
                                onDragEnd = {
                                    if (currentPath.size > 1) vm.addDrawPath(DrawPath(currentPath, state.drawColor, state.drawStroke))
                                    currentPath = emptyList()
                                }
                            )
                        }
                    } else Modifier

                    Canvas(Modifier.fillMaxSize().then(drawMod)) {
                        drawImage(bmp.asImageBitmap(), dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()))
                        // Draw current stroke preview
                        if (currentPath.size > 1) {
                            val path = Path()
                            currentPath.forEachIndexed { i, (x, y) ->
                                if (i == 0) path.moveTo(x * size.width, y * size.height)
                                else path.lineTo(x * size.width, y * size.height)
                            }
                            drawPath(path, color = state.drawColor, style = Stroke(width = state.drawStroke))
                        }
                    }
                } ?: if (state.isLoading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                }
            }

            // ── Filters row ───────────────────────────────────────────────────
            LazyRow(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(EditorFilter.values().toList()) { filter ->
                    val selected = state.filter == filter
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { vm.setFilter(filter) }) {
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                            .border(if (selected) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)) {
                            Icon(Icons.Default.Image, null, Modifier.align(Alignment.Center),
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(filter.label, style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Tools row ─────────────────────────────────────────────────────
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                ToolButton(Icons.Default.RotateLeft, "Лево") { vm.rotate(-90f) }
                ToolButton(Icons.Default.RotateRight, "Право") { vm.rotate(90f) }
                ToolButton(Icons.Default.Brush, "Рисовать", state.isDrawMode) { vm.toggleDraw() }
                ToolButton(Icons.Default.Title, "Текст", state.isTextMode) { vm.toggleText(); if (!state.isTextMode) showTextDialog = true }
                // Draw color picker
                Box(Modifier.size(40.dp).clip(CircleShape)
                    .background(state.drawColor)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable {
                        // Cycle through colors
                        val colors = listOf(Color.Red, Color.Blue, Color.Green, Color.Yellow, Color.White, Color.Black)
                        val idx = colors.indexOf(state.drawColor)
                        vm.setDrawColor(colors[(idx + 1) % colors.size])
                    })
            }

            // Brush size slider (only in draw mode)
            if (state.isDrawMode) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LineWeight, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Slider(value = state.drawStroke, onValueChange = { vm.setDrawStroke(it) },
                        valueRange = 2f..30f, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    // Text input dialog
    if (showTextDialog) {
        AlertDialog(onDismissRequest = { showTextDialog = false },
            title = { Text("Добавить текст") },
            text = {
                OutlinedTextField(value = textInput, onValueChange = { textInput = it },
                    label = { Text("Текст") }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton({
                    if (textInput.isNotBlank()) {
                        vm.addText(TextOverlay(textInput, 0.1f, 0.5f, state.drawColor, 5f))
                        textInput = ""
                    }
                    showTextDialog = false
                }) { Text("Добавить") }
            },
            dismissButton = { TextButton({ showTextDialog = false; textInput = "" }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)) {
        Icon(icon, label, Modifier.size(28.dp),
            tint = if (active) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
